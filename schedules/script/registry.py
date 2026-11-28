"""Explicit registration of production schedule adapters."""

from datetime import date
from pathlib import Path
from typing import Any

from .adapters import esi
from .contract import FacultyAdapter, FacultyKey, FacultySpec


def registered_faculties(schedules: Path, today: date) -> dict[FacultyKey, tuple[FacultySpec, FacultyAdapter[Any]]]:
    config = esi.load_config(today)
    adapter = esi.EsiAdapter(schedules / "input", url=config.source_url, key=config.spec.key)
    return {config.spec.key: (config.spec, adapter)}
