"""Load declarative per-adapter faculty configuration from config.json."""

import json
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any

from .contract import FacultyKey, FacultySpec, PeriodSpec, default_period_path


@dataclass(frozen=True)
class FacultyConfig:
    spec: FacultySpec
    source_url: str
    raw: Mapping[str, Any]


def academic_year_label(today: date) -> str:
    year = today.year if today.month >= 7 else today.year - 1
    return f"{year}-{year + 1}"


def load_faculty_config(path: Path, today: date) -> FacultyConfig:
    """Parse an adapter's declarative config.json into a FacultySpec and source URL."""
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"Expected a JSON object in {path}.")

    key = FacultyKey(str(data["university_id"]).strip(), str(data["faculty_id"]).strip())
    academic_year = academic_year_label(today)

    raw_periods = data.get("periods")
    if not isinstance(raw_periods, list) or not raw_periods:
        raise ValueError(f"Missing non-empty 'periods' list in {path}.")

    periods: list[PeriodSpec] = []
    for item in raw_periods:
        if not isinstance(item, dict):
            raise ValueError(f"Each period in {path} must be a JSON object.")
        period_id = str(item["id"]).strip()
        period_path = item.get("path") or default_period_path(key, period_id)
        if "description_template" in item and item["description_template"] is not None:
            description = str(item["description_template"]).format(academic_year=academic_year)
        else:
            raw_desc = item.get("description")
            description = str(raw_desc) if raw_desc is not None else None
        periods.append(
            PeriodSpec(
                id=period_id,
                index=int(item["index"]),
                name=str(item["name"]),
                description=description,
                path=str(period_path),
                allow_empty=bool(item.get("allow_empty", False)),
            )
        )

    removed_raw = data.get("removed_periods") or ()
    spec = FacultySpec(
        key=key,
        university_name=str(data["university_name"]),
        faculty_name=str(data["faculty_name"]),
        periods=tuple(periods),
        fcm_topic_prefix=data.get("fcm_topic_prefix"),
        subgroups_label=data.get("subgroups_label"),
        period_type=data.get("period_type"),
        period_count=data.get("period_count"),
        legacy_index=bool(data.get("legacy_index", False)),
        removed_periods=tuple(str(pid) for pid in removed_raw),
    )

    source_url = str(data.get("source_url") or "").strip()
    if not source_url:
        raise ValueError(f"Missing non-empty 'source_url' in {path}.")

    return FacultyConfig(spec=spec, source_url=source_url, raw=data)
