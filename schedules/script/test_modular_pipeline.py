"""Exercise the pipeline with a non-ESI source and invalid publication results."""

import csv
import json
import tempfile
import unittest
from dataclasses import replace
from datetime import date, time
from pathlib import Path
from unittest.mock import patch

from schedules.script.contract import (
    Candidate,
    FacultyKey,
    FacultySchedule,
    FacultySpec,
    ParseFailure,
    PeriodSpec,
    Session,
)
from schedules.script.pipeline import run_pipeline, validate_specs
from schedules.script.registry import registered_faculties

KEY = FacultyKey("mock-university", "mock-faculty")
PERIOD = PeriodSpec("P3", 3, "Study block", None, "mock-university/mock-faculty/P3.json")
SPEC = FacultySpec(KEY, "Mock University", "Mock Faculty", (PERIOD,), period_type="CUSTOM", period_count=3)
TODAY = date(2026, 10, 15)


class CsvAdapter:
    def acquire(self, *, force: bool):
        raise AssertionError("Fixture runs must stay offline.")

    def read_local(self, fixture: Path | None) -> Candidate[str]:
        if fixture is None:
            raise AssertionError("A CSV fixture path is required.")
        return Candidate(fixture.read_text(encoding="utf-8"))

    def normalize(self, payload: str, *, strict: bool) -> FacultySchedule:
        sessions = {}
        for row in csv.DictReader(payload.splitlines()):
            session = Session(
                row["course"], row["title"], row["class"], int(row["weekday"]),
                time.fromisoformat(row["from"]), time.fromisoformat(row["to"]),
                "lecture", "", "", False, "",
            )
            sessions.setdefault(row["block"], []).append(session)
        return FacultySchedule(KEY, {period: tuple(slots) for period, slots in sessions.items()})


class TestModularPipeline(unittest.TestCase):
    def setUp(self):
        self.root = Path(self.enterContext(tempfile.TemporaryDirectory()))
        self.fixture = self.root / "schedule.csv"
        self.dist = self.root / "dist"
        self.header = "block,course,title,class,weekday,from,to\n"
        self.fixture.write_text(self.header + "P3,101,Reading,A,7,08:00,09:30\n", encoding="utf-8")

    def run_csv(self, spec=SPEC):
        return run_pipeline(spec, CsvAdapter(), self.dist, TODAY, fixture=self.fixture, strict=True)

    def snapshot(self):
        return {path: path.read_bytes() for path in self.dist.rglob("*.json")}

    def test_non_esi_csv_publishes_only_its_declared_catalog_entry(self):
        self.assertNotIn(KEY, registered_faculties(self.root, TODAY))
        self.assertEqual(self.run_csv(), {PERIOD.path, "schedules_catalog.json"})
        self.assertFalse((self.dist / "schedules_index.json").exists())
        slot, = json.loads((self.dist / PERIOD.path).read_bytes())
        self.assertEqual((slot["codigo"], slot["asignatura"], slot["cuatrimestre"], slot["dia"], slot["hora_inicio"]),
                         ("101", "Reading", "P3", "Domingo", "08:00"))
        catalog = json.loads((self.dist / "schedules_catalog.json").read_bytes())
        university, = catalog["universities"]
        faculty, = university["faculties"]
        self.assertEqual((university["id"], faculty["id"]), (KEY.university_id, KEY.faculty_id))
        self.assertEqual(faculty["period_count"], 3)
        self.assertEqual(faculty["terms"][0]["path"], PERIOD.path)

    def test_invalid_or_empty_result_preserves_last_publication(self):
        self.run_csv()
        original = self.snapshot()
        for row in ("", "1C,101,Reading,A,7,08:00,09:30\n", "P3,101,Reading,A,8,08:00,09:30\n"):
            with self.subTest(row=row):
                self.fixture.write_text(self.header + row, encoding="utf-8")
                self.assertIsInstance(self.run_csv(), ParseFailure)
                self.assertEqual(self.snapshot(), original)

    def test_unsafe_duplicate_and_mismatched_period_configuration_is_rejected(self):
        for period in (
            replace(PERIOD, path="../outside.json"),
            replace(PERIOD, path="schedules_catalog.json"),
            replace(PERIOD, index=2),
        ):
            with self.subTest(period=period), self.assertRaises(ValueError):
                self.run_csv(replace(SPEC, periods=(period,)))
        other = replace(SPEC, key=FacultyKey("mock-university", "other"))
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            validate_specs((SPEC, other), self.dist.resolve())
        self.assertFalse(self.dist.exists())

    def test_failed_replacement_restores_previous_files(self):
        self.run_csv()
        original = self.snapshot()
        self.fixture.write_text(self.header + "P3,101,Writing,A,7,08:00,09:30\n", encoding="utf-8")
        replace_path = Path.replace

        def fail_catalog_replace(path, target):
            if target.name == "schedules_catalog.json":
                raise PermissionError("Simulated locked catalog")
            return replace_path(path, target)

        with patch.object(Path, "replace", fail_catalog_replace), self.assertRaises(PermissionError):
            self.run_csv()
        self.assertEqual(self.snapshot(), original)
