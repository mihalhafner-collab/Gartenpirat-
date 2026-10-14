#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import mimetypes
import re
import secrets
import shutil
import socket
import sqlite3
import sys
import threading
import time
import zipfile
from datetime import date, datetime, timedelta
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

import db
import invoicing


STATIC = Path(__file__).resolve().parent / "static"
MAX_REQUEST = 25 * 1024 * 1024
ACCESS_PIN = f"{secrets.randbelow(1_000_000):06d}"
SESSION_TOKEN = secrets.token_urlsafe(32)
AUTH_ATTEMPTS = {}
AUTH_LOCK = threading.Lock()


def parse_voice(text: str) -> dict:
    """Regelbasierte deutsche Erfassung ohne externe oder kostenpflichtige KI."""
    transcript = " ".join(text.strip().split())
    lower = transcript.lower()
    result = {
        "transcript": transcript,
        "activity": transcript,
        "work_date": date.today().isoformat(),
        "source": "Spracheingabe",
    }
    if "gestern" in lower:
        result["work_date"] = (date.today() - timedelta(days=1)).isoformat()
    match = re.search(r"\b(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?\b", lower)
    if match:
        day, month, year = match.groups()
        year = year or str(date.today().year)
        year = "20" + year if len(year) == 2 else year
        try:
            result["work_date"] = date(int(year), int(month), int(day)).isoformat()
        except ValueError:
            pass
    match = re.search(
        r"(?:von\s+)?(\d{1,2})(?::(\d{2}))?\s*(?:uhr)?\s*(?:bis|\-|–)\s*(\d{1,2})(?::(\d{2}))?",
        lower,
    )
    if match:
        start_h, start_m, end_h, end_m = match.groups()
        result["start_time"] = f"{int(start_h):02d}:{int(start_m or 0):02d}"
        result["end_time"] = f"{int(end_h):02d}:{int(end_m or 0):02d}"
    match = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:minuten?|min)\s+pause", lower)
    if match:
        result["break_minutes"] = int(float(match.group(1).replace(",", ".")))
    match = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:kilometer|km)\b", lower)
    if match:
        result["kilometers"] = float(match.group(1).replace(",", "."))
    match = re.search(
        r"(?:material|verbraucht|eingebaut|entsorgt)\s*[:\-]?\s*(.+?)(?:[.;]|$)", transcript, re.I
    )
    if match:
        result["material"] = match.group(1).strip()
    if result.get("start_time") and result.get("end_time"):
        result["hours"] = db.hours_from_times(
            result["start_time"], result["end_time"], int(result.get("break_minutes", 0))
        )
    else:
        match = re.search(r"(\d+(?:[.,]\d+)?)\s*stunden?\b", lower)
        if match:
            result["hours"] = float(match.group(1).replace(",", "."))
    return result


def save_photo(data: dict) -> dict:
    data = db.existing_payload('photos', data)
    if not data.get("customer_id") or not data.get("data"):
        raise ValueError("Kunde und Foto sind erforderlich.")
    with db.connect() as database:
        if data.get("order_id"):
            order = database.execute("SELECT customer_id FROM orders WHERE id=?", (data["order_id"],)).fetchone()
            if not order: raise ValueError("Der ausgewählte Auftrag existiert nicht.")
            if order["customer_id"] != data["customer_id"]: raise ValueError("Kunde und Auftrag des Fotos passen nicht zusammen.")
        if data.get("worklog_id"):
            worklog = database.execute("SELECT customer_id,order_id FROM worklogs WHERE id=?", (data["worklog_id"],)).fetchone()
            if not worklog: raise ValueError("Die ausgewählte Arbeitsaufzeichnung existiert nicht.")
            if worklog["customer_id"] != data["customer_id"]: raise ValueError("Kunde und Arbeitsaufzeichnung des Fotos passen nicht zusammen.")
            if data.get("order_id") and worklog["order_id"] and worklog["order_id"] != data["order_id"]: raise ValueError("Auftrag und Arbeitsaufzeichnung des Fotos passen nicht zusammen.")
        for key,table,label in [('customer_id','customers','Kunde'),('order_id','orders','Auftrag'),('worklog_id','worklogs','Arbeitsaufzeichnung')]:
            if data.get(key) and not database.execute(f'SELECT 1 FROM {table} WHERE id=?',(data[key],)).fetchone():
                raise ValueError(f'Foto noch nicht übernommen: Zugeordnete {label} fehlt. Bitte die Zuordnung prüfen; das Original bleibt in der mobilen Datei erhalten.')
    encoded = str(data["data"])
    encoded = encoded.split(",", 1)[1] if "," in encoded else encoded
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > 20 * 1024 * 1024:
        raise ValueError("Das Foto darf maximal 20 MB groß sein.")
    original = Path(str(data.get("original_name") or "foto.jpg")).name
    extension = Path(original).suffix.lower()
    if extension not in {".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic"}:
        extension = ".jpg"
    filename = hashlib.sha256(raw).hexdigest()[:24] + extension
    target = db.PHOTO_DIR / filename
    if not target.exists():
        target.write_bytes(raw)
    identifier = data.get("id") or db.uid("pho")
    with db.connect() as database:
        database.execute(
            """INSERT INTO photos
            (id,customer_id,order_id,worklog_id,filename,original_name,caption,customer_visible,created_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET customer_id=excluded.customer_id,order_id=excluded.order_id,
            worklog_id=excluded.worklog_id,filename=excluded.filename,original_name=excluded.original_name,
            caption=excluded.caption,customer_visible=excluded.customer_visible""",
            (
                identifier, data["customer_id"], data.get("order_id") or None,
                data.get("worklog_id") or None, filename, original, data.get("caption", ""),
                db.truth(data.get("customer_visible")), data.get('created_at') or db.now(),
            ),
        )
        saved = db.one(database.execute("SELECT * FROM photos WHERE id=?", (identifier,)))
    saved["url"] = "/photos/" + filename
    return saved


