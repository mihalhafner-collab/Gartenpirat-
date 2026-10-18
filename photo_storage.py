"""Validated, content-addressed storage for customer-photo originals.

This module deliberately has no dependency on the database or Drive sync.  It is
used by both HTTP uploads and Drive attachment imports so that the same limits,
MIME detection and fail-closed hash checks apply to every write path.
"""
from __future__ import annotations

import base64
import binascii
import hashlib
import io
import os
import re
import tempfile
from pathlib import Path


MAX_PHOTO_BYTES = 20 * 1024 * 1024
MAX_IMAGE_PIXELS = 40_000_000
MAX_IMAGE_EDGE = 16_384

MIME_EXTENSIONS = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
    "image/heic": ".heic",
}
MIME_ALIASES = {"image/jpg": "image/jpeg", "image/pjpeg": "image/jpeg", "image/heif": "image/heic"}
ROLES = {"profile", "documentation", "before", "during", "after", "damage", "other"}
CANONICAL_STORAGE_NAME = re.compile(r"[0-9a-f]{64}\.(?:jpg|png|webp|gif|heic)")


def _mime(value: str | None) -> str:
    value = str(value or "").strip().lower()
    return MIME_ALIASES.get(value, value)


def decode_data(value: object) -> tuple[bytes, str | None]:
    """Decode a legacy data URI or plain base64 value with a strict size limit."""
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Kunde und Foto sind erforderlich.")
    encoded, declared = value.strip(), None
    if encoded.startswith("data:"):
        match = re.fullmatch(r"data:([^;,]+)(?:;[^,]*)?;base64,(.*)", encoded, re.DOTALL | re.IGNORECASE)
        if not match:
            raise ValueError("Das Foto besitzt kein gültiges Base64-Datenformat.")
        declared, encoded = _mime(match.group(1)), match.group(2)
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        raise ValueError("Das Foto besitzt keine gültigen Base64-Daten.")
    if not raw:
        raise ValueError("Das Foto ist leer.")
    if len(raw) > MAX_PHOTO_BYTES:
        raise ValueError("Das Foto darf maximal 20 MB groß sein.")
    return raw, declared


def sniff_mime(raw: bytes) -> str | None:
    if raw.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if raw.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if raw.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if len(raw) >= 12 and raw[:4] == b"RIFF" and raw[8:12] == b"WEBP":
        return "image/webp"
    if len(raw) >= 12 and raw[4:8] == b"ftyp" and raw[8:12] in {
        b"heic", b"heix", b"hevc", b"hevx", b"heim", b"heis", b"mif1", b"msf1"
    }:
        return "image/heic"
    return None


def _fallback_dimensions(raw: bytes, mime_type: str) -> tuple[int, int] | None:
    if mime_type == "image/png" and len(raw) >= 24:
        return int.from_bytes(raw[16:20], "big"), int.from_bytes(raw[20:24], "big")
    if mime_type == "image/gif" and len(raw) >= 10:
        return int.from_bytes(raw[6:8], "little"), int.from_bytes(raw[8:10], "little")
    if mime_type == "image/webp" and len(raw) >= 30 and raw[12:16] == b"VP8X":
        return 1 + int.from_bytes(raw[24:27], "little"), 1 + int.from_bytes(raw[27:30], "little")
    if mime_type == "image/jpeg":
        offset = 2
        while offset + 4 <= len(raw):
            if raw[offset] != 0xFF:
                offset += 1
                continue
            marker = raw[offset + 1]
            offset += 2
            if marker in {0xD8, 0xD9}:
                continue
            if offset + 2 > len(raw):
                break
            length = int.from_bytes(raw[offset:offset + 2], "big")
            if length < 2 or offset + length > len(raw):
                break
            if marker in {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF} and length >= 7:
                return int.from_bytes(raw[offset + 3:offset + 5], "big"), int.from_bytes(raw[offset + 5:offset + 7], "big")
            offset += length
    return None


