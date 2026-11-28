"""Offline characterization of ESI HEAD, GET, and cache behavior."""

import hashlib
import json
import tempfile
import unittest
import urllib.error
from datetime import date
from pathlib import Path
from unittest.mock import MagicMock, patch

from schedules.script.adapters.esi import EsiAdapter, specification
from schedules.script.adapters.esi import source as esi_source
from schedules.script.contract import Candidate, SourceFailure, Unchanged
from schedules.script.pipeline import run_batch_pipeline

URL = "https://example.invalid/schedules.json"
FIXTURE = Path(__file__).resolve().parent / "fixtures" / "esi"


class TestEsiAcquisition(unittest.TestCase):
    def setUp(self):
        self.temp = Path(self.enterContext(tempfile.TemporaryDirectory()))
        self.cache = self.temp / "schedules.json"
        self.meta = self.temp / "esi_meta.json"
        self.body = b'[{"asignatura": "Test Subject", "codigo": "TEST"}]'
        self.headers = {"ETag": '"etag-initial"', "Last-Modified": "Mon, 19 Oct 2026 07:06:11 GMT"}
        self.initial_meta = {
            "url": URL,
            "etag": self.headers["ETag"],
            "last_modified": self.headers["Last-Modified"],
            "sha256": hashlib.sha256(self.body).hexdigest(),
        }
        self.cache.write_bytes(self.body)
        self.meta.write_text(json.dumps(self.initial_meta), encoding="utf-8")
        self.enterContext(patch.object(esi_source.time, "sleep"))
        self.urlopen = self.enterContext(patch.object(esi_source.urllib.request, "urlopen"))

    def responses(self, headers, body=None):
        head = MagicMock()
        head.__enter__.return_value.headers = headers
        responses = [head]
        if body is not None:
            get = MagicMock()
            get.__enter__.return_value.read.return_value = body
            responses.append(get)
        return responses

    def acquire(self, *, force=False):
        return esi_source.acquire(URL, self.cache, self.meta, force=force)

    def assert_methods(self, *expected):
        self.assertEqual([call.args[0].get_method() for call in self.urlopen.call_args_list], list(expected))

    def assert_cached(self, body, metadata):
        self.assertEqual(self.cache.read_bytes(), body)
        self.assertEqual(json.loads(self.meta.read_text(encoding="utf-8")), metadata)

    def test_unchanged_headers_skip_get_and_leave_files_intact(self):
        self.urlopen.side_effect = self.responses(self.headers)
        before = self.meta.read_bytes()
        self.assertEqual(self.acquire(), Unchanged())
        self.assert_methods("HEAD")
        self.assertEqual(self.meta.read_bytes(), before)
        self.assertEqual(self.cache.read_bytes(), self.body)

    def test_missing_etag_and_last_modified_validators_always_downloads(self):
        empty_meta = {**self.initial_meta, "etag": "", "last_modified": ""}
        self.meta.write_text(json.dumps(empty_meta), encoding="utf-8")
        updated_body = b'[{"asignatura": "Updated Subject", "codigo": "UPD"}]'
        self.urlopen.side_effect = self.responses({}, updated_body)
        result = self.acquire()
        self.assertEqual(result, Candidate(updated_body))
        self.assert_methods("HEAD", "GET")
        self.assert_cached(self.body, empty_meta)
        assert isinstance(result, Candidate) and result.commit_cache is not None
        result.commit_cache()
        self.assert_cached(updated_body, {**empty_meta, "sha256": hashlib.sha256(updated_body).hexdigest()})

    def test_changed_header_with_same_body_refreshes_metadata_without_new_candidate(self):
        changed = "Wed, 28 Oct 2026 12:00:00 GMT"
        self.urlopen.side_effect = self.responses({**self.headers, "Last-Modified": changed}, self.body)
        self.assertEqual(self.acquire(), Unchanged())
        self.assert_methods("HEAD", "GET")
        self.assert_cached(self.body, {**self.initial_meta, "last_modified": changed})

    def test_force_produces_candidate_even_with_same_headers_and_body(self):
        self.urlopen.side_effect = self.responses(self.headers, self.body)
        self.assertEqual(self.acquire(force=True), Candidate(self.body))
        self.assert_methods("HEAD", "GET")

    def test_missing_input_triggers_download_even_with_unchanged_headers(self):
        self.cache.unlink()
        self.urlopen.side_effect = self.responses(self.headers, self.body)
        result = self.acquire()
        self.assertEqual(result, Candidate(self.body))
        self.assert_methods("HEAD", "GET")
        self.assertFalse(self.cache.exists())
        assert isinstance(result, Candidate) and result.commit_cache is not None
        result.commit_cache()
        self.assert_cached(self.body, self.initial_meta)

    def test_head_http_403_retries_then_returns_source_failure(self):
        self.urlopen.side_effect = urllib.error.HTTPError(URL, 403, "Forbidden", {}, None)
        result = self.acquire()
        self.assertIsInstance(result, SourceFailure)
        self.assertIn("403", result.diagnostic)
        self.assert_methods("HEAD", "HEAD", "HEAD")
        self.assert_cached(self.body, self.initial_meta)

    def test_head_timeout_retries_then_returns_source_failure(self):
        self.urlopen.side_effect = urllib.error.URLError("Connection timed out")
        result = self.acquire()
        self.assertIsInstance(result, SourceFailure)
        self.assertIn("Connection timed out", result.diagnostic)
        self.assert_methods("HEAD", "HEAD", "HEAD")
        self.assert_cached(self.body, self.initial_meta)

    def test_failed_head_does_not_prevent_forced_get(self):
        self.urlopen.side_effect = [urllib.error.URLError("Connection timed out")] * 3 + self.responses({}, self.body)[1:]
        self.assertEqual(self.acquire(force=True), Candidate(self.body))
        self.assert_methods("HEAD", "HEAD", "HEAD", "GET")

    def test_download_failure_preserves_cache(self):
        self.urlopen.side_effect = self.responses({"ETag": '"new"'}) + [
            urllib.error.HTTPError(URL, 503, "Service Unavailable", {}, None)
        ] * 3
        self.assertIsInstance(self.acquire(), SourceFailure)
        self.assert_methods("HEAD", "GET", "GET", "GET")
        self.assert_cached(self.body, self.initial_meta)

    def test_invalid_json_payload_preserves_cache(self):
        self.urlopen.side_effect = self.responses({"ETag": '"new"'}, b"<html>Error</html>")
        self.assertIsInstance(self.acquire(), SourceFailure)
        self.assert_cached(self.body, self.initial_meta)

    def test_valid_update_downloads_and_caches_file_and_headers(self):
        body = b'[{"asignatura": "New Subject", "codigo": "NEW"}]'
        self.urlopen.side_effect = self.responses({**self.headers, "ETag": '"new"'}, body)
        result = self.acquire()
        self.assertEqual(result, Candidate(body))
        # Cache stays untouched until explicitly committed after validation/publication.
        self.assert_cached(self.body, self.initial_meta)
        assert isinstance(result, Candidate) and result.commit_cache is not None
        result.commit_cache()
        self.assert_cached(body, {
            **self.initial_meta, "etag": '"new"', "sha256": hashlib.sha256(body).hexdigest(),
        })

    def test_unchanged_remote_headers_reprocess_cached_input_after_mapping_edit(self):
        self.cache.write_bytes((FIXTURE / "source.json").read_bytes())
        mappings_path = self.temp / "mappings.json"
        mappings = json.loads((FIXTURE / "mappings.json").read_text(encoding="utf-8"))
        mappings_path.write_text(json.dumps(mappings), encoding="utf-8")
        dist = self.temp / "dist"
        today = date(2026, 10, 15)
        adapter = EsiAdapter(self.temp, mappings_path, URL)
        spec = specification(today)
        seed = run_batch_pipeline(((spec, adapter),), dist, today, strict=True)
        self.assertEqual(seed.status, "ok")
        before = (dist / "1C.json").read_bytes()

        code = json.loads(before)[0]["codigo"]
        updated = mappings["matters"][code] + " (2026)"
        mappings["matters"][code] = updated
        mappings_path.write_text(json.dumps(mappings), encoding="utf-8")
        self.urlopen.side_effect = self.responses(self.headers)
        result = run_batch_pipeline(((spec, adapter),), dist, today, remote=True, strict=True)

        self.assert_methods("HEAD")
        self.assertEqual(result.status, "ok")
        self.assertIn("1C.json", result.written_files)
        self.assertIn(updated.encode(), (dist / "1C.json").read_bytes())

    def test_source_and_parse_failures_preserve_all_public_files_and_cache(self):
        dist = self.temp / "dist"
        today = date(2026, 10, 15)
        adapter = EsiAdapter(self.temp, FIXTURE / "mappings.json", URL)
        spec = specification(today)
        valid_source = (FIXTURE / "source.json").read_bytes()
        self.urlopen.side_effect = self.responses({**self.headers, "ETag": '"seed"'}, valid_source)
        seed = run_batch_pipeline(((spec, adapter),), dist, today, remote=True, strict=True)
        self.assertEqual(seed.status, "ok")
        original_dist = {path.name: path.read_bytes() for path in dist.iterdir()}
        expected_meta = {
            **self.initial_meta,
            "etag": '"seed"',
            "sha256": hashlib.sha256(valid_source).hexdigest(),
        }
        self.assert_cached(valid_source, expected_meta)

        # 1) Network timeout fails at source stage and leaves dist + cache intact.
        self.urlopen.side_effect = urllib.error.URLError("Connection timed out")
        timeout_res = run_batch_pipeline(((spec, adapter),), dist, today, remote=True, strict=True)
        self.assertEqual(timeout_res.status, "failed")
        self.assertEqual(timeout_res.outcomes[0].stage, "source")
        self.assertEqual({path.name: path.read_bytes() for path in dist.iterdir()}, original_dist)
        self.assert_cached(valid_source, expected_meta)

        # 2) Unexpected JSON dict fails at source stage and leaves dist + cache intact.
        self.urlopen.side_effect = self.responses({"ETag": '"bad-dict"'}, b'{"error": "unexpected shape"}')
        dict_res = run_batch_pipeline(((spec, adapter),), dist, today, remote=True, strict=True)
        self.assertEqual(dict_res.status, "failed")
        self.assertEqual(dict_res.outcomes[0].stage, "source")
        self.assertEqual({path.name: path.read_bytes() for path in dist.iterdir()}, original_dist)
        self.assert_cached(valid_source, expected_meta)

        # 3) Valid JSON array with unresolved strict mapping fails at parse stage without overwriting cache.
        bad_entries = json.loads(valid_source.decode("utf-8"))
        bad_entries[0]["asignatura"] = "ZZZ_UNMAPPED_999"
        self.urlopen.side_effect = self.responses({"ETag": '"bad-mapping"'}, json.dumps(bad_entries).encode("utf-8"))
        parse_res = run_batch_pipeline(((spec, adapter),), dist, today, remote=True, strict=True)
        self.assertEqual(parse_res.status, "failed")
        self.assertEqual(parse_res.outcomes[0].stage, "parse")
        self.assertEqual({path.name: path.read_bytes() for path in dist.iterdir()}, original_dist)
        self.assert_cached(valid_source, expected_meta)
