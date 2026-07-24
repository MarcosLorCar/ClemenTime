#!/usr/bin/env python3
"""
convert_schedule.py

Automates conversion of PDF/MD university course schedules into structured JSON.
Uses Docling for PDF -> MD conversion with caching (when available),
interactive CLI prompts for matter/professor mapping,
and advanced table parsing with horizontal deduplication and vertical slot merging.
"""

import sys
import os
import re
import json
import argparse
from typing import Dict, List, Optional, Tuple, Any

# Map Spanish days to standard DayOfWeek strings
DAY_MAP = {
    "lunes": "MONDAY",
    "martes": "TUESDAY",
    "miércoles": "WEDNESDAY",
    "miercoles": "WEDNESDAY",
    "jueves": "THURSDAY",
    "viernes": "FRIDAY"
}

# Regex to match professor abbreviations (e.g. MariaL.Lopez, Jesus.Serrano, PeterS.Normile)
PROF_REGEX = r'\b([A-Za-zÁÉÍÓÚáéíóúñ][A-Za-z0-9áéíóúñÁÉÍÓÚ]*(?:\.[A-Za-zÁÉÍÓÚáéíóúñ][A-Za-z0-9áéíóúñÁÉÍÓÚ]*)+)\b'

def clean_group_name(yr: str, grp_raw: str) -> str:
    """Normalizes raw group header text into clean group name."""
    grp_raw = re.sub(r'^[1234]º\s*', '', grp_raw)
    for s in ['Ciudad Real', 'Esc. Superior de Informatica', 'Bilingüe', 'MUFPS', 'ESI']:
        grp_raw = grp_raw.replace(s, '')
    grp_raw = grp_raw.strip()

    if yr != '4º' and not grp_raw.startswith('Optativas'):
        if grp_raw:
            grp_raw = grp_raw.split()[0]
    elif yr == '4º':
        if 'Computac' in grp_raw or ' CO' in grp_raw or grp_raw == 'CO':
            grp_raw = 'Computación'
        elif 'Computador' in grp_raw or ' IC' in grp_raw or grp_raw == 'IC':
            grp_raw = 'Ing. Computadores'
        elif 'Software' in grp_raw or ' IS' in grp_raw or grp_raw == 'IS':
            grp_raw = 'Ing. Software'
        elif 'Optativa' in grp_raw or ' OPT' in grp_raw or grp_raw == 'OPT':
            grp_raw = 'Optativas C1'
        elif 'Informac' in grp_raw or ' TI' in grp_raw or 'Tecnol' in grp_raw or grp_raw == 'TI':
            grp_raw = 'Tecnol. Información'
    return grp_raw