def inspect(raw: bytes, declared_mime: str | None = None) -> dict:
    """Validate bytes as a bounded image and return canonical attachment metadata."""
    if not isinstance(raw, bytes) or not raw:
        raise ValueError("Das Foto ist leer.")
    if len(raw) > MAX_PHOTO_BYTES:
        raise ValueError("Das Foto darf maximal 20 MB groß sein.")
    mime_type = sniff_mime(raw)
    if mime_type not in MIME_EXTENSIONS:
        raise ValueError("Die Datei ist kein unterstütztes Foto.")
    declared_mime = _mime(declared_mime)
    if declared_mime and declared_mime != mime_type:
        raise ValueError("Dateityp und tatsächlicher Bildinhalt stimmen nicht überein.")

    dimensions = None
    try:
        from PIL import Image, UnidentifiedImageError
        with Image.open(io.BytesIO(raw)) as image:
            dimensions = image.size
            image.verify()
    except ImportError:
        dimensions = _fallback_dimensions(raw, mime_type)
    except (OSError, ValueError, UnidentifiedImageError):
        raise ValueError("Das Foto ist beschädigt oder kann nicht gelesen werden.")
    if dimensions is None:
        raise ValueError("Der Bildinhalt kann auf diesem System nicht sicher geprüft werden.")
    width, height = dimensions
    if width <= 0 or height <= 0 or width > MAX_IMAGE_EDGE or height > MAX_IMAGE_EDGE or width * height > MAX_IMAGE_PIXELS:
        raise ValueError("Das Foto besitzt unzulässig große Bildabmessungen.")
    digest = hashlib.sha256(raw).hexdigest()
    return {
        "content_sha256": digest,
        "mime_type": mime_type,
        "byte_size": len(raw),
        "storage_name": digest + MIME_EXTENSIONS[mime_type],
        "width": width,
        "height": height,
    }


def normalized_role(value: object, caption: object = "") -> str:
    role = str(value or "").strip().lower()
    if not role and str(caption or "").strip().casefold() in {"profilbild", "profile", "profilfoto"}:
        role = "profile"
    return role if role in ROLES else "documentation"


def original_name(value: object, mime_type: str) -> str:
    name = Path(str(value or ("foto" + MIME_EXTENSIONS[mime_type]))).name.strip()
    if not name or name in {".", ".."}:
        name = "foto" + MIME_EXTENSIONS[mime_type]
    return name[:240]


def is_canonical_storage_name(value: object) -> bool:
    """Return whether *value* is an exact content-addressed photo basename."""
    return bool(CANONICAL_STORAGE_NAME.fullmatch(str(value or "")))


def read_verified(path: Path, expected_sha256: str | None = None, expected_size: int | None = None,
                  expected_mime: str | None = None) -> tuple[bytes, dict]:
    if not path.is_file():
        raise ValueError("Das Foto-Original fehlt.")
    if path.stat().st_size > MAX_PHOTO_BYTES:
        raise ValueError("Das Foto darf maximal 20 MB groß sein.")
    raw = path.read_bytes()
    metadata = inspect(raw, expected_mime)
    if expected_sha256 and metadata["content_sha256"] != str(expected_sha256).lower():
        raise ValueError("Die Prüfsumme des Foto-Originals stimmt nicht überein.")
    if expected_size not in (None, "") and metadata["byte_size"] != int(expected_size):
        raise ValueError("Die Dateigröße des Foto-Originals stimmt nicht überein.")
    return raw, metadata


def store_atomic(directory: Path, raw: bytes, metadata: dict) -> tuple[Path, bool]:
    """Store a verified blob without ever overwriting a pre-existing path."""
    directory.mkdir(parents=True, exist_ok=True)
    name = str(metadata.get("storage_name", ""))
    if not is_canonical_storage_name(name):
        raise ValueError("Ungültiger interner Foto-Dateiname.")
    target = directory / name
    if target.exists():
        read_verified(target, metadata["content_sha256"], metadata["byte_size"], metadata["mime_type"])
        return target, False
    descriptor, temporary_name = tempfile.mkstemp(prefix=".photo-upload-", suffix=".tmp", dir=directory)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        read_verified(temporary, metadata["content_sha256"], metadata["byte_size"], metadata["mime_type"])
        try:
            os.link(temporary, target)
            created = True
        except FileExistsError:
            created = False
        except OSError as error:
            raise OSError("Foto konnte nicht atomar und überschreibgeschützt gespeichert werden.") from error
        read_verified(target, metadata["content_sha256"], metadata["byte_size"], metadata["mime_type"])
        return target, created
    finally:
        temporary.unlink(missing_ok=True)


def legacy_metadata(path: Path) -> dict:
    """Hash a legacy file without rejecting historical/corrupt data during migration."""
    raw = path.read_bytes()
    return {
        "content_sha256": hashlib.sha256(raw).hexdigest(),
        "byte_size": len(raw),
        "mime_type": sniff_mime(raw),
        "storage_name": path.name,
    }
