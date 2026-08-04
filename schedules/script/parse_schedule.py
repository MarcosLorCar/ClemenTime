#!/usr/bin/env python3
import json
import os
import sys
import argparse
import random
import re
import hashlib
import time
import unicodedata
from difflib import SequenceMatcher
from typing import Dict, List, Optional, Tuple, Any
from pydantic import BaseModel, Field
from pdf2image import convert_from_path
from google import genai
from google.genai import types, errors

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MAPPINGS_FILE = os.path.join(SCRIPT_DIR, "mappings.json")
CACHE_DIR = os.path.join(SCRIPT_DIR, ".cache")
DEFAULT_DIST_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "dist"))
DEFAULT_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")

# --- Pydantic Schema for Direct Flat Output ---

class JsonFlatSlot(BaseModel):
    grupo: str = Field(default="", description="Group code, e.g. '1A', '1º A', '3º ISO'")
    cuatrimestre: str = Field(default="1C", description="'1C' or '2C'")
    dia: str = Field(default="", description="Day of week in Spanish: 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'")
    hora_inicio: str = Field(default="", description="Start time in HH:mm format, e.g. '08:30'")
    hora_fin: str = Field(default="", description="End time in HH:mm format, e.g. '10:00'")
    asignatura: str = Field(default="", description="Subject name or shorthand code")
    tipo: str = Field(default="teoría", description="'teoría', 'laboratorio', or 'evento'")
    aula: str = Field(default="", description="Classroom name or code")
    profesor: Optional[str] = Field(default="", description="Professor name if present")
    es_laboratorio: bool = Field(default=False, description="True if entry is a laboratory session")
    grupo_practicas: Optional[str] = Field(default="", description="Lab group variant if present, e.g. 'Lab-A1' or 'Lab-A1/A2'")

class PageFlatSchedule(BaseModel):
    slots: List[JsonFlatSlot] = []

# --- Mapping Utilities ---

def remove_accents(text: str) -> str:
    if not text:
        return ""
    nfkd_form = unicodedata.normalize('NFKD', text)
    return "".join([c for c in nfkd_form if not unicodedata.combining(c)])

def load_mappings() -> Dict:
    if os.path.exists(MAPPINGS_FILE):
        with open(MAPPINGS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"matters": {}, "professors": {}, "classrooms": {}}

def save_mappings(mappings: Dict):
    with open(MAPPINGS_FILE, "w", encoding="utf-8") as f:
        json.dump(mappings, f, indent=2, ensure_ascii=False)

# Codes that fell through every matching strategy while running non-interactively.
# In --non-interactive mode they are passed through verbatim, which would ship raw codes
# like "JRGP" to users, so main() reports them and exits non-zero instead.
UNRESOLVED: Dict[str, set] = {}


def record_unresolved(code: str, category: str):
    UNRESOLVED.setdefault(category, set()).add(code)


def resolve_mapping(
    code: str,
    category: str,
    mappings: Dict,
    interactive: bool = True,
    track: bool = True,
) -> Tuple[str, str]:
    if not code:
        return code, code

    if category == "classrooms" and re.match(r'^lab[-_\s]', remove_accents(code).lower()):
        return code, code

    category_map = mappings.get(category, {})

    if code in category_map:
        return code, category_map[code]

    for k, v in category_map.items():
        if v == code:
            return k, v

    norm_code = remove_accents(code).lower()
    for key, val in category_map.items():
        if remove_accents(key).lower() == norm_code or remove_accents(val).lower() == norm_code:
            return key, val

    code_tokens = set(re.findall(r'\w+', norm_code))
    for key, val in category_map.items():
        key_norm = remove_accents(key).lower()
        val_norm = remove_accents(val).lower()
        if key_norm and (key_norm in norm_code or norm_code in key_norm):
            return key, val
        if val_norm and (val_norm in norm_code or norm_code in val_norm):
            return key, val
        key_tokens = set(re.findall(r'\w+', key_norm))
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

    if best_score >= 0.8:
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
    mappings: Dict,
    interactive: bool = True,
    track: bool = True,
) -> str:
    _, val = resolve_mapping(code, category, mappings, interactive, track)
    return val

