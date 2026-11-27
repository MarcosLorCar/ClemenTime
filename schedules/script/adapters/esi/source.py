"""Acquire and cache the ESI TV hall feed."""

import hashlib
import http.client
import json
import os
import time
import urllib.request
from pathlib import Path

from ...contract import Candidate, SourceFailure, Unchanged

REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; ClemenTime-UpdateChecker/1.0; +https://github.com/MarcosLorCar/ClemenTime)",
    "Accept": "application/json, text/plain, */*",
}
HTTP_ERRORS = (OSError, http.client.HTTPException, ValueError)


def source_url() -> str:
    url = os.getenv("SCHEDULE_API_URL") or os.getenv("SCHEDULE_SOURCE_URL")
    if url and url.endswith(".json"):
        return url
    return "https://esi.uclm.es/TV/hall/horarios.json"


def fetch(url: str, method: str, timeout: int):
    request = urllib.request.Request(url, headers=REQUEST_HEADERS, method=method)
    for attempt in range(3):
        try:
            return urllib.request.urlopen(request, timeout=timeout)
        except HTTP_ERRORS:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def read_local(path: Path) -> Candidate[bytes] | SourceFailure:
    if path.suffix.lower() != ".json":
        return SourceFailure(f"Unsupported ESI input format: {path}. Expected a .json file.")
    try:
        return Candidate(path.read_bytes())
    except OSError as error:
        return SourceFailure(f"Could not read {path}: {error}")


def acquire(
    url: str, input_path: Path, metadata_path: Path, *, force: bool
) -> Candidate[bytes] | Unchanged | SourceFailure:
    etag = last_modified = ""
    try:
        with fetch(url, "HEAD", timeout=10) as response:
            etag = response.headers.get("ETag", "")
            last_modified = response.headers.get("Last-Modified", "")
    except HTTP_ERRORS as error:
        if not force:
            return SourceFailure(f"Failed to fetch HEAD from {url}: {error}")
        print(f"[ESI] HEAD request failed ({error}). Forcing a download.")

    try:
        saved = json.loads(metadata_path.read_text(encoding="utf-8"))
        if not isinstance(saved, dict):
            raise ValueError("Expected a JSON object")
    except FileNotFoundError:
        saved = {}
    except (OSError, ValueError) as error:
        print(f"[ESI] Could not read {metadata_path}: {error}. Refreshing the source.")
        saved = {}

    headers = {"url": url, "last_modified": last_modified, "etag": etag}
    input_exists = input_path.is_file()
    if not force and input_exists and all(saved.get(key) == value for key, value in headers.items()):
        return Unchanged()

    try:
        with fetch(url, "GET", timeout=30) as response:
            content = response.read()
    except HTTP_ERRORS as error:
        return SourceFailure(f"Failed to download schedule from {url}: {error}")

    try:
        parsed = json.loads(content.decode("utf-8"))
        if not isinstance(parsed, (list, dict)) or not parsed:
            raise ValueError("Expected a nonempty JSON object or array")
    except ValueError as error:
        return SourceFailure(f"Downloaded payload from {url} is invalid JSON: {error}")

    digest = hashlib.sha256(content).hexdigest()
    try:
        input_path.parent.mkdir(parents=True, exist_ok=True)
        input_path.write_bytes(content)
        metadata_path.write_text(json.dumps({**headers, "sha256": digest}, indent=2) + "\n", encoding="utf-8")
    except OSError as error:
        return SourceFailure(f"Could not cache ESI input: {error}")

    if force or not input_exists or digest != saved.get("sha256"):
        return Candidate(content)
    return Unchanged()