class InteractiveMapper:
    """Manages persistent mappings.json and prompts user for missing symbols."""
    def __init__(self, mapping_path: str = "mappings.json", non_interactive: bool = False, strict: bool = False):
        self.mapping_path = mapping_path
        self.non_interactive = non_interactive
        self.strict = strict
        self.missing_matters: set = set()
        self.missing_professors: set = set()
        self.missing_classrooms: set = set()
        self.matters: Dict[str, str] = {}
        self.professors: Dict[str, str] = {}
        self.classrooms: Dict[str, str] = {}
        self.load()

    def load(self):
        if os.path.exists(self.mapping_path):
            try:
                with open(self.mapping_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self.matters = data.get("matters", {})
                    self.professors = data.get("professors", {})
                    self.classrooms = data.get("classrooms", {})
            except Exception as e:
                print(f"[Warning] Could not load {self.mapping_path}: {e}")

    def save(self):
        data = {
            "matters": self.matters,
            "professors": self.professors,
            "classrooms": self.classrooms
        }
        try:
            os.makedirs(os.path.dirname(os.path.abspath(self.mapping_path)), exist_ok=True)
            with open(self.mapping_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        except Exception as e:
            print(f"[Warning] Could not save {self.mapping_path}: {e}")

    def get_matter_name(self, code: str) -> str:
        if code in self.matters:
            return self.matters[code]
        self.missing_matters.add(code)
        if self.non_interactive or self.strict:
            return code
        
        print(f"\n[CLI Prompt] Matter code '{code}' is not mapped.")
        val = input(f"  --> Enter full name for matter '{code}' [{code}]: ").strip()
        if not val:
            val = code
        self.matters[code] = val
        self.save()
        return val

    def get_professor_name(self, abbrev: Optional[str]) -> Optional[str]:
        if not abbrev:
            return None
        abbrev = abbrev.strip(".")
        if abbrev in self.professors:
            return self.professors[abbrev]
        self.missing_professors.add(abbrev)
        if self.non_interactive or self.strict:
            return abbrev
        
        print(f"\n[CLI Prompt] Professor abbreviation '{abbrev}' is not mapped.")
        val = input(f"  --> Enter full name for professor '{abbrev}' [{abbrev}]: ").strip()
        if not val:
            val = abbrev
        self.professors[abbrev] = val
        self.save()
        return val

    def get_classroom_name(self, abbrev: Optional[str]) -> Optional[str]:
        if not abbrev:
            return None
        abbrev = abbrev.strip()
        if abbrev in self.classrooms:
            return self.classrooms[abbrev]
        self.missing_classrooms.add(abbrev)
        if self.non_interactive or self.strict:
            return abbrev
        
        print(f"\n[CLI Prompt] Classroom abbreviation '{abbrev}' is not mapped.")
        val = input(f"  --> Enter full name for classroom '{abbrev}' [{abbrev}]: ").strip()
        if not val:
            val = abbrev
        self.classrooms[abbrev] = val
        self.save()
        return val


class ScheduleParser:
    """Parses markdown content generated by Docling into structured slot objects."""
    def __init__(self, mapper: InteractiveMapper):
        self.mapper = mapper

    def _find_classroom_key(self, text: str) -> Optional[str]:
        sorted_keys = sorted(self.mapper.classrooms.keys(), key=len, reverse=True)
        for key in sorted_keys:
            if key in text:
                return key
        return None

    def parse_markdown(self, md_content: str) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
        lines = md_content.splitlines()
        
        global_matters_dict: Dict[str, Dict[str, Any]] = {}
        years_list: List[Dict[str, Any]] = []

        current_year: Optional[str] = None
        current_group: Optional[str] = None
        
        table_lines: List[str] = []

        def flush_table():
            nonlocal table_lines, current_year, current_group
            if not table_lines:
                return
            self._process_table(table_lines, current_year, current_group, global_matters_dict, years_list)
            table_lines = []

        for line in lines:
            line_str = line.strip()
            
            # Detect standalone Year / Group headers
            header_match = re.match(r'^(1º|2º|3º|4º)\s+([A-Za-z0-9ÁÉÍÓÚáéíóúñ\.\s]+)$', line_str)
            if header_match and not line_str.startswith('|'):
                flush_table()
                yr = header_match.group(1)
                grp_full = header_match.group(2).strip()
                current_year = yr
                current_group = clean_group_name(yr, grp_full)
                continue

            if line_str.startswith('|'):
                table_lines.append(line_str)
            else:
                flush_table()

        flush_table()
        
        global_matters = list(global_matters_dict.values())
        return global_matters, years_list

    def _process_table(self, table_lines: List[str], year: Optional[str], group: Optional[str],
                       global_matters_dict: Dict[str, Dict[str, Any]], years_list: List[Dict[str, Any]]):
        if len(table_lines) < 2:
            return

        header_idx = 0
        # Check if row 0 contains embedded group header (e.g. "| 1º D | 1º D | ... |")
        row0_cols = [c.strip() for c in table_lines[0].split('|')[1:-1]]
        row0_text = " ".join(row0_cols)
        hdr_match = re.search(r'\b([1234]º)\s+([A-Za-z0-9ÁÉÍÓÚáéíóúñ\.\s]+)', row0_text)
        if hdr_match:
            year = hdr_match.group(1)
            group = clean_group_name(year, hdr_match.group(2))
            header_idx = 1
            if header_idx < len(table_lines) and table_lines[header_idx].startswith('|--'):
                header_idx += 1

        if header_idx >= len(table_lines):
            return

        # Parse header row for days of the week
        headers = [h.strip() for h in table_lines[header_idx].split('|')[1:-1]]
        col_days: List[Optional[str]] = []
        valid_days_count = 0

        for h in headers:
            h_clean = h.lower()
            found_day = None
            for day_key, day_val in DAY_MAP.items():
                if day_key in h_clean:
                    found_day = day_val
                    valid_days_count += 1
                    break
            col_days.append(found_day)

        # Fallback for tables where Docling corrupted the day header line
        default_days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
        if valid_days_count < 3:
            col_days = [None]  # Index 0 corresponds to time column
            for idx in range(1, len(headers)):
                if idx - 1 < len(default_days):
                    col_days.append(default_days[idx - 1])
                else:
                    col_days.append(default_days[-1])

        raw_slots: List[Dict[str, Any]] = []

        # Keep track of preceding row's entry by (col_idx, day) to handle multi-row merged PDF cells
        prev_row_entries: Dict[Tuple[int, str], Dict[str, Any]] = {}

        for line in table_lines[header_idx + 1:]:
            if line.startswith('|--') or line.startswith('|:--'):
                continue
            cols = [c.strip() for c in line.split('|')[1:-1]]
            if not cols:
                continue

            time_col = cols[0]
            time_match = re.search(r'(\d{1,2}:\d{2})\s+(\d{1,2}:\d{2})', time_col)
            if not time_match:
                continue

            start_time = time_match.group(1)
            end_time = time_match.group(2)

            if len(start_time) == 4: start_time = "0" + start_time
            if len(end_time) == 4: end_time = "0" + end_time

            for col_idx in range(1, len(cols)):
                if col_idx >= len(col_days):
                    break
                day = col_days[col_idx]
                if not day:
                    continue

                cell_text = cols[col_idx]
                if not cell_text:
                    continue

                parsed_entries = self._parse_cell_entries(cell_text)
                
                # Check for continuation cell (classroom / professor without subject code, matching preceding slot)
                if not parsed_entries and prev_row_entries.get((col_idx, day)):
                    prev_entry = prev_row_entries[(col_idx, day)]
                    room_key = self._find_classroom_key(cell_text)
                    classroom = self.mapper.get_classroom_name(room_key) if room_key else None
                    prof_match = re.search(PROF_REGEX, cell_text)
                    prof_abbrev = prof_match.group(1) if prof_match else None

                    if classroom or prof_abbrev:
                        cont_entry = {
                            "code": prev_entry["code"],
                            "entryType": prev_entry["entryType"],
                            "classroom": classroom or prev_entry["classroom"],
                            "professor": prof_abbrev or prev_entry["professor"],
                            "groupName": prev_entry["groupName"],
                            "dayOfWeek": day,
                            "startTime": start_time,
                            "endTime": end_time,
                            "year": year,
                            "group": group
                        }
                        raw_slots.append(cont_entry)
                        prev_row_entries[(col_idx, day)] = cont_entry
                        continue

                for entry in parsed_entries:
                    entry['dayOfWeek'] = day
                    entry['startTime'] = start_time
                    entry['endTime'] = end_time
                    entry['year'] = year
                    entry['group'] = group
                    raw_slots.append(entry)
                    prev_row_entries[(col_idx, day)] = entry

        self._merge_and_store_slots(raw_slots, global_matters_dict, years_list)

    def _parse_cell_entries(self, cell_text: str) -> List[Dict[str, Any]]:
        entries = []
        cell_text = cell_text.replace('*', '').strip()
        if not cell_text:
            return entries

        if "PruebasProgreso" in cell_text or "Pruebas de" in cell_text:
            m = re.search(r'0\.02\+3-Charles', cell_text)
            raw_room = "0.02+3-Charles" if m else "0.02+3-Charles"
            room = self.mapper.get_classroom_name(raw_room)
            entries.append({
                "code": "PruebasProgreso",
                "entryType": "THEORY",
                "classroom": room,
                "professor": None,
                "groupName": None
            })
            return entries

        if "Conferencias" in cell_text:
            entries.append({
                "code": "Conferencias",
                "entryType": "THEORY",
                "classroom": self.mapper.get_classroom_name("Alan Turing"),
                "professor": None,
                "groupName": None
            })
            return entries

        sub_chunks = re.split(r'(?=\b(?:FunProg1|Calculo|Cálculo|Fisica|Física|TeCo|FunGesEmpr|FunGesEmp|OrCo|SSOO1|EstDatos|IngSw1|IngSw2|Lógica|Logica|ArCo|SistDistr|SisInt|SistInt|IPO1|ApDistInt|ComerElect|CompAvanz|DisSInterac|GesPrySw|IngNeg|MinerDat|Multim|ProcLeng|ProcesIS|QSistSw|SegRed|SegSisInf|SegSisSw|SisEmpot|SisMultAg|TecApAut|TecSWeb)\b)', cell_text)

        for chunk in sub_chunks:
            chunk = chunk.strip()
            if not chunk:
                continue

            is_lab = "-L" in chunk or "Lab-" in chunk or "Lab " in chunk
            entry_type = "LAB" if is_lab else "THEORY"

            code_match = re.search(r'\b(FunProg1|Calculo|Cálculo|Fisica|Física|TeCo|FunGesEmpr|FunGesEmp|OrCo|SSOO1|EstDatos|IngSw1|IngSw2|Lógica|Logica|ArCo|SistDistr|SisInt|SistInt|IPO1|ApDistInt|ComerElect|CompAvanz|DisSInterac|GesPrySw|IngNeg|MinerDat|Multim|ProcLeng|ProcesIS|QSistSw|SegRed|SegSisInf|SegSisSw|SisEmpot|SisMultAg|TecApAut|TecSWeb)\b', chunk)
            if not code_match:
                continue

            raw_code = code_match.group(1)
            code = raw_code
            if code == "FunGesEmp": code = "FunGesEmpr"
            if code == "Cálculo": code = "Calculo"
            if code == "Física": code = "Fisica"
            if code == "Logica": code = "Lógica"
            if code == "SistInt": code = "SisInt"

            lab_match = re.search(r'\b(Lab-[\w\/]+|Lab-B[CD]\d*)\b', chunk)
            group_name = lab_match.group(1) if lab_match else None

            room_key = self._find_classroom_key(chunk)
            classroom = self.mapper.get_classroom_name(room_key) if room_key else None

            prof_match = re.search(PROF_REGEX, chunk)
            prof_abbrev = prof_match.group(1) if prof_match else None

            entries.append({
                "code": code,
                "entryType": entry_type,
                "classroom": classroom,
                "professor": prof_abbrev,
                "groupName": group_name
            })

        return entries

    def _merge_and_store_slots(self, raw_slots: List[Dict[str, Any]],
                               global_matters_dict: Dict[str, Dict[str, Any]],
                               years_list: List[Dict[str, Any]]):
        # Step 1: Horizontal merge of slots at the exact same time
        time_buckets: Dict[Tuple, List[Dict[str, Any]]] = {}
        for s in raw_slots:
            tb_key = (
                s['year'],
                s['group'],
                s['code'],
                s['dayOfWeek'],
                s['entryType'],
                s['startTime'],
                s['endTime']
            )
            time_buckets.setdefault(tb_key, []).append(s)

        unique_raw: List[Dict[str, Any]] = []
        for tb_key, slots in time_buckets.items():
            merged_slots_for_bucket: List[Dict[str, Any]] = []
            for s in slots:
                merged_into_existing = False
                for existing in merged_slots_for_bucket:
                    # Check field compatibility
                    classroom_compat = (
                        existing['classroom'] is None or
                        s['classroom'] is None or
                        existing['classroom'] == s['classroom']
                    )
                    professor_compat = (
                        existing['professor'] is None or
                        s['professor'] is None or
                        existing['professor'] == s['professor']
                    )
                    group_name_compat = (
                        existing['groupName'] is None or
                        s['groupName'] is None or
                        existing['groupName'] == s['groupName']
                    )

                    if classroom_compat and professor_compat and group_name_compat:
                        # Merge compatible fields
                        if existing['classroom'] is None:
                            existing['classroom'] = s['classroom']
                        if existing['professor'] is None:
                            existing['professor'] = s['professor']
                        if existing['groupName'] is None:
                            existing['groupName'] = s['groupName']
                        merged_into_existing = True
                        break
                if not merged_into_existing:
                    # Append a copy to prevent mutation of the original dicts
                    merged_slots_for_bucket.append(dict(s))

            unique_raw.extend(merged_slots_for_bucket)

        # Bucket slots by (year, group, code, dayOfWeek, entryType)
        buckets: Dict[Tuple, List[Dict[str, Any]]] = {}
        for s in unique_raw:
            b_key = (s['year'], s['group'], s['code'], s['dayOfWeek'], s['entryType'])
            buckets.setdefault(b_key, []).append(s)

        final_slots: List[Dict[str, Any]] = []

        for b_key, slot_group in buckets.items():
            slot_group.sort(key=lambda x: x['startTime'])
            
            merged: List[Dict[str, Any]] = []
            for s in slot_group:
                if not merged:
                    merged.append(s)
                else:
                    last = merged[-1]
                    room_ok = (last['classroom'] is None or s['classroom'] is None or last['classroom'] == s['classroom'])
                    prof_ok = (last['professor'] is None or s['professor'] is None or last['professor'] == s['professor'])
                    group_ok = (last['groupName'] is None or s['groupName'] is None or last['groupName'] == s['groupName'])

                    if last['endTime'] == s['startTime'] and room_ok and prof_ok and group_ok:
                        last['endTime'] = s['endTime']
                        if not last['classroom'] and s['classroom']: last['classroom'] = s['classroom']
                        if not last['professor'] and s['professor']: last['professor'] = s['professor']
                        if not last['groupName'] and s['groupName']: last['groupName'] = s['groupName']
                    else:
                        merged.append(s)
            final_slots.extend(merged)

        for s in final_slots:
            code = s['code']
            prof_name = self.mapper.get_professor_name(s['professor'])
            matter_name = self.mapper.get_matter_name(code)

            slot_obj = {
                "dayOfWeek": s['dayOfWeek'],
                "startTime": s['startTime'],
                "endTime": s['endTime'],
                "classroom": s['classroom'],
                "groupName": s['groupName'],
                "entryType": s['entryType'],
                "professor": prof_name
            }

            if code in ["PruebasProgreso", "Conferencias"]:
                if code not in global_matters_dict:
                    global_matters_dict[code] = {
                        "code": code,
                        "name": matter_name,
                        "color": None,
                        "theorySlots": [],
                        "labVariants": {}
                    }
                existing = global_matters_dict[code]["theorySlots"]
                if not any(e['dayOfWeek'] == slot_obj['dayOfWeek'] and e['startTime'] == slot_obj['startTime'] for e in existing):
                    existing.append(slot_obj)
                continue

            yr_name = s['year'] or "1º"
            grp_name = s['group'] or "A"

            target_yr = None
            for y in years_list:
                if y['name'] == yr_name:
                    target_yr = y
                    break
            if not target_yr:
                target_yr = {"name": yr_name, "matters": [], "groups": []}
                years_list.append(target_yr)

            target_grp = None
            for g in target_yr['groups']:
                if g['name'] == grp_name:
                    target_grp = g
                    break
            if not target_grp:
                target_grp = {"name": grp_name, "matters": []}
                target_yr['groups'].append(target_grp)

            target_matter = None
            for m in target_grp['matters']:
                if m['code'] == code:
                    target_matter = m
                    break
            if not target_matter:
                target_matter = {
                    "code": code,
                    "name": matter_name,
                    "color": None,
                    "theorySlots": [],
                    "labVariants": {}
                }
                target_grp['matters'].append(target_matter)

            if s['entryType'] == "THEORY":
                target_matter["theorySlots"].append(slot_obj)
            else:
                var_name = s['groupName'] or "Lab-1"
                if var_name not in target_matter["labVariants"]:
                    target_matter["labVariants"][var_name] = []
                target_matter["labVariants"][var_name].append(slot_obj)


def convert_pdf_to_md(pdf_path: str, md_cache_path: str, force_pdf: bool = False) -> str:
    """Uses Docling to convert PDF to Markdown, caching output locally."""
    if not force_pdf and os.path.exists(md_cache_path):
        print(f"[Info] Reading cached Markdown file: {md_cache_path}")
        with open(md_cache_path, "r", encoding="utf-8") as f:
            return f.read()

    print(f"[Info] Converting PDF with Docling: {pdf_path} ...")
    try:
        from docling.document_converter import DocumentConverter
        converter = DocumentConverter()
        result = converter.convert(pdf_path)
        md_content = result.document.export_to_markdown()
        
        os.makedirs(os.path.dirname(os.path.abspath(md_cache_path)), exist_ok=True)
        with open(md_cache_path, "w", encoding="utf-8") as f:
            f.write(md_content)
        print(f"[Info] Cached intermediate Markdown saved to: {md_cache_path}")
        return md_content
    except ImportError:
        print("[Error] docling library is not installed. Run 'pip install docling'.")
        sys.exit(1)
    except Exception as e:
        print(f"[Error] Docling conversion failed: {e}")
        sys.exit(1)


def process_single_file(pdf_path: Optional[str], md_path: str, output_json_path: str,
                        mapper: InteractiveMapper, force_pdf: bool = False, md_only: bool = False) -> bool:
    """Processes a single PDF or MD file into JSON schedule."""
    if md_only or not pdf_path or not os.path.exists(pdf_path):
        if not os.path.exists(md_path):
            print(f"[Error] Markdown file not found: {md_path}")
            return False
        print(f"[Info] Processing MD file directly: {md_path}")
        with open(md_path, "r", encoding="utf-8") as f:
            md_content = f.read()
    else:
        md_content = convert_pdf_to_md(pdf_path, md_path, force_pdf=force_pdf)

    parser_engine = ScheduleParser(mapper=mapper)
    global_matters, years_list = parser_engine.parse_markdown(md_content)

    title = os.path.splitext(os.path.basename(output_json_path))[0].replace("_", " ")

    final_json = {
        "version": 1,
        "title": title,
        "matters": global_matters,
        "years": years_list
    }

    os.makedirs(os.path.dirname(os.path.abspath(output_json_path)), exist_ok=True)
    with open(output_json_path, "w", encoding="utf-8") as f:
        json.dump(final_json, f, indent=2, ensure_ascii=False)

    print(f"[Success] Schedule exported to: {output_json_path}")
    return True


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    schedules_dir = os.path.abspath(os.path.join(script_dir, ".."))

    default_pdf_dir = os.path.join(schedules_dir, "pdf")
    default_md_dir = os.path.join(schedules_dir, "md")
    default_dist_dir = os.path.join(schedules_dir, "dist")
    default_mappings_file = os.path.join(script_dir, "mappings.json")

    parser = argparse.ArgumentParser(description="Automated PDF/MD -> Mapped JSON schedule parser.")
    parser.add_argument("pdf_path", nargs="?", default=None, help="Path to specific input PDF file")
    parser.add_argument("-o", "--output", default=None, help="Path to output JSON file")
    parser.add_argument("--md-file", default=None, help="Path to intermediate MD cache file")
    parser.add_argument("--mappings-file", default=default_mappings_file, help="Path to mappings JSON file")
    parser.add_argument("--pdf-dir", default=default_pdf_dir, help="Directory containing source PDF files")
    parser.add_argument("--md-dir", default=default_md_dir, help="Directory containing MD files")
    parser.add_argument("--dist-dir", default=default_dist_dir, help="Directory for output JSON files")
    parser.add_argument("--all", action="store_true", help="Process all schedules in PDF/MD directories")
    parser.add_argument("--md-only", action="store_true", help="Process MD files only without running Docling")
    parser.add_argument("--non-interactive", action="store_true", help="Run without interactive CLI prompts")
    parser.add_argument("--strict", action="store_true", help="Fail with exit code 1 if unmapped symbols exist")
    parser.add_argument("--force-pdf", action="store_true", help="Force re-running Docling even if MD cache exists")

    args = parser.parse_args()

    mapper = InteractiveMapper(
        mapping_path=args.mappings_file,
        non_interactive=args.non_interactive,
        strict=args.strict
    )

    if args.pdf_path:
        base_name = os.path.splitext(os.path.basename(args.pdf_path))[0]
        md_path = args.md_file or os.path.join(args.md_dir, base_name + ".md")
        out_path = args.output or os.path.join(args.dist_dir, base_name + ".json")

        success = process_single_file(args.pdf_path, md_path, out_path, mapper, force_pdf=args.force_pdf, md_only=args.md_only)
        if not success:
            sys.exit(1)
    else:
        # Batch mode: scan pdf_dir or md_dir
        os.makedirs(args.md_dir, exist_ok=True)
        os.makedirs(args.dist_dir, exist_ok=True)

        targets = set()

        if os.path.exists(args.pdf_dir):
            for f in os.listdir(args.pdf_dir):
                if f.endswith(".pdf"):
                    targets.add(os.path.splitext(f)[0])

        if os.path.exists(args.md_dir):
            for f in os.listdir(args.md_dir):
                if f.endswith(".md"):
                    targets.add(os.path.splitext(f)[0])

        if not targets:
            print("[Info] No PDF or MD schedule files found to process.")
            return

        print(f"[Info] Found {len(targets)} schedule file(s) to process.")

        for base_name in sorted(targets):
            pdf_path = os.path.join(args.pdf_dir, base_name + ".pdf")
            md_path = os.path.join(args.md_dir, base_name + ".md")
            out_path = os.path.join(args.dist_dir, base_name + ".json")

            if not os.path.exists(pdf_path):
                pdf_path = None

            process_single_file(pdf_path, md_path, out_path, mapper, force_pdf=args.force_pdf, md_only=args.md_only)

    if (args.strict or args.non_interactive) and (mapper.missing_matters or mapper.missing_professors or mapper.missing_classrooms):
        print("\n[ERROR] Unmapped symbols encountered during conversion:")
        if mapper.missing_matters:
            print(f"  Unmapped Matter Codes ({len(mapper.missing_matters)}): {sorted(mapper.missing_matters)}")
        if mapper.missing_professors:
            print(f"  Unmapped Professors ({len(mapper.missing_professors)}): {sorted(mapper.missing_professors)}")
        if mapper.missing_classrooms:
            print(f"  Unmapped Classrooms ({len(mapper.missing_classrooms)}): {sorted(mapper.missing_classrooms)}")
        print("\n[FAIL] Strict/Non-interactive mode: Please run 'python3 process_schedules.py' locally to update mappings.json.")
        sys.exit(1)


if __name__ == "__main__":
    main()
