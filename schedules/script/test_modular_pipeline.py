"""Exercise the modular pipeline with multi-faculty publication, partial failures, explicit removals, and safety checks."""

import csv
import hashlib
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from dataclasses import replace
from datetime import date, time
from pathlib import Path
from unittest.mock import patch

import process_schedules
from schedules.script.adapters.esi import ESI_KEY, EsiAdapter
from schedules.script.adapters.esi import specification as esi_specification
from schedules.script.config import load_faculty_config
from schedules.script.contract import (
    Candidate,
    FacultyKey,
    FacultySchedule,
    FacultySpec,
    ParseFailure,
    PeriodSpec,
    Session,
    SourceFailure,
)
from schedules.script.pipeline import format_batch_summary, run_batch_pipeline, run_pipeline, validate_specs
from schedules.script.publication import publish_batch
from schedules.script.registry import default_period_path, registered_faculties

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "esi"
KEY = FacultyKey("mock-university", "mock-faculty")
PERIOD = PeriodSpec("P3", 3, "Study block", None, default_period_path(KEY, "P3"))
SPEC = FacultySpec(KEY, "Mock University", "Mock Faculty", (PERIOD,), period_type="CUSTOM", period_count=3)
TODAY = date(2026, 10, 15)


class CsvAdapter:
    def __init__(self, fixture: Path | None = None, *, fail_source: str | None = None, fail_parse: str | None = None):
        self.fixture = fixture
        self.fail_source = fail_source
        self.fail_parse = fail_parse

    def acquire(self, *, force: bool):
        if self.fail_source is not None:
            return SourceFailure(self.fail_source)
        if self.fixture is None:
            raise AssertionError("Fixture runs must stay offline unless a fixture is configured.")
        return Candidate(self.fixture.read_text(encoding="utf-8"))

    def read_local(self, fixture: Path | None) -> Candidate[str] | SourceFailure:
        if self.fail_source is not None:
            return SourceFailure(self.fail_source)
        target = fixture if fixture is not None else self.fixture
        if target is None:
            raise AssertionError("A CSV fixture path is required.")
        return Candidate(target.read_text(encoding="utf-8"))

    def normalize(self, payload: str, *, strict: bool) -> FacultySchedule | ParseFailure:
        if self.fail_parse is not None:
            return ParseFailure(self.fail_parse)
        sessions: dict[str, list[Session]] = {}
        for row in csv.DictReader(payload.splitlines()):
            session = Session(
                row["course"], row["title"], row["class"], int(row["weekday"]),
                time.fromisoformat(row["from"]), time.fromisoformat(row["to"]),
                "lecture", "", "", False, "",
            )
            sessions.setdefault(row["block"], []).append(session)
        return FacultySchedule(KEY, {period: tuple(slots) for period, slots in sessions.items()})


