#!/usr/bin/env python3
"""
Unit tests for check_esi_update.py verifying resilience against network failures,
upstream HTTP 403/5xx errors, corrupted payloads, and retry/fail-on-error behaviors.
"""

import io
import json
import os
import shutil
import tempfile
import unittest
import urllib.error
from unittest.mock import MagicMock, patch

import schedules.script.check_esi_update as check_esi


class TestCheckEsiUpdate(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.input_dir = os.path.join(self.test_dir, "input")
        os.makedirs(self.input_dir, exist_ok=True)

        self.meta_file = os.path.join(self.input_dir, "esi_meta.json")
        self.input_json = os.path.join(self.input_dir, "schedules.json")
        self.gh_output_file = os.path.join(self.test_dir, "github_output.txt")

        # Patch globals in check_esi module
        self.patchers = [
            patch.object(check_esi, "INPUT_DIR", self.input_dir),
            patch.object(check_esi, "META_FILE", self.meta_file),
            patch.object(check_esi, "INPUT_JSON", self.input_json),
            patch.dict(os.environ, {"GITHUB_OUTPUT": self.gh_output_file}),
        ]
        for p in self.patchers:
            p.start()

        # Seed initial existing schedule & meta
        self.sample_schedules = [{"asignatura": "Test Subject", "codigo": "TEST"}]
        with open(self.input_json, "w", encoding="utf-8") as f:
            json.dump(self.sample_schedules, f)

        self.initial_meta = {
            "url": "https://esi.uclm.es/TV/hall/horarios.json",
            "last_modified": "Mon, 19 Oct 2026 07:06:11 GMT",
            "etag": '"etag-initial"',
            "sha256": check_esi.compute_sha256(json.dumps(self.sample_schedules).encode("utf-8")),
        }
        with open(self.meta_file, "w", encoding="utf-8") as f:
            json.dump(self.initial_meta, f)

    def tearDown(self):
        for p in reversed(self.patchers):
            p.stop()
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def read_github_output(self):
        if not os.path.exists(self.gh_output_file):
            return ""
        with open(self.gh_output_file, "r", encoding="utf-8") as f:
            return f.read()

    @patch("schedules.script.check_esi_update.time.sleep")
    @patch("schedules.script.check_esi_update.urllib.request.urlopen")
    def test_head_http_403_exits_cleanly_with_no_update_by_default(self, mock_urlopen, mock_sleep):
        """When ESI server returns HTTP 403 on HEAD, check_esi_update should exit 0 and emit has_update=false."""
        mock_urlopen.side_effect = urllib.error.HTTPError(
            url="https://esi.uclm.es/TV/hall/horarios.json",
            code=403,
            msg="Forbidden",
            hdrs={},
            fp=None,
        )

        with patch("sys.argv", ["check_esi_update.py"]):
            with self.assertRaises(SystemExit) as cm:
                check_esi.main()
            self.assertEqual(cm.exception.code, 0)

        output = self.read_github_output()
        self.assertIn("has_update=false", output)

        # Existing files must remain intact
        with open(self.meta_file, "r", encoding="utf-8") as f:
            meta = json.load(f)
        self.assertEqual(meta["etag"], '"etag-initial"')

    @patch("schedules.script.check_esi_update.time.sleep")
    @patch("schedules.script.check_esi_update.urllib.request.urlopen")
    def test_head_http_403_with_fail_on_error_exits_1(self, mock_urlopen, mock_sleep):
        """When --fail-on-error is supplied, HTTP 403 should exit with code 1."""
        mock_urlopen.side_effect = urllib.error.HTTPError(
            url="https://esi.uclm.es/TV/hall/horarios.json",
            code=403,
            msg="Forbidden",
            hdrs={},
            fp=None,
        )

        with patch("sys.argv", ["check_esi_update.py", "--fail-on-error"]):
            with self.assertRaises(SystemExit) as cm:
                check_esi.main()
            self.assertEqual(cm.exception.code, 1)

    @patch("schedules.script.check_esi_update.time.sleep")
    @patch("schedules.script.check_esi_update.urllib.request.urlopen")
    def test_head_network_timeout_exits_cleanly(self, mock_urlopen, mock_sleep):
        """When connection times out, script should exit 0 and emit has_update=false."""
        mock_urlopen.side_effect = urllib.error.URLError("Connection timed out")

        with patch("sys.argv", ["check_esi_update.py"]):
            with self.assertRaises(SystemExit) as cm:
                check_esi.main()
            self.assertEqual(cm.exception.code, 0)

        output = self.read_github_output()
        self.assertIn("has_update=false", output)

    @patch("schedules.script.check_esi_update.time.sleep")
    @patch("schedules.script.check_esi_update.urllib.request.urlopen")
    def test_download_failure_preserves_files_and_exits_0(self, mock_urlopen, mock_sleep):
        """If HEAD detects new ETag but GET fails with 503, preserve existing files and exit 0."""
        # 1st call (HEAD) succeeds with new ETag
        head_resp = MagicMock()
        head_resp.headers = {
            "Last-Modified": "Wed, 28 Oct 2026 12:00:00 GMT",
            "ETag": '"etag-new"',
        }
        head_resp.__enter__.return_value = head_resp

        # Subsequent GET calls fail
        get_err = urllib.error.HTTPError(
            url="https://esi.uclm.es/TV/hall/horarios.json",
            code=503,
            msg="Service Unavailable",
            hdrs={},
            fp=None,
        )

        mock_urlopen.side_effect = [head_resp, get_err, get_err, get_err]

        with patch("sys.argv", ["check_esi_update.py"]):
            with self.assertRaises(SystemExit) as cm:
                check_esi.main()
            self.assertEqual(cm.exception.code, 0)

        output = self.read_github_output()
        self.assertIn("has_update=false", output)

        # Content must not be overwritten
        with open(self.input_json, "r", encoding="utf-8") as f:
            content = json.load(f)
        self.assertEqual(content, self.sample_schedules)

    @patch("schedules.script.check_esi_update.time.sleep")
    @patch("schedules.script.check_esi_update.urllib.request.urlopen")
    def test_invalid_json_payload_rejected(self, mock_urlopen, mock_sleep):
        """If server returns 200 OK with HTML error page, do not overwrite schedule file."""
        head_resp = MagicMock()
        head_resp.headers = {
            "Last-Modified": "Wed, 28 Oct 2026 12:00:00 GMT",
            "ETag": '"etag-html"',
        }
        head_resp.__enter__.return_value = head_resp

        get_resp = MagicMock()
        get_resp.read.return_value = b"<html><head><title>500 Internal Error</title></head></html>"
        get_resp.__enter__.return_value = get_resp

        mock_urlopen.side_effect = [head_resp, get_resp]

        with patch("sys.argv", ["check_esi_update.py"]):
            with self.assertRaises(SystemExit) as cm:
                check_esi.main()
            self.assertEqual(cm.exception.code, 0)

        output = self.read_github_output()
        self.assertIn("has_update=false", output)

        # Existing schedules.json was preserved
        with open(self.input_json, "r", encoding="utf-8") as f:
            content = json.load(f)
        self.assertEqual(content, self.sample_schedules)

    @patch("schedules.script.check_esi_update.time.sleep")
    @patch("schedules.script.check_esi_update.urllib.request.urlopen")
    def test_valid_update_downloads_and_emits_has_update_true(self, mock_urlopen, mock_sleep):
        """Successful fetch with updated schedules saves new file and sets has_update=true."""
        new_payload = [{"asignatura": "New Subject", "codigo": "NEW"}]
        new_bytes = json.dumps(new_payload).encode("utf-8")

        head_resp = MagicMock()
        head_resp.headers = {
            "Last-Modified": "Wed, 28 Oct 2026 12:00:00 GMT",
            "ETag": '"etag-updated"',
        }
        head_resp.__enter__.return_value = head_resp

        get_resp = MagicMock()
        get_resp.read.return_value = new_bytes
        get_resp.__enter__.return_value = get_resp

        mock_urlopen.side_effect = [head_resp, get_resp]

        with patch("sys.argv", ["check_esi_update.py"]):
            with self.assertRaises(SystemExit) as cm:
                check_esi.main()
            self.assertEqual(cm.exception.code, 0)

        output = self.read_github_output()
        self.assertIn("has_update=true", output)

        # Schedules and metadata updated
        with open(self.input_json, "r", encoding="utf-8") as f:
            content = json.load(f)
        self.assertEqual(content, new_payload)

        with open(self.meta_file, "r", encoding="utf-8") as f:
            meta = json.load(f)
        self.assertEqual(meta["etag"], '"etag-updated"')
        self.assertEqual(meta["sha256"], check_esi.compute_sha256(new_bytes))


if __name__ == "__main__":
    unittest.main()
