#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
import unicodedata
from difflib import SequenceMatcher

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MAPPINGS_FILE = os.path.join(SCRIPT_DIR, "mappings.json")
DEFAULT_DIST_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "dist"))


# --- Mapping Utilities ---


def remove_accents(text: str) -> str:
    if not text:
        return ""
    nfkd_form = unicodedata.normalize("NFKD", text)
    return "".join([c for c in nfkd_form if not unicodedata.combining(c)])


def load_mappings() -> dict:
    if os.path.exists(MAPPINGS_FILE):
        with open(MAPPINGS_FILE, encoding="utf-8") as f:
            return json.load(f)
    return {"matters": {}, "professors": {}, "classrooms": {}}


def save_mappings(mappings: dict) -> None:
    with open(MAPPINGS_FILE, "w", encoding="utf-8") as f:
        json.dump(mappings, f, indent=2, ensure_ascii=False)


# Codes that fell through every matching strategy while running non-interactively.
# In --non-interactive mode they are passed through verbatim, which would ship raw codes
# like "JRGP" to users, so process_json_schedule() reports them and exits non-zero instead.
UNRESOLVED: dict[str, set[str]] = {}


def record_unresolved(code: str, category: str) -> None:
    UNRESOLVED.setdefault(category, set()).add(code)


def clean_subject_code(code: str) -> str:
    """Removes laboratory suffixes like '-L', '-Lab', '(L)' from subject codes."""
    if not code:
        return code
    return re.sub(r"(-L|\(L\)|-Lab|\.L|\.Lab)$", "", code.strip(), flags=re.IGNORECASE)


def resolve_mapping(
    code: str,
    category: str,
    mappings: dict,
    interactive: bool = True,
    track: bool = True,
) -> tuple[str, str]:
    if not code:
        return code, code

    if category == "classrooms":
        if re.match(r"^lab[-_\s]", remove_accents(code).lower()):
            return code, code
        room_match = re.match(r"^([A-Z0-9]+\.\d+|LD\d+)", code, re.IGNORECASE)
        if room_match:
            prefix = room_match.group(1).upper()
            category_map = mappings.get(category, {})
            for key, val in category_map.items():
                if val.upper().startswith(prefix) or key.upper().startswith(prefix):
                    return code, val

    category_map = mappings.get(category, {})

    if code in category_map:
        return code, category_map[code]

    for k, v in category_map.items():
        if v == code:
            return k, v

    norm_code = remove_accents(code).lower().strip(". ,")
    for key, val in category_map.items():
        if remove_accents(key).lower().strip(". ,") == norm_code or remove_accents(val).lower().strip(". ,") == norm_code:
            return key, val

    code_tokens = set(re.findall(r"\w+", norm_code))
    for key, val in category_map.items():
        key_norm = remove_accents(key).lower()
        val_norm = remove_accents(val).lower()
        if key_norm and re.search(rf"\b{re.escape(key_norm)}\b", norm_code):
            return key, val
        if val_norm and re.search(rf"\b{re.escape(val_norm)}\b", norm_code):
            return key, val
        key_words = re.findall(r"\w+", key_norm)
        val_words = re.findall(r"\w+", val_norm)
        if norm_code and len(norm_code) >= 3 and any(w.startswith(norm_code) for w in key_words + val_words):
            return key, val
        key_tokens = set(re.findall(r"\w+", key_norm))
        if key_tokens and key_tokens.issubset(code_tokens):
            return key, val

    best_key, best_val = None, None
    best_score = 0.0
    for key, val in category_map.items():
        score_key = SequenceMatcher(None, norm_code, remove_accents(key).lower()).ratio()
        score_val = SequenceMatcher(None, norm_code, remove_accents(val).lower()).ratio()
        max_score = max(score_key, score_val)
        if max_score > best_score:
            best_score = max_score
            best_key, best_val = key, val

    if best_score >= 0.8 and best_key is not None and best_val is not None:
        return best_key, best_val

    if not interactive:
        if track:
            record_unresolved(code, category)
        return code, code

    print(f"\n[?] Unknown {category[:-1]} found: '{code}'", flush=True)
    val = input(f"    Enter full name for '{code}' (or press Enter to use as is): ").strip()
    val = val if val else code
    category_map[code] = val
    save_mappings(mappings)
    return code, val


def get_mapping(
    code: str,
    category: str,
    mappings: dict,
    interactive: bool = True,
    track: bool = True,
) -> str:
    _, val = resolve_mapping(code, category, mappings, interactive, track)
    return val


def resolve_professors(prof_str: str, mappings: dict, interactive: bool) -> str:
    if not prof_str:
        return ""
    prof_str = prof_str.strip()
    # track=False on the speculative lookups below: they are probes, and only the final
    # call decides whether this professor really went unresolved.
    full_match = get_mapping(prof_str, "professors", mappings, interactive=False, track=False)
    if full_match != prof_str:
        return full_match

    # Probe matching dotted parts (e.g. 'David.V', 'Jesus.F') against professor mapping entries
    prof_map = mappings.get("professors", {})
    parts = [remove_accents(p).lower() for p in prof_str.split(".") if p.strip()]
    if parts:
        for key, val in prof_map.items():
            key_parts = [remove_accents(p).lower() for p in re.split(r"[\s\.]+", key) if p.strip()]
            val_parts = [remove_accents(p).lower() for p in re.split(r"[\s\.]+", val) if p.strip()]
            for target_parts in [key_parts, val_parts]:
                if len(parts) <= len(target_parts) and all(
                    target_parts[i].startswith(parts[i]) for i in range(len(parts))
                ):
                    return val

    tokens = prof_str.split()
    if len(tokens) > 1 and any("." in t for t in tokens):
        resolved = [
            get_mapping(token, "professors", mappings, interactive=False, track=False)
            for token in tokens
        ]
        if all(r != t for r, t in zip(resolved, tokens, strict=True)):
            return " ".join(resolved)
    return get_mapping(prof_str, "professors", mappings, interactive)