def resolve_professors(prof_str: str, mappings: Dict, interactive: bool) -> str:
    if not prof_str:
        return ""
    prof_str = prof_str.strip()
    # track=False on the speculative lookups below: they are probes, and only the final
    # call decides whether this professor really went unresolved.
    full_match = get_mapping(prof_str, "professors", mappings, interactive=False, track=False)
    if full_match != prof_str:
        return full_match
    tokens = prof_str.split()
    if len(tokens) > 1 and any('.' in t for t in tokens):
        resolved = []
        for token in tokens:
            name = get_mapping(token, "professors", mappings, interactive=False, track=False)
            resolved.append(name)
        if all(r != t for r, t in zip(resolved, tokens)):
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
        "friday": "Viernes"
    }
    return mapping.get(d_clean, d)

# --- Error Handling Utilities ---

def is_daily_quota_exhausted(error_obj: Any) -> bool:
    """Recursively search for indicators that the DAILY quota is exhausted."""
    data_str = str(error_obj).lower()

    if "per_day" in data_str or "perday" in data_str:
        return True

    if "limit: 0" in data_str and "requests" in data_str:
        return True

    if isinstance(error_obj, dict):
        for k, v in error_obj.items():
            if k == "quotaId" and isinstance(v, str) and "perday" in v.lower():
                return True
            if is_daily_quota_exhausted(v):
                return True
    elif isinstance(error_obj, list):
        for item in error_obj:
            if is_daily_quota_exhausted(item):
                return True

    return False

# --- Cache Utilities ---

def get_page_cache_path(page_img) -> str:
    img_bytes = page_img.tobytes()
    content_hash = hashlib.sha256(img_bytes).hexdigest()
    return os.path.join(CACHE_DIR, f"{content_hash}.json")

def sanitize_subject_and_classroom(raw_asig: str, raw_aula: str) -> Tuple[str, str]:
    if not raw_asig:
        return "", raw_aula.strip()

    asig = raw_asig.strip()
    aula = raw_aula.strip()

    # 1. Catch "Pruebas de...", "Pruebas. ESI", or "Pruebas..."
    if re.search(r'pruebas', asig, re.IGNORECASE):
        room_match = re.search(r'(\d+\.\d+[\w\+\-\.\s]*|A\d\.\d+[\w\+\-\.\s]*|F\d\.\d+[\w\+\-\.\s]*|LD\d[\w\+\-\.\s]*)', asig, re.IGNORECASE)
        if room_match and not aula:
            aula = room_match.group(1).strip()
        return "Pruebas de Progreso", aula

    # 2. Separate appended classroom code (e.g. "Algebra 0.04-Hedy" or "Algebra A1.1")
    room_match = re.search(r'\s+(\d+\.\d+[\w\+\-\.\s]*|[A-Z]\d\.\d+[\w\+\-\.\s]*|LD\d[\w\+\-\.\s]*)$', asig)
    if room_match:
        if not aula:
            aula = room_match.group(1).strip()
        asig = asig[:room_match.start()].strip()

    return asig, aula

def normalize_group_name(group: str) -> str:
    if not group:
        return ""
    g = group.strip()
    m = re.search(r'(\d+)\s*º?\s*([A-Za-z]+)', g)
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
        "carmack", "gates", "ruiz", "shannon", "vonn"
    ]
    if any(kw in p_lower for kw in room_or_event_keywords):
        return ""
    return p

# --- Core PDF Processor ---

def load_env_file():
    for path in [".env", "schedules/script/.env", os.path.join(os.path.dirname(__file__), ".env"), os.path.join(os.path.dirname(__file__), "..", "..", ".env")]:
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        k = k.strip()
                        v = v.strip().strip('"').strip("'")
                        if k not in os.environ:
                            os.environ[k] = v

