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

def resolve_mapping(code: str, category: str, mappings: Dict, interactive: bool = True) -> Tuple[str, str]:
    if not code:
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
        return code, code

    print(f"\n[?] Unknown {category[:-1]} found: '{code}'")
    val = input(f"    Enter full name for '{code}' (or press Enter to use as is): ").strip()
    val = val if val else code
    category_map[code] = val
    save_mappings(mappings)
    return code, val

def get_mapping(code: str, category: str, mappings: Dict, interactive: bool = True) -> str:
    _, val = resolve_mapping(code, category, mappings, interactive)
    return val

def resolve_professors(prof_str: str, mappings: Dict, interactive: bool) -> str:
    if not prof_str:
        return ""
    prof_str = prof_str.strip()
    full_match = get_mapping(prof_str, "professors", mappings, interactive=False)
    if full_match != prof_str:
        return full_match
    tokens = prof_str.split()
    if len(tokens) > 1 and any('.' in t for t in tokens):
        resolved = []
        for token in tokens:
            name = get_mapping(token, "professors", mappings, interactive=False)
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

# --- Core PDF Processor ---

def process_pdf_schedule(
    pdf_path: str,
    output_dir: str = DEFAULT_DIST_DIR,
    interactive: bool = True,
    model_id: str = None,
    clear_cache: bool = False
) -> List[Dict]:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("[Error] GEMINI_API_KEY environment variable not set.")
        sys.exit(1)

    env_model = os.getenv("GEMINI_MODEL")
    if not model_id:
        model_id = env_model if env_model else DEFAULT_MODEL

    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(CACHE_DIR, exist_ok=True)

    client = genai.Client(api_key=api_key)
    mappings = load_mappings()

    filename = os.path.basename(pdf_path)
    default_semester = "1C"
    if "2C" in filename.upper() or "2_CUATRIMESTRE" in filename.upper():
        default_semester = "2C"
    elif "1C" in filename.upper() or "1_CUATRIMESTRE" in filename.upper():
        default_semester = "1C"

    print(f"\n[PDF] Converting '{filename}' to images (Default semester: {default_semester})...")
    pages = convert_from_path(pdf_path, dpi=200)

    prompt = """
    Extract all course schedule time slots from this university schedule page into a flat list of JSON objects matching the schema.

    RULES:
    1. EXTRACT ALL SLOTS: Extract every class slot (Theory, Laboratory, Exams/Events) shown on this page.
    2. CUATRIMESTRE: Identify if this page belongs to '1C' (Primer Cuatrimestre) or '2C' (Segundo Cuatrimestre). Use '1C' or '2C'.
    3. DIA: Name of day in Spanish (Lunes, Martes, Miércoles, Jueves, Viernes).
    4. HORA INICIO & FIN: Format HH:mm (e.g. 08:30, 10:00).
    5. GRUPO: Group name (e.g. '1A', '1º A', '2B', '3º ISO').
    6. ASIGNATURA: Subject name or code (e.g. 'Álgebra', 'Calculo', 'Pruebas de Progreso').
    7. TIPO: 'teoría' for lectures, 'laboratorio' for labs, 'evento' for exams/events.
    8. AULA: Classroom code or name (e.g. 'A1.1', 'Charles Babbage - 0.02+3').
    9. PROFESOR: Professor name if listed, otherwise empty string.
    10. ES_LABORATORIO: true if laboratory session, false otherwise.
    11. GRUPO_PRACTICAS: Lab variant name (e.g. 'Lab-A1', 'Lab-A1/A2') if applicable, else empty string.
    """

    all_raw_slots = []

    for i, page_img in enumerate(pages):
        cache_path = get_page_cache_path(page_img)

        if not clear_cache and os.path.exists(cache_path):
            print(f"  -> Page {i + 1}/{len(pages)}: Loaded from cache.")
            with open(cache_path, "r", encoding="utf-8") as f:
                page_data = json.load(f)
        else:
            max_retries = 10
            base_delay = 15
            page_data = None

            for attempt in range(max_retries):
                try:
                    print(f"  -> Page {i + 1}/{len(pages)}: Parsing with Gemini ({model_id}, attempt {attempt + 1})...")
                    response = client.models.generate_content(
                        model=model_id,
                        contents=[page_img, prompt],
                        config=types.GenerateContentConfig(
                            response_mime_type="application/json",
                            response_schema=PageFlatSchedule,
                            temperature=0.1
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
                        print(f"\n[!] CRITICAL: Daily quota for model '{model_id}' exhausted.")
                        sys.exit(1)

                    if "404" in str(e) or "not found" in str(e).lower() or "no longer available" in str(e).lower():
                        print(f"\n[!] ERROR: Model '{model_id}' is not found or no longer available.")
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
                        print(f"     Rate limit reached (429/RESOURCE_EXHAUSTED). Waiting {wait_time:.1f}s before retry...")
                        time.sleep(wait_time)
                    else:
                        if wait_time:
                            print(f"     Client error ({status}). Waiting {wait_time:.1f}s before retry...")
                            time.sleep(wait_time)
                        else:
                            print(f"     Fatal Client error: {e}")
                            sys.exit(1)

                except Exception as e:
                    print(f"     [Warning] Attempt {attempt + 1} failed: {e}")
                    if attempt == max_retries - 1:
                        print(f"     [Error] Skipping page {i + 1} after {max_retries} attempts.")
                        break
                    time.sleep(5)

        if page_data and "slots" in page_data:
            all_raw_slots.extend(page_data["slots"])

    print(f"[Extracted] {len(all_raw_slots)} raw slots across {len(pages)} pages.")

    # Post-processing & Deduplication
    processed_slots = []
    seen_keys = set()

    for slot in all_raw_slots:
        raw_asig = (slot.get("asignatura") or "").strip()
        if not raw_asig or "universidad de mayores" in raw_asig.lower() or "univmayores" in raw_asig.lower():
            continue

        sem_val = (slot.get("cuatrimestre") or default_semester).strip().upper()
        sem_key = "2C" if "2" in sem_val else "1C"

        prof_raw = (slot.get("profesor") or "").strip()
        prof = resolve_professors(prof_raw, mappings, interactive) if prof_raw else ""

        aula_raw = (slot.get("aula") or "").strip()
        classroom = get_mapping(aula_raw, "classrooms", mappings, interactive) if aula_raw else ""

        asig_norm = get_mapping(raw_asig, "matters", mappings, interactive) if raw_asig else raw_asig
        day_norm = normalize_spanish_day(slot.get("dia", ""))

        start_time = format_time(slot.get("hora_inicio", ""), is_end_time=False)
        end_time = format_time(slot.get("hora_fin", ""), is_end_time=True)

        group = (slot.get("grupo") or "").strip()

        slot_key = (
            group,
            sem_key,
            day_norm,
            start_time,
            end_time,
            asig_norm,
            slot.get("tipo", "teoría"),
            classroom,
            prof,
            bool(slot.get("es_laboratorio", False)),
            (slot.get("grupo_practicas") or "").strip()
        )

        if slot_key not in seen_keys:
            seen_keys.add(slot_key)
            processed_slots.append({
                "grupo": group,
                "cuatrimestre": sem_key,
                "dia": day_norm,
                "hora_inicio": start_time,
                "hora_fin": end_time,
                "asignatura": asig_norm,
                "tipo": slot.get("tipo", "teoría"),
                "aula": classroom,
                "profesor": prof,
                "es_laboratorio": bool(slot.get("es_laboratorio", False)),
                "grupo_practicas": (slot.get("grupo_practicas") or "").strip()
            })

    print(f"[Deduplicated] {len(processed_slots)} unique slots remaining.")
    return processed_slots

def main():
    parser = argparse.ArgumentParser(description="Parse schedule PDF files directly into flat schedule JSONs.")
    parser.add_argument("pdf_file", help="Path to PDF schedule file.")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive mapping prompts.")
    parser.add_argument("--model", default=None, help=f"Gemini model ID (default: {DEFAULT_MODEL}).")
    parser.add_argument("--clear-cache", action="store_true", help="Clear page response cache before running.")
    args = parser.parse_args()

    if not os.path.exists(args.pdf_file):
        print(f"[Error] File '{args.pdf_file}' not found.")
        sys.exit(1)

    interactive = not args.non_interactive
    slots = process_pdf_schedule(
        pdf_path=args.pdf_file,
        interactive=interactive,
        model_id=args.model,
        clear_cache=args.clear_cache
    )

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