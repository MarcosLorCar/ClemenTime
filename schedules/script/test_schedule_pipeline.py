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
from datetime import datetime
from pathlib import Path
from unittest.mock import patch

from schedules.script import generate_index, notify_fcm, parse_schedule

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT_DIR = SCRIPT_DIR.parents[1]
FIXTURES = SCRIPT_DIR / "fixtures" / "esi"
PERIOD_FILES = ("1C.json", "2C.json")
INDEX_FILES = ("schedules_index.json", "schedules_catalog.json")


class TestEsiPipelineContract(unittest.TestCase):
    def setUp(self):
        self.sandbox = Path(self.enterContext(tempfile.TemporaryDirectory()))
        self.dist = self.sandbox / "schedules" / "dist"

    def parse_fixture(self):
        with (
            patch.object(parse_schedule, "MAPPINGS_FILE", FIXTURES / "mappings.json"),
            patch.object(parse_schedule, "UNRESOLVED", {}),
            redirect_stdout(io.StringIO()),
        ):
            parse_schedule.process_json_schedule(FIXTURES / "source.json", interactive=False, output_dir=self.dist)

    def generate_indexes(self, day):
        with patch.object(generate_index, "datetime") as clock, redirect_stdout(io.StringIO()):
            clock.now.return_value = datetime(2026, 10, day)
            generate_index.generate_index(self.dist)

    def read_dist_json(self, name):
        return json.loads((self.dist / name).read_text(encoding="utf-8"))

    def test_parser_matches_published_slot_bytes(self):
        self.parse_fixture()
        self.assertEqual({path.name for path in self.dist.iterdir()}, set(PERIOD_FILES))
        for name in PERIOD_FILES:
            with self.subTest(period_file=name):
                # The current text-mode writer uses native line endings. Keep the reference
                # fixtures portable while checking all other serialization bytes.
                reference = (FIXTURES / name).read_text(encoding="utf-8")
                self.assertEqual((self.dist / name).read_bytes(), reference.replace("\n", os.linesep).encode("utf-8"))

    def test_catalog_legacy_index_and_fcm_share_esi_identity_and_hashes(self):
        self.parse_fixture()
        self.generate_indexes(day=15)
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
        self.generate_indexes(day=15)
        initial = {path.name: path.read_bytes() for path in self.dist.iterdir()}

        self.parse_fixture()
        self.generate_indexes(day=16)
        self.assertEqual({path.name: path.read_bytes() for path in self.dist.iterdir()}, initial)

        changed = self.dist / "2C.json"
        changed.write_bytes(initial[changed.name] + os.linesep.encode("ascii"))
        self.generate_indexes(day=17)
        legacy = self.read_dist_json(INDEX_FILES[0])
        terms = self.read_dist_json(INDEX_FILES[1])["universities"][0]["faculties"][0]["terms"]
        expected = [
            ("1C", hashlib.sha256(initial["1C.json"]).hexdigest(), "2026-10-15"),
            ("2C", hashlib.sha256(changed.read_bytes()).hexdigest(), "2026-10-17"),
        ]
        for entries in (legacy, terms):
            self.assertEqual([(item["id"], item["hash"], item["updatedTime"]) for item in entries], expected)

    def test_strict_cli_leaves_published_files_and_mappings_intact_on_unknown_codes(self):
        self.parse_fixture()
        self.generate_indexes(day=15)
        before = {path.name: path.read_bytes() for path in self.dist.iterdir()}
        sandbox_scripts = self.sandbox / "schedules" / "script"
        sandbox_scripts.mkdir()
        for name in ("parse_schedule.py", "generate_index.py"):
            shutil.copyfile(SCRIPT_DIR / name, sandbox_scripts / name)
        cli = self.sandbox / "process_schedules.py"
        shutil.copyfile(ROOT_DIR / cli.name, cli)
        mappings = sandbox_scripts / "mappings.json"
        original_mappings = (FIXTURES / mappings.name).read_bytes()
        mappings.write_bytes(original_mappings)

        source = self.sandbox / "unknown.json"
        entries = json.loads((FIXTURES / "source.json").read_text(encoding="utf-8"))
        # Change a valid 1C slot and introduce both kinds of unresolved mapping in 2C.
        entries[0]["hora_fin"] = "10:30"
        entries[6].update(asignatura="ZZZ999", profesor="XYZ123")
        source.write_text(json.dumps(entries), encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(cli), "--strict", str(source)],
            cwd=self.sandbox,
            env={**os.environ, "PYTHONIOENCODING": "utf-8"},
            stdin=subprocess.DEVNULL,
            capture_output=True,
            encoding="utf-8",
            timeout=15,
        )

        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("matters: ZZZ999", result.stderr)
        self.assertIn("professors: XYZ123", result.stderr)
        self.assertNotIn("Regenerating schedule index", result.stdout)
        self.assertEqual({path.name: path.read_bytes() for path in self.dist.iterdir()}, before)
        self.assertEqual(mappings.read_bytes(), original_mappings)
