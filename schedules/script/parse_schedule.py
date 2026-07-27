import json
import time
import os
import sys
import argparse
import random
import re
import hashlib
from typing import Dict, List, Optional, Any
from pdf2image import convert_from_path
from pydantic import BaseModel, Field
from google import genai
from google.genai import types, errors

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

# --- Configuration & Environment ---

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MAPPINGS_FILE = os.path.join(SCRIPT_DIR, "mappings.json")
CACHE_DIR = os.path.join(SCRIPT_DIR, ".cache")
DEFAULT_DIST_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "dist"))
DEFAULT_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")

# --- Models ---

def normalize_weekday(day: str) -> str:
    """Normalize Spanish or lowercase weekdays to English uppercase."""
    if not day:
        return day
    day = day.upper().strip()
    mapping = {
        "LUNES": "MONDAY",
        "MARTES": "TUESDAY",
        "MIERCOLES": "WEDNESDAY",
        "MIÉRCOLES": "WEDNESDAY",
        "JUEVES": "THURSDAY",
        "VIERNES": "FRIDAY",
        "SABADO": "SATURDAY",
        "SÁBADO": "SATURDAY",
        "DOMINGO": "SUNDAY"
    }
    return mapping.get(day, day)

def normalize_group_name(name: str) -> str:
    """Normalize group names (e.g., '1º A Bilingue' -> '1º A')."""
    if not name:
        return name
    # Remove 'Bilingue' or 'Bilingüe' (case insensitive)
    name = re.sub(r'\s+Bilingü?e', '', name, flags=re.IGNORECASE).strip()
    return name

def normalize_year_name(name: str) -> str:
    """Normalize year/track names for consistent merging (e.g., '3º SOFTWARE' -> '3º ISO')."""
    if not name:
        return name
    name = name.strip()

    # Track mapping
    tracks = {
        "ISO": "ISO",
        "SOFTWARE": "ISO",
        "COMPUTADORES": "IC",
        "IC": "IC",
        "COMPUTACION": "Co",
        "COMPUTACIÓN": "Co",
        "CO": "Co",
        "TECNOLOGIAS": "TI",
        "TECNOLOGÍAS": "TI",
        "TI": "TI"
    }

    upper_name = name.upper()
    for key, val in tracks.items():
        if key in upper_name:
            year_match = re.search(r'(\d+º?)', name)
            year_prefix = year_match.group(1) if year_match else ""
            return f"{year_prefix} {val}".strip()

    return name

class JsonTimeSlot(BaseModel):
    dayOfWeek: str = Field(description="MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY")
    startTime: str = Field(description="HH:mm format")
    endTime: str = Field(description="HH:mm format")
    classroom: Optional[str] = None
    groupName: Optional[str] = Field(None, description="e.g. 'Lab-A1' or null")
    entryType: str = Field(description="'THEORY' or 'LAB'")
    professor: Optional[str] = None

    def model_post_init(self, __context):
        self.dayOfWeek = normalize_weekday(self.dayOfWeek)

class JsonLabVariant(BaseModel):
    name: str = Field(description="e.g. 'Lab-A1'")
    slots: List[JsonTimeSlot] = []

class JsonSubject(BaseModel):
    code: str
    name: str
    color: Optional[int] = None
    semester: Optional[int] = 1
    theorySlots: List[JsonTimeSlot] = []
    labVariants: List[JsonLabVariant] = Field(default_factory=list, description="List of variants (e.g. Lab groups)")
    isDummy: bool = False

class JsonGroup(BaseModel):
    name: str
    matters: List[JsonSubject] = []

    def model_post_init(self, __context):
        self.name = normalize_group_name(self.name)

class JsonYear(BaseModel):
    name: str
    matters: List[JsonSubject] = []
    groups: List[JsonGroup] = []

    def model_post_init(self, __context):
        self.name = normalize_year_name(self.name)

class ScheduleJsonSchema(BaseModel):
    version: int = 1
    title: Optional[str] = None
    semester: Optional[int] = 1
    matters: List[JsonSubject] = []
    years: List[JsonYear] = []

# --- Mapping Utilities ---

def load_mappings() -> Dict:
    if os.path.exists(MAPPINGS_FILE):
        with open(MAPPINGS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"matters": {}, "professors": {}, "classrooms": {}}

