#!/usr/bin/env python3
"""
Unit tests for notify_fcm.py verifying topic naming, data-only payload generation,
missing-secret safe exit, and HTTP dispatch behavior.
"""

import io
import json
import os
import shutil
import tempfile
import unittest
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
                        }
                    ],
                }
            ],
        }
        with open(self.catalog_path, "w", encoding="utf-8") as f:
            json.dump(self.sample_catalog, f)

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_build_topic_name(self):
        self.assertEqual(notify_fcm.build_topic_name("uclm", "esi", "uclm_esi"), "sched_uclm_esi")
        self.assertEqual(notify_fcm.build_topic_name("UPM", "ETSII", None), "sched_upm_etsii")

    def test_build_fcm_messages_data_only_payload(self):
        messages = notify_fcm.build_fcm_messages(self.sample_catalog, updated_at="2026-11-17T12:00:00+00:00")
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
    def test_main_dispatches_to_fcm_v1_endpoint(self, mock_token, mock_urlopen):
        mock_resp = io.BytesIO(json.dumps({"name": "projects/clemen/messages/123"}).encode("utf-8"))
        mock_urlopen.return_value.__enter__.return_value = mock_resp

        sa = json.dumps({
            "project_id": "clementime-test",
            "client_email": "fcm@clementime-test.iam.gserviceaccount.com",
            "private_key": "-----BEGIN PRIVATE KEY-----\nFAKE\n-----END PRIVATE KEY-----\n",
        })

        with patch.dict(os.environ, {"FCM_SERVICE_ACCOUNT_KEY": sa}, clear=False):
            with patch("sys.argv", ["notify_fcm.py", "--catalog", self.catalog_path]):
                rc = notify_fcm.main()

        self.assertEqual(rc, 0)
        mock_token.assert_called_once()
        mock_urlopen.assert_called_once()
        req = mock_urlopen.call_args[0][0]
        self.assertEqual(req.full_url, "https://fcm.googleapis.com/v1/projects/clementime-test/messages:send")
        self.assertEqual(req.headers["Authorization"], "Bearer fake-oauth-token")


if __name__ == "__main__":
    unittest.main()
