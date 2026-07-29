import json
import os
import sys
import argparse
import re
import unicodedata
from difflib import SequenceMatcher
from typing import Dict, List, Any, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MAPPINGS_FILE = os.path.join(SCRIPT_DIR, "mappings.json")
DEFAULT_DIST_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "dist"))

def remove_accents(text: str) -> str:
    """Normalize unicode characters to remove accents for fuzzy key comparison."""
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

def clean_subject_code(code: str) -> str:
    """Removes laboratory suffixes like '-L', '-Lab', '(L)' from subject codes."""
    if not code:
        return code
    return re.sub(r'(-L|\(L\)|-Lab)$', '', code.strip(), flags=re.IGNORECASE)

def resolve_mapping(code: str, category: str, mappings: Dict, interactive: bool = True) -> Tuple[str, str]:
    """
    Resolves code against mappings and returns a tuple of (canonical_key, full_name).
    """
    if not code:
        return code, code

    category_map = mappings.get(category, {})

    # 1. Exact Key Match
    if code in category_map:
        return code, category_map[code]

    # 2. Exact Value Match
    for k, v in category_map.items():
        if v == code:
            return k, v

    # 3. Case & Accent Insensitive Match
    norm_code = remove_accents(code).lower()
    for key, val in category_map.items():
        if remove_accents(key).lower() == norm_code or remove_accents(val).lower() == norm_code:
            return key, val

    # 4. Substring / Token Matching
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

    # 5. Fuzzy Matching (Threshold 0.8)
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

    # 6. Fallback to Prompt
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

    # Step 1: Full string match without prompting
    full_match = get_mapping(prof_str, "professors", mappings, interactive=False)
    if full_match != prof_str:
        return full_match

    # Step 2: Separate multi-teacher string if needed
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
    """
    Format H:MM to HH:MM.
    If is_end_time is True and minutes are 50 (e.g. 09:50), round up to next hour (e.g. 10:00).
    """
    if not time_str:
        return time_str

    parts = time_str.split(":")
    if len(parts) == 2:
        hours = int(parts[0])
        minutes = int(parts[1])

        if is_end_time and minutes == 50:
            hours = (hours + 1) % 24
            minutes = 0

        return f"{hours:02d}:{minutes:02d}"
    return time_str

def process_raw_json(raw_entries: List[Dict], interactive: bool = True) -> Dict[str, List[Dict]]:
    mappings = load_mappings()

    semesters: Dict[str, List[Dict]] = {
        "1C": [],
        "2C": []
    }

    for entry in raw_entries:
        raw_asig = entry.get("asignatura", "").strip()

        # 1. Ignore Universidad de Mayores
        if "universidad de mayores" in raw_asig.lower() or "univmayores" in raw_asig.lower():
            continue

        cuatrimestre_val = entry.get("cuatrimestre", "1C").strip()
        sem_key = "2C" if "2" in cuatrimestre_val else "1C"

        prof_raw = entry.get("profesor", "")
        prof = resolve_professors(prof_raw, mappings, interactive) if prof_raw else ""

        aula_raw = entry.get("aula", "")
        classroom = get_mapping(aula_raw, "classrooms", mappings, interactive) if aula_raw else ""

        asig_normalized = get_mapping(raw_asig, "matters", mappings, interactive) if raw_asig else raw_asig

        clean_slot = {
            "grupo": entry.get("grupo", ""),
            "cuatrimestre": sem_key,
            "dia": entry.get("dia", ""),
            "hora_inicio": format_time(entry.get("hora_inicio", ""), is_end_time=False),
            "hora_fin": format_time(entry.get("hora_fin", ""), is_end_time=True),
            "asignatura": asig_normalized,
            "tipo": entry.get("tipo", "teoría"),
            "aula": classroom,
            "profesor": prof,
            "es_laboratorio": bool(entry.get("es_laboratorio", False)),
            "grupo_practicas": entry.get("grupo_practicas", "")
        }

        semesters[sem_key].append(clean_slot)

    return semesters

def main():
    parser = argparse.ArgumentParser(description="Process raw schedule JSON into flat array semester JSONs.")
    parser.add_argument("input_json", help="Path to input raw JSON file.")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive prompts.")
    args = parser.parse_args()

    if not os.path.exists(args.input_json):
        print(f"Error: File '{args.input_json}' not found.")
        sys.exit(1)

    with open(args.input_json, "r", encoding="utf-8") as f:
        raw_data = json.load(f)

    interactive = not args.non_interactive
    results = process_raw_json(raw_data, interactive=interactive)

    os.makedirs(DEFAULT_DIST_DIR, exist_ok=True)

    print("\n--- Output Configuration ---")
    for sem_key, slots in results.items():
        filename = f"{sem_key}.json"
        out_file = os.path.join(DEFAULT_DIST_DIR, filename)
        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(slots, f, indent=2, ensure_ascii=False)
        print(f"  -> Saved {len(slots)} slots to: {out_file}")

if __name__ == "__main__":
    main()