def create_backup(folder="") -> dict:
    with DRIVE_SYNC_LOCK:
        return _create_backup(folder)


def _create_backup(folder="") -> dict:
    if folder:
        db.save_settings({"backup_folder": folder})
    else:
        folder = db.settings().get("backup_folder", "")
    destination = Path(folder).expanduser() if folder else db.BACKUP_DIR
    destination.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S_%f")
    target = destination / f"Haus-Garten-Sicherung_{stamp}.zip"
    # SQLite's backup API includes committed WAL data even during concurrent writes.
    import tempfile
    with tempfile.TemporaryDirectory() as staging:
        copy = Path(staging) / 'haus-garten.db'
        backup = sqlite3.connect(copy)
        try:
            with db.connect() as source:
                source.backup(backup)
        finally:
            backup.close()
        # The MCP request ledger contains no business text, but preserving it prevents a
        # previously confirmed ChatGPT write from being repeated after a restore.
        integration_state = db.DATA_DIR / 'mcp-integration.db'
        integration_copy = Path(staging) / 'mcp-integration.db'
        if integration_state.is_file():
            state_backup = sqlite3.connect(integration_copy)
            try:
                state_source = sqlite3.connect(integration_state)
                try:
                    state_source.backup(state_backup)
                finally:
                    state_source.close()
            finally:
                state_backup.close()
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.write(copy, "haus-garten.db")
            if integration_copy.is_file():
                archive.write(integration_copy, "mcp-integration.db")
            for name in ('drive-receipts.json','api-receipts.json','drive-held-deletions.json','drive-invoice-rejections.json'):
                control = db.DATA_DIR / name
                if control.is_file(): archive.write(control, name)
            for photo in db.PHOTO_DIR.iterdir():
                if photo.is_file():
                    archive.write(photo, f"photos/{photo.name}")
    return {"ok": True, "path": str(target), "size": target.stat().st_size}


DRIVE_SYNC_LOCK = threading.RLock()
SYNC_CAPABILITIES = {"worklog_compare_and_save": True, "entity_compare_and_save": True,
                     "customer_custom_fields_compare_and_save": True, "billing_relations": True}


