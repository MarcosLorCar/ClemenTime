"""Production adapter for the ESI faculty in Ciudad Real."""

from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

from ...config import FacultyConfig, load_faculty_config
from ...contract import (
    Candidate,
    FacultyKey,
    FacultySchedule,
    FacultySpec,
    ParseFailure,
    SourceFailure,
    Unchanged,
)
from . import source
from .normalize import normalize

CONFIG_PATH = Path(__file__).with_name("config.json")
ESI_KEY = FacultyKey("uclm", "esi")


def load_config(today: date) -> FacultyConfig:
    return load_faculty_config(CONFIG_PATH, today)


def specification(today: date) -> FacultySpec:
    return load_config(today).spec


def _default_source_url() -> str:
    return load_config(date.today()).source_url


@dataclass(frozen=True)
class EsiAdapter:
    input_dir: Path
    mappings_path: Path = Path(__file__).with_name("mappings.json")
    url: str = field(default_factory=_default_source_url)
    key: FacultyKey = ESI_KEY

    @property
    def cached_input(self) -> Path:
        return self.input_dir / "schedules.json"

    def acquire(self, *, force: bool) -> Candidate[bytes] | Unchanged | SourceFailure:
        return source.acquire(self.url, self.cached_input, self.input_dir / "esi_meta.json", force=force)

    def read_local(self, fixture: Path | None) -> Candidate[bytes] | SourceFailure:
        return source.read_local(fixture if fixture is not None else self.cached_input)

    def normalize(self, payload: bytes, *, strict: bool) -> FacultySchedule | ParseFailure:
        return normalize(payload, self.key, self.mappings_path, strict=strict)
