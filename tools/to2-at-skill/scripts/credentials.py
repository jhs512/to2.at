"""Per-user API key storage. Windows DPAPI; private files on POSIX."""
import ctypes
import os
from pathlib import Path


def credential_path():
    root = Path(os.environ.get("CODEX_HOME") or Path.home() / ".codex")
    return root / "secrets" / "to2-at.key"


def dpapi(data, protect):
    from ctypes import wintypes

    class Blob(ctypes.Structure):
        _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_byte))]

    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_byte)))
    result = Blob()
    crypt = ctypes.WinDLL("crypt32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    func = crypt.CryptProtectData if protect else crypt.CryptUnprotectData
    func.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                     ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    func.restype = wintypes.BOOL
    if not func(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(result)):
        raise OSError("Credential encryption/decryption failed")
    try:
        return ctypes.string_at(result.data, result.size)
    finally:
        kernel.LocalFree(ctypes.cast(result.data, ctypes.c_void_p))


def save_key(key):
    path = credential_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = key.encode("ascii")
    if os.name == "nt":
        payload = dpapi(payload, True)
    # Atomic replacement with restrictive permissions before writing.
    temp = path.with_suffix(".tmp")
    descriptor = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
        temp.replace(path)
    finally:
        temp.unlink(missing_ok=True)


def read_key():
    if os.environ.get("TO2_API_KEY"):
        return os.environ["TO2_API_KEY"]
    if os.environ.get("TO2_API_KEY_FILE"):
        return Path(os.environ["TO2_API_KEY_FILE"]).read_text(encoding="utf-8").strip()
    path = credential_path()
    if not path.exists():
        return None
    payload = path.read_bytes()
    if os.name == "nt":
        payload = dpapi(payload, False)
    return payload.decode("ascii")