def refresh_drive_capabilities(expected_snapshot_hash):
    """Explicit release operation: only metadata, never imports pending actions."""
    if not re.fullmatch(r'[0-9a-f]{64}', str(expected_snapshot_hash)):
        raise ValueError('Für die technische Aktualisierung wird der geprüfte Dateistand benötigt.')
    with DRIVE_SYNC_LOCK:
        folder = str(db.settings().get('backup_folder', '')).strip()
        if not folder:
            raise ValueError('Der bestehende Drive-Ordner ist nicht eingerichtet.')
        target = Path(folder) / 'HausGarten-Laptop.json'
        before = target.read_bytes()
        if hashlib.sha256(before).hexdigest() != expected_snapshot_hash:
            raise ValueError('Der Drive-Datenstand hat sich geändert. Keine Datei überschrieben.')
        snapshot = json.loads(before.decode('utf-8-sig'))
        if not isinstance(snapshot, dict):
            raise ValueError('Ungültiger Drive-Datenexport.')
        for key in ('customers', 'orders', 'worklogs', 'invoices', 'photos'):
            rows = snapshot.get(key)
            if not isinstance(rows, list) or any(not isinstance(row, dict) or not row.get('id') for row in rows):
                raise ValueError('Unvollständiger Datenexport. Keine technische Freigabe.')
            if len({str(row['id']) for row in rows}) != len(rows):
                raise ValueError('Doppelte IDs im Datenexport. Keine technische Freigabe.')
        current = snapshot.get('capabilities', {})
        if not isinstance(current, dict):
            raise ValueError('Ungültige Sync-Angaben. Keine Datei überschrieben.')
        original_data = {key: value for key, value in snapshot.items() if key != 'capabilities'}
        backup = db.BACKUP_DIR / ('HausGarten-Laptop-before-customer-tools-' + expected_snapshot_hash[:16] + '.json')
        backup.parent.mkdir(parents=True, exist_ok=True)
        if backup.exists():
            if backup.read_bytes() != before:
                raise ValueError('Sicherung stimmt nicht überein. Aktualisierung angehalten.')
        else:
            with backup.open('xb') as handle:
                handle.write(before)
        if target.read_bytes() != before:
            raise ValueError('Der Drive-Datenstand wurde parallel geändert. Keine Datei überschrieben.')
        snapshot['capabilities'] = {**current, **SYNC_CAPABILITIES}
        write_json_atomic(target, snapshot)
        after = json.loads(target.read_text(encoding='utf-8'))
        if {key: value for key, value in after.items() if key != 'capabilities'} != original_data:
            raise RuntimeError('Datenvergleich fehlgeschlagen. Sicherung prüfen.')
        return {'ok': True, 'records_unchanged': True, 'pending_actions_imported': 0,
                'path': str(target), 'backup': str(backup),
                'snapshot_hash': hashlib.sha256(target.read_bytes()).hexdigest()}