def save_mappings(mappings: Dict):
    with open(MAPPINGS_FILE, "w", encoding="utf-8") as f:
        json.dump(mappings, f, indent=2, ensure_ascii=False)

def get_mapping(code: str, category: str, mappings: Dict, interactive: bool = True) -> str:
    """Resolve a mapping, optionally prompting the user."""
    # If the code is already a known value (full name), return it as is
    if code in mappings[category].values():
        return code

    if code in mappings[category]:
        return mappings[category][code]

    if not interactive:
        return code

    print(f"\n[?] Unknown {category[:-1]} found: '{code}'")
    val = input(f"    Enter full name for '{code}' (or press Enter to use as is): ").strip()

    if not val:
        val = code

    mappings[category][code] = val
    save_mappings(mappings)
    return val

# --- Consolidation Utilities ---

def merge_subjects(subjects: List[Dict]) -> List[Dict]:
    """Merge subjects by code, combining their slots and lab variants while deduplicating exact matches."""
    merged: Dict[str, Dict] = {}

    def get_slot_key(slot):
        return (slot.get("dayOfWeek"), slot.get("startTime"), slot.get("endTime"), slot.get("classroom"))

    for s in subjects:
        code = s["code"]
        if code not in merged:
            merged[code] = s
            # Initialize with unique slots from the first occurrence
            theory_unique = []
            seen_theory = set()
            for slot in s.get("theorySlots", []):
                key = get_slot_key(slot)
                if key not in seen_theory:
                    theory_unique.append(slot)
                    seen_theory.add(key)
            merged[code]["theorySlots"] = theory_unique
            merged[code]["_seen_theory"] = seen_theory

            # Initialize lab variants
            merged[code]["_seen_labs"] = {}
            new_variants = []
            for v in s.get("labVariants", []):
                v_name = v["name"]
                merged[code]["_seen_labs"][v_name] = set()
                unique_v_slots = []
                for slot in v.get("slots", []):
                    key = get_slot_key(slot)
                    if key not in merged[code]["_seen_labs"][v_name]:
                        unique_v_slots.append(slot)
                        merged[code]["_seen_labs"][v_name].add(key)
                v["slots"] = unique_v_slots
                new_variants.append(v)
            merged[code]["labVariants"] = new_variants
        else:
            # Merge theory slots with deduplication
            for slot in s.get("theorySlots", []):
                key = get_slot_key(slot)
                if key not in merged[code]["_seen_theory"]:
                    merged[code]["theorySlots"].append(slot)
                    merged[code]["_seen_theory"].add(key)

            # Merge lab variants with deduplication
            current_labs = {v["name"]: v for v in merged[code].get("labVariants", [])}
            for new_v in s.get("labVariants", []):
                v_name = new_v["name"]
                if v_name not in current_labs:
                    current_labs[v_name] = new_v
                    merged[code]["_seen_labs"][v_name] = set()
                    unique_v_slots = []
                    for slot in new_v.get("slots", []):
                        key = get_slot_key(slot)
                        if key not in merged[code]["_seen_labs"][v_name]:
                            unique_v_slots.append(slot)
                            merged[code]["_seen_labs"][v_name].add(key)
                    new_v["slots"] = unique_v_slots
                else:
                    for slot in new_v.get("slots", []):
                        key = get_slot_key(slot)
                        if key not in merged[code]["_seen_labs"][v_name]:
                            current_labs[v_name]["slots"].append(slot)
                            merged[code]["_seen_labs"][v_name].add(key)
            merged[code]["labVariants"] = list(current_labs.values())

    # Cleanup tracking fields
    for s in merged.values():
        s.pop("_seen_theory", None)
        s.pop("_seen_labs", None)

    return list(merged.values())

def merge_groups(groups: List[Dict]) -> List[Dict]:
    """Merge groups by name, consolidating their subjects."""
    merged: Dict[str, Dict] = {}
    for g in groups:
        name = g["name"]
        if name not in merged:
            merged[name] = g
        else:
            merged[name]["matters"].extend(g.get("matters", []))

    for g in merged.values():
        g["matters"] = merge_subjects(g.get("matters", []))
    return list(merged.values())

