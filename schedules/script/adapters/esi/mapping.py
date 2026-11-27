"""Resolve ESI subject codes and professor names against faculty mappings."""

import json
import re
import unicodedata
from difflib import SequenceMatcher
from pathlib import Path


def remove_accents(text: str) -> str:
    if not text:
        return ""
    nfkd_form = unicodedata.normalize("NFKD", text)
    return "".join([c for c in nfkd_form if not unicodedata.combining(c)])


class MappingResolver:
    def __init__(self, path: Path):
        self.path = path
        try:
            self.mappings = json.loads(path.read_text(encoding="utf-8"))
        except FileNotFoundError:
            self.mappings = {"matters": {}, "professors": {}, "classrooms": {}}
        self.unresolved: dict[str, set[str]] = {}

    def resolve_mapping(
        self,
        code: str,
        category: str,
        interactive: bool = True,
        track: bool = True,
    ) -> tuple[str, str]:
        if not code:
            return code, code

        category_map = self.mappings.setdefault(category, {})

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
                self.unresolved.setdefault(category, set()).add(code)
            return code, code

        print(f"\n[?] Unknown {category[:-1]} found: '{code}'", flush=True)
        val = input(f"    Enter full name for '{code}' (or press Enter to use as is): ").strip()
        val = val if val else code
        category_map[code] = val
        self.path.write_text(json.dumps(self.mappings, indent=2, ensure_ascii=False), encoding="utf-8")
        return code, val

    def get_mapping(
        self,
        code: str,
        category: str,
        interactive: bool = True,
        track: bool = True,
    ) -> str:
        _, val = self.resolve_mapping(code, category, interactive, track)
        return val

    def resolve_professors(self, prof_str: str, interactive: bool) -> str:
        if not prof_str:
            return ""
        prof_str = prof_str.strip()
        # track=False on the speculative lookups below: they are probes, and only the final
        # call decides whether this professor really went unresolved.
        full_match = self.get_mapping(prof_str, "professors", interactive=False, track=False)
        if full_match != prof_str:
            return full_match

        # Probe matching dotted parts (e.g. 'David.V', 'Jesus.F') against professor mapping entries
        prof_map = self.mappings.get("professors", {})
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
                self.get_mapping(token, "professors", interactive=False, track=False)
                for token in tokens
            ]
            if all(r != t for r, t in zip(resolved, tokens, strict=True)):
                return " ".join(resolved)
        return self.get_mapping(prof_str, "professors", interactive)
