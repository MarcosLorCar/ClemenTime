"""The boundary between faculty adapters and schedule publication."""

from collections.abc import Mapping
from dataclasses import dataclass
from datetime import time
from pathlib import Path
from typing import Generic, Protocol, TypeVar

Payload = TypeVar("Payload")


@dataclass(frozen=True)
class FacultyKey:
    university_id: str
    faculty_id: str

    def __str__(self) -> str:
        return f"{self.university_id}/{self.faculty_id}"


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


class FacultyAdapter(Protocol[Payload]):
    def acquire(self, *, force: bool) -> Candidate[Payload] | Unchanged | SourceFailure: ...

    def read_local(self, fixture: Path | None) -> Candidate[Payload] | SourceFailure: ...

    def normalize(self, payload: Payload, *, strict: bool) -> FacultySchedule | ParseFailure: ...