def merge_years(years: List[Dict]) -> List[Dict]:
    """Merge years by name, consolidating their root matters and groups."""
    merged: Dict[str, Dict] = {}
    for y in years:
        # Use normalized name for merging
        name = normalize_year_name(y["name"])
        if name not in merged:
            merged[name] = y
            merged[name]["name"] = name
        else:
            merged[name]["matters"].extend(y.get("matters", []))
            merged[name]["groups"].extend(y.get("groups", []))

    for y in merged.values():
        y["matters"] = merge_subjects(y.get("matters", []))
        y["groups"] = merge_groups(y.get("groups", []))
    return list(merged.values())

def consolidate_root_matters(schedule: Dict):
    """
    Ensure root-level subjects that belong to specific years are moved there.
    Truly global events (Pruebas, Conferencias) stay at the root.
    """
    global_codes = {"PruebasProgreso", "Conferencias", "UnivMayores"}
    root_matters = schedule.get("matters", [])
    years = schedule.get("years", [])

    new_root = []
    for rm in root_matters:
        code = rm["code"]
        if code in global_codes:
            new_root.append(rm)
            continue

        # Check if this subject exists inside any year or group
        moved = False
        for year in years:
            # Check year matters
            for ym in year.get("matters", []):
                if ym["code"] == code:
                    ym["theorySlots"].extend(rm.get("theorySlots", []))
                    # Lab variants merge
                    current_labs = {v["name"]: v for v in ym.get("labVariants", [])}
                    for new_v in rm.get("labVariants", []):
                        if new_v["name"] in current_labs:
                            current_labs[new_v["name"]]["slots"].extend(new_v.get("slots", []))
                        else:
                            current_labs[new_v["name"]] = new_v
                    ym["labVariants"] = list(current_labs.values())
                    moved = True

            # Check group matters
            for group in year.get("groups", []):
                for gm in group.get("matters", []):
                    if gm["code"] == code:
                        gm["theorySlots"].extend(rm.get("theorySlots", []))
                        # Similar lab variant merge
                        current_labs = {v["name"]: v for v in gm.get("labVariants", [])}
                        for new_v in rm.get("labVariants", []):
                            if new_v["name"] in current_labs:
                                current_labs[new_v["name"]]["slots"].extend(new_v.get("slots", []))
                            else:
                                current_labs[new_v["name"]] = new_v
                        gm["labVariants"] = list(current_labs.values())
                        moved = True

        if not moved:
            new_root.append(rm)

    # Final deduplication of slots in years (since we just appended)
    for year in years:
        year["matters"] = merge_subjects(year.get("matters", []))
        for group in year.get("groups", []):
            group["matters"] = merge_subjects(group.get("matters", []))

    schedule["matters"] = merge_subjects(new_root)

# --- Cache Utilities ---

def get_page_cache_path(page_img, system_prompt_base):
    """Generate a unique cache path for a page based on its content."""
    img_bytes = page_img.tobytes()
    content_hash = hashlib.sha256(img_bytes).hexdigest()

    # We use only the image hash for the cache key to prevent minor prompt
    # refinements from invalidating the entire cache.
    cache_key = hashlib.sha256(f"{content_hash}".encode('utf-8')).hexdigest()
    return os.path.join(CACHE_DIR, f"{cache_key}.json")

# --- Error Handling Utilities ---

def is_daily_quota_exhausted(error_obj: Any) -> bool:
    """Recursively search for indicators that the DAILY quota is exhausted."""
    data_str = str(error_obj).lower()

    # Check for direct keywords in the string representation
    if "per_day" in data_str or "perday" in data_str:
        return True

    # Check for 'limit: 0' which often indicates unavailable/exhausted model
    if "limit: 0" in data_str and "requests" in data_str:
        return True

    # Recursive check if it's a dict/list
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

# --- Core Logic ---