class FailingAdapter:
    def __init__(self, *, source_error: str | None = None, parse_error: str | None = None):
        self.source_error = source_error
        self.parse_error = parse_error

    def acquire(self, *, force: bool):
        if self.source_error is not None:
            return SourceFailure(self.source_error)
        return Candidate(b"[]")

    def read_local(self, fixture: Path | None):
        if self.source_error is not None:
            return SourceFailure(self.source_error)
        return Candidate(b"[]")

    def normalize(self, payload, *, strict: bool):
        return ParseFailure(self.parse_error or "Simulated parse failure")


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
        return {path.relative_to(self.dist).as_posix(): path.read_bytes() for path in self.dist.rglob("*.json")}

    def seed_esi(self, day=15):
        today = date(2026, 10, day)
        spec = esi_specification(today)
        adapter = EsiAdapter(self.root / "esi-input", FIXTURES / "mappings.json", "https://example.invalid/schedules.json")
        with redirect_stdout(io.StringIO()):
            result = run_pipeline(spec, adapter, self.dist, today, fixture=FIXTURES / "source.json", strict=True)
        self.assertIsInstance(result, set)
        return spec

    def test_non_esi_csv_publishes_only_its_declared_catalog_entry(self):
        self.assertNotIn(KEY, registered_faculties(self.root, TODAY))
        self.assertEqual(self.run_csv(), {PERIOD.path, "schedules_catalog.json"})
        self.assertFalse((self.dist / "schedules_index.json").exists())
        slot, = json.loads((self.dist / PERIOD.path).read_bytes())
        self.assertEqual(
            (slot["codigo"], slot["asignatura"], slot["cuatrimestre"], slot["dia"], slot["hora_inicio"]),
            ("101", "Reading", "P3", "Domingo", "08:00"),
        )
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
            replace(PERIOD, path="/abs/P3.json"),
            replace(PERIOD, path="./P3.json"),
            replace(PERIOD, path="mock-university\\mock-faculty\\P3.json"),
            replace(PERIOD, path="mock-university//mock-faculty/P3.json"),
            replace(PERIOD, path="schedules_catalog.json"),
            replace(PERIOD, path="schedules_index.json"),
            replace(PERIOD, index=2),
        ):
            with self.subTest(period=period), self.assertRaises(ValueError):
                self.run_csv(replace(SPEC, periods=(period,)))

        # Path collision across faculties
        other = replace(SPEC, key=FacultyKey("mock-university", "other"))
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            validate_specs((SPEC, other), self.dist.resolve())

        # Case-insensitive path collision
        case_collision = replace(
            SPEC,
            key=FacultyKey("mock-university", "other"),
            periods=(replace(PERIOD, path="mock-university/mock-faculty/p3.json"),),
        )
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            validate_specs((SPEC, case_collision), self.dist.resolve())

        # Parent-child path collision
        child_collision = replace(
            SPEC,
            key=FacultyKey("mock-university", "other"),
            periods=(replace(PERIOD, path=f"{PERIOD.path}/nested.json"),),
        )
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            validate_specs((SPEC, child_collision), self.dist.resolve())

        # Duplicate faculty key
        with self.assertRaisesRegex(ValueError, "Duplicate faculty key"):
            validate_specs((SPEC, SPEC), self.dist.resolve())

        # Conflicting university names for same university_id
        conflicting_uni = replace(
            SPEC,
            key=FacultyKey("mock-university", "other"),
            university_name="Different Name",
            periods=(replace(PERIOD, path="mock-university/other/P3.json"),),
        )
        with self.assertRaisesRegex(ValueError, "Conflicting university names"):
            validate_specs((SPEC, conflicting_uni), self.dist.resolve())

        # Multiple legacy index owners
        esi_spec = esi_specification(TODAY)
        second_legacy = replace(
            SPEC,
            legacy_index=True,
        )
        with self.assertRaisesRegex(ValueError, "legacy index"):
            validate_specs((esi_spec, second_legacy), self.dist.resolve())

        # Conflicting FCM topic
        topic_collision = replace(
            SPEC,
            fcm_topic_prefix="uclm_esi",
        )
        with self.assertRaisesRegex(ValueError, "Conflicting FCM topic"):
            validate_specs((esi_spec, topic_collision), self.dist.resolve())

        # Overlapping removed_periods and active periods
        with self.assertRaisesRegex(ValueError, "removed period"):
            validate_specs((replace(SPEC, removed_periods=("P3",)),), self.dist.resolve())

        self.assertFalse(self.dist.exists())

    def test_symlink_escape_inside_dist_is_rejected(self):
        self.dist.mkdir(parents=True, exist_ok=True)
        outside = self.root / "outside"
        outside.mkdir()
        link = self.dist / "mock-university"
        try:
            link.symlink_to(outside, target_is_directory=True)
        except OSError:
            # Windows without symlink privileges: verify the symlink guard via mock.
            with patch.object(Path, "is_symlink", side_effect=lambda self=None: True):
                with self.assertRaisesRegex(ValueError, "Symlink|regular files"):
                    validate_specs((SPEC,), self.dist.resolve())
            return

        with self.assertRaisesRegex(ValueError, "Symlink|Conflicting or unsafe"):
            validate_specs((SPEC,), self.dist.resolve())

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

    def test_esi_failure_allows_other_faculty_to_progress_without_erasing_esi(self):
        esi_spec = self.seed_esi(day=15)
        esi_1c_before = (self.dist / "1C.json").read_bytes()
        esi_2c_before = (self.dist / "2C.json").read_bytes()
        legacy_before = (self.dist / "schedules_index.json").read_bytes()

        batch = run_batch_pipeline(
            entries=(
                (esi_spec, FailingAdapter(source_error="ESI endpoint returned 503")),
                (SPEC, CsvAdapter(self.fixture)),
            ),
            dist=self.dist,
            today=date(2026, 10, 16),
            remote=True,
            strict=True,
        )

        self.assertEqual(batch.status, "partial")
        self.assertTrue(batch.has_failures)
        self.assertFalse(batch.all_failed)
        self.assertEqual(batch.changed_schedule_faculties, (KEY,))
        self.assertEqual((self.dist / "1C.json").read_bytes(), esi_1c_before)
        self.assertEqual((self.dist / "2C.json").read_bytes(), esi_2c_before)
        self.assertEqual((self.dist / "schedules_index.json").read_bytes(), legacy_before)
        self.assertTrue((self.dist / PERIOD.path).is_file())

        catalog = json.loads((self.dist / "schedules_catalog.json").read_text(encoding="utf-8"))
        uni_ids = {u["id"]: u for u in catalog["universities"]}
        self.assertIn("uclm", uni_ids)
        self.assertIn(KEY.university_id, uni_ids)
        self.assertEqual(uni_ids["uclm"]["faculties"][0]["id"], "esi")
        self.assertEqual(len(uni_ids["uclm"]["faculties"][0]["terms"]), 2)
        self.assertEqual(uni_ids[KEY.university_id]["faculties"][0]["id"], KEY.faculty_id)

        summary = format_batch_summary(batch, mode="sync")
        self.assertIn("Overall status: **partial**", summary)
        self.assertIn("`uclm/esi` | **failed**", summary)
        self.assertIn("`mock-university/mock-faculty` | **published**", summary)

    def test_initial_failure_of_new_faculty_does_not_add_empty_entry_to_catalog(self):
        esi_spec = self.seed_esi(day=15)
        esi_adapter = EsiAdapter(self.root / "esi-input", FIXTURES / "mappings.json", "https://example.invalid/schedules.json")
        (self.root / "esi-input").mkdir(parents=True, exist_ok=True)
        (self.root / "esi-input" / "schedules.json").write_bytes((FIXTURES / "source.json").read_bytes())
        before = self.snapshot()

        with redirect_stdout(io.StringIO()):
            batch = run_batch_pipeline(
                entries=(
                    (esi_spec, esi_adapter),
                    (SPEC, FailingAdapter(parse_error="HTML structure changed")),
                ),
                dist=self.dist,
                today=date(2026, 10, 16),
                remote=False,
                strict=True,
            )

        self.assertEqual(batch.status, "partial")
        self.assertEqual(batch.changed_schedule_faculties, ())
        self.assertEqual(self.snapshot(), before)
        catalog = json.loads((self.dist / "schedules_catalog.json").read_text(encoding="utf-8"))
        self.assertEqual([u["id"] for u in catalog["universities"]], ["uclm"])
        self.assertFalse((self.dist / PERIOD.path).exists())

    def test_explicit_period_removal_required_and_unexpected_missing_period_rejected(self):
        p1 = PeriodSpec("1C", 1, "First", None, default_period_path(KEY, "1C"))
        p2 = PeriodSpec("2C", 2, "Second", None, default_period_path(KEY, "2C"))
        two_period_spec = replace(SPEC, periods=(p1, p2), period_type="SEMESTERS", period_count=2)
        self.fixture.write_text(
            self.header + "1C,101,Alpha,A,1,09:00,10:30\n2C,201,Beta,A,2,09:00,10:30\n",
            encoding="utf-8",
        )
        run_pipeline(two_period_spec, CsvAdapter(), self.dist, TODAY, fixture=self.fixture, strict=True)
        snapshot_both = self.snapshot()

        # 1) Dropping 2C from spec without explicit removal must fail and keep files intact.
        one_period_spec = replace(two_period_spec, periods=(p1,))
        with self.assertRaisesRegex(ValueError, "Refusing to remove previously published period '2C'"):
            publish_batch((one_period_spec,), self.dist, date(2026, 10, 16), {})
        self.assertEqual(self.snapshot(), snapshot_both)

        # 2) Unexpected missing file on disk for previously published 2C must also fail.
        (self.dist / p2.path).unlink()
        with self.assertRaisesRegex(ValueError, "Previously published period '2C'.*missing"):
            publish_batch((two_period_spec,), self.dist, date(2026, 10, 16), {})

        # Restore file, then explicitly authorize removing 2C via removed_periods.
        (self.dist / p2.path).write_bytes(snapshot_both[p2.path])
        explicit_spec = replace(one_period_spec, removed_periods=("2C",))
        res = publish_batch((explicit_spec,), self.dist, date(2026, 10, 16), {})
        self.assertEqual(res.status, "ok")
        self.assertEqual(res.changed_schedule_faculties, (KEY,))
        self.assertEqual(res.removed_files, frozenset({p2.path}))
        self.assertFalse((self.dist / p2.path).exists())
        catalog = json.loads((self.dist / "schedules_catalog.json").read_text(encoding="utf-8"))
        terms = catalog["universities"][0]["faculties"][0]["terms"]
        self.assertEqual([t["id"] for t in terms], ["1C"])

        # Explicitly removing the final remaining period removes the faculty cleanly without
        # listing a missing catalog entry in changed_schedule_faculties.
        retire_all = publish_batch((replace(one_period_spec, removed_periods=("2C",)),), self.dist, date(2026, 10, 17), {}, remove_periods={KEY: ("1C",)})
        self.assertEqual(retire_all.status, "ok")
        self.assertEqual(retire_all.removed_files, frozenset({p1.path}))
        self.assertEqual(retire_all.changed_schedule_faculties, ())
        self.assertEqual(json.loads((self.dist / "schedules_catalog.json").read_text(encoding="utf-8"))["universities"], [])

    def test_manual_dist_edit_index_only_preserves_other_faculties_and_foreign_entries(self):
        esi_spec = self.seed_esi(day=15)
        self.run_csv()
        unpublished_key = FacultyKey("mock-university", "unpublished")
        unpublished_spec = FacultySpec(
            unpublished_key,
            "Mock University",
            "Unpublished Faculty",
            (PeriodSpec("1C", 1, "First", None, default_period_path(unpublished_key, "1C")),),
        )

        # Inject a foreign (unregistered) faculty entry into schedules_catalog.json.
        catalog_path = self.dist / "schedules_catalog.json"
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        foreign_uni = {
            "id": "external",
            "name": "External Community University",
            "faculties": [{
                "id": "community",
                "name": "Community Maintained Faculty",
                "terms": [{
                    "id": "1C",
                    "name": "1C",
                    "description": "Hand maintained",
                    "path": "external/community/1C.json",
                    "hash": "foreign-hash-123",
                    "updatedTime": "2026-09-01",
                }],
            }],
        }
        catalog["universities"].append(foreign_uni)
        catalog_path.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
        legacy_before = (self.dist / "schedules_index.json").read_bytes()

        # Simulate a manual edit to ONLY the non-ESI faculty's dist JSON.
        non_esi_file = self.dist / PERIOD.path
        edited_bytes = non_esi_file.read_bytes() + os.linesep.encode("ascii")
        non_esi_file.write_bytes(edited_bytes)

        result = publish_batch(
            specs=(esi_spec, SPEC, unpublished_spec),
            dist=self.dist,
            today=date(2026, 10, 20),
            schedules_by_faculty={},
            index_only=True,
            isolate_failures=True,
        )

        self.assertEqual(result.status, "ok")
        self.assertEqual(result.changed_schedule_faculties, (KEY,))
        self.assertEqual((self.dist / "schedules_index.json").read_bytes(), legacy_before)

        updated_catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        unis = {u["id"]: u for u in updated_catalog["universities"]}
        # ESI preserved untouched with original updatedTime
        self.assertEqual(unis["uclm"]["faculties"][0]["terms"][0]["updatedTime"], "2026-10-15")
        # Non-ESI updated with new hash and updatedTime; unpublished NOT added
        mock_facs = {f["id"]: f for f in unis[KEY.university_id]["faculties"]}
        self.assertEqual(set(mock_facs), {KEY.faculty_id})
        self.assertEqual(mock_facs[KEY.faculty_id]["terms"][0]["hash"], hashlib.sha256(edited_bytes).hexdigest())
        self.assertEqual(mock_facs[KEY.faculty_id]["terms"][0]["updatedTime"], "2026-10-20")
        # Foreign entry preserved verbatim
        self.assertEqual(unis["external"], foreign_uni)

    def test_cli_all_partial_failure_returns_exit_2_and_writes_report_and_summary(self):
        esi_spec = self.seed_esi(day=15)
        fake_registry = {
            ESI_KEY: (esi_spec, FailingAdapter(source_error="Simulated ESI timeout")),
            KEY: (SPEC, CsvAdapter(self.fixture)),
        }
        report_path = self.root / "report.json"
        summary_path = self.root / "summary.md"

        with (
            patch.object(process_schedules, "registered_faculties", return_value=fake_registry),
            redirect_stdout(io.StringIO()),
            redirect_stderr(io.StringIO()),
        ):
            rc = process_schedules.main([
                "--all",
                "--sync",
                "--strict",
                "--dist-dir", str(self.dist),
                "--report", str(report_path),
                "--summary-file", str(summary_path),
            ])

        self.assertEqual(rc, process_schedules.EXIT_PARTIAL)
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual(report["status"], "partial")
        self.assertEqual(report["changed_schedule_faculties"], [str(KEY)])
        summary = summary_path.read_text(encoding="utf-8")
        self.assertIn("Overall status: **partial**", summary)
        self.assertIn("Simulated ESI timeout", summary)

    def test_declarative_faculty_config_loads_defaults_and_rejects_missing_fields(self):
        cfg_path = self.root / "config.json"
        cfg_path.write_text(
            json.dumps({
                "university_id": "uclm",
                "university_name": "Universidad de Castilla-La Mancha",
                "faculty_id": "sample",
                "faculty_name": "Sample Faculty",
                "fcm_topic_prefix": "uclm_sample",
                "source_url": "https://example.invalid/sample/horarios",
                "period_type": "SEMESTERS",
                "period_count": 2,
                "periods": [
                    {
                        "id": "1C",
                        "index": 1,
                        "name": "Primer Cuatrimestre",
                        "description_template": "Sample 1C ({academic_year})",
                    },
                ],
            }),
            encoding="utf-8",
        )
        loaded = load_faculty_config(cfg_path, TODAY)
        self.assertEqual(loaded.source_url, "https://example.invalid/sample/horarios")
        self.assertEqual(loaded.spec.key, FacultyKey("uclm", "sample"))
        self.assertEqual(loaded.spec.periods[0].path, "uclm/sample/1C.json")
        self.assertEqual(loaded.spec.periods[0].description, "Sample 1C (2026-2027)")
        self.assertFalse(loaded.spec.legacy_index)

        # Missing source_url is rejected visibly.
        cfg_path.write_text(
            json.dumps({
                "university_id": "uclm",
                "university_name": "UCLM",
                "faculty_id": "sample",
                "faculty_name": "Sample",
                "periods": [{"id": "1C", "index": 1, "name": "1C"}],
            }),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "source_url"):
            load_faculty_config(cfg_path, TODAY)
