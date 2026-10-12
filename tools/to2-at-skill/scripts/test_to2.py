"""Contract tests against an isolated loopback HTTP server; no production writes."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout
import io
import credentials
import to2

CLI = Path(__file__).with_name("to2.py")
FAKE_KEY = "to2_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def handle_request(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        self.server.calls.append((self.command, self.path, self.headers.get("Authorization"), body))
        status, result, redirect = self.server.answer
        self.send_response(status)
        if redirect:
            self.send_header("Location", redirect)
        self.end_headers()
        if result is not None:
            self.wfile.write(json.dumps(result).encode())

    do_GET = handle_request
    do_POST = handle_request
    do_DELETE = handle_request


class ContractTest(unittest.TestCase):
    def setUp(self):
        self.storage = tempfile.TemporaryDirectory()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.calls = []
        self.server.answer = (200, {"status": "UP"}, None)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = "http://127.0.0.1:" + str(self.server.server_port)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.storage.cleanup()

    def run_cli(self, *args, api_key=FAKE_KEY, key_file=None):
        env = dict(os.environ)
        env["CODEX_HOME"] = self.storage.name
        env.pop("TO2_API_KEY", None)
        env.pop("TO2_API_KEY_FILE", None)
        if api_key:
            env["TO2_API_KEY"] = api_key
        if key_file:
            env["TO2_API_KEY_FILE"] = key_file
        return subprocess.run([sys.executable, str(CLI), "--api-origin", self.origin, *args],
                              env=env, capture_output=True, text=True, encoding="utf-8")

    def test_public_operations_never_send_cookie(self):
        for command in [("health",), ("resolve", "1")]:
            result = self.run_cli(*command)
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([call[1] for call in self.server.calls], ["/actuator/health", "/to/1"])
        self.assertTrue(all(call[2] is None for call in self.server.calls))

    def test_owner_reads_and_writes(self):
        cases = [(("me",), "GET", "/me", 200, {"nickname": "Owner"}),
                 (("list",), "GET", "/links", 200, []),
                 (("create", "https://example.com/a?x=1"), "POST", "/links", 201,
                  {"code": "7", "shortUrl": "https://to2.at/7"}),
                 (("delete", "7"), "DELETE", "/links/7", 204, None)]
        for command, method, path, status, payload in cases:
            self.server.answer = (status, payload, None)
            result = self.run_cli(*command)
            self.assertEqual(result.returncode, 0, result.stderr)
            call = self.server.calls[-1]
            self.assertEqual(call[:3], (method, path, "Bearer " + FAKE_KEY))
            self.assertNotIn(FAKE_KEY, result.stdout + result.stderr)
            if method == "POST":
                self.assertEqual(json.loads(call[3]), {"target": command[1]})
            if method == "DELETE":
                self.assertEqual(json.loads(result.stdout), {"deleted": "7"})

    def test_key_file(self):
        with tempfile.TemporaryDirectory() as temp:
            key_path = Path(temp) / "api-key"
            key_path.write_text(FAKE_KEY, encoding="utf-8")
            result = self.run_cli("me", api_key=None, key_file=str(key_path))
            self.assertEqual(result.returncode, 0)
            self.assertEqual(self.server.calls[-1][2], "Bearer " + FAKE_KEY)

    def test_missing_or_malformed_key_stops_before_network(self):
        for session in (None, "bad\nBearer injection"):
            result = self.run_cli("create", "https://example.com", api_key=session)
            self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.server.calls, [])

    def test_redirect_not_followed_and_credential_not_printed(self):
        self.server.answer = (302, None, self.origin + "/secret?api_key=" + FAKE_KEY)
        result = self.run_cli("me")
        self.assertEqual(len(self.server.calls), 1)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(FAKE_KEY, result.stderr + result.stdout)

    def test_server_errors_not_retried(self):
        self.server.answer = (503, None, None)
        result = self.run_cli("create", "https://example.com")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.server.calls), 1)
        self.assertIn("unknown", result.stderr)

    def test_bad_target_message_and_missing_code(self):
        self.server.answer = (400, {"message": "Invalid Target"}, None)
        result = self.run_cli("create", "invalid")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(json.loads(result.stderr)["message"], "Invalid Target")
        self.server.answer = (404, None, None)
        result = self.run_cli("resolve", "999")
        self.assertEqual(json.loads(result.stderr)["status"], 404)

    def test_invalid_code_origin_and_secret_reflection(self):
        for code in ("0", "01", "../me", "9223372036854775808"):
            self.assertNotEqual(self.run_cli("resolve", code).returncode, 0)
        self.assertEqual(self.server.calls, [])
        result = self.run_cli("--api-origin", "https://other.example", "me")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.server.calls, [])
        self.server.answer = (200, {"secret": FAKE_KEY}, None)
        result = self.run_cli("me")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(FAKE_KEY, result.stdout + result.stderr)

    def test_hidden_setup_encrypted_storage_and_clear(self):
        env = {"CODEX_HOME": self.storage.name}
        for name in ("TO2_API_KEY", "TO2_API_KEY_FILE"):
            env[name] = ""
        output = io.StringIO()
        with patch.dict(os.environ, env), patch("sys.stdin.isatty", return_value=True), \
                patch("getpass.getpass", return_value=FAKE_KEY), redirect_stdout(output):
            self.assertEqual(to2.main(["auth-set"]), 0)
            self.assertEqual(credentials.read_key(), FAKE_KEY)
            saved = credentials.credential_path().read_bytes()
            if os.name == "nt":
                self.assertNotIn(FAKE_KEY.encode(), saved)
            else:
                self.assertEqual(credentials.credential_path().stat().st_mode & 0o777, 0o600)
            self.assertEqual(to2.main(["auth-status"]), 0)
            self.assertEqual(to2.main(["auth-clear"]), 0)
            self.assertIsNone(credentials.read_key())
        self.assertNotIn(FAKE_KEY, output.getvalue())

    def test_empty_json_response_is_reported_without_traceback(self):
        self.server.answer = (200, None, None)
        result = self.run_cli("me")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Traceback", result.stderr)


if __name__ == "__main__":
    unittest.main()
