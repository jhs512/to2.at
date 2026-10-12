#!/usr/bin/env python3
"""to2.at API client. No dependencies, redirects or automatic retries."""
import argparse
import getpass
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from credentials import credential_path, read_key, save_key


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def code_value(value):
    if not re.fullmatch(r"[1-9][0-9]*", value) or int(value) > 9223372036854775807:
        raise argparse.ArgumentTypeError("Code must be a positive canonical 64-bit integer")
    return value


def origin_value(value):
    parsed = urllib.parse.urlsplit(value)
    production = value == "https://api.to2.at"
    local = (parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}
             and not parsed.username and not parsed.password
             and parsed.path in {"", "/"} and not parsed.query and not parsed.fragment)
    if not (production or local):
        raise argparse.ArgumentTypeError("Origin must be https://api.to2.at or an HTTP loopback mock server")
    return value.rstrip("/")


def emit(value, stream=None):
    print(json.dumps(value, ensure_ascii=False), file=stream or sys.stdout)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-origin", type=origin_value, default="https://api.to2.at")
    parser.add_argument("--timeout", type=float, default=20)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("health", "me", "list"):
        sub.add_parser(name)
    for name in ("auth-set", "auth-status", "auth-clear"):
        sub.add_parser(name)
    sub.add_parser("resolve").add_argument("code", type=code_value)
    sub.add_parser("delete").add_argument("code", type=code_value)
    sub.add_parser("create").add_argument("target")
    args = parser.parse_args(argv)
    if args.timeout <= 0:
        parser.error("timeout must be positive")

    if args.command.startswith("auth-"):
        try:
            if args.command == "auth-set":
                if not sys.stdin.isatty():
                    emit({"error": "Run auth-set in an interactive terminal for hidden input"}, sys.stderr)
                    return 2
                key = getpass.getpass("to2.at API key (hidden): ").strip()
                if not re.fullmatch(r"to2_[A-Za-z0-9_-]{43}", key):
                    emit({"error": "Invalid API key format"}, sys.stderr)
                    return 2
                save_key(key)
                emit({"configured": True})
            elif args.command == "auth-clear":
                credential_path().unlink(missing_ok=True)
                emit({"localKeyRemoved": True, "note": "Revoke on the website to disable the server key"})
            else:
                emit({"configured": bool(read_key()), "serverVerified": False})
            return 0
        except (OSError, ValueError):
            emit({"error": "Cannot access private API key storage"}, sys.stderr)
            return 2

    protected = args.command in {"me", "list", "create", "delete"}
    headers = {"Accept": "application/json"}
    api_key = None
    if protected:
        try:
            api_key = read_key()
        except (OSError, ValueError):
            emit({"error": "Cannot access private API key storage"}, sys.stderr)
            return 2
        if not api_key:
            emit({"error": "API key required: run auth-set or configure TO2_API_KEY"}, sys.stderr)
            return 2
        if not re.fullmatch(r"to2_[A-Za-z0-9_-]{43}", api_key):
            emit({"error": "Invalid API key format"}, sys.stderr)
            return 2
        headers["Authorization"] = "Bearer " + api_key

    paths = {"health": "/actuator/health", "me": "/me", "list": "/links", "create": "/links"}
    path = paths.get(args.command)
    if args.command == "resolve":
        path = "/to/" + args.code
    if args.command == "delete":
        path = "/links/" + args.code
    method = {"create": "POST", "delete": "DELETE"}.get(args.command, "GET")
    data = None
    if args.command == "create":
        data = json.dumps({"target": args.target}, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(args.api_origin + path, headers=headers, data=data, method=method)
    opener = urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(request, timeout=args.timeout) as response:
            raw = response.read()
            if not raw and args.command != "delete":
                raise ValueError("Empty API response")
            result = json.loads(raw) if raw else {"deleted": args.code}
            # Never expose a credential reflected by a malformed server response.
            serialized = json.dumps(result, ensure_ascii=False)
            if api_key and api_key in serialized:
                emit({"error": "Response contains API key material; output suppressed"}, sys.stderr)
                return 1
            expected = {"create": 201, "delete": 204}.get(args.command, 200)
            if response.status != expected:
                emit({"error": "Unexpected success status", "status": response.status}, sys.stderr)
                return 1
            emit(result)
            return 0
    except urllib.error.HTTPError as exc:
        status = exc.code
        result = {"error": "API request failed", "status": status}
        if status in {301, 302, 303, 307, 308, 401, 403}:
            result["error"] = "API key invalid, revoked, or unsupported; verify deployment and register an active key"
        elif status == 400:
            try:
                message = json.loads(exc.read()).get("message")
                if isinstance(message, str) and (not api_key or api_key not in message):
                    result["message"] = message
            except (ValueError, AttributeError):
                pass
        if method == "POST" and status >= 500:
            result["outcome"] = "unknown; inspect list before considering another create"
        emit(result, sys.stderr)
        return 1
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        result = {"error": "Connection failed or invalid JSON response"}
        if method == "POST":
            result["outcome"] = "unknown; inspect list before considering another create"
        emit(result, sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
