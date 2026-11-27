#!/usr/bin/env python3
"""
Unit tests for notify_fcm.py verifying topic naming, data-only payload generation,
selective faculty/report filtering, missing-secret safe exit, transient retries,
and partial-failure reporting.
"""

import io
import json
import os
import shutil
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

import schedules.script.notify_fcm as notify_fcm


class TestNotifyFcm(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.catalog_path = os.path.join(self.test_dir, "schedules_catalog.json")
        self.sample_catalog = {
            "version": 2,
            "universities": [
                {
                    "id": "uclm",
                    "name": "UCLM",
                    "faculties": [
                        {
                            "id": "esi",
                            "name": "ESI",
                            "fcm_topic_prefix": "uclm_esi",
                            "terms": [
                                {"id": "1C", "hash": "hash1c_abc"},
                                {"id": "2C", "hash": "hash2c_def"},
                            ],
                        },
                        {
                            "id": "esii",
                            "name": "ESII",
                            "terms": [
                                {"id": "1C", "hash": "hash_esii_1c"},
                            ],
                        },
                    ],
                }
            ],
        }
        with open(self.catalog_path, "w", encoding="utf-8") as f:
            json.dump(self.sample_catalog, f)
        self.sa_json = json.dumps({
            "project_id": "clementime-test",
            "client_email": "fcm@clementime-test.iam.gserviceaccount.com",
            "private_key": "-----BEGIN PRIVATE KEY-----\nFAKE\n-----END PRIVATE KEY-----\n",
        })

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_build_topic_name(self):
        self.assertEqual(notify_fcm.build_topic_name("uclm", "esi", "uclm_esi"), "sched_uclm_esi")
        self.assertEqual(notify_fcm.build_topic_name("UPM", "ETSII", None), "sched_upm_etsii")
        self.assertEqual(notify_fcm.build_topic_name("upm", "etsiinf", "sched_UPM ETSiInf"), "sched_upm_etsiinf")
        self.assertEqual(notify_fcm.build_topic_name("upm", "etsiinf", "SCHED_upm.etsiinf"), "sched_upm_etsiinf")
        self.assertEqual(notify_fcm.build_topic_name("u.c.l.m", "e.s.i", None), "sched_u_c_l_m_e_s_i")
        with self.assertRaises(ValueError):
            notify_fcm.build_topic_name("", "esi", None)
        with self.assertRaises(ValueError):
            notify_fcm.build_topic_name("uclm", "   ", None)

    def test_build_fcm_messages_data_only_payload_and_selective_filter(self):
        messages = notify_fcm.build_fcm_messages(
            self.sample_catalog,
            updated_at="2026-11-17T12:00:00+00:00",
            faculties=["uclm/esi"],
        )
        self.assertEqual(len(messages), 1)
        msg = messages[0]["message"]
        self.assertEqual(msg["topic"], "sched_uclm_esi")
        self.assertNotIn("notification", msg)
        self.assertEqual(msg["android"]["priority"], "high")
        self.assertEqual(msg["data"]["university"], "uclm")
        self.assertEqual(msg["data"]["faculty"], "esi")
        self.assertEqual(msg["data"]["hash_1C"], "hash1c_abc")
        self.assertEqual(msg["data"]["hash_2C"], "hash2c_def")

    def test_main_skips_gracefully_when_secret_missing(self):
        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": ""}, clear=False):
            with patch("sys.argv", ["notify_fcm.py", "--catalog", self.catalog_path]):
                rc = notify_fcm.main()
        self.assertEqual(rc, 0)

    @patch("schedules.script.notify_fcm.urllib.request.urlopen")
    @patch("schedules.script.notify_fcm.fetch_oauth_access_token", return_value="fake-oauth-token")
    def test_main_dispatches_only_changed_faculties_from_report(self, mock_token, mock_urlopen):
        report_path = os.path.join(self.test_dir, "report.json")
        with open(report_path, "w", encoding="utf-8") as handle:
            json.dump({"changed_schedule_faculties": ["uclm/esii"]}, handle)

        mock_resp = io.BytesIO(json.dumps({"name": "projects/clemen/messages/123"}).encode("utf-8"))
        mock_urlopen.return_value.__enter__.return_value = mock_resp

        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": self.sa_json}, clear=False):
            with patch("sys.argv", ["notify_fcm.py", "--catalog", self.catalog_path, "--report", report_path]):
                rc = notify_fcm.main()

        self.assertEqual(rc, 0)
        mock_token.assert_called_once()
        mock_urlopen.assert_called_once()
        req = mock_urlopen.call_args[0][0]
        self.assertEqual(req.full_url, "https://fcm.googleapis.com/v1/projects/clementime-test/messages:send")
        self.assertEqual(req.headers["Authorization"], "Bearer fake-oauth-token")
        body = json.loads(req.data.decode("utf-8"))
        self.assertEqual(body["message"]["topic"], "sched_uclm_esii")
        self.assertEqual(body["message"]["data"]["hash_1C"], "hash_esii_1c")

    @patch("schedules.script.notify_fcm.fetch_oauth_access_token")
    def test_main_skips_fcm_when_report_has_no_changed_schedules(self, mock_token):
        report_path = os.path.join(self.test_dir, "empty_report.json")
        with open(report_path, "w", encoding="utf-8") as handle:
            json.dump({"changed_schedule_faculties": []}, handle)

        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": self.sa_json}, clear=False):
            with patch("sys.argv", ["notify_fcm.py", "--catalog", self.catalog_path, "--report", report_path]):
                rc = notify_fcm.main()

        self.assertEqual(rc, 0)
        mock_token.assert_not_called()

    def test_main_rejects_unknown_faculty_filter(self):
        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": self.sa_json}, clear=False):
            with patch("sys.argv", ["notify_fcm.py", "--catalog", self.catalog_path, "--faculty", "uclm/missing"]):
                rc = notify_fcm.main()
        self.assertEqual(rc, 1)

    @patch("schedules.script.notify_fcm.time.sleep")
    @patch("schedules.script.notify_fcm.urllib.request.urlopen")
    @patch("schedules.script.notify_fcm.fetch_oauth_access_token", return_value="fake-oauth-token")
    def test_main_retries_transient_503_and_succeeds(self, mock_token, mock_urlopen, mock_sleep):
        transient = urllib.error.HTTPError(
            "https://fcm.googleapis.com/v1/projects/clementime-test/messages:send",
            503,
            "Service Unavailable",
            {},
            io.BytesIO(b"unavailable"),
        )
        ok_resp = io.BytesIO(json.dumps({"name": "projects/clemen/messages/456"}).encode("utf-8"))
        ok_ctx = unittest.mock.MagicMock()
        ok_ctx.__enter__.return_value = ok_resp
        mock_urlopen.side_effect = [transient, ok_ctx]

        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": self.sa_json}, clear=False):
            with patch("sys.argv", ["notify_fcm.py", "--catalog", self.catalog_path, "--faculty", "uclm/esi"]):
                rc = notify_fcm.main()

        self.assertEqual(rc, 0)
        self.assertEqual(mock_urlopen.call_count, 2)
        mock_sleep.assert_called_once()

    @patch("schedules.script.notify_fcm.time.sleep")
    @patch("schedules.script.notify_fcm.urllib.request.urlopen")
    @patch("schedules.script.notify_fcm.fetch_oauth_access_token", return_value="fake-oauth-token")
    def test_partial_fcm_failure_continues_other_faculties_and_writes_retry_guidance(self, mock_token, mock_urlopen, mock_sleep):
        summary_path = os.path.join(self.test_dir, "summary.md")
        bad_req = urllib.error.HTTPError(
            "https://fcm.googleapis.com/v1/projects/clementime-test/messages:send",
            400,
            "Bad Request",
            {},
            io.BytesIO(b"bad topic"),
        )
        ok_resp = io.BytesIO(json.dumps({"name": "projects/clemen/messages/789"}).encode("utf-8"))
        ok_ctx = unittest.mock.MagicMock()
        ok_ctx.__enter__.return_value = ok_resp
        mock_urlopen.side_effect = [bad_req, ok_ctx]

        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": self.sa_json}, clear=False):
            with patch(
                "sys.argv",
                ["notify_fcm.py", "--catalog", self.catalog_path, "--summary-file", summary_path],
            ):
                rc = notify_fcm.main()

        self.assertEqual(rc, 1)
        # Non-retryable 400 must not retry, and the second faculty must still be dispatched.
        self.assertEqual(mock_urlopen.call_count, 2)
        mock_sleep.assert_not_called()
        with open(summary_path, encoding="utf-8") as handle:
            summary = handle.read()
        self.assertIn("not** reverted", summary)
        self.assertIn("--faculty uclm/esi", summary)
        self.assertIn("Dispatched `uclm/esii`", summary)


if __name__ == "__main__":
    unittest.main()
