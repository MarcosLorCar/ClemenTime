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

# Weekday translation mapping
WEEKDAY_MAP = {
    "LUNES": "MONDAY",
    "MARTES": "TUESDAY",
    "MIÉRCOLES": "WEDNESDAY",
    "MIERCOLES": "WEDNESDAY",
    "JUEVES": "THURSDAY",
    "VIERNES": "FRIDAY",
    "SÁBADO": "SATURDAY",
    "SABADO": "SATURDAY",
    "DOMINGO": "SUNDAY"
}

# Global root-level subjects
GLOBAL_SUBJECTS = {"Pruebas de Progreso", "Conferencias", "PruebasProgreso"}

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
        return None

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

def format_time(time_str: str) -> str:
    parts = time_str.split(":")
    if len(parts) == 2:
        return f"{int(parts[0]):02d}:{int(parts[1]):02d}"
    return time_str

def parse_year_and_group(grupo_str: str):
    grupo_str = grupo_str.strip()
    match = re.match(r"^(\d+)([A-Z])$", grupo_str)
    if match:
        year_num, group_char = match.groups()
        year_name = f"{year_num}º"
        group_name = f"{year_num}º {group_char}"
        return year_name, group_name

    if not grupo_str.endswith("º") and not grupo_str[0].isdigit():
        return grupo_str, None
    return grupo_str, None

def transform_slot(entry: Dict, mappings: Dict, interactive: bool) -> Dict:
    day = WEEKDAY_MAP.get(entry.get("dia", "").upper(), entry.get("dia", "").upper())
    start_time = format_time(entry.get("hora_inicio", ""))
    end_time = format_time(entry.get("hora_fin", ""))

    entry_type = "LAB" if entry.get("es_laboratorio", False) else "THEORY"

    prof_raw = entry.get("profesor", "")
    prof = resolve_professors(prof_raw, mappings, interactive) if prof_raw else None

    aula_raw = entry.get("aula", "")
    classroom = get_mapping(aula_raw, "classrooms", mappings, interactive) if aula_raw else None

    return {
        "dayOfWeek": day,
        "startTime": start_time,
        "endTime": end_time,
        "classroom": classroom,
        "groupName": entry.get("grupo_practicas") or None,
        "entryType": entry_type,
        "professor": prof
    }

def merge_subject_dicts(target: Dict, source: Dict):
    """Merges source subject slots into target subject."""
    for slot in source.get("theorySlots", []):
        if slot not in target["theorySlots"]:
            target["theorySlots"].append(slot)

    for v_name, slots in source.get("labVariants", {}).items():
        if v_name not in target["labVariants"]:
            target["labVariants"][v_name] = []
        for slot in slots:
            if slot not in target["labVariants"][v_name]:
                target["labVariants"][v_name].append(slot)

def consolidate_subjects(subject_map: Dict[str, Dict]) -> List[Dict]:
    """Ensures subjects with identical canonical full names are merged into a single subject."""
    merged: Dict[str, Dict] = {}
    for sub_code, subj in subject_map.items():
        key = subj["name"]
        if key not in merged:
            merged[key] = subj
        else:
            merge_subject_dicts(merged[key], subj)
    return list(merged.values())

def process_raw_json(raw_entries: List[Dict], interactive: bool = True) -> Dict[str, Dict]:
    mappings = load_mappings()

    semesters: Dict[str, Dict] = {
        "1C": {"version": 1, "title": "Cuatrimestre 1", "semester": 1, "matters": [], "years": []},
        "2C": {"version": 1, "title": "Cuatrimestre 2", "semester": 2, "matters": [], "years": []}
    }

    global_matters: Dict[str, Dict[str, Dict]] = {"1C": {}, "2C": {}}
    structured_years: Dict[str, Dict[str, Any]] = {"1C": {}, "2C": {}}

    for entry in raw_entries:
        raw_asig = entry.get("asignatura", "").strip()

        # 1. Ignore Universidad de Mayores
        if "universidad de mayores" in raw_asig.lower() or "univmayores" in raw_asig.lower():
            continue

        sem_key = entry.get("cuatrimestre", "1C").upper()
        if sem_key not in semesters:
            sem_key = "1C"

        clean_code = clean_subject_code(raw_asig)
        canonical_code, full_name = resolve_mapping(clean_code, "matters", mappings, interactive)
        slot = transform_slot(entry, mappings, interactive)

        def get_or_create_subject(container: Dict[str, Dict], sub_code: str, sub_name: str, sem_num: int) -> Dict:
            if sub_name not in container:
                container[sub_name] = {
                    "code": sub_code,
                    "name": sub_name,
                    "color": None,
                    "semester": sem_num,
                    "theorySlots": [],
                    "labVariants": {},
                    "isDummy": False
                }
            return container[sub_name]

        def add_slot_to_subject(subject: Dict, slot_data: Dict):
            if slot_data["entryType"] == "LAB":
                variant_name = entry.get("grupo_practicas") or "Lab"
                if variant_name not in subject["labVariants"]:
                    subject["labVariants"][variant_name] = []
                if slot_data not in subject["labVariants"][variant_name]:
                    subject["labVariants"][variant_name].append(slot_data)
            else:
                if slot_data not in subject["theorySlots"]:
                    subject["theorySlots"].append(slot_data)

        # 2. Global subjects
        if canonical_code in GLOBAL_SUBJECTS or clean_code in GLOBAL_SUBJECTS or raw_asig in GLOBAL_SUBJECTS:
            subj = get_or_create_subject(global_matters[sem_key], canonical_code, full_name, 1 if sem_key == "1C" else 2)
            add_slot_to_subject(subj, slot)
            continue

        # 3. Standard subjects
        year_name, group_name = parse_year_and_group(entry.get("grupo", ""))

        if year_name not in structured_years[sem_key]:
            structured_years[sem_key][year_name] = {"matters": {}, "groups": {}}

        year_entry = structured_years[sem_key][year_name]

        if group_name:
            if group_name not in year_entry["groups"]:
                year_entry["groups"][group_name] = {}
            subj = get_or_create_subject(year_entry["groups"][group_name], canonical_code, full_name, 1 if sem_key == "1C" else 2)
        else:
            subj = get_or_create_subject(year_entry["matters"], canonical_code, full_name, 1 if sem_key == "1C" else 2)

        add_slot_to_subject(subj, slot)

    # Build output schemas
    for sem_key, schema in semesters.items():
        schema["matters"] = consolidate_subjects(global_matters[sem_key])

        for y_name, y_data in structured_years[sem_key].items():
            year_matters = consolidate_subjects(y_data["matters"])
            groups_list = []
            for g_name, g_matters in y_data["groups"].items():
                groups_list.append({
                    "name": g_name,
                    "matters": consolidate_subjects(g_matters)
                })

            schema["years"].append({
                "name": y_name,
                "matters": year_matters,
                "groups": groups_list
            })

    return semesters

def main():
    parser = argparse.ArgumentParser(description="Process combined schedule JSON into semester schemas.")
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
    for sem_key, schema in results.items():
        default_filename = f"{sem_key}.json"

        if interactive:
            user_filename = input(f"Enter filename for {sem_key} output (default: {default_filename}): ").strip()
            filename = user_filename if user_filename else default_filename
        else:
            filename = default_filename

        # Ensure .json extension
        if not filename.endswith(".json"):
            filename += ".json"

        out_file = os.path.join(DEFAULT_DIST_DIR, filename)
        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(schema, f, indent=2, ensure_ascii=False)
        print(f"Generated: {out_file}")

if __name__ == "__main__":
    main()