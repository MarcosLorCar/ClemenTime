"""Interpret rows from the ESI TV hall endpoint."""

import json
import re
from datetime import time
from pathlib import Path

from ...contract import FacultyKey, FacultySchedule, ParseFailure, Session
from .mapping import MappingResolver, remove_accents


def parse_time(value: str, *, is_end_time: bool = False) -> time:
    hours, minutes = map(int, value.split(":"))
    parsed = time(hours, minutes)
    if is_end_time and parsed.minute == 50:
        return time((parsed.hour + 1) % 24)
    return parsed


def parse_day(value: str) -> int:
    key = remove_accents(value.strip()).lower()
    days = {
        "lunes": 1, "monday": 1,
        "martes": 2, "tuesday": 2,
        "miercoles": 3, "wednesday": 3,
        "jueves": 4, "thursday": 4,
        "viernes": 5, "friday": 5,
        "sabado": 6, "domingo": 7,
    }
    if key not in days:
        raise ValueError(f"Unknown ESI day: {value!r}")
    return days[key]


def clean_subject_code(code: str) -> str:
    """Removes laboratory suffixes like '-L', '-Lab', '(L)' from subject codes."""
    if not code:
        return code
    return re.sub(r"(-L|\(L\)|-Lab|\.L|\.Lab)$", "", code.strip(), flags=re.IGNORECASE)


def sanitize_subject_and_classroom(raw_asig: str, raw_aula: str) -> tuple[str, str]:
    if not raw_asig:
        return "", raw_aula.strip()

    asig = raw_asig.strip()
    aula = raw_aula.strip()

    # 1. Catch "Pruebas de...", "Pruebas. ESI", or "Pruebas..."
    if re.search(r"pruebas", asig, re.IGNORECASE):
        room_match = re.search(
            r"(\d+\.\d+[\w\+\-\.\s]*|A\d\.\d+[\w\+\-\.\s]*|F\d\.\d+[\w\+\-\.\s]*|LD\d[\w\+\-\.\s]*)",
            asig,
            re.IGNORECASE,
        )
        if room_match and not aula:
            aula = room_match.group(1).strip()
        return "Pruebas de Progreso", aula

    # 2. Separate appended classroom code (e.g. "Algebra 0.04-Hedy" or "Algebra A1.1")
    room_match = re.search(r"\s+(\d+\.\d+[\w\+\-\.\s]*|[A-Z]\d\.\d+[\w\+\-\.\s]*|LD\d[\w\+\-\.\s]*)$", asig)
    if room_match:
        if not aula:
            aula = room_match.group(1).strip()
        asig = asig[: room_match.start()].strip()

    return asig, aula


def normalize_group_name(group: str) -> str:
    if not group:
        return ""
    g = group.strip()
    m = re.search(r"(\d+)\s*º?\s*([A-Za-z]+)", g)
    if m:
        year = m.group(1)
        spec = m.group(2).upper()
        spec_map = {
            "ING": "IC",
            "IS": "ISO",
            "TECNOL": "TI",
            "TECNOLOGIA": "TI",
            "TECNOLOGIAS": "TI",
            "COMP": "CO",
        }
        spec = spec_map.get(spec, spec)
        return f"{year}{spec}"
    return g


def sanitize_professor(prof_raw: str) -> str:
    if not prof_raw:
        return ""
    p = prof_raw.strip()
    p_lower = p.lower()
    room_or_event_keywords = [
        "pruebas", "conferencias", "univmayores", "grado",
        "turing", "babbage", "hedy", "lamarr", "boole", "neumann",
        "knuth", "dijkstra", "ritchie", "hopper", "lovelace", "aula",
        "charles", "esi", "minsky", "jobs", "berners", "lee", "cirac",
        "carmack", "gates", "ruiz", "shannon", "vonn",
    ]
    if any(kw in p_lower for kw in room_or_event_keywords):
        return ""
    return p