def format_time(time_str: str, is_end_time: bool = False) -> str:
    if not time_str:
        return time_str
    parts = time_str.split(":")
    if len(parts) == 2:
        try:
            hours = int(parts[0])
            minutes = int(parts[1])
            if is_end_time and minutes == 50:
                hours = (hours + 1) % 24
                minutes = 0
            return f"{hours:02d}:{minutes:02d}"
        except ValueError:
            pass
    return time_str


def normalize_spanish_day(day: str) -> str:
    if not day:
        return ""
    d = day.strip().capitalize()
    d_clean = remove_accents(d).lower()
    mapping = {
        "lunes": "Lunes",
        "martes": "Martes",
        "miercoles": "Miércoles",
        "jueves": "Jueves",
        "viernes": "Viernes",
        "sabado": "Sábado",
        "domingo": "Domingo",
        "monday": "Lunes",
        "tuesday": "Martes",
        "wednesday": "Miércoles",
        "thursday": "Jueves",
        "friday": "Viernes",
    }
    return mapping.get(d_clean, d)


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


def process_json_schedule(
    json_path: str,
    interactive: bool = True,
    output_dir: str = DEFAULT_DIST_DIR,
) -> dict[str, list[dict]]:
    """
    Parses a raw JSON schedule feed (such as from the ESI TV hall endpoint) into
    normalized, deduplicated flat slot lists split by semester.
    """
    with open(json_path, encoding="utf-8") as f:
        raw_entries = json.load(f)

    if not isinstance(raw_entries, list):
        print(f"[Error] Expected a JSON list of slot entries in {json_path}", file=sys.stderr)
        sys.exit(1)

    mappings = load_mappings()
    processed_slots = []
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

        canonical_code, asig_norm = resolve_mapping(clean_code, "matters", mappings, interactive)

        prof_raw = sanitize_professor(entry.get("profesor") or "")
        prof = resolve_professors(prof_raw, mappings, interactive) if prof_raw else ""

        classroom = clean_aula
        day_norm = normalize_spanish_day(entry.get("dia", ""))
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
        start_time = format_time(entry.get("hora_inicio", ""), is_end_time=False)
        end_time = format_time(entry.get("hora_fin", ""), is_end_time=True)

        # 5. Composite deduplication key
        # Retains group so shared multi-group classes are preserved for each attending group
        slot_key = (
            group,
            sem_key,
            day_norm,
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
            processed_slots.append(
                {
                    "grupo": group,
                    "cuatrimestre": sem_key,
                    "dia": day_norm,
                    "hora_inicio": start_time,
                    "hora_fin": end_time,
                    "codigo": canonical_code,
                    "asignatura": asig_norm,
                    "tipo": slot_type,
                    "aula": classroom,
                    "profesor": prof,
                    "es_laboratorio": is_lab,
                    "grupo_practicas": grupo_prac,
                }
            )

    print(f"[Extracted] {len(raw_entries)} raw entries from {json_path}.")
    print(f"[Deduplicated] {len(processed_slots)} unique slots remaining.")

    if not interactive and UNRESOLVED:
        print("\n[Error] Unresolved mappings (nothing was written):", file=sys.stderr)
        for category in sorted(UNRESOLVED):
            for code in sorted(UNRESOLVED[category]):
                print(f"    {category}: {code}", file=sys.stderr)
        print(
            f"\nAdd them to {MAPPINGS_FILE} and re-run, or run without --non-interactive "
            "to be prompted for each.",
            file=sys.stderr,
        )
        sys.exit(1)

    os.makedirs(output_dir, exist_ok=True)
    by_semester: dict[str, list[dict]] = {"1C": [], "2C": []}
    for s in processed_slots:
        sem = s.get("cuatrimestre", "1C")
        by_semester.setdefault(sem, []).append(s)

    day_order = {"Lunes": 1, "Martes": 2, "Miércoles": 3, "Jueves": 4, "Viernes": 5, "Sábado": 6, "Domingo": 7}
    for sem_key, sem_slots in by_semester.items():
        if sem_slots:
            sem_slots.sort(key=lambda x: (x["grupo"], day_order.get(x["dia"], 99), x["hora_inicio"], x["codigo"]))
            out_file = os.path.join(output_dir, f"{sem_key}.json")
            with open(out_file, "w", encoding="utf-8") as f:
                json.dump(sem_slots, f, indent=2, ensure_ascii=False)
            print(f"  -> Wrote {len(sem_slots)} slots to {out_file}")

    return by_semester


def main() -> None:
    parser = argparse.ArgumentParser(description="Parse schedule JSON files into flat semester JSONs.")
    parser.add_argument("file", help="Path to schedule JSON file.")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive mapping prompts.")
    args = parser.parse_args()

    if not os.path.exists(args.file):
        print(f"[Error] File '{args.file}' not found.", file=sys.stderr)
        sys.exit(1)

    if not args.file.endswith(".json"):
        print(f"[Error] Unsupported input format '{args.file}': only .json files are supported.", file=sys.stderr)
        sys.exit(1)

    process_json_schedule(args.file, interactive=not args.non_interactive)


if __name__ == "__main__":
    main()