def process_pdf_schedule(pdf_path: str, output_path: str = None, interactive: bool = True, model_id: str = None, clear_cache: bool = False):
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("Error: GEMINI_API_KEY environment variable not set.")
        sys.exit(1)

    if not model_id:
        model_id = DEFAULT_MODEL

    if not output_path:
        base_name = os.path.splitext(os.path.basename(pdf_path))[0]
        output_path = os.path.join(DEFAULT_DIST_DIR, f"{base_name}.json")

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    os.makedirs(CACHE_DIR, exist_ok=True)

    client = genai.Client(api_key=api_key)
    mappings = load_mappings()

    print(f"Converting PDF '{pdf_path}' to images...")
    pages = convert_from_path(pdf_path, dpi=200)

    combined_schedule = {
        "version": 1,
        "title": os.path.splitext(os.path.basename(pdf_path))[0],
        "semester": 1,
        "matters": [],
        "years": []
    }

    mappings_str = json.dumps(mappings, indent=2, ensure_ascii=False)

    # Base prompt rules for caching (excludes dynamic mappings)
    system_prompt_rules = """
    Extract course schedule tables from the image into the provided JSON schema.

    RULES:
    1. EXTRACT ALL DATA: Identify subjects, professors, classrooms, and time slots.
    2. RESOLVE CODES: Use the CONTEXT to expand codes (e.g., 'TeCo' -> 'Tecnologia de Computadores').
    3. CANONICAL KEYS: If an abbreviation in the PDF looks like a partial or slightly different match for an existing key in the CONTEXT (e.g. 'JulioAlberto.Lópe' vs 'JulioAlberto.López'), you MUST use the exact key from the context in the 'code' or 'professor' field.
    4. NEW SYMBOLS: If you find a code NOT in the mappings, return the raw abbreviation as 'code' and try to guess a reasonable 'name'.
    5. NEGATIVE CONSTRAINTS: DO NOT extract "ESI" as a professor name. It is the institution name.
    6. SPECIALIZATIONS (TRACKS): Identify headers indicating the 4 specializations: Co (Computación), ISO (Ingeniería del Software), TI (Tecnologías de la Información), and IC (Ingeniería de Computadores).
    7. YEAR ORGANIZATION: Organize these tracks as 'years' (e.g., '3º ISO', '4º IC', '3º Co').
       If a subject (like 'API') appears in a track-specific table, place it INSIDE that year/track entry, even if shared.
    8. GROUPS: Identify year groups (e.g. '1º A', '2º C') and organize 'matters' under 'years' -> 'groups'.
       NOTE: Ignore "Bilingue" or "Bilingüe" in group names. '1º A Bilingue' should just be '1º A'.
    9. LABS: If a slot is split into sub-columns (e.g. A1, A2), place them in 'labVariants' as objects with 'name' and 'slots'.
    10. ROOT MATTERS: Root 'matters' list is STRICTLY for truly global events with no specific academic year/track (e.g., 'Pruebas de Progreso', 'Conferencias', 'Universidad de Mayores').
    11. WEEKDAYS: dayOfWeek MUST be in ENGLISH and UPPERCASE (MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).
       Even if the PDF is in Spanish (Lunes, Martes...), you MUST translate to English.
    """

    full_system_prompt = f"{system_prompt_rules}\n\nCONTEXT:\nUse these existing mappings for abbreviations and codes:\n{mappings_str}"

    for i, page_img in enumerate(pages):
        cache_path = get_page_cache_path(page_img, system_prompt_rules)

        if not clear_cache and os.path.exists(cache_path):
            print(f"Loading page {i + 1}/{len(pages)} from cache...")
            with open(cache_path, "r", encoding="utf-8") as f:
                page_json_data = json.load(f)
        else:
            max_retries = 10
            base_delay = 15
            page_json_data = None

            for attempt in range(max_retries):
                try:
                    print(f"Parsing page {i + 1}/{len(pages)} (Attempt {attempt + 1})...")

                    response = client.models.generate_content(
                        model=model_id,
                        contents=[page_img, full_system_prompt],
                        config=types.GenerateContentConfig(
                            response_mime_type="application/json",
                            response_schema=ScheduleJsonSchema,
                            temperature=0.1
                        ),
                    )

                    page_json_data = json.loads(response.text)

                    # Save to cache
                    with open(cache_path, "w", encoding="utf-8") as f:
                        json.dump(page_json_data, f, indent=2, ensure_ascii=False)

                    break

                except errors.ClientError as e:
                    error_data = e.args[1] if len(e.args) > 1 else {}
                    status = error_data.get("status", "")

                    # 1. Check for Daily Quota exhaustion (Bulletproof)
                    if is_daily_quota_exhausted(error_data) or is_daily_quota_exhausted(str(e)):
                        print(f"\n[!] CRITICAL: Daily quota for model '{model_id}' has been exhausted (or limit is 0).")
                        print("    Please wait until tomorrow or try a different model using --model or GEMINI_MODEL in .env.")
                        sys.exit(1)

                    # 2. Check for Model Not Found / Retired
                    if "404" in str(e) or "not found" in str(e).lower() or "no longer available" in str(e).lower():
                        print(f"\n[!] ERROR: Model '{model_id}' is not found or no longer available.")
                        print("    Please check available models with 'uv run schedules/script/list_models.py'.")
                        sys.exit(1)

                    # 3. Extract suggested retry delay
                    wait_time = None
                    # Search specifically in RetryInfo if available
                    error_info = error_data.get("error", {})
                    retry_info = next((d for d in error_info.get("details", []) if "retryDelay" in d), None)

                    if retry_info:
                        delay_match = re.search(r"(\d+\.?\d*)", str(retry_info["retryDelay"]))
                        if delay_match:
                            wait_time = float(delay_match.group(1)) + 1.5

                    if status == "RESOURCE_EXHAUSTED" or "429" in str(e):
                        if wait_time is None:
                            wait_time = base_delay * (2 ** attempt) + random.uniform(0, 5)
                        print(f"    Rate limit reached. Waiting {wait_time:.1f}s before retry...")
                        time.sleep(wait_time)
                    else:
                        # For other client errors, use suggested delay if available, else exit
                        if wait_time:
                            print(f"    Client error ({status}). Waiting {wait_time:.1f}s before retry...")
                            time.sleep(wait_time)
                        else:
                            print(f"    Fatal Client error: {e}")
                            sys.exit(1)

                except Exception as e:
                    print(f"    Unexpected error parsing page {i+1}: {e}")
                    if attempt == max_retries - 1:
                        print("    Max retries reached. Skipping page.")
                        break
                    time.sleep(5)

        if page_json_data:
            # Validate with Pydantic to ensure all fields exist (fixes KeyError)
            page_obj = ScheduleJsonSchema.model_validate(page_json_data)

            # Collect raw data from pages
            if page_obj.matters:
                combined_schedule["matters"].extend([m.model_dump() for m in page_obj.matters])
            if page_obj.years:
                combined_schedule["years"].extend([y.model_dump() for y in page_obj.years])

    # Consolidation step: Merge duplicates hierarchically
    print("Consolidating extracted data...")
    combined_schedule["years"] = merge_years(combined_schedule["years"])
    consolidate_root_matters(combined_schedule)

    print("\nVerifying consolidated data against mappings...")

    def verify_subject(subject_dict):
        subject_dict["name"] = get_mapping(subject_dict["code"], "matters", mappings, interactive)

        for slot in subject_dict.get("theorySlots", []):
            if slot.get("professor"):
                slot["professor"] = get_mapping(slot["professor"], "professors", mappings, interactive)
            if slot.get("classroom"):
                slot["classroom"] = get_mapping(slot["classroom"], "classrooms", mappings, interactive)

        # Handle list of variants and convert to dict for output compatibility
        variants_dict = {}
        for variant in subject_dict.get("labVariants", []):
            v_name = variant.get("name")
            v_slots = variant.get("slots", [])
            for slot in v_slots:
                if slot.get("professor"):
                    slot["professor"] = get_mapping(slot["professor"], "professors", mappings, interactive)
                if slot.get("classroom"):
                    slot["classroom"] = get_mapping(slot["classroom"], "classrooms", mappings, interactive)
            variants_dict[v_name] = v_slots

        subject_dict["labVariants"] = variants_dict

    # Process all levels
    for s in combined_schedule["matters"]: verify_subject(s)
    for y in combined_schedule["years"]:
        for s in y.get("matters", []): verify_subject(s)
        for g in y.get("groups", []):
            for s in g.get("matters", []): verify_subject(s)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(combined_schedule, f, indent=2, ensure_ascii=False)

    print(f"\nDone! Saved to {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Parse PDF schedule using Gemini LLM.")
    parser.add_argument("pdf", help="Path to input PDF file.")
    parser.add_argument("-o", "--output", help="Path for exported schedule JSON file.")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive prompts.")
    parser.add_argument("--model", help=f"Gemini model ID to use (default: {DEFAULT_MODEL}).")
    parser.add_argument("--clear-cache", action="store_true", help="Clear the page cache before running.")

    args = parser.parse_args()

    if not os.path.exists(args.pdf):
        print(f"Error: File '{args.pdf}' not found.")
    else:
        process_pdf_schedule(args.pdf, args.output, not args.non_interactive, args.model, args.clear_cache)