def normalize(payload: bytes, key: FacultyKey, mappings_path: Path, *, strict: bool) -> FacultySchedule | ParseFailure:
    try:
        entries = json.loads(payload.decode("utf-8"))
        if not isinstance(entries, list) or any(not isinstance(entry, dict) for entry in entries):
            return ParseFailure("Expected a JSON list of slot entries.")
        resolver = MappingResolver(mappings_path)
        sessions = normalize_entries(entries, resolver, interactive=not strict)
    except (AttributeError, EOFError, OSError, TypeError, ValueError) as error:
        return ParseFailure(f"Could not interpret ESI input: {error}")

    if resolver.unresolved:
        unresolved = "\n".join(
            f"    {category}: {code}"
            for category, codes in sorted(resolver.unresolved.items())
            for code in sorted(codes)
        )
        return ParseFailure(
            f"Unresolved mappings:\n{unresolved}\n"
            f"Add them to {mappings_path} or run without --strict to enter names interactively."
        )

    print(f"[ESI] Normalized {len(entries)} rows into {sum(map(len, sessions.values()))} unique slots.")
    return FacultySchedule(key, {period: tuple(slots) for period, slots in sessions.items()})


def normalize_entries(raw_entries: list[dict], resolver: MappingResolver, *, interactive: bool) -> dict[str, list[Session]]:
    sessions: dict[str, list[Session]] = {}
    seen_keys = set()

    for entry in raw_entries:
        raw_asig = (entry.get("asignatura") or "").strip()
        raw_aula = (entry.get("aula") or "").strip()

        clean_asig, clean_aula = sanitize_subject_and_classroom(raw_asig, raw_aula)

        # 1. Filter Universidad de Mayores
        if not clean_asig or "universidad de mayores" in clean_asig.lower() or "univmayores" in clean_asig.lower():
            continue

        sem_val = str(entry.get("cuatrimestre") or "1C").strip().upper()
        sem_key = "2C" if "2" in sem_val else "1C"

        is_lab = bool(entry.get("es_laboratorio", False))
        grupo_prac = (entry.get("grupo_practicas") or "").strip()
        slot_type = (entry.get("tipo") or "teoría").strip().lower()

        # 2. Strip lab suffix from subject code
        clean_code = clean_subject_code(clean_asig)
        is_lab_indicator = (
            bool(grupo_prac)
            or clean_asig != clean_code
            or clean_asig.endswith("-L")
            or clean_asig.endswith("-L.")
            or bool(re.search(r"\bLD\d?", clean_aula, re.IGNORECASE))
        )

        if is_lab_indicator:
            is_lab = True
            if slot_type != "evento":
                slot_type = "laboratorio"

        canonical_code, asig_norm = resolver.resolve_mapping(clean_code, "matters", interactive)

        prof_raw = sanitize_professor(entry.get("profesor") or "")
        prof = resolver.resolve_professors(prof_raw, interactive) if prof_raw else ""

        classroom = clean_aula
        day = parse_day(entry.get("dia", ""))
        group = normalize_group_name(entry.get("grupo") or "")

        # 3. Collapse replicated faculty-wide events (Pruebas de Progreso & Conferencias)
        if asig_norm in ["Pruebas de Progreso", "Conferencias", "PruebasProgreso"] or canonical_code in [
            "Pruebas de Progreso",
            "Conferencias",
            "PruebasProgreso",
        ]:
            group = "GENERAL"
            prof = ""
            slot_type = "evento"
            is_lab = False
            grupo_prac = ""
            if "pruebas" in asig_norm.lower() or "pruebas" in canonical_code.lower():
                canonical_code = "Pruebas de Progreso"
                asig_norm = "Pruebas de Progreso"
                classroom = clean_aula or "Charles Babbage - 0.02+3"
            elif asig_norm == "Conferencias" or canonical_code == "Conferencias":
                canonical_code = "Conferencias"
                asig_norm = "Conferencias"
                classroom = clean_aula or "Alan Turing - Salón de Actos"

        # 4. Format and round times (padding 8:30 -> 08:30 and rounding :50 -> :00)
        start_time = parse_time(entry.get("hora_inicio", ""), is_end_time=False)
        end_time = parse_time(entry.get("hora_fin", ""), is_end_time=True)

        # 5. Composite deduplication key
        # Retains group so shared multi-group classes are preserved for each attending group
        slot_key = (
            group,
            sem_key,
            day,
            start_time,
            end_time,
            asig_norm,
            slot_type,
            classroom,
            prof,
            is_lab,
            grupo_prac,
        )

        if slot_key not in seen_keys:
            seen_keys.add(slot_key)
            sessions.setdefault(sem_key, []).append(
                Session(canonical_code, asig_norm, group, day, start_time, end_time,
                        slot_type, classroom, prof, is_lab, grupo_prac)
            )

    return sessions
