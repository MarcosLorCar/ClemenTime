"""The boundary between faculty adapters and schedule publication."""

from collections.abc import Mapping
from dataclasses import dataclass
from datetime import time
from pathlib import Path
from typing import Generic, Literal, Protocol, TypeVar

Payload = TypeVar("Payload")
FacultyStatus = Literal["published", "unchanged", "failed"]


@dataclass(frozen=True)
class FacultyKey:
    university_id: str
    faculty_id: str

    def __str__(self) -> str:
        return f"{self.university_id}/{self.faculty_id}"

    @classmethod
    def parse(cls, raw: str) -> "FacultyKey":
        parts = raw.strip().split("/")
        if len(parts) != 2 or not parts[0] or not parts[1]:
            raise ValueError(f"Invalid faculty key {raw!r}. Expected '<university_id>/<faculty_id>'.")
        return cls(parts[0], parts[1])


@dataclass(frozen=True)
class PeriodSpec:
    id: str
    index: int
    name: str
    description: str | None
    path: str
    allow_empty: bool = False


@dataclass(frozen=True)
class FacultySpec:
    key: FacultyKey
    university_name: str
    faculty_name: str
    periods: tuple[PeriodSpec, ...]
    fcm_topic_prefix: str | None = None
    subgroups_label: str | None = None
    period_type: str | None = None
    period_count: int | None = None
    legacy_index: bool = False
    removed_periods: tuple[str, ...] = ()


@dataclass(frozen=True)
class Session:
    subject_code: str
    subject_name: str
    group: str
    day: int
    start: time
    end: time
    kind: str
    room: str
    professor: str
    is_lab: bool
    subgroup: str


@dataclass(frozen=True)
class FacultySchedule:
    key: FacultyKey
    sessions_by_period: Mapping[str, tuple[Session, ...]]


@dataclass(frozen=True)
class Candidate(Generic[Payload]):
    payload: Payload


@dataclass(frozen=True)
class Unchanged:
    pass


@dataclass(frozen=True)
class SourceFailure:
    diagnostic: str


@dataclass(frozen=True)
class ParseFailure:
    diagnostic: str


@dataclass(frozen=True)
class FacultyOutcome:
    key: FacultyKey
    status: FacultyStatus
    changed_periods: tuple[str, ...] = ()
    written_files: tuple[str, ...] = ()
    removed_files: tuple[str, ...] = ()
    stage: str | None = None
    diagnostic: str | None = None

    def to_dict(self) -> dict[str, object]:
        return {
            "faculty": str(self.key),
            "status": self.status,
            "changed_periods": list(self.changed_periods),
            "written_files": list(self.written_files),
            "removed_files": list(self.removed_files),
            "stage": self.stage,
            "diagnostic": self.diagnostic,
        }


@dataclass(frozen=True)
class BatchResult:
    outcomes: tuple[FacultyOutcome, ...]
    written_files: frozenset[str] = frozenset()
    removed_files: frozenset[str] = frozenset()
    changed_schedule_faculties: tuple[FacultyKey, ...] = ()

    @property
    def has_failures(self) -> bool:
        return any(outcome.status == "failed" for outcome in self.outcomes)

    @property
    def all_failed(self) -> bool:
        return bool(self.outcomes) and all(outcome.status == "failed" for outcome in self.outcomes)

    @property
    def status(self) -> Literal["ok", "partial", "failed"]:
        if not self.has_failures:
            return "ok"
        if self.all_failed:
            return "failed"
        return "partial"

    def to_report_dict(self, *, mode: str) -> dict[str, object]:
        return {
            "mode": mode,
            "status": self.status,
            "changed_schedule_faculties": [str(key) for key in self.changed_schedule_faculties],
            "written_files": sorted(self.written_files),
            "removed_files": sorted(self.removed_files),
            "faculties": [outcome.to_dict() for outcome in self.outcomes],
        }


class FacultyAdapter(Protocol[Payload]):
    def acquire(self, *, force: bool) -> Candidate[Payload] | Unchanged | SourceFailure: ...

    def read_local(self, fixture: Path | None) -> Candidate[Payload] | SourceFailure: ...

    def normalize(self, payload: Payload, *, strict: bool) -> FacultySchedule | ParseFailure: ...
