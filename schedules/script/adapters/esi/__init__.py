"""Production adapter for the ESI faculty in Ciudad Real."""

from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

from ...contract import (
    Candidate,
    FacultyKey,
    FacultySchedule,
    FacultySpec,
    ParseFailure,
    PeriodSpec,
    SourceFailure,
    Unchanged,
)
from . import source
from .normalize import normalize

ESI_KEY = FacultyKey("uclm", "esi")


def specification(today: date) -> FacultySpec:
    year = today.year if today.month >= 7 else today.year - 1
    academic_year = f"{year}-{year + 1}"
    return FacultySpec(
        key=ESI_KEY,
        university_name="Universidad de Castilla-La Mancha",
        faculty_name="Escuela Superior de Informática (Ciudad Real)",
        periods=tuple(
            PeriodSpec(
                f"{index}C", index, f"{ordinal} Cuatrimestre",
                f"Horario oficial ESI UCLM - {index}º Cuatrimestre ({academic_year})",
                f"{index}C.json",
            )
            for index, ordinal in ((1, "Primer"), (2, "Segundo"))
        ),
        fcm_topic_prefix="uclm_esi",
        subgroups_label="Laboratorios",
        legacy_index=True,
    )


@dataclass(frozen=True)
class EsiAdapter:
    input_dir: Path
    mappings_path: Path = Path(__file__).with_name("mappings.json")
    url: str = field(default_factory=source.source_url)

    @property
    def cached_input(self) -> Path:
        return self.input_dir / "schedules.json"

    def acquire(self, *, force: bool) -> Candidate[bytes] | Unchanged | SourceFailure:
        return source.acquire(self.url, self.cached_input, self.input_dir / "esi_meta.json", force=force)

    def read_local(self, fixture: Path | None) -> Candidate[bytes] | SourceFailure:
        return source.read_local(fixture if fixture is not None else self.cached_input)

    def normalize(self, payload: bytes, *, strict: bool) -> FacultySchedule | ParseFailure:
        return normalize(payload, ESI_KEY, self.mappings_path, strict=strict)