def process_pdf_schedule(
    pdf_path: str,
    output_dir: str = DEFAULT_DIST_DIR,
    interactive: bool = True,
    model_id: str = None,
    clear_cache: bool = False,
    target_pages: Optional[List[int]] = None
) -> List[Dict]:
    try:
        sys.stdout.reconfigure(line_buffering=True)
        sys.stderr.reconfigure(line_buffering=True)
    except Exception:
        pass

    load_env_file()
    api_key = os.getenv("GEMINI_API_KEY")
    api_key_alt = os.getenv("GEMINI_API_KEY_ALT")
    if not api_key:
        print("[Error] GEMINI_API_KEY environment variable not set.")
        sys.exit(1)

    active_api_key = api_key
    used_alt_key = False

    env_model = os.getenv("GEMINI_MODEL")
    if not model_id:
        model_id = env_model if env_model else DEFAULT_MODEL

    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(CACHE_DIR, exist_ok=True)

    client = genai.Client(api_key=active_api_key)
    mappings = load_mappings()

    filename = os.path.basename(pdf_path)
    default_semester = "1C"
    if "2C" in filename.upper() or "2_CUATRIMESTRE" in filename.upper():
        default_semester = "2C"
    elif "1C" in filename.upper() or "1_CUATRIMESTRE" in filename.upper():
        default_semester = "1C"

    print(f"\n[PDF] Converting '{filename}' to images (Default semester: {default_semester})...", flush=True)
    pages = convert_from_path(pdf_path, dpi=200)

    prompt = """
    Extract all course schedule time slots from this university schedule page into a flat list of JSON objects matching the schema.

    === TABLE STRUCTURE & DAY COLUMNS ===
    The schedule table contains 5 vertical day columns from left to right:
    - 1st Column (immediately right of the time column): LUNES
    - 2nd Column: MARTES
    - 3rd Column: MIÉRCOLES
    - 4th Column: JUEVES
    - 5th Column: VIERNES

    SPATIAL COLUMN ALIGNMENT (CRITICAL):
    1. Always trace straight UP to the top day header (Lunes, Martes, Miércoles, Jueves, Viernes) to verify the day column for each box.
    2. SUB-DIVIDED LAB CELLS: Inside a day column, a cell may be split vertically into 2 sub-boxes (e.g., left half for Lab-1/Lab-B1, right half for Lab-2/Lab-B2). Sub-boxes sharing the same main column borders belong to the SAME day.
    3. DO NOT drift Column 3 (Miércoles) slots into Column 4 (Jueves). Check column line alignment from the top header to the bottom of the table.

    === TIME DURATION & BOX BOUNDARIES ===
    1. Time format: HH:mm (e.g., 08:30, 10:00, 11:30, 13:00, 14:30, 15:30, 17:00, 18:30, 20:00, 21:30).
    2. UNIFIED MULTI-ROW BOXES (3-HOUR SESSIONS):
       If a single unified box with NO internal horizontal line spans across 2 time rows (e.g. 08:30 to 11:30, 18:30 to 21:30, 11:30 to 14:30), output the full start and end time (e.g. hora_inicio: "08:30", hora_fin: "11:30" or hora_inicio: "18:30", hora_fin: "21:30"). DO NOT split or truncate a unified 3-hour box.
    3. SEPARATE ADJACENT 1.5-HOUR SUB-BOXES:
       If two adjacent time rows (e.g. 11:30-13:00 and 13:00-14:30) have separate sub-boxes divided by a horizontal line, or have different lab variant codes (e.g. Lab-A1 vs Lab-A2), extract them as TWO SEPARATE 1.5-hour slots (11:30-13:00 and 13:00-14:30).

    === GLOBAL FACULTY EVENTS ('GENERAL') ===
    1. 'Pruebas de Progreso' (or 'Pruebas de.', 'PruebasProgreso', 'Pruebas...') and 'Conferencias' are general faculty-wide events.
    2. For 'Pruebas de Progreso':
       - asignatura: "Pruebas de Progreso"
       - tipo: "evento"
       - es_laboratorio: false
       - grupo: "GENERAL"
       - aula: "0.02-Charles Babbage"
       - profesor: ""
    3. For 'Conferencias':
       - asignatura: "Conferencias"
       - tipo: "evento"
       - es_laboratorio: false
       - grupo: "GENERAL"
       - aula: "Alan Turing"
       - profesor: ""

    === FIELD EXTRACTION RULES ===
    1. GRUPO: Group or specialization code matching the page header (e.g. '1A', '1B', '1C', '1D', '2A', '2B', '2C', '2D', '3A', '3B', '3C', '3IC', '3ISO', '3TI', '3CO', '4IC', '4ISO', '4TI', '4CO').
       Pay attention: Page 1 = 1A, Page 2 = 1B, Page 3 = 1C, Page 4 = 1D.
    2. ASIGNATURA: Subject name or code ONLY (e.g. 'Álgebra', 'Cálculo', 'Física', 'Fundamentos de Programación I', 'Sistemas Operativos I').
    3. TIPO: 'teoría' for lectures, 'laboratorio' for labs, 'evento' for exams/events.
    4. AULA: Classroom code or name (e.g. 'A1.1-John Von Neumann', 'A2.2-Grace Murray', '0.02-Charles Babbage', 'LD1-Ignacio Cirac', 'LD2-Dennis Ritchie', 'LD3-Bill Gates', 'LD4-John Carmack', '0.07-Claude Shannon', '0.04-Hedy Lamarr').
    5. PROFESOR: Full professor name if listed, else empty string.
    6. ES_LABORATORIO: true if laboratory, false otherwise.
    7. GRUPO_PRACTICAS: Lab variant code (e.g. 'Lab-A1', 'Lab-A2', 'Lab-B1', 'Lab-B2', 'Lab-BC') if present, else empty string.
    """

    all_raw_slots = []

    for i, page_img in enumerate(pages):
        page_num = i + 1
        cache_path = get_page_cache_path(page_img)
        force_reparse = target_pages is not None and page_num in target_pages

        if not clear_cache and not force_reparse and os.path.exists(cache_path):
            print(f"  -> Page {page_num}/{len(pages)}: Loaded from cache.", flush=True)
            with open(cache_path, "r", encoding="utf-8") as f:
                page_data = json.load(f)
        else:
            max_retries = 10
            base_delay = 15
            page_data = None

            for attempt in range(max_retries):
                try:
                    print(f"  -> Page {i + 1}/{len(pages)}: Parsing with Gemini ({model_id}, attempt {attempt + 1})...", flush=True)
                    response = client.models.generate_content(
                        model=model_id,
                        contents=[prompt, page_img],
                        config=types.GenerateContentConfig(
                            response_mime_type="application/json",
                            response_schema=PageFlatSchedule,
                            temperature=0.0
                        ),
                    )

                    page_data = json.loads(response.text)

                    with open(cache_path, "w", encoding="utf-8") as f:
                        json.dump(page_data, f, indent=2, ensure_ascii=False)

                    break

                except errors.ClientError as e:
                    error_data = e.args[1] if len(e.args) > 1 else {}
                    status = error_data.get("status", "")

                    if is_daily_quota_exhausted(error_data) or is_daily_quota_exhausted(str(e)):
                        if api_key_alt and not used_alt_key:
                            print(f"\n[!] Daily quota exhausted for primary API key. Switching to GEMINI_API_KEY_ALT...", flush=True)
                            active_api_key = api_key_alt
                            client = genai.Client(api_key=active_api_key)
                            used_alt_key = True
                            time.sleep(2)
                            continue
                        else:
                            print(f"\n[!] CRITICAL: Daily quota for model '{model_id}' exhausted.", flush=True)
                            sys.exit(1)

                    if "404" in str(e) or "not found" in str(e).lower() or "no longer available" in str(e).lower():
                        print(f"\n[!] ERROR: Model '{model_id}' is not found or no longer available.", flush=True)
                        sys.exit(1)

                    wait_time = None
                    error_info = error_data.get("error", {})
                    retry_info = next((d for d in error_info.get("details", []) if "retryDelay" in d), None)

                    if retry_info:
                        delay_match = re.search(r"(\d+\.?\d*)", str(retry_info["retryDelay"]))
                        if delay_match:
                            wait_time = float(delay_match.group(1)) + 1.5

                    if status == "RESOURCE_EXHAUSTED" or "429" in str(e):
                        if wait_time is None:
                            wait_time = base_delay * (2 ** attempt) + random.uniform(0, 5)
                        print(f"     Rate limit reached (429/RESOURCE_EXHAUSTED). Waiting {wait_time:.1f}s before retry...", flush=True)
                        time.sleep(wait_time)
                    else:
                        if wait_time:
                            print(f"     Client error ({status}). Waiting {wait_time:.1f}s before retry...", flush=True)
                            time.sleep(wait_time)
                        else:
                            print(f"     Fatal Client error: {e}", flush=True)
                            sys.exit(1)

                except Exception as e:
                    print(f"     [Warning] Attempt {attempt + 1} failed: {e}", flush=True)
                    if attempt == max_retries - 1:
                        print(f"     [Error] Skipping page {i + 1} after {max_retries} attempts.", flush=True)
                        break
                    time.sleep(5)

        if page_data and "slots" in page_data:
            all_raw_slots.extend(page_data["slots"])

    print(f"[Extracted] {len(all_raw_slots)} raw slots across {len(pages)} pages.", flush=True)

    # Post-processing & Deduplication
    processed_slots = []
    seen_keys = set()

    for slot in all_raw_slots:
        raw_asig = (slot.get("asignatura") or "").strip()
        raw_aula = (slot.get("aula") or "").strip()

        clean_asig, clean_aula = sanitize_subject_and_classroom(raw_asig, raw_aula)

        if not clean_asig or "universidad de mayores" in clean_asig.lower() or "univmayores" in clean_asig.lower():
            continue

        sem_val = (slot.get("cuatrimestre") or default_semester).strip().upper()
        sem_key = "2C" if "2" in sem_val else "1C"

        prof_raw = sanitize_professor(slot.get("profesor") or "")
        prof = resolve_professors(prof_raw, mappings, interactive) if prof_raw else ""

        classroom = get_mapping(clean_aula, "classrooms", mappings, interactive) if clean_aula else ""
        asig_norm = get_mapping(clean_asig, "matters", mappings, interactive) if clean_asig else clean_asig
        day_norm = normalize_spanish_day(slot.get("dia", ""))

        group = normalize_group_name(slot.get("grupo") or "")
        slot_type = slot.get("tipo", "teoría")
        is_lab = bool(slot.get("es_laboratorio", False))
        grupo_prac = (slot.get("grupo_practicas") or "").strip()

        if asig_norm in ["Pruebas de Progreso", "Conferencias", "PruebasProgreso"]:
            group = "GENERAL"
            prof = ""
            slot_type = "evento"
            is_lab = False
            grupo_prac = ""
            if "pruebas" in asig_norm.lower():
                classroom = "0.02-Charles Babbage"
            elif asig_norm == "Conferencias":
                classroom = "Alan Turing"

        start_time = format_time(slot.get("hora_inicio", ""), is_end_time=False)
        end_time = format_time(slot.get("hora_fin", ""), is_end_time=True)

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
            grupo_prac
        )

        if slot_key not in seen_keys:
            seen_keys.add(slot_key)
            processed_slots.append({
                "grupo": group,
                "cuatrimestre": sem_key,
                "dia": day_norm,
                "hora_inicio": start_time,
                "hora_fin": end_time,
                "codigo": clean_asig,
                "asignatura": asig_norm,
                "tipo": slot_type,
                "aula": classroom,
                "profesor": prof,
                "es_laboratorio": is_lab,
                "grupo_practicas": grupo_prac
            })

    print(f"[Deduplicated] {len(processed_slots)} unique slots remaining.")
    return processed_slots

