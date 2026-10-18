"""Safe access to the existing Google-Drive exchange files.

The MCP integration treats ``HausGarten-Laptop.json`` as the read-only master
snapshot.  Its only business-data write is an atomic append to the ``actions``
array of ``HausGarten-ChatGPT.json``.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import db


SNAPSHOT_NAME = "HausGarten-Laptop.json"
CHANNEL_NAME = "HausGarten-ChatGPT.json"
MAX_SNAPSHOT_BYTES = 25 * 1024 * 1024
MAX_CHANNEL_BYTES = 10 * 1024 * 1024
MAX_ACTIONS = 5000
WRITE_LOCK = threading.RLock()


class DriveStoreError(ValueError):
    def __init__(self, code: str, message: str, *, details: dict | None = None,
                 retryable: bool = False):
        super().__init__(message)
        self.code = code
        self.details = details or {}
        self.retryable = retryable


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), default=str)


def digest(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def resolve_drive_folder() -> Path:
    configured = str(os.environ.get("HAUSGARTEN_MCP_DRIVE_FOLDER", "")).strip()
    if not configured:
        try:
            configured = str(db.settings().get("backup_folder", "")).strip()
        except Exception as error:
            raise DriveStoreError(
                "DRIVE_NOT_CONFIGURED",
                "Der lokale Google-Drive-Ordner ist nicht konfiguriert.",
            ) from error
    if not configured:
        raise DriveStoreError(
            "DRIVE_NOT_CONFIGURED",
            "Der lokale Google-Drive-Ordner ist nicht konfiguriert.",
        )
    folder = Path(configured).expanduser().resolve()
    if not folder.is_dir():
        raise DriveStoreError(
            "DRIVE_UNAVAILABLE",
            "Der konfigurierte Google-Drive-Ordner ist derzeit nicht erreichbar.",
            retryable=True,
        )
    return folder


def paths() -> tuple[Path, Path]:
    folder = resolve_drive_folder()
    return folder / SNAPSHOT_NAME, folder / CHANNEL_NAME


def _read_json(path: Path, maximum: int, label: str) -> tuple[dict, str]:
    try:
        stat = path.stat()
        if stat.st_size > maximum:
            raise DriveStoreError("FILE_TOO_LARGE", f"{label} ist unerwartet groß; Verarbeitung angehalten.")
        raw = path.read_bytes()
        value = json.loads(raw.decode("utf-8-sig"))
    except FileNotFoundError as error:
        raise DriveStoreError("FILE_MISSING", f"{label} wurde im Drive-Ordner nicht gefunden.") from error
    except UnicodeError as error:
        raise DriveStoreError("INVALID_JSON", f"{label} ist nicht als UTF-8 lesbar.") from error
    except json.JSONDecodeError as error:
        raise DriveStoreError("INVALID_JSON", f"{label} enthält kein gültiges JSON.") from error
    except OSError as error:
        raise DriveStoreError("DRIVE_UNAVAILABLE", f"{label} kann derzeit nicht gelesen werden.", retryable=True) from error
    if not isinstance(value, dict):
        raise DriveStoreError("INVALID_SCHEMA", f"{label} muss ein JSON-Objekt enthalten.")
    return value, hashlib.sha256(raw).hexdigest()


def _index(rows: Any, label: str) -> dict[str, dict]:
    if not isinstance(rows, list):
        raise DriveStoreError("INVALID_SCHEMA", f"{label} muss eine Liste sein.")
    output: dict[str, dict] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise DriveStoreError("INVALID_SCHEMA", f"{label} enthält einen ungültigen Datensatz.")
        identifier = row.get("id")
        if not isinstance(identifier, str) or not identifier.strip():
            raise DriveStoreError("INVALID_SCHEMA", f"{label} enthält einen Datensatz ohne gültige ID.")
        if identifier in output:
            raise DriveStoreError("DUPLICATE_ID", f"{label} enthält die ID {identifier} mehrfach.")
        output[identifier] = row
    return output


def load_snapshot() -> dict:
    snapshot_path, _ = paths()
    value, fingerprint = _read_json(snapshot_path, MAX_SNAPSHOT_BYTES, SNAPSHOT_NAME)
    import drive_publication
    published=drive_publication.latest(snapshot_path.parent)
    if published is not None:
        value=published
        fingerprint=value['_publication']['state_sha256']
    if value.get("format") != 1:
        raise DriveStoreError("INVALID_SCHEMA", "HausGarten-Laptop.json hat ein nicht unterstütztes Format.")
    customers = _index(value.get("customers"), "customers")
    orders = _index(value.get("orders"), "orders")
    worklogs = _index(value.get("worklogs"), "worklogs")
    for order in orders.values():
        if order.get("customer_id") not in customers:
            raise DriveStoreError("BROKEN_RELATION", "Ein Auftrag verweist auf einen nicht vorhandenen Kunden.",
                                  details={"order_id": order["id"]})
    for worklog in worklogs.values():
        customer_id = worklog.get("customer_id")
        order_id = worklog.get("order_id") or ""
        if customer_id not in customers:
            raise DriveStoreError("BROKEN_RELATION", "Eine Arbeitsaufzeichnung verweist auf einen nicht vorhandenen Kunden.",
                                  details={"work_record_id": worklog["id"]})
        if order_id:
            order = orders.get(order_id)
            if not order:
                raise DriveStoreError("BROKEN_RELATION", "Eine Arbeitsaufzeichnung verweist auf einen nicht vorhandenen Auftrag.",
                                      details={"work_record_id": worklog["id"]})
            if order.get("customer_id") != customer_id:
                raise DriveStoreError("RELATION_MISMATCH", "Kunde und Auftrag einer Arbeitsaufzeichnung passen nicht zusammen.",
                                      details={"work_record_id": worklog["id"]})
    accepted = value.get("accepted_ids", [])
    if not isinstance(accepted, list) or any(not isinstance(item, str) for item in accepted):
        raise DriveStoreError("INVALID_SCHEMA", "accepted_ids in HausGarten-Laptop.json ist ungültig.")
    value["_indexes"] = {"customers": customers, "orders": orders, "worklogs": worklogs}
    value["_fingerprint"] = fingerprint
    return value


def load_channel() -> tuple[dict, str]:
    _, channel_path = paths()
    value, fingerprint = _read_json(channel_path, MAX_CHANNEL_BYTES, CHANNEL_NAME)
    if value.get("format") != 1:
        raise DriveStoreError("INVALID_SCHEMA", "HausGarten-ChatGPT.json hat ein nicht unterstütztes Format.")
    actions = value.get("actions")
    if not isinstance(actions, list) or len(actions) > MAX_ACTIONS:
        raise DriveStoreError("INVALID_SCHEMA", "Die Aktionsliste in HausGarten-ChatGPT.json ist ungültig oder zu groß.")
    seen: set[str] = set()
    for action in actions:
        if not isinstance(action, dict) or not isinstance(action.get("payload"), dict):
            raise DriveStoreError("INVALID_SCHEMA", "HausGarten-ChatGPT.json enthält eine ungültige Aktion.")
        client_id = action.get("client_id")
        if not isinstance(client_id, str) or not client_id.strip():
            raise DriveStoreError("INVALID_SCHEMA", "Eine ChatGPT-Aktion besitzt keine gültige client_id.")
        if client_id in seen:
            raise DriveStoreError("DUPLICATE_ID", "Eine client_id kommt im ChatGPT-Kanal mehrfach vor.",
                                  details={"client_id": client_id})
        seen.add(client_id)
    return value, fingerprint


def channel_action(client_id: str, package: dict | None = None) -> dict | None:
    package = package or load_channel()[0]
    return next((item for item in package["actions"] if item.get("client_id") == client_id), None)


def _backup_channel(channel_path: Path) -> Path:
    backup_folder = Path(db.DATA_DIR) / "mcp-channel-backups"
    backup_folder.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    target = backup_folder / f"HausGarten-ChatGPT.{timestamp}.json"
    shutil.copy2(channel_path, target)
    return target


def append_action(action: dict) -> dict:
    """Append one validated action with replay protection and atomic replacement."""
    if not isinstance(action, dict) or action.get("kind") != "worklog" or not isinstance(action.get("payload"), dict):
        raise DriveStoreError("INVALID_ACTION", "Nur gültige Arbeitsaufzeichnungs-Aktionen sind freigegeben.")
    client_id = str(action.get("client_id") or "").strip()
    if not client_id:
        raise DriveStoreError("INVALID_ACTION", "Die eindeutige Aktionskennung fehlt.")
    with WRITE_LOCK:
        snapshot = load_snapshot()
        package, baseline = load_channel()
        existing = channel_action(client_id, package)
        if existing:
            if digest(existing) != digest(action):
                raise DriveStoreError("REQUEST_ID_REUSED", "Diese request_id wurde bereits für andere Inhalte verwendet.")
            status = "accepted" if client_id in set(snapshot.get("accepted_ids", [])) else "queued"
            return {"status": status, "client_id": client_id, "replayed": True, "action": existing}
        if client_id in set(snapshot.get("accepted_ids", [])):
            return {"status": "accepted", "client_id": client_id, "replayed": True, "action": action}
        if len(package["actions"]) >= MAX_ACTIONS:
            raise DriveStoreError("CHANNEL_FULL", "Der ChatGPT-Aktionskanal ist voll und muss zuerst abgeglichen werden.")
        package["actions"].append(action)
        _, channel_path = paths()
        _, current = _read_json(channel_path, MAX_CHANNEL_BYTES, CHANNEL_NAME)
        if current != baseline:
            raise DriveStoreError("CHANNEL_CONFLICT", "Der Drive-Kanal wurde gleichzeitig geändert. Bitte erneut versuchen.", retryable=True)
        _backup_channel(channel_path)
        temporary = channel_path.with_name(
            f".{CHANNEL_NAME}.{os.getpid()}.{threading.get_ident()}.tmp"
        )
        try:
            encoded = json.dumps(package, ensure_ascii=False, indent=2).encode("utf-8")
            with temporary.open("wb") as handle:
                handle.write(encoded)
                handle.flush()
                os.fsync(handle.fileno())
            _, last_check = _read_json(channel_path, MAX_CHANNEL_BYTES, CHANNEL_NAME)
            if last_check != baseline:
                raise DriveStoreError("CHANNEL_CONFLICT", "Der Drive-Kanal wurde gleichzeitig geändert. Bitte erneut versuchen.", retryable=True)
            os.replace(temporary, channel_path)
        except DriveStoreError:
            raise
        except OSError as error:
            raise DriveStoreError("DRIVE_WRITE_FAILED", "Die Aktion konnte nicht sicher in Google Drive gespeichert werden.", retryable=True) from error
        finally:
            temporary.unlink(missing_ok=True)
        verified, _ = load_channel()
        stored = channel_action(client_id, verified)
        if not stored or digest(stored) != digest(action):
            raise DriveStoreError("WRITE_VERIFICATION_FAILED", "Die geschriebene Aktion konnte nicht bestätigt werden.", retryable=True)
        return {"status": "queued", "client_id": client_id, "replayed": False,
                "action": stored}
