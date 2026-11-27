"""Explicit registration of production schedule adapters."""

from datetime import date
from pathlib import Path
from typing import Any

from .adapters import esi
from .contract import FacultyAdapter, FacultyKey, FacultySpec


def default_period_path(key: FacultyKey, period_id: str) -> str:
    """Standard nested distribution path for non-legacy faculties."""
    return f"{key.university_id}/{key.faculty_id}/{period_id}.json"


def registered_faculties(schedules: Path, today: date) -> dict[FacultyKey, tuple[FacultySpec, FacultyAdapter[Any]]]:
    spec = esi.specification(today)
    adapter = esi.EsiAdapter(schedules / "input")
    return {spec.key: (spec, adapter)}