def main():
    parser = argparse.ArgumentParser(description="Parse schedule PDF files directly into flat schedule JSONs.")
    parser.add_argument("pdf_file", help="Path to PDF schedule file.")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive mapping prompts.")
    parser.add_argument("--model", default=None, help=f"Gemini model ID (default: {DEFAULT_MODEL}).")
    parser.add_argument("--clear-cache", action="store_true", help="Clear page response cache before running.")
    parser.add_argument("--page", type=int, action="append", dest="target_pages", help="Specific 1-based page number to re-parse (e.g. --page 10).")
    parser.add_argument("--pages", type=int, nargs="+", dest="target_pages_list", help="Specific 1-based page numbers to re-parse (e.g. --pages 10 11).")
    args = parser.parse_args()

    target_pages = args.target_pages or []
    if args.target_pages_list:
        target_pages.extend(args.target_pages_list)
    target_pages = list(set(target_pages)) if target_pages else None

    if not os.path.exists(args.pdf_file):
        print(f"[Error] File '{args.pdf_file}' not found.")
        sys.exit(1)

    interactive = not args.non_interactive
    slots = process_pdf_schedule(
        pdf_path=args.pdf_file,
        interactive=interactive,
        model_id=args.model,
        clear_cache=args.clear_cache,
        target_pages=target_pages
    )

    # Fail before writing anything: in non-interactive mode unknown codes are passed
    # through verbatim, so writing them would publish raw codes to users' devices.
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

    by_semester: Dict[str, List[Dict]] = {"1C": [], "2C": []}
    for s in slots:
        sem = s.get("cuatrimestre", "1C")
        by_semester[sem].append(s)

    for sem_key, sem_slots in by_semester.items():
        if sem_slots:
            out_file = os.path.join(DEFAULT_DIST_DIR, f"{sem_key}.json")
            with open(out_file, "w", encoding="utf-8") as f:
                json.dump(sem_slots, f, indent=2, ensure_ascii=False)
            print(f"  -> Wrote {len(sem_slots)} slots to {out_file}")

if __name__ == "__main__":
    main()