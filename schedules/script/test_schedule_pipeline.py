"""Offline characterization of the ESI parser and its public distribution files."""

import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from datetime import date
from pathlib import Path

from schedules.script import notify_fcm
from schedules.script.adapters.esi import EsiAdapter, specification
from schedules.script.pipeline import run_batch_pipeline
from schedules.script.publication import publish_batch

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT_DIR = SCRIPT_DIR.parents[1]
FIXTURES = SCRIPT_DIR / "fixtures" / "esi"
PERIOD_FILES = ("1C.json", "2C.json")
INDEX_FILES = ("schedules_index.json", "schedules_catalog.json")


class TestEsiPipelineContract(unittest.TestCase):
    def setUp(self):
        self.sandbox = Path(self.enterContext(tempfile.TemporaryDirectory()))
        self.dist = self.sandbox / "schedules" / "dist"

    def parse_fixture(self, day=15, fixture=FIXTURES / "source.json"):
        today = date(2026, 10, day)
        spec = specification(today)
        adapter = EsiAdapter(self.sandbox, FIXTURES / "mappings.json", "https://example.invalid/schedules.json")
        with redirect_stdout(io.StringIO()):
            result = run_batch_pipeline(
                entries=((spec, adapter),),
                dist=self.dist,
                today=today,
                fixture_by_faculty={spec.key: fixture},
                strict=True,
            )
        self.assertEqual(result.status, "ok", result.outcomes)
        return result

    def generate_indexes(self, day):
        today = date(2026, 10, day)
        result = publish_batch((specification(today),), self.dist, today, {})
        self.assertEqual(result.status, "ok", result.outcomes)

    def read_dist_json(self, name):
        return json.loads((self.dist / name).read_text(encoding="utf-8"))

    def test_parser_matches_published_slot_bytes(self):
        self.parse_fixture()
        self.assertEqual({path.name for path in self.dist.iterdir()}, set(PERIOD_FILES + INDEX_FILES))
        for name in PERIOD_FILES:
            with self.subTest(period_file=name):
                published = (self.dist / name).read_bytes()
                self.assertNotIn(b"\r\n", published)
                reference = (FIXTURES / name).read_text(encoding="utf-8").encode("utf-8")
                self.assertEqual(published, reference)

    def test_catalog_legacy_index_and_fcm_share_esi_identity_and_hashes(self):
        self.parse_fixture()
        hashes = {
            Path(name).stem: hashlib.sha256((self.dist / name).read_bytes()).hexdigest()
            for name in PERIOD_FILES
        }
        legacy = [
            {
                "id": f"{number}C",
                "title": f"{ordinal} Cuatrimestre",
                "description": f"Horario oficial ESI UCLM - {number}º Cuatrimestre (2026-2027)",
                "path": f"{number}C.json",
                "hash": hashes[f"{number}C"],
                "updatedTime": "2026-10-15",
            }
            for number, ordinal in [(1, "Primer"), (2, "Segundo")]
        ]
        self.assertEqual(self.read_dist_json(INDEX_FILES[0]), legacy)
        catalog = self.read_dist_json(INDEX_FILES[1])
        self.assertEqual(catalog, {
            "version": 2,
            "universities": [{
                "id": "uclm",
                "name": "Universidad de Castilla-La Mancha",
                "faculties": [{
                    "id": "esi",
                    "name": "Escuela Superior de Informática (Ciudad Real)",
                    "fcm_topic_prefix": "uclm_esi",
                    "subgroups_label": "Laboratorios",
                    "terms": [
                        {**{key: value for key, value in entry.items() if key != "title"}, "name": entry["title"]}
                        for entry in legacy
                    ],
                }],
            }],
        })
        timestamp = "2026-10-15T00:00:00+00:00"
        self.assertEqual(notify_fcm.build_fcm_messages(catalog, updated_at=timestamp), [{
            "message": {
                "topic": "sched_uclm_esi",
                "data": {
                    "university": "uclm",
                    "faculty": "esi",
                    "updated_at": timestamp,
                    "hash_1C": hashes["1C"],
                    "hash_2C": hashes["2C"],
                },
                "android": {"priority": "high"},
            },
        }])

    def test_regeneration_keeps_hash_and_updated_time_until_period_bytes_change(self):
        self.parse_fixture()
        initial = {path.name: path.read_bytes() for path in self.dist.iterdir()}

        self.parse_fixture(day=16)
        self.assertEqual({path.name: path.read_bytes() for path in self.dist.iterdir()}, initial)

        changed = self.dist / "2C.json"
        changed.write_bytes(initial[changed.name] + b"\n")
        self.generate_indexes(day=17)
        legacy = self.read_dist_json(INDEX_FILES[0])
        terms = self.read_dist_json(INDEX_FILES[1])["universities"][0]["faculties"][0]["terms"]
        expected = [
            ("1C", hashlib.sha256(initial["1C.json"]).hexdigest(), "2026-10-15"),
            ("2C", hashlib.sha256(changed.read_bytes()).hexdigest(), "2026-10-17"),
        ]
        for entries in (legacy, terms):
            self.assertEqual([(item["id"], item["hash"], item["updatedTime"]) for item in entries], expected)

    def sandbox_cli(self):
        sandbox_scripts = self.sandbox / "schedules" / "script"
        shutil.copytree(
            SCRIPT_DIR, sandbox_scripts,
            ignore=shutil.ignore_patterns("__pycache__", "fixtures", "test_*"),
        )
        cli = self.sandbox / "process_schedules.py"
        shutil.copyfile(ROOT_DIR / cli.name, cli)
        shutil.copyfile(FIXTURES / "mappings.json", sandbox_scripts / "adapters" / "esi" / "mappings.json")
        return cli

    def run_cli(self, cli, *args):
        return subprocess.run(
            [sys.executable, str(cli), *args],
            cwd=self.sandbox,
            env={**os.environ, "PYTHONIOENCODING": "utf-8"},
            stdin=subprocess.DEVNULL,
            capture_output=True,
            encoding="utf-8",
            timeout=15,
        )

    def test_strict_cli_leaves_published_files_and_mappings_intact_on_unknown_codes(self):
        self.parse_fixture()
        before = {path.name: path.read_bytes() for path in self.dist.iterdir()}
        cli = self.sandbox_cli()
        mappings = self.sandbox / "schedules" / "script" / "adapters" / "esi" / "mappings.json"
        original_mappings = mappings.read_bytes()
        source = self.sandbox / "unknown.json"
        entries = json.loads((FIXTURES / "source.json").read_text(encoding="utf-8"))
        # Change a valid 1C slot and introduce both kinds of unresolved mapping in 2C.
        entries[0]["hora_fin"] = "10:30"
        entries[6].update(asignatura="ZZZ999", profesor="XYZ123")
        source.write_text(json.dumps(entries), encoding="utf-8")
        result = self.run_cli(cli, "--strict", str(source))

        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("matters: ZZZ999", result.stderr)
        self.assertIn("professors: XYZ123", result.stderr)
        self.assertNotIn("[Published]", result.stdout)
        self.assertEqual({path.name: path.read_bytes() for path in self.dist.iterdir()}, before)
        self.assertEqual(mappings.read_bytes(), original_mappings)

    def test_explicit_faculty_and_fixture_reprocess_offline(self):
        cli = self.sandbox_cli()
        result = self.run_cli(cli, "--faculty", "uclm/esi", "--strict", "--fixture", str(FIXTURES / "source.json"))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({path.name for path in self.dist.iterdir()}, set(PERIOD_FILES + INDEX_FILES))

    def test_partial_input_keeps_previous_period(self):
        self.parse_fixture()
        original = (self.dist / "2C.json").read_bytes()
        source = self.sandbox / "one-period.json"
        entries = json.loads((FIXTURES / "source.json").read_text(encoding="utf-8"))
        source.write_text(json.dumps([entry for entry in entries if entry["cuatrimestre"] == "1C"]), encoding="utf-8")
        self.parse_fixture(day=16, fixture=source)
        self.assertEqual((self.dist / "2C.json").read_bytes(), original)
        self.assertEqual(self.read_dist_json(INDEX_FILES[0])[1]["updatedTime"], "2026-10-15")

    def test_committed_dist_files_have_lf_and_match_catalog_and_legacy_hashes(self):
        dist_dir = ROOT_DIR / "schedules" / "dist"
        catalog_path = dist_dir / "schedules_catalog.json"
        legacy_path = dist_dir / "schedules_index.json"

        for index_path in (catalog_path, legacy_path):
            raw = index_path.read_bytes()
            self.assertNotIn(b"\r\n", raw, f"{index_path} must use LF line endings")

        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        self.assertEqual(catalog.get("version"), 2)
        term_count = 0
        for uni in catalog.get("universities", []):
            for faculty in uni.get("faculties", []):
                for term in faculty.get("terms", []):
                    term_count += 1
                    period_file = dist_dir / term["path"]
                    self.assertTrue(period_file.is_file(), f"Missing committed period file: {term['path']}")
                    raw = period_file.read_bytes()
                    self.assertNotIn(b"\r\n", raw, f"{term['path']} must use LF line endings")
                    self.assertEqual(
                        hashlib.sha256(raw).hexdigest(),
                        term["hash"],
                        f"SHA-256 mismatch for committed {term['path']} vs schedules_catalog.json",
                    )
        self.assertGreater(term_count, 0)

        legacy = json.loads(legacy_path.read_text(encoding="utf-8"))
        for entry in legacy:
            period_file = dist_dir / entry["path"]
            raw = period_file.read_bytes()
            self.assertEqual(
                hashlib.sha256(raw).hexdigest(),
                entry["hash"],
                f"SHA-256 mismatch for committed {entry['path']} vs schedules_index.json",
            )

        for fixture_name in PERIOD_FILES:
            self.assertNotIn(b"\r\n", (FIXTURES / fixture_name).read_bytes())