def write_json_atomic(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
    temporary.replace(path)


def read_receipts(path, label='Abgleichbestätigungen'):
    if not path.exists(): return set()
    try:
        values = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(values, list) or any(not isinstance(item, str) for item in values): raise ValueError()
        return set(values)
    except (ValueError, OSError):
        raise ValueError(f'Die {label} sind beschädigt. Abgleich angehalten; bitte Sicherung prüfen. Keine Schutzkennungen wurden zurückgesetzt.')


def apply_sync_action(action):
    if not isinstance(action, dict) or not isinstance(action.get('payload', {}), dict):
        raise ValueError('Ungültiger Eintrag im Abgleichpaket.')
    kind = action.get('kind')
    if kind not in SAVERS: raise ValueError('Unbekannte Datenart im Abgleichpaket.')
    payload = dict(action.get('payload', {}))
    tables={'customer':'customers','order':'orders','worklog':'worklogs'}
    if kind in tables and payload.get('id') and not payload.get('_mcp_create') and not payload.get('_mcp_base') and not payload.get('_sync_base'):
        with db.connect() as connection:
            current=db.one(connection.execute('SELECT * FROM '+tables[kind]+' WHERE id=?',(payload['id'],)))
        if current:
            numbers={'hours','break_minutes','kilometers','billed','customer_visible','active','garden_area'}
            def compare(key,value):
                if key in numbers:return db.number(value,0)
                if key=='custom_fields':return json.dumps(json.loads(value or '{}') if isinstance(value,str) else value or {},sort_keys=True)
                return '' if value is None else value
            if any(compare(key,value)!=compare(key,current.get(key)) for key,value in payload.items()
                   if key in current and key not in {'created_at','updated_at','source'}):
                raise ValueError('Ältere Änderung ohne Vergleichsstand: bitte manuell prüfen. Vorhandene Daten bleiben unverändert.')
            return current  # A repeated identical legacy action is a read-only no-op.
    # Manual Pixel edits use the same guarded saver as MCP, not a parallel write path.
    # An older queued work record must not adopt a customer default introduced later.
    if kind == "worklog" and "billing_customer_id" not in payload:
        with db.connect() as connection:
            prior = db.one(connection.execute("SELECT * FROM worklogs WHERE id=?", (payload.get("id"),)))
        payload["billing_customer_id"] = (prior.get("billing_customer_id") or prior["customer_id"]) if prior else payload.get("customer_id")
    if kind in ('customer', 'order', 'worklog'):
        base = payload.get('_sync_base')
        if base is not None:
            if not isinstance(base, dict) or not base.get('id') or base['id'] != payload.get('id'):
                raise ValueError('Ungültiger Vergleichsstand der Pixel-Änderung.')
            payload['_mcp_base'] = base
        if payload.get('updated_at'):
            payload['_sync_updated_at'] = payload['updated_at']
    if kind in ('customer','order','worklog','invoice','photo') and not payload.get('id'):
        client_id = str(action.get('client_id') or '')
        if not client_id: raise ValueError('Die eindeutige Abgleichkennung fehlt.')
        payload['id'] = kind + '_sync_' + hashlib.sha256(client_id.encode()).hexdigest()[:32]
    return SAVERS[kind](payload)


def drive_sync(folder="", invoice_only=False) -> dict:
    with DRIVE_SYNC_LOCK:
        return _drive_sync(folder,invoice_only)


def guard_drive_export(previous, candidate, imported_records):
    """A temporary web replica must not erase records/newer revisions held by Drive."""
    if not previous:
        return
    numeric = {'hours', 'break_minutes', 'kilometers', 'billed', 'customer_visible', 'active', 'garden_area', 'total', 'tax_rate'}
    for collection in ('customers', 'orders', 'worklogs', 'invoices', 'photos'):
        rows = previous.get(collection)
        if not isinstance(rows, list) or any(not isinstance(row, dict) or not row.get('id') for row in rows):
            raise ValueError('Drive-Gesamtstand ist unvollständig. Kein Export eines Ersatzbestandes.')
        if len({row['id'] for row in rows}) != len(rows):
            raise ValueError('Doppelte IDs im Drive-Gesamtstand. Keine Datei überschrieben.')
        local = {row['id']: row for row in candidate[collection]}
        for row in rows:
            current = local.get(row['id'])
            if current is None:
                raise ValueError('Drive enthält Datensätze, die der Web-App fehlen. Gesamtstand nicht überschrieben; Datenabgleich prüfen.')
            if (collection, row['id']) in imported_records:
                continue
            def comparable(key, value):
                if key in numeric:
                    return float(value or 0)
                if key == 'custom_fields':
                    return json.dumps(json.loads(value or '{}') if isinstance(value, str) else value or {},sort_keys=True)
                return '' if value is None else value
            # Joined display fields and generated previews are not stored business columns.
            ignored = {'customer_name', 'order_title', 'total_hours', 'thumbnail_base64', 'thumbnail_mime', 'pdf_base64', 'updated_at', 'created_at'}
            changed = any(comparable(key, value) != comparable(key, current.get(key))
                          for key, value in row.items() if key in current and key not in ignored)
            if not changed:
                continue
            try:
                remote_time = datetime.fromisoformat(str(row['updated_at']).replace('Z', '+00:00'))
                local_time = datetime.fromisoformat(str(current['updated_at']).replace('Z', '+00:00'))
                if remote_time.tzinfo is None or local_time.tzinfo is None or remote_time >= local_time:
                    raise ValueError()
            except (KeyError, TypeError, ValueError):
                raise ValueError('Drive enthält einen neueren oder widersprüchlichen Datenstand. Web-Export angehalten; keine Cloud-Daten überschrieben.')


def _drive_sync(folder="", invoice_only=False) -> dict:
    """Tauscht kleine, idempotente JSON-Pakete über einen Drive-Desktop-Ordner aus."""
    if folder:
        db.save_settings({"backup_folder": folder, "drive_sync_enabled": "1"})
    settings = db.settings()
    configured = str(settings.get("backup_folder", "")).strip()
    if not configured:
        raise ValueError("Bitte zuerst den lokalen Google-Drive-Ordner auswählen.")
    destination = Path(configured).expanduser()
    destination.mkdir(parents=True, exist_ok=True)
    target = destination / 'HausGarten-Laptop.json'
    before = target.read_bytes() if target.exists() else None
    previous = json.loads(before.decode('utf-8-sig')) if before else {}
    if not isinstance(previous, dict):
        raise ValueError('Drive-Gesamtstand ist beschädigt. Keine Datei überschrieben.')
    imported_records = set()

    receipts_path = db.DATA_DIR / "drive-receipts.json"
    accepted = read_receipts(receipts_path)
    rejected_path=db.DATA_DIR/'drive-invoice-rejections.json'
    try: rejected=json.loads(rejected_path.read_text(encoding='utf-8'))
    except (FileNotFoundError,json.JSONDecodeError): rejected={}
    # A damaged hold list must never turn protected old deletes into live actions.
    held=read_receipts(db.DATA_DIR/'drive-held-deletions.json', 'gesicherten Löschsperren')

    imported, failed = 0, []
    sources = list(destination.glob("HausGarten-Mobile-*.json"))
    chatgpt_source = destination / "HausGarten-ChatGPT.json"
    if chatgpt_source.exists():
        sources.append(chatgpt_source)
    for source in sources:
        try:
            package = json.loads(source.read_text(encoding="utf-8"))
            if not isinstance(package, dict) or not isinstance(package.get('actions', []), list):
                raise ValueError('Ungültiges Abgleichpaket. Eine Liste von Änderungen wird erwartet.')
            for action in package.get("actions", []):
                if not isinstance(action, dict) or not isinstance(action.get('payload', {}), dict):
                    failed.append({'file':source.name,'error':'Ungültiger Eintrag im Abgleichpaket.'})
                    continue
                action_id = str(action.get("client_id", ""))
                if not action_id or action_id in accepted:
                    continue
                kind = action.get("kind")
                if kind == 'delete' and action_id in held:
                    continue
                if kind == 'delete':
                    failed.append({'file':source.name,'client_id':action_id,'kind':kind,
                                   'code':'DELETE_REVIEW_REQUIRED','error':'Löschänderung bleibt zur gesonderten Prüfung erhalten. Keine Drive-Daten automatisch gelöscht.'})
                    continue
                if kind == 'invoice_issue' and action_id in rejected:
                    failed.append(rejected[action_id])
                    continue
                if invoice_only and kind not in ('invoice', 'invoice_issue'):
                    continue
                try:
                    apply_sync_action(action)
                    collection = {'customer':'customers','order':'orders','worklog':'worklogs','invoice':'invoices','photo':'photos'}.get(kind)
                    if collection and action.get('payload', {}).get('id'):
                        imported_records.add((collection, action['payload']['id']))
                    accepted.add(action_id)
                    write_json_atomic(receipts_path, sorted(accepted))
                    imported += 1
                except Exception as error:
                    failure={'file':source.name,'client_id':action_id,'kind':kind,'invoice_id':action.get('payload',{}).get('id',''),'error':str(error)}
                    failed.append(failure)
                    if kind == 'invoice_issue': rejected[action_id]=failure
        except Exception as error:
            failed.append({"file": source.name, "error": str(error)})

    write_json_atomic(receipts_path, sorted(accepted))
    write_json_atomic(rejected_path, rejected)
    snapshot = {**previous,
        "format": 1,
        "exported_at": db.now(),
        "capabilities": {**previous.get('capabilities', {}), **SYNC_CAPABILITIES},
        "customers": db.list_customers(),
        "orders": db.list_orders(),
        "worklogs": db.list_worklogs(),
        "invoices": db.list_invoices(),
        "photos": db.mobile_photo_thumbnails(db.list_photos()),
        "dashboard": db.dashboard(),
        "accepted_ids": sorted(accepted),
        "sync_errors": list({item.get('client_id',item['file']):item for item in [*rejected.values(),*failed]}.values()),
        "rejected_invoice_ids": list(rejected),
        "held_delete_ids": sorted(held),
        "invoice_issuer": {key: settings.get(key, '') for key in (
            'business_name', 'owner_name', 'business_street', 'business_postal_code',
            'business_city', 'business_legal_info', 'business_iban', 'business_bank',
            'business_bic', 'business_phone', 'business_email')},
    }
    for invoice in snapshot['invoices']:
        if invoice.get('issued_at'):
            invoice['pdf_base64'] = base64.b64encode(invoicing.issued_pdf(invoice['id'])).decode('ascii')
            archive_folder = destination / 'Rechnungen' / str(invoice['invoice_date'][:4])
            archive_folder.mkdir(parents=True, exist_ok=True)
            archive_name = re.sub(r'[^A-Za-z0-9_-]', '-', invoice['invoice_number']) + '.pdf'
            archive_file = archive_folder / archive_name
            if not archive_file.exists():
                archive_file.write_bytes(invoicing.issued_pdf(invoice['id']))
            elif hashlib.sha256(archive_file.read_bytes()).hexdigest() != hashlib.sha256(invoicing.issued_pdf(invoice['id'])).hexdigest():
                failed.append({'file':archive_file.name,'error':'Archiv-PDF weicht ab; keine Datei überschrieben. Sicherung prüfen.'})
    drive_photos = destination / "HausGarten-Fotos"
    drive_photos.mkdir(exist_ok=True)
    for photo in db.PHOTO_DIR.iterdir():
        if photo.is_file():
            target_photo = drive_photos / photo.name
            if not target_photo.exists() or target_photo.stat().st_mtime < photo.stat().st_mtime:
                shutil.copy2(photo, target_photo)
    temporary = destination / "HausGarten-Laptop.tmp"
    target = destination / "HausGarten-Laptop.json"
    # Include failures discovered during archive/photo export, not only import.
    snapshot['sync_errors'] = list({item.get('client_id',item['file']):item for item in [*rejected.values(),*failed]}.values())
    try:
        guard_drive_export(previous, snapshot, imported_records)
    except ValueError as conflict:
        failed.append({'file':target.name, 'code':'REMOTE_SNAPSHOT_CONFLICT', 'error':str(conflict)})
        return {'ok':False,'imported':imported,'failed':failed,'path':str(target),
                'snapshot_exported':False,'confirmation_scope':'local_only','cloud_confirmed':False}
    if (target.read_bytes() if target.exists() else None) != before:
        raise ValueError('Drive-Gesamtstand wurde parallel geändert. Keine Datei überschrieben; Abgleich erneut versuchen.')
    if before:
        backup = db.BACKUP_DIR / ('Drive-before-web-export-' + hashlib.sha256(before).hexdigest()[:20] + '.json')
        if not backup.exists():
            with backup.open('xb') as handle:
                handle.write(before)
    temporary.write_text(json.dumps(snapshot, ensure_ascii=False), encoding="utf-8")
    temporary.replace(target)
    return {"ok": not failed, "imported": imported, "failed": failed, "path": str(target), "photos": len(list(drive_photos.iterdir())), "confirmation_scope":"desktop_drive_file", "cloud_confirmed":False}


def drive_sync_loop():
    while True:
        try:
            settings = db.settings()
            if settings.get("drive_sync_enabled") == "1" and settings.get("backup_folder"):
                drive_sync(invoice_only=db.settings().get('drive_invoice_only') == '1')
        except Exception as error:
            sys.stdout.write(f"[Drive-Abgleich] {error}\n")
        time.sleep(30)


SAVERS = {
    "customer": db.save_customer,
    "order": db.save_order,
    "worklog": db.save_worklog,
    "invoice": db.save_invoice,
    "invoice_issue": lambda payload: invoicing.issue(str(payload.get('id','')), payload.get('confirmed') is True),
    "photo": save_photo,
    "delete": lambda payload: db.remove_entity(str(payload.get("entity", "")), str(payload.get("id", ""))),
}


class AppHTTPServer(ThreadingHTTPServer):
    # Assets and API collections are loaded in parallel by browsers.
    request_queue_size = 64


class Handler(SimpleHTTPRequestHandler):
    server_version = "HausGarten/0.1"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC), **kwargs)

    def log_message(self, fmt, *args):
        sys.stdout.write(f"[{datetime.now().strftime('%H:%M:%S')}] {fmt % args}\n")

    def send_json(self, value, status=HTTPStatus.OK):
        content = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(content)

    def fail(self, message, status=HTTPStatus.BAD_REQUEST):
        if status == HTTPStatus.INTERNAL_SERVER_ERROR:
            self.log_error('Internal request error: %s', type(message).__name__)
            message = 'Die Anfrage konnte nicht verarbeitet werden. Deine Eingaben bleiben erhalten; bitte erneut versuchen.'
        elif isinstance(message, sqlite3.IntegrityError):
            message = 'Der Eintrag ist bereits vorhanden oder eine Zuordnung fehlt. Bitte Kunde, Auftrag und bestehende Einträge prüfen.'
        payload = {"error": str(message)}
        if isinstance(message, db.DuplicateWarning):
            payload.update({"code": message.code, "matches": message.matches})
            status = HTTPStatus.CONFLICT
        self.send_json(payload, status)

    def body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length < 0 or length > MAX_REQUEST:
            raise ValueError("Die Anfrage ist zu groß.")
        raw = self.rfile.read(length)
        try:
            result = json.loads(raw.decode('utf-8')) if raw else {}
        except (UnicodeError, ValueError):
            raise ValueError('Ungültiges Datenpaket. Bitte die Eingabe erneut senden.')
        if not isinstance(result, dict): raise ValueError('Die Anfrage muss ein gültiges Datenobjekt enthalten.')
        return result

    def safe_origin(self):
        origin = self.headers.get('Origin')
        # Native/file clients have no Origin or "null". Browsers must be same-origin.
        if origin and origin != 'null':
            parsed = urlparse(origin)
            if parsed.scheme not in ('http','https') or parsed.netloc != self.headers.get('Host'):
                self.fail('Diese Anfrage stammt nicht aus der App und wurde blockiert.', HTTPStatus.FORBIDDEN)
                return False
        host = (urlparse('//'+self.headers.get('Host', '')).hostname or '').lower()
        if self.is_local() and host not in ('localhost','127.0.0.1','::1'):
            self.fail('Ungültige lokale App-Adresse.', HTTPStatus.FORBIDDEN)
            return False
        return True

    def is_local(self):
        return self.client_address[0] in {"127.0.0.1", "::1"}

    def authorized(self):
        if self.is_local():
            return True
        cookies = self.headers.get("Cookie", "")
        return any(part.strip() == f"hg_session={SESSION_TOKEN}" for part in cookies.split(";"))

    def require_authorization(self, path):
        if not self.safe_origin(): return False
        if path in {"/api/health", "/api/auth"}:
            return True
        if (path.startswith("/api/") or path.startswith("/photos/")) and not self.authorized():
            self.fail("Zugangscode erforderlich.", HTTPStatus.UNAUTHORIZED)
            return False
        return True

    def do_GET(self):
        parsed = urlparse(self.path)
        path, query = parsed.path, parse_qs(parsed.query)
        try:
            if not self.require_authorization(path):
                return
            if path == "/api/health":
                return self.send_json({"ok": True, "time": db.now()})
            if path == "/api/dashboard":
                return self.send_json(db.dashboard())
            if path == "/api/integrity":
                return self.send_json(db.integrity_report())
            if path == "/api/customers":
                return self.send_json(db.list_customers(query.get("q", [""])[0]))
            if path == "/api/orders":
                return self.send_json(db.list_orders(query.get("customer_id", [None])[0]))
            if path == "/api/worklogs":
                filters = {key: query.get(key, [None])[0] for key in ("customer_id", "order_id", "work_date")}
                filters["unbilled"] = query.get("unbilled", [""])[0] == "1"
                return self.send_json(db.list_worklogs(filters))
            if path == "/api/invoices":
                return self.send_json(db.list_invoices(query.get("customer_id", [None])[0]))
            if path.startswith('/api/invoice-pdf/') or path.startswith('/api/invoice-preview/'):
                identifier = unquote(path.rsplit('/', 1)[-1])
                content = invoicing.preview(identifier) if path.startswith('/api/invoice-preview/') else invoicing.issued_pdf(identifier)
                self.send_response(HTTPStatus.OK)
                self.send_header('Content-Type', 'application/pdf')
                self.send_header('Content-Length', str(len(content)))
                self.send_header('Content-Disposition', 'inline; filename="Rechnung.pdf"')
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(content)
                return
            if path == "/api/photos":
                filters = {key: query.get(key, [None])[0] for key in ("customer_id", "order_id", "worklog_id")}
                return self.send_json(db.list_photos(filters))
            if path.startswith("/api/customer-view/"):
                result = db.customer_view(unquote(path.rsplit("/", 1)[-1]))
                return self.send_json(result) if result else self.fail("Kunde nicht gefunden.", HTTPStatus.NOT_FOUND)
            if path == "/api/settings":
                return self.send_json(db.settings())
            if path.startswith("/photos/"):
                return self.photo(unquote(path[len("/photos/"):]))
            return super().do_GET()
        except Exception as error:
            self.fail(error, HTTPStatus.INTERNAL_SERVER_ERROR)

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            if not self.safe_origin(): return
            data = self.body()
            if path == "/api/auth":
                client = self.client_address[0]
                if not self.is_local():
                    with AUTH_LOCK:
                        attempts, started = AUTH_ATTEMPTS.get(client, (0, time.monotonic()))
                        if time.monotonic()-started >= 300: attempts, started = 0, time.monotonic()
                        if attempts >= 5:
                            return self.fail('Zu viele falsche Zugangscodes. Bitte fünf Minuten warten.',HTTPStatus.TOO_MANY_REQUESTS)
                        AUTH_ATTEMPTS[client]=(attempts+1, started)
                if secrets.compare_digest(str(data.get("pin", "")), ACCESS_PIN):
                    with AUTH_LOCK: AUTH_ATTEMPTS.pop(client,None)
                    content = json.dumps({"ok": True}).encode("utf-8")
                    self.send_response(HTTPStatus.OK)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(content)))
                    self.send_header("Set-Cookie", f"hg_session={SESSION_TOKEN}; Path=/; HttpOnly; SameSite=Strict")
                    self.end_headers()
                    self.wfile.write(content)
                    return
                return self.fail("Zugangscode ist nicht korrekt.", HTTPStatus.UNAUTHORIZED)
            if not self.require_authorization(path):
                return
            direct = {
                "/api/customers": db.save_customer,
                "/api/orders": db.save_order,
                "/api/worklogs": db.save_worklog,
                "/api/invoices": db.save_invoice,
                "/api/photos": save_photo,
            }
            if path in direct:
                with DRIVE_SYNC_LOCK: saved = direct[path](data)
                return self.send_json(saved, HTTPStatus.CREATED)
            if path == '/api/invoice-issue':
                with DRIVE_SYNC_LOCK: invoice = invoicing.issue(str(data.get('id','')), data.get('confirmed') is True)
                return self.send_json(invoice)
            if path == "/api/voice/parse":
                return self.send_json(parse_voice(str(data.get("text", ""))))
            if path == "/api/backup":
                return self.send_json(create_backup(str(data.get("folder", "")).strip()))
            if path == "/api/drive-sync":
                return self.send_json(drive_sync(str(data.get("folder", "")).strip(),invoice_only=db.settings().get('drive_invoice_only') == '1'))
            if path == "/api/settings":
                return self.send_json(db.save_settings(data))
            if path == "/api/sync":
                results = []
                actions = data.get('actions', [])
                if not isinstance(actions, list): raise ValueError('Ungültige Liste von Abgleichaktionen.')
                with DRIVE_SYNC_LOCK:
                    receipt_file = db.DATA_DIR / 'api-receipts.json'
                    accepted = read_receipts(receipt_file)
                    for action in actions:
                        client_id = action.get('client_id') if isinstance(action, dict) else None
                        try:
                            if not isinstance(client_id, str) or not client_id: raise ValueError('Die eindeutige Abgleichkennung fehlt oder ist ungültig.')
                            saved = None if client_id in accepted else apply_sync_action(action)
                            accepted.add(client_id)
                            write_json_atomic(receipt_file, sorted(accepted))
                            results.append({'client_id':client_id, 'ok':True, 'result':saved})
                        except Exception as error:
                            message = str(error) if isinstance(error, ValueError) else 'Eintrag nicht übernommen. Bitte Zuordnungen und Eingaben prüfen.'
                            results.append({'client_id':client_id, 'ok':False, 'error':message})
                return self.send_json({"results": results})
            self.fail("Unbekannter Endpunkt.", HTTPStatus.NOT_FOUND)
        except (ValueError, KeyError, sqlite3.IntegrityError) as error:
            self.fail(error)
        except Exception as error:
            self.fail(error, HTTPStatus.INTERNAL_SERVER_ERROR)

    def do_PUT(self):
        path = urlparse(self.path).path
        parts = path.strip("/").split("/")
        try:
            if not self.require_authorization(path):
                return
            if len(parts) != 3 or parts[0] != "api":
                return self.fail("Unbekannter Endpunkt.", HTTPStatus.NOT_FOUND)
            data = self.body()
            data["id"] = parts[2]
            savers = {
                "customers": db.save_customer, "orders": db.save_order,
                "worklogs": db.save_worklog, "invoices": db.save_invoice,
            }
            if parts[1] not in savers:
                return self.fail("Unbekannter Endpunkt.", HTTPStatus.NOT_FOUND)
            with DRIVE_SYNC_LOCK: saved = savers[parts[1]](data)
            self.send_json(saved)
        except (ValueError, KeyError, sqlite3.IntegrityError) as error:
            self.fail(error)
        except Exception as error:
            self.fail(error, HTTPStatus.INTERNAL_SERVER_ERROR)

    def do_DELETE(self):
        path = urlparse(self.path).path
        parts = path.strip("/").split("/")
        try:
            if not self.require_authorization(path):
                return
            if len(parts) != 3 or parts[0] != "api":
                return self.fail("Unbekannter Endpunkt.", HTTPStatus.NOT_FOUND)
            types = {"customers": "customer", "orders": "order", "worklogs": "worklog", "invoices": "invoice", "photos": "photo"}
            if parts[1] not in types:
                return self.fail("Unbekannter Eintragstyp.", HTTPStatus.NOT_FOUND)
            with DRIVE_SYNC_LOCK: result = db.remove_entity(types[parts[1]], unquote(parts[2]))
            return self.send_json(result)
        except (ValueError, KeyError, sqlite3.IntegrityError) as error:
            self.fail(error)
        except Exception as error:
            self.fail(error, HTTPStatus.INTERNAL_SERVER_ERROR)

    def photo(self, filename):
        target = db.PHOTO_DIR / Path(filename).name
        if not target.is_file():
            return self.send_error(HTTPStatus.NOT_FOUND)
        content = target.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", mimetypes.guess_type(target.name)[0] or "application/octet-stream")
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Cache-Control", "private,max-age=86400")
        self.end_headers()
        self.wfile.write(content)


def local_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as channel:
            channel.connect(("8.8.8.8", 80))
            return channel.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    options = parser.parse_args()
    db.init()
    threading.Thread(target=drive_sync_loop, daemon=True).start()
    server = AppHTTPServer((options.host, options.port), Handler)
    print("\nHaus- & Gartenservice-App gestartet")
    print(f"Laptop: http://localhost:{options.port}")
    print(f"Android im selben WLAN: http://{local_ip()}:{options.port}")
    if options.host not in {"127.0.0.1", "localhost", "::1"}:
        print(f"Zugangscode für Android: {ACCESS_PIN}")
    print("Beenden mit Strg+C\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
