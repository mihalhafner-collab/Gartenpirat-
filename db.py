from __future__ import annotations

import json
import base64
import io
import sqlite3
import uuid
import math
import re
from difflib import SequenceMatcher
from decimal import Decimal, ROUND_HALF_UP
from datetime import date, datetime, timezone
from pathlib import Path
import billing
import photo_storage


ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
DB_PATH = DATA_DIR / "haus-garten.db"
PHOTO_DIR = DATA_DIR / "photos"
BACKUP_DIR = DATA_DIR / "backups"


def now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def uid(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex}"


class DuplicateWarning(ValueError):
    code = "DUPLICATE"
    def __init__(self, message: str, matches: list[dict]):
        super().__init__(message)
        self.matches = matches

WORKLOG_DEFAULTS = {"break_minutes": 0, "kilometers": 0, "billed": 0, "customer_visible": 1}

def sync_stamp(data):
    """Preserve a validated Pixel revision on journal import, not on normal UI writes."""
    value = data.get('_sync_updated_at')
    if value is None:
        return now()
    if not isinstance(value, str) or len(value) > 40:
        raise ValueError('Ungültiger Änderungszeitpunkt im Drive-Abgleich.')
    try:
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        if parsed.tzinfo is None:
            raise ValueError()
    except (ValueError, TypeError):
        raise ValueError('Ungültiger Änderungszeitpunkt im Drive-Abgleich.')
    return value

def _norm(value):
    return re.sub(r"\s+", " ", str(value or "").strip().casefold())

def _digits(value):
    return re.sub(r"\D", "", str(value or ""))

def _similar(left, right):
    left, right = _norm(left), _norm(right)
    return SequenceMatcher(None, left, right).ratio() if left and right else 0.0

def customer_duplicate_matches(data, exclude_id=None):
    with connect() as database:
        rows = database.execute("SELECT * FROM customers WHERE (? IS NULL OR id<>?)", (exclude_id, exclude_id)).fetchall()
    name = _norm(data.get("name") or " ".join(filter(None, [data.get("first_name"), data.get("last_name")])))
    address = _norm(" ".join(filter(None, [data.get("street"), data.get("postal_code"), data.get("city")])))
    email, phone, sevdesk = _norm(data.get("email")), _digits(data.get("phone")), _norm(data.get("sevdesk_contact_id"))
    matches = []
    for row in rows:
        exact = ((email and email == _norm(row["email"])) or (phone and phone == _digits(row["phone"])) or (sevdesk and sevdesk == _norm(row["sevdesk_contact_id"])))
        name_close = _similar(name, row["name"]) >= .88 or (_norm(row["company"]) and _similar(name, row["company"]) >= .88)
        row_address = _norm(" ".join(filter(None, [row["street"], row["postal_code"], row["city"]])))
        address_close = address and row_address and (address == row_address or _similar(address, row_address) >= .92)
        if exact or (name_close and (address_close or _norm(data.get("city")) == _norm(row["city"]))):
            matches.append({"id": row["id"], "name": row["name"], "company": row["company"], "city": row["city"], "active": bool(row["active"])})
    return matches

def order_duplicate_matches(data, exclude_id=None):
    with connect() as database:
        rows = database.execute("SELECT * FROM orders WHERE customer_id=? AND (? IS NULL OR id<>?)", (data.get("customer_id"), exclude_id, exclude_id)).fetchall()
    title, description, location = data.get("title"), data.get("description"), data.get("location")
    try:
        target_date = date.fromisoformat(str(data.get("scheduled_date"))) if data.get("scheduled_date") else None
    except ValueError:
        target_date = None
    matches = []
    for row in rows:
        if _norm(row["status"]) in {"erledigt", "abgerechnet", "storniert"}:
            continue
        date_close = True
        if target_date and row["scheduled_date"]:
            try: date_close = abs((target_date - date.fromisoformat(row["scheduled_date"])).days) <= 2
            except ValueError: date_close = False
        title_close = _similar(title, row["title"]) >= .86
        location_close = not location or not row["location"] or _norm(location) == _norm(row["location"])
        description_close = not description or not row["description"] or _similar(description, row["description"]) >= .82
        if title_close and date_close and location_close and description_close:
            matches.append({"id": row["id"], "title": row["title"], "scheduled_date": row["scheduled_date"], "location": row["location"], "status": row["status"]})
    return matches

def integrity_report():
    checks = {}
    with connect() as database:
        checks["orders_missing_customer"] = [r[0] for r in database.execute("SELECT id FROM orders WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id=orders.customer_id)")]
        checks["worklogs_missing_customer"] = [r[0] for r in database.execute("SELECT id FROM worklogs WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id=worklogs.customer_id)")]
        checks["worklogs_missing_order"] = [r[0] for r in database.execute("SELECT id FROM worklogs WHERE order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.id=worklogs.order_id)")]
        checks["worklogs_customer_order_mismatch"] = [r[0] for r in database.execute("SELECT w.id FROM worklogs w JOIN orders o ON o.id=w.order_id WHERE w.customer_id<>o.customer_id")]
        checks["customers_invalid_billing"] = [r[0] for r in database.execute("SELECT id FROM customers WHERE billing_mode NOT IN ('direct','via_customer') OR (billing_mode='via_customer' AND (default_billing_customer_id IS NULL OR default_billing_customer_id=id OR NOT EXISTS(SELECT 1 FROM customers c WHERE c.id=customers.default_billing_customer_id)))")]
        for table in ("orders","worklogs"):
            checks[table+"_missing_billing_customer"] = [r[0] for r in database.execute(f"SELECT id FROM {table} WHERE billing_customer_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM customers c WHERE c.id={table}.billing_customer_id)")]
        checks["invoices_customer_order_mismatch"] = [r[0] for r in database.execute("SELECT i.id FROM invoices i JOIN orders o ON o.id=i.order_id WHERE i.customer_id<>o.customer_id")]
        checks["photos_invalid_relation"] = [r[0] for r in database.execute("SELECT p.id FROM photos p LEFT JOIN customers c ON c.id=p.customer_id LEFT JOIN orders o ON o.id=p.order_id LEFT JOIN worklogs w ON w.id=p.worklog_id WHERE c.id IS NULL OR (p.order_id IS NOT NULL AND o.id IS NULL) OR (p.worklog_id IS NOT NULL AND w.id IS NULL) OR (p.order_id IS NOT NULL AND o.customer_id<>p.customer_id) OR (p.worklog_id IS NOT NULL AND w.customer_id<>p.customer_id)")]
        photo_rows = dicts(database.execute("SELECT id,filename,storage_name,content_sha256,byte_size,mime_type FROM photos"))
    referenced, missing, unsafe, hash_mismatch, size_mismatch, invalid_content = set(), [], [], [], [], []
    for row in photo_rows:
        name = str(row.get("storage_name") or row.get("filename") or "")
        if not name or Path(name).name != name:
            unsafe.append(row["id"])
            continue
        referenced.add(name)
        legacy_name = str(row.get("filename") or "")
        if legacy_name and Path(legacy_name).name == legacy_name:
            # Canonical migration deliberately retains the old file as a
            # recoverable copy; it is still owned by this photo, not an orphan.
            referenced.add(legacy_name)
        source = PHOTO_DIR / name
        if not source.is_file():
            missing.append(row["id"])
            continue
        try:
            metadata = photo_storage.inspect(source.read_bytes())
            if row.get("content_sha256") and metadata["content_sha256"] != str(row["content_sha256"]).lower():
                hash_mismatch.append(row["id"])
            if row.get("byte_size") is not None and metadata["byte_size"] != int(row["byte_size"]):
                size_mismatch.append(row["id"])
            if not metadata.get("mime_type") or (row.get("mime_type") and metadata["mime_type"] != row["mime_type"]):
                invalid_content.append(row["id"])
        except (OSError, ValueError):
            invalid_content.append(row["id"])
    checks["photos_unsafe_filename"] = unsafe
    checks["photos_missing_file"] = missing
    checks["photos_hash_mismatch"] = hash_mismatch
    checks["photos_size_mismatch"] = size_mismatch
    checks["photos_invalid_content"] = invalid_content
    checks["photo_files_orphaned"] = sorted(
        item.name for item in PHOTO_DIR.iterdir()
        if item.is_file() and not item.name.startswith(".photo-upload-") and item.name not in referenced
    ) if PHOTO_DIR.is_dir() else []
    checks["ok"] = not any(checks.values())
    checks["counts"] = {key: len(value) for key, value in checks.items() if isinstance(value, list)}
    return checks


class ClosingConnection(sqlite3.Connection):
    """Transaktion abschließen und die Datei auch unter Windows sicher freigeben."""

    def __exit__(self, exc_type, exc_value, traceback):
        try:
            if exc_type is None:
                self.commit()
            else:
                self.rollback()
        finally:
            self.close()
        return False


def connect() -> sqlite3.Connection:
    database = sqlite3.connect(DB_PATH, timeout=20, factory=ClosingConnection)
    database.row_factory = sqlite3.Row
    database.execute("PRAGMA foreign_keys=ON")
    database.execute("PRAGMA journal_mode=WAL")
    return database


def init() -> None:
    for folder in (DATA_DIR, PHOTO_DIR, BACKUP_DIR):
        folder.mkdir(parents=True, exist_ok=True)
    # A recoverable SQLite backup BEFORE the additive billing migration.
    if DB_PATH.is_file():
        probe = sqlite3.connect(DB_PATH)
        try:
            columns = {r[1] for r in probe.execute("PRAGMA table_info(worklogs)")}
            if columns and "billing_customer_id" not in columns:
                target = sqlite3.connect(BACKUP_DIR / ("before-billing-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f") + ".db"))
                try: probe.backup(target)
                finally: target.close()
            photo_columns = {r[1] for r in probe.execute("PRAGMA table_info(photos)")}
            required_photo_columns = {"content_sha256", "byte_size", "mime_type", "storage_name", "role"}
            photo_schema_upgrade = bool(photo_columns and not required_photo_columns.issubset(photo_columns))
            if photo_schema_upgrade:
                target = sqlite3.connect(BACKUP_DIR / ("before-photo-attachments-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f") + ".db"))
                try: probe.backup(target)
                finally: target.close()
            elif photo_columns and required_photo_columns.issubset(photo_columns):
                # Some installations may already have the additive columns from an
                # earlier build while still pointing at legacy, non-addressed names.
                # Back up that exact database once before changing only the pointer;
                # the original legacy file itself is never renamed or removed.
                legacy_reference = any(
                    not photo_storage.is_canonical_storage_name(row[0] or row[1])
                    for row in probe.execute("SELECT storage_name,filename FROM photos")
                )
                if legacy_reference and not any(BACKUP_DIR.glob("before-photo-canonical-*.db")):
                    target = sqlite3.connect(BACKUP_DIR / ("before-photo-canonical-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f") + ".db"))
                    try: probe.backup(target)
                    finally: target.close()
        finally: probe.close()
    with connect() as database:
        database.executescript(
            """
            CREATE TABLE IF NOT EXISTS customers (
              id TEXT PRIMARY KEY, name TEXT NOT NULL, first_name TEXT DEFAULT '',
              last_name TEXT DEFAULT '', company TEXT DEFAULT '', street TEXT DEFAULT '',
              postal_code TEXT DEFAULT '', city TEXT DEFAULT '', country TEXT DEFAULT '',
              email TEXT DEFAULT '', phone TEXT DEFAULT '', notes TEXT DEFAULT '', custom_fields TEXT DEFAULT '{}',
              garden_area REAL, sevdesk_contact_id TEXT UNIQUE,
              sevdesk_customer_number TEXT, active INTEGER NOT NULL DEFAULT 1,
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS orders (
              id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id),
              title TEXT NOT NULL, description TEXT DEFAULT '', location TEXT DEFAULT '',
              scheduled_date TEXT, status TEXT NOT NULL DEFAULT 'Geplant',
              customer_visible INTEGER NOT NULL DEFAULT 1,
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS worklogs (
              id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id),
              order_id TEXT REFERENCES orders(id), work_date TEXT NOT NULL,
              start_time TEXT, end_time TEXT, break_minutes INTEGER NOT NULL DEFAULT 0,
              hours REAL NOT NULL DEFAULT 0, activity TEXT NOT NULL, location TEXT DEFAULT '',
              kilometers REAL NOT NULL DEFAULT 0, material TEXT DEFAULT '', notes TEXT DEFAULT '',
              billed INTEGER NOT NULL DEFAULT 0, customer_visible INTEGER NOT NULL DEFAULT 1,
              source TEXT NOT NULL DEFAULT 'Manuell', created_at TEXT NOT NULL, updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS invoices (
              id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id),
              order_id TEXT REFERENCES orders(id), invoice_number TEXT NOT NULL,
              invoice_date TEXT, service_start TEXT, service_end TEXT,
              amount REAL NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'Entwurf',
              notes TEXT DEFAULT '', sevdesk_id TEXT UNIQUE,
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS photos (
              id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id),
              order_id TEXT REFERENCES orders(id), worklog_id TEXT REFERENCES worklogs(id),
              filename TEXT NOT NULL, original_name TEXT NOT NULL, caption TEXT DEFAULT '',
              customer_visible INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL,
              content_sha256 TEXT, byte_size INTEGER, mime_type TEXT, storage_name TEXT,
              role TEXT NOT NULL DEFAULT 'documentation'
            );
            CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS invoice_details (
              invoice_id TEXT PRIMARY KEY REFERENCES invoices(id) ON DELETE CASCADE,
              payload TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS issued_invoice_files (
              invoice_id TEXT PRIMARY KEY REFERENCES invoices(id),
              pdf BLOB NOT NULL, sha256 TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS ix_orders_customer ON orders(customer_id);
            CREATE INDEX IF NOT EXISTS ix_worklogs_customer ON worklogs(customer_id);
            CREATE INDEX IF NOT EXISTS ix_worklogs_date ON worklogs(work_date);
            CREATE INDEX IF NOT EXISTS ix_invoices_customer ON invoices(customer_id);
            CREATE INDEX IF NOT EXISTS ix_photos_customer ON photos(customer_id);
            """
        )
        customer_columns = {row[1] for row in database.execute("PRAGMA table_info(customers)")}
        if "custom_fields" not in customer_columns:
            database.execute("ALTER TABLE customers ADD COLUMN custom_fields TEXT DEFAULT '{}'")
        # Additive only: legacy worklogs retain NULL (= their actual customer).
        additions = {"customers": {"billing_mode": "TEXT NOT NULL DEFAULT 'direct'",
                    "default_billing_customer_id": "TEXT REFERENCES customers(id)"},
                    "orders": {"billing_customer_id": "TEXT REFERENCES customers(id)"},
                    "worklogs": {"billing_customer_id": "TEXT REFERENCES customers(id)"},
                    "photos": {"content_sha256": "TEXT", "byte_size": "INTEGER", "mime_type": "TEXT",
                               "storage_name": "TEXT", "role": "TEXT NOT NULL DEFAULT 'documentation'"}}
        for table, fields in additions.items():
            present = {r[1] for r in database.execute(f"PRAGMA table_info({table})")}
            for name, definition in fields.items():
                if name not in present:
                    database.execute(f"ALTER TABLE {table} ADD COLUMN {name} {definition}")
        database.execute("CREATE INDEX IF NOT EXISTS ix_worklogs_billing ON worklogs(billing_customer_id)")
        database.execute("CREATE INDEX IF NOT EXISTS ix_photos_content_sha256 ON photos(content_sha256)")
        # Preserve legacy names, IDs and bytes. A valid legacy original is copied
        # atomically to its content-addressed name; only storage_name and additive
        # metadata point at the verified copy. Invalid/tampered history remains
        # untouched so integrity_report() keeps the problem visible.
        for row in database.execute("SELECT id,filename,caption,content_sha256,byte_size,mime_type,storage_name,role FROM photos").fetchall():
            role = str(row["role"] or "").strip().lower()
            if str(row["caption"] or "").strip().casefold() in {"profilbild", "profile", "profilfoto"} and role in {"", "documentation"}:
                role = "profile"
            role = photo_storage.normalized_role(role, row["caption"])
            database.execute("UPDATE photos SET role=? WHERE id=?", (role, row["id"]))

            configured = str(row["storage_name"] or row["filename"] or "")
            if not configured or Path(configured).name != configured:
                continue
            source = PHOTO_DIR / configured
            if not source.is_file() and configured != str(row["filename"] or ""):
                legacy_name = str(row["filename"] or "")
                if legacy_name and Path(legacy_name).name == legacy_name:
                    legacy_source = PHOTO_DIR / legacy_name
                    if legacy_source.is_file():
                        source = legacy_source
            if not source.is_file():
                continue
            try:
                raw = source.read_bytes()
                metadata = photo_storage.inspect(raw)
            except (OSError, ValueError):
                continue

            # A canonical name is itself an integrity assertion. Never silently
            # bless different bytes or replace explicit, conflicting metadata.
            if photo_storage.is_canonical_storage_name(configured):
                if metadata["storage_name"] != configured:
                    continue
                database.execute(
                    """UPDATE photos SET content_sha256=COALESCE(content_sha256,?),
                       byte_size=COALESCE(byte_size,?),mime_type=COALESCE(mime_type,?) WHERE id=?""",
                    (metadata["content_sha256"], metadata["byte_size"], metadata["mime_type"], row["id"]),
                )
                continue

            if ((row["content_sha256"] and str(row["content_sha256"]).lower() != metadata["content_sha256"])
                    or (row["byte_size"] is not None and int(row["byte_size"]) != metadata["byte_size"])
                    or (row["mime_type"] and row["mime_type"] != metadata["mime_type"])):
                continue
            try:
                photo_storage.store_atomic(PHOTO_DIR, raw, metadata)
            except (OSError, ValueError):
                continue
            database.execute(
                """UPDATE photos SET content_sha256=?,byte_size=?,mime_type=?,storage_name=? WHERE id=?""",
                (metadata["content_sha256"], metadata["byte_size"], metadata["mime_type"],
                 metadata["storage_name"], row["id"]),
            )


def dicts(cursor) -> list[dict]:
    return [dict(item) for item in cursor.fetchall()]


def one(cursor) -> dict | None:
    item = cursor.fetchone()
    return dict(item) if item else None


def truth(value, default=False) -> int:
    if value is None:
        return int(default)
    return int(value in (True, 1, "1", "true", "True", "ja", "Ja"))


def number(value, default=0.0):
    if value in (None, ""):
        return default
    result = float(str(value).replace(",", "."))
    if not math.isfinite(result):
        raise ValueError("Bitte eine endliche Zahl eingeben.")
    return result


def existing_payload(table, data):
    """Preserve omitted fields in updates; explicit empty fields still clear values."""
    if not data.get('id'):
        return dict(data)
    with connect() as database:
        row = one(database.execute(f'SELECT * FROM {table} WHERE id=?', (data['id'],)))
    return {**(row or {}), **data}


def valid_date(value, label):
    try:
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', str(value)):
            raise ValueError()
        date.fromisoformat(str(value))
    except (TypeError, ValueError):
        raise ValueError(f"Bitte ein gültiges Datum für {label} auswählen.")


def time_minutes(value):
    if not re.fullmatch(r'(?:[01]\d|2[0-3]):[0-5]\d', str(value)):
        raise ValueError("Bitte gültige Uhrzeiten im Format HH:MM eingeben.")
    hours, minutes = map(int, value.split(':'))
    return hours * 60 + minutes


def hours_from_times(start: str | None, end: str | None, pause: int) -> float:
    if not start or not end:
        return 0.0
    try:
        beginning = datetime.strptime(start, "%H:%M")
        finish = datetime.strptime(end, "%H:%M")
    except ValueError:
        return 0.0
    minutes = (finish - beginning).total_seconds() / 60
    if minutes < 0:
        minutes += 1440
    return round(max(0, minutes - pause) / 60, 2)


def dashboard() -> dict:
    with connect() as database:
        result = {
            "customers": database.execute("SELECT COUNT(*) FROM customers WHERE active=1").fetchone()[0],
            "open_orders": database.execute(
                "SELECT COUNT(*) FROM orders WHERE status NOT IN ('Erledigt','Abgerechnet')"
            ).fetchone()[0],
            "unbilled_hours": round(database.execute(
                "SELECT COALESCE(SUM(hours),0) FROM worklogs WHERE billed=0"
            ).fetchone()[0], 2),
            "open_invoices": round(database.execute(
                "SELECT COALESCE(SUM(amount),0) FROM invoices WHERE status IN ('Gestellt','Offen','Fällig')"
            ).fetchone()[0], 2),
            "today_logs": database.execute(
                "SELECT COUNT(*) FROM worklogs WHERE work_date=?", (date.today().isoformat(),)
            ).fetchone()[0],
        }
        result["recent"] = dicts(database.execute(
            """SELECT w.*,c.name customer_name,o.title order_title FROM worklogs w
               JOIN customers c ON c.id=w.customer_id LEFT JOIN orders o ON o.id=w.order_id
               ORDER BY w.work_date DESC,w.created_at DESC LIMIT 8"""
        ))
    return result


def list_customers(search="") -> list[dict]:
    with connect() as database:
        if search:
            match = f"%{search}%"
            return dicts(database.execute(
                """SELECT * FROM customers WHERE name LIKE ? OR company LIKE ? OR city LIKE ?
                   OR email LIKE ? OR street LIKE ? OR postal_code LIKE ? OR phone LIKE ?
                   ORDER BY active DESC,name COLLATE NOCASE""",
                (match,) * 7,
            ))
        return dicts(database.execute(
            "SELECT * FROM customers ORDER BY active DESC,name COLLATE NOCASE"
        ))


def list_orders(customer_id=None) -> list[dict]:
    where, values = ("WHERE o.customer_id=?", (customer_id,)) if customer_id else ("", ())
    with connect() as database:
        return dicts(database.execute(
            f"""SELECT o.*,c.name customer_name,
              (SELECT COALESCE(SUM(w.hours),0) FROM worklogs w WHERE w.order_id=o.id) total_hours
              FROM orders o JOIN customers c ON c.id=o.customer_id {where}
              ORDER BY CASE o.status WHEN 'Aktiv' THEN 0 WHEN 'Geplant' THEN 1 ELSE 2 END,
              o.scheduled_date,o.updated_at DESC""", values
        ))


def list_worklogs(filters: dict | None = None) -> list[dict]:
    filters = filters or {}
    clauses, values = [], []
    for key in ("customer_id", "order_id", "work_date"):
        if filters.get(key):
            clauses.append(f"w.{key}=?")
            values.append(filters[key])
    if filters.get("unbilled"):
        clauses.append("w.billed=0")
    where = "WHERE " + " AND ".join(clauses) if clauses else ""
    with connect() as database:
        return dicts(database.execute(
            f"""SELECT w.*,c.name customer_name,o.title order_title FROM worklogs w
              JOIN customers c ON c.id=w.customer_id LEFT JOIN orders o ON o.id=w.order_id
              {where} ORDER BY w.work_date DESC,w.start_time DESC,w.created_at DESC""",
            tuple(values),
        ))


def list_invoices(customer_id=None) -> list[dict]:
    where, values = ("WHERE i.customer_id=?", (customer_id,)) if customer_id else ("", ())
    with connect() as database:
        rows = dicts(database.execute(
            f"""SELECT i.*,c.name customer_name,o.title order_title FROM invoices i
              JOIN customers c ON c.id=i.customer_id LEFT JOIN orders o ON o.id=i.order_id
              {where} ORDER BY i.invoice_date DESC,i.invoice_number DESC""", values
        ))
        details = {r['invoice_id']: json.loads(r['payload']) for r in database.execute('SELECT * FROM invoice_details')}
        for row in rows:
            row.update(details.get(row['id'], {}))
        return rows


def list_photos(filters: dict | None = None) -> list[dict]:
    filters = filters or {}
    clauses, values = [], []
    for key in ("customer_id", "order_id", "worklog_id"):
        if filters.get(key):
            clauses.append(f"p.{key}=?")
            values.append(filters[key])
    where = "WHERE " + " AND ".join(clauses) if clauses else ""
    with connect() as database:
        output = dicts(database.execute(
            f"""SELECT p.*,c.name customer_name,o.title order_title FROM photos p
              JOIN customers c ON c.id=p.customer_id LEFT JOIN orders o ON o.id=p.order_id
              {where} ORDER BY p.created_at DESC""", tuple(values)
        ))
    for item in output:
        item["url"] = "/photos/" + item["filename"]
    return output


def mobile_photo_thumbnails(photos: list[dict], size: int = 144) -> list[dict]:
    """Add small embedded previews for mobile snapshots without changing photo records."""
    try:
        from PIL import Image, ImageOps
    except ImportError:
        return photos
    for item in photos:
        source = PHOTO_DIR / Path(str(item.get("storage_name") or item.get("filename", ""))).name
        try:
            with Image.open(source) as image:
                image = ImageOps.exif_transpose(image).convert("RGB")
                image.thumbnail((size, size))
                output = io.BytesIO()
                image.save(output, format="JPEG", quality=48, optimize=True)
            item["thumbnail_data"] = "data:image/jpeg;base64," + base64.b64encode(output.getvalue()).decode("ascii")
        except (OSError, ValueError):
            continue
    return photos


def save_customer(data: dict) -> dict:
    data = existing_payload('customers', data)
    identifier, stamp = data.get("id") or uid("cus"), sync_stamp(data)
    name = str(data.get("name", "")).strip()
    if not name: raise ValueError("Der Kundenname fehlt.")
    if (not data.get("id") or data.get("_new_record")) and not data.get("allow_duplicate"):
        matches = customer_duplicate_matches(data)
        if matches: raise DuplicateWarning("Ein ähnlicher Kunde existiert bereits.", matches)
    columns = {"name":name,"first_name":data.get("first_name",""),"last_name":data.get("last_name",""),"company":data.get("company",""),"street":data.get("street",""),"postal_code":data.get("postal_code",""),"city":data.get("city",""),"country":data.get("country",""),"email":data.get("email",""),"phone":data.get("phone",""),"notes":data.get("notes",""),"custom_fields":json.dumps(data.get("custom_fields",{}),ensure_ascii=False) if isinstance(data.get("custom_fields",{}),dict) else str(data.get("custom_fields") or "{}"),"garden_area":number(data.get("garden_area"),None),"sevdesk_contact_id":data.get("sevdesk_contact_id") or None,"sevdesk_customer_number":data.get("sevdesk_customer_number") or None,"active":truth(data.get("active"),True),"updated_at":stamp}
    with connect() as database:
        mode, target = billing.customer_config({**data, "id":identifier},
            {r["id"]:dict(r) for r in database.execute("SELECT * FROM customers")})
    columns.update(billing_mode=mode, default_billing_customer_id=target)
    return _upsert("customers", identifier, columns, stamp, expected=_entity_mcp_expected(data))

def save_order(data: dict) -> dict:
    data = existing_payload('orders', data)
    identifier, stamp = data.get("id") or uid("ord"), sync_stamp(data)
    customer_id, title = str(data.get("customer_id") or "").strip(), str(data.get("title","")).strip()
    if not customer_id or not title: raise ValueError("Kunde und Auftragsbezeichnung sind erforderlich.")
    with connect() as database:
        if not database.execute("SELECT 1 FROM customers WHERE id=?", (customer_id,)).fetchone(): raise ValueError("Der ausgewählte Kunde existiert nicht.")
        previous = database.execute('SELECT customer_id FROM orders WHERE id=?', (identifier,)).fetchone()
        if previous and previous['customer_id'] != customer_id:
            for table in ('worklogs', 'invoices', 'photos'):
                if database.execute(f'SELECT 1 FROM {table} WHERE order_id=? LIMIT 1', (identifier,)).fetchone():
                    raise ValueError('Der Kunde kann nicht geändert werden: Dieser Auftrag besitzt bereits zugeordnete Arbeiten, Rechnungen oder Fotos.')
    if data.get('scheduled_date'):
        valid_date(data['scheduled_date'], 'den Auftrag')
    if (not data.get("id") or data.get("_new_record")) and not data.get("allow_duplicate"):
        matches = order_duplicate_matches(data)
        if matches: raise DuplicateWarning("Ein ähnlicher Auftrag existiert bereits.", matches)
    columns = {"customer_id":customer_id,"title":title,"description":data.get("description",""),"location":data.get("location",""),"scheduled_date":data.get("scheduled_date") or None,"status":data.get("status") or "Geplant","customer_visible":truth(data.get("customer_visible"),True),"updated_at":stamp}
    columns["billing_customer_id"] = data.get("billing_customer_id") or None
    return _upsert("orders", identifier, columns, stamp, expected=_entity_mcp_expected(data))

def save_worklog(data: dict) -> dict:
    data = existing_payload('worklogs', data)
    identifier, stamp = data.get("id") or uid("wrk"), sync_stamp(data)
    customer_id, activity = str(data.get("customer_id") or "").strip(), str(data.get("activity","")).strip()
    if not customer_id or not activity: raise ValueError("Kunde und Tätigkeit sind erforderlich.")
    is_new, require_order = not data.get("id"), truth(data.get("_require_order") or data.get("require_order"))
    with connect() as database:
        previous = one(database.execute('SELECT * FROM worklogs WHERE id=?', (identifier,)))
        is_new = previous is None
        if not database.execute("SELECT 1 FROM customers WHERE id=?", (customer_id,)).fetchone(): raise ValueError("Der ausgewählte Kunde existiert nicht.")
        order_id = data.get("order_id") or None
        if require_order and is_new and not order_id: raise ValueError("Bitte zuerst einen Auftrag auswählen oder anlegen.")
        order = one(database.execute("SELECT * FROM orders WHERE id=?", (order_id,))) if order_id else None
        if order_id and not order: raise ValueError("Der ausgewählte Auftrag existiert nicht.")
        if order and order["customer_id"] != customer_id: raise ValueError("Kunde und Auftrag passen nicht zusammen.")
        if order and not data.get("location"): data["location"] = order["location"]
        customers = {r["id"]:dict(r) for r in database.execute("SELECT * FROM customers")}
        recipient = data.get("billing_customer_id") or (
            billing.recipient(previous) if previous else
            customer_id if data.get("_sync_updated_at") else
            billing.default_recipient(customer_id, order, customers))
        if recipient not in customers: raise ValueError("Der Rechnungsempfänger existiert nicht.")
        if previous:
            for photo in database.execute('SELECT customer_id,order_id FROM photos WHERE worklog_id=?', (identifier,)):
                if photo['customer_id'] != customer_id or (photo['order_id'] and photo['order_id'] != order_id):
                    raise ValueError('Bitte zuerst die Fotozuordnung prüfen. Die Änderung würde Kunde oder Auftrag des Fotos widersprechen.')
            for detail in database.execute('SELECT payload FROM invoice_details'):
                invoice = json.loads(detail['payload'])
                if identifier not in invoice.get('worklog_ids', []):
                    continue
                if previous['customer_id'] != customer_id or previous['order_id'] != order_id or billing.recipient(previous) != recipient:
                    raise ValueError('Diese Arbeit ist mit einer Rechnung verknüpft. Kunde und Auftrag bleiben erhalten.')
                if invoice.get('issued_at'):
                    locked = ('work_date','start_time','end_time','break_minutes','hours','activity','material','kilometers')
                    numeric = {'break_minutes','hours','kilometers'}
                    def comparable(key, value):
                        return number(value, 0) if key in numeric else str(value if value is not None else '')
                    if any(comparable(key,data.get(key)) != comparable(key,previous[key]) for key in locked) or not truth(data.get('billed')):
                        raise ValueError('Abgerechnete Leistungsdaten bleiben unverändert. Interne Notizen können weiterhin ergänzt werden.')
    try:
        raw_pause = number(data.get('break_minutes'), WORKLOG_DEFAULTS['break_minutes'])
        if raw_pause != int(raw_pause): raise ValueError('Pause in ganzen Minuten eingeben.')
        pause, kilometers, work_hours = int(raw_pause), number(data.get("kilometers"),WORKLOG_DEFAULTS['kilometers']), number(data.get("hours"),0)
    except (TypeError,ValueError): raise ValueError("Bitte gültige Zahlen für Pause, Stunden und Kilometer eingeben.")
    if pause < 0 or kilometers < 0 or work_hours < 0 or not all(math.isfinite(v) for v in (pause,kilometers,work_hours)): raise ValueError("Pause, Arbeitsstunden und Kilometer dürfen nicht negativ sein.")
    work_date = data.get("work_date") or date.today().isoformat()
    valid_date(work_date, 'die Arbeit')
    start, end = data.get("start_time"), data.get("end_time")
    if bool(start) != bool(end): raise ValueError('Bitte Beginn und Ende gemeinsam angeben oder nur die Arbeitsdauer.')
    if start and end:
        minutes = time_minutes(end) - time_minutes(start)
        if minutes < 0 and truth(data.get('allow_overnight')): minutes += 1440
        if minutes <= 0: raise ValueError('Die Endzeit muss nach dem Beginn liegen. Tageswechsel ausdrücklich bestätigen.')
        if pause >= minutes: raise ValueError('Die Pause muss kürzer als der gesamte Einsatz sein.')
        work_hours = round((minutes - pause) / 60, 2)
    elif is_new and work_hours <= 0: raise ValueError("Bitte eine positive Arbeitsdauer oder Beginn und Ende eintragen.")
    columns = {"customer_id":customer_id,"order_id":data.get("order_id") or None,"work_date":work_date,"start_time":start or None,"end_time":end or None,"break_minutes":pause,"hours":work_hours,"activity":activity,"location":data.get("location",""),"kilometers":kilometers,"material":data.get("material",""),"notes":data.get("notes",""),"billed":truth(data.get("billed"),0),"customer_visible":truth(data.get("customer_visible"),1),"source":data.get("source") or "Manuell","updated_at":stamp}
    # Optional Pixel-MCP compare-and-save guard. Legacy/UI requests stay unchanged.
    columns["billing_customer_id"] = recipient
    expected = data.get('_mcp_base')
    if data.get('_mcp_create') is True:
        expected = {}
    if expected is not None:
        allowed = {'id', 'customer_id', 'billing_customer_id', 'order_id', 'work_date', 'activity', 'hours',
                   'start_time', 'end_time', 'break_minutes', 'material', 'kilometers',
                   'notes', 'location', 'customer_visible', 'billed', 'updated_at'}
        if not isinstance(expected, dict) or set(expected) - allowed:
            raise ValueError('Ungültige Vergleichsdaten der ChatGPT-Änderung.')
    return _upsert("worklogs", identifier, columns, stamp, expected=expected)


def save_invoice(data: dict) -> dict:
    data = existing_payload('invoices', data)
    identifier, stamp = data.get("id") or uid("inv"), now()
    with connect() as database:
        if database.execute('SELECT 1 FROM issued_invoice_files WHERE invoice_id=?', (identifier,)).fetchone():
            raise ValueError('Abgeschlossene Rechnungen können nicht überschrieben werden. Korrekturen benötigen einen eigenen Beleg.')
        previous_details = database.execute('SELECT payload FROM invoice_details WHERE invoice_id=?',(identifier,)).fetchone()
        if previous_details:
            # Partial edits must retain and revalidate the existing performance links.
            data = {**json.loads(previous_details['payload']), **data}
    if not data.get("customer_id") or not str(data.get("invoice_number", "")).strip():
        raise ValueError("Kunde und Rechnungsnummer sind erforderlich.")
    if data.get("order_id"):
        with connect() as database:
            order = database.execute("SELECT customer_id FROM orders WHERE id=?", (data["order_id"],)).fetchone()
        if not order: raise ValueError("Der ausgewählte Auftrag existiert nicht.")
        if order["customer_id"] != data["customer_id"]: raise ValueError("Kunde und Auftrag der Rechnung passen nicht zusammen.")
    columns = {
        "customer_id": data["customer_id"], "order_id": data.get("order_id") or None,
        "invoice_number": str(data["invoice_number"]).strip(), "invoice_date": data.get("invoice_date") or None,
        "service_start": data.get("service_start") or None, "service_end": data.get("service_end") or None,
        "amount": number(data.get("amount"), 0), "status": data.get("status") or "Entwurf",
        "notes": data.get("notes", ""), "sevdesk_id": data.get("sevdesk_id") or None,
        "updated_at": stamp,
    }
    details = None
    if 'positions' in data:
        details = calculate_invoice(data)
        if columns['status'] != 'Entwurf' or columns['sevdesk_id']:
            raise ValueError('Positionen können hier nur für lokale Entwürfe bearbeitet werden.')
        with connect() as database:
            existing = database.execute('SELECT status,sevdesk_id FROM invoices WHERE id=?', (identifier,)).fetchone()
            if existing and (existing['status'] != 'Entwurf' or existing['sevdesk_id']):
                raise ValueError('Bereits ausgestellte oder sevDesk-Rechnungen bleiben unverändert.')
            for worklog_id in details['worklog_ids']:
                worklog = one(database.execute('SELECT customer_id,billing_customer_id,billed FROM worklogs WHERE id=?', (worklog_id,)))
                if not worklog or billing.recipient(worklog) != columns['customer_id'] or worklog['billed']:
                    raise ValueError('Ausgewählte Arbeitsaufzeichnungen müssen offen sein und zum Kunden gehören.')
        columns['amount'] = details['gross_total']
    with connect() as database:
        database.execute('BEGIN IMMEDIATE')
        if database.execute('SELECT 1 FROM issued_invoice_files WHERE invoice_id=?', (identifier,)).fetchone():
            raise ValueError('Die Rechnung wurde inzwischen abgeschlossen und bleibt unverändert.')
        if '_mcp_invoice_positions' in data:
            current_details = database.execute('SELECT payload FROM invoice_details WHERE invoice_id=?',(identifier,)).fetchone()
            current_positions = json.loads(current_details['payload']).get('positions') if current_details else None
            if current_positions != data['_mcp_invoice_positions']:
                raise ValueError('Die Rechnungspositionen wurden inzwischen geändert. Bitte erneut öffnen.')
            if '_mcp_invoice_payment_days' in data and data['_mcp_invoice_payment_days'] != (json.loads(current_details['payload']).get('payment_days',14) if current_details else 14):
                raise ValueError('Das Zahlungsziel wurde inzwischen geändert. Bitte erneut öffnen.')
        saved = _upsert("invoices", identifier, columns, stamp, database, expected=_entity_mcp_expected(data))
        if details is not None:
            database.execute('INSERT OR REPLACE INTO invoice_details(invoice_id,payload) VALUES(?,?)',
                             (identifier, json.dumps(details, ensure_ascii=False)))
            saved.update(details)
    return saved


def calculate_invoice(data: dict) -> dict:
    """Calculate drafts with decimal, per-line currency rounding; never mark work billed."""
    def decimal(value):
        try:
            result = Decimal(str(value or 0).replace(',', '.'))
        except Exception:
            raise ValueError('Bitte gültige Zahlen für Menge, Preis, Rabatt und Steuer eingeben.')
        if not result.is_finite():
            raise ValueError('Ungültiger Zahlenwert.')
        return result
    def rounded(value):
        return value.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    positions, net, tax = [], Decimal(0), Decimal(0)
    if not isinstance(data['positions'], list) or not 1 <= len(data['positions']) <= 200:
        raise ValueError('Bitte mindestens eine Rechnungsposition hinzufügen (höchstens 200).')
    for item in data['positions']:
        if not isinstance(item, dict):
            raise ValueError('Jede Rechnungsposition muss gültige Felder für Bezeichnung, Menge und Preis enthalten.')
        title = str(item.get('title', '')).strip()
        quantity, price = decimal(item.get('quantity')), decimal(item.get('price'))
        rate, discount = decimal(item.get('tax_rate')), decimal(item.get('discount'))
        if rate != 0:
            raise ValueError('Kleinunternehmerrechnung: Umsatzsteuer darf nicht ausgewiesen werden. Bitte Steuer auf 0 setzen.')
        if not title or quantity <= 0 or price < 0 or not 0 <= rate <= 100 or not 0 <= discount <= 100:
            raise ValueError('Jede Position benötigt eine Bezeichnung, positive Menge und gültige Preise/Prozente.')
        subtotal = rounded(quantity * price * (1 - discount / 100))
        line_tax = rounded(subtotal * rate / 100)
        positions.append({'title': title, 'quantity': float(quantity), 'price': float(price),
                          'description': str(item.get('description','')).strip(),
                          'tax_rate': float(rate), 'discount': float(discount),
                          'unit': str(item.get('unit', 'Stk.')), 'net': float(subtotal), 'tax': float(line_tax),
                          'worklog_id': str(item.get('worklog_id',''))})
        net += subtotal
        tax += line_tax
    days = int(data.get('payment_days', 14))
    if not 0 <= days <= 365:
        raise ValueError('Zahlungsziel muss zwischen 0 und 365 Tagen liegen.')
    return {'positions': positions, 'net_total': float(net), 'tax_total': float(tax),
            'gross_total': float(net + tax), 'payment_days': days,
            'header_text': str(data.get('header_text', '')), 'footer_text': str(data.get('footer_text', '')),
            'worklog_ids': list(dict.fromkeys([p['worklog_id'] for p in positions if p['worklog_id']])),
            'tax_exemption': 'Umsatzsteuerfrei aufgrund der Kleinunternehmerregelung gemäß § 6 Abs. 1 Z 27 UStG.',
            'domestic_service_confirmed': bool(truth(data.get('domestic_service_confirmed')))}


def remove_entity(entity: str, identifier: str) -> dict:
    """Safely remove an item; keep customer history by archiving referenced customers."""
    tables = {"customer": "customers", "order": "orders", "worklog": "worklogs", "invoice": "invoices", "photo": "photos"}
    if entity not in tables:
        raise ValueError("Dieser Eintragstyp kann nicht entfernt werden.")
    table = tables[entity]
    with connect() as database:
        row = database.execute(f"SELECT * FROM {table} WHERE id=?", (identifier,)).fetchone()
        if not row:
            raise ValueError("Der Eintrag wurde nicht gefunden.")
        if entity == "customer":
            linked = sum(database.execute(f"SELECT COUNT(*) FROM {child} WHERE customer_id=?", (identifier,)).fetchone()[0]
                         for child in ("orders", "worklogs", "invoices", "photos"))
            linked += database.execute("SELECT COUNT(*) FROM customers WHERE default_billing_customer_id=?", (identifier,)).fetchone()[0]
            linked += sum(database.execute(f"SELECT COUNT(*) FROM {child} WHERE billing_customer_id=?", (identifier,)).fetchone()[0] for child in ("orders","worklogs"))
            if linked:
                database.execute("UPDATE customers SET active=0,updated_at=? WHERE id=?", (now(), identifier))
                return {"id": identifier, "archived": True, "message": "Kunde archiviert; verknüpfte Historie bleibt erhalten."}
        elif entity == "order":
            linked = sum(database.execute(f"SELECT COUNT(*) FROM {child} WHERE order_id=?", (identifier,)).fetchone()[0]
                         for child in ("worklogs", "invoices", "photos"))
            if linked:
                database.execute("UPDATE orders SET status='Storniert',updated_at=? WHERE id=?", (now(), identifier))
                return {"id": identifier, "archived": True, "message": "Auftrag storniert; verknüpfte Arbeitsaufzeichnungen und Rechnungen bleiben erhalten."}
        elif entity == "invoice" and row["sevdesk_id"]:
            raise ValueError("Diese Rechnung ist mit sevDesk verknüpft. Entferne sie bitte in sevDesk.")
        elif entity == "invoice" and row["status"] not in ("Entwurf", "Storniert"):
            raise ValueError("Nur Entwürfe oder stornierte Rechnungen können hier gelöscht werden.")
        elif entity == "worklog":
            for invoice_detail in database.execute('SELECT payload FROM invoice_details'):
                archived = json.loads(invoice_detail['payload'])
                if archived.get('issued_at') and identifier in archived.get('worklog_ids', []):
                    raise ValueError('Diese Arbeit ist mit einer abgeschlossenen Rechnung verknüpft und darf nicht gelöscht werden.')
                if identifier in archived.get('worklog_ids', []):
                    raise ValueError('Diese Arbeit ist mit einem Rechnungsentwurf verknüpft. Bitte zuerst aus dem Entwurf entfernen, bevor du sie löschst.')
            database.execute("UPDATE photos SET worklog_id=NULL WHERE worklog_id=?", (identifier,))
        database.execute(f"DELETE FROM {table} WHERE id=?", (identifier,))
    if entity == "photo":
        storage_name = row["storage_name"] if "storage_name" in row.keys() else None
        candidates = {str(row["filename"] or ""), str(storage_name or "")}
        with connect() as database:
            for name in candidates:
                if not name or Path(name).name != name:
                    continue
                shared = database.execute(
                    'SELECT 1 FROM photos WHERE filename=? OR storage_name=? LIMIT 1', (name, name)
                ).fetchone()
                if not shared:
                    (PHOTO_DIR / name).unlink(missing_ok=True)
    return {"id": identifier, "archived": False, "message": "Eintrag entfernt."}


def _entity_mcp_expected(data):
    if data.get('_mcp_create') is True:
        return {}
    expected = data.get('_mcp_base')
    if expected is not None:
        allowed = {'id', 'updated_at', 'name', 'title', 'customer_id', 'billing_customer_id', 'billing_mode', 'default_billing_customer_id', 'status', 'invoice_number',
                   'company', 'first_name', 'last_name', 'street', 'postal_code', 'city',
                   'country', 'phone', 'email', 'notes', 'active', 'garden_area',
                   'sevdesk_customer_number', 'custom_fields', 'description', 'location', 'scheduled_date',
                   'customer_visible', 'invoice_date', 'service_start', 'service_end'}
        if not isinstance(expected, dict) or set(expected) - allowed or not expected.get('id') or not expected.get('updated_at'):
            raise ValueError('Ungültige Vergleichsdaten der ChatGPT-Änderung.')
    return expected


def _upsert(table: str, identifier: str, columns: dict, stamp: str, database=None, expected=None) -> dict:
    if database is None:
        with connect() as connection:
            connection.execute('BEGIN IMMEDIATE')
            return _upsert(table, identifier, columns, stamp, connection, expected)
    # Validate recipients inside the same transaction, even for manual writes.
    if table == "customers":
        billing.customer_config({**columns,"id":identifier},
            {r["id"]:dict(r) for r in database.execute("SELECT * FROM customers")})
    if table in ("orders","worklogs") and columns.get("billing_customer_id"):
        if not database.execute("SELECT 1 FROM customers WHERE id=?", (columns["billing_customer_id"],)).fetchone():
            raise ValueError("Der Rechnungsempfänger existiert nicht mehr.")
    if expected is not None:
        current = one(database.execute(f'SELECT * FROM {table} WHERE id=?', (identifier,)))
        if table == 'worklogs':
            if not database.execute('SELECT 1 FROM customers WHERE id=?', (columns['customer_id'],)).fetchone():
                raise ValueError('Der ausgewählte Kunde existiert nicht mehr.')
            if columns.get('order_id'):
                order = database.execute('SELECT customer_id FROM orders WHERE id=?', (columns['order_id'],)).fetchone()
                if not order or order['customer_id'] != columns['customer_id']:
                    raise ValueError('Kunde und Auftrag passen nicht mehr zusammen. Bitte erneut abgleichen.')
            # Related records can change after save_worklog's initial validation.
            # Recheck under the same write lock as the guarded update.
            for photo in database.execute('SELECT customer_id,order_id FROM photos WHERE worklog_id=?', (identifier,)):
                if photo['customer_id'] != columns['customer_id'] or (photo['order_id'] and photo['order_id'] != columns.get('order_id')):
                    raise ValueError('Die Fotozuordnung wurde inzwischen geändert. Bitte Kunde und Auftrag erneut prüfen.')
        if not expected:
            if current:
                raise ValueError('Die neue ChatGPT-Aufzeichnung existiert bereits. Keine Überschreibung vorgenommen.')
        else:
            if current is None or expected.get('id') != identifier:
                raise ValueError('Die Arbeitsaufzeichnung existiert nicht mehr. Keine Wiederanlage vorgenommen.')
            numeric = {'hours', 'break_minutes', 'kilometers', 'customer_visible', 'billed', 'active', 'garden_area'}
            def comparable(key, value):
                if key == 'custom_fields':
                    try:
                        decoded = json.loads(value) if isinstance(value, str) else value
                        if not isinstance(decoded, dict):
                            raise ValueError('Kunden-Zusatzfelder müssen ein Objekt sein.')
                        def typed(item):
                            if item is None: return ('null',)
                            if isinstance(item, bool): return ('bool', item)
                            if isinstance(item, (int, float)):
                                value = Decimal(str(item))
                                if not value.is_finite():
                                    raise ValueError('Ungültige Zahl in Kunden-Zusatzfeldern.')
                                return ('number', format(value.normalize(), 'f'))
                            if isinstance(item, str): return ('string', item)
                            if isinstance(item, dict): return ('object', tuple((key, typed(value)) for key, value in sorted(item.items())))
                            if isinstance(item, list): return ('array', tuple(typed(value) for value in item))
                            raise ValueError('Ungültige Kunden-Zusatzfelder.')
                        return typed(decoded)
                    except (TypeError, json.JSONDecodeError):
                        raise ValueError('Bestehende Kunden-Zusatzfelder bitte prüfen.')
                return number(value, 0) if key in numeric else str(value if value is not None else '')
            if any(comparable(key, value) != comparable(key, current.get(key)) for key, value in expected.items()):
                entity = {'customers': 'Der Kunde', 'orders': 'Der Auftrag', 'invoices': 'Die Rechnung'}.get(table, 'Die Arbeitsaufzeichnung')
                raise ValueError(entity + ' wurde inzwischen geändert. Bitte erneut öffnen und abgleichen.')
    return _upsert_in_connection(table, identifier, columns, stamp, database)


def _upsert_in_connection(table, identifier, columns, stamp, database):
    if database is not None:
        exists = database.execute(f"SELECT 1 FROM {table} WHERE id=?", (identifier,)).fetchone()
        if exists:
            assignments = ",".join(f"{key}=?" for key in columns)
            database.execute(
                f"UPDATE {table} SET {assignments} WHERE id=?", tuple(columns.values()) + (identifier,)
            )
        else:
            insert = {"id": identifier, **columns, "created_at": stamp}
            names = ",".join(insert)
            placeholders = ",".join("?" for _ in insert)
            database.execute(f"INSERT INTO {table}({names}) VALUES({placeholders})", tuple(insert.values()))
        return one(database.execute(f"SELECT * FROM {table} WHERE id=?", (identifier,)))


def customer_view(customer_id: str) -> dict | None:
    with connect() as database:
        customer = one(database.execute(
            "SELECT id,name,company,city FROM customers WHERE id=? AND active=1", (customer_id,)
        ))
        if not customer:
            return None
        orders = dicts(database.execute(
            """SELECT id,title,description,location,scheduled_date,status FROM orders
               WHERE customer_id=? AND customer_visible=1 ORDER BY scheduled_date DESC""", (customer_id,)
        ))
        worklogs = dicts(database.execute(
            """SELECT id,order_id,work_date,hours,activity,location,material FROM worklogs
               WHERE customer_id=? AND customer_visible=1 ORDER BY work_date DESC""", (customer_id,)
        ))
        photos = dicts(database.execute(
            """SELECT id,order_id,worklog_id,filename,caption,created_at,content_sha256,
                      byte_size,mime_type,storage_name,role FROM photos
               WHERE customer_id=? AND customer_visible=1 ORDER BY created_at DESC""", (customer_id,)
        ))
    for photo in photos:
        photo["url"] = "/photos/" + photo["filename"]
    return {"customer": customer, "orders": orders, "worklogs": worklogs, "photos": photos}


def settings() -> dict:
    with connect() as database:
        return {row["key"]: row["value"] for row in database.execute("SELECT * FROM settings")}


def save_settings(values: dict) -> dict:
    allowed = {"backup_folder", "business_name", "owner_name", "drive_sync_enabled", "home_address",
               "business_street", "business_postal_code", "business_city", "business_legal_info", "business_iban",
               "invoice_prefix", "invoice_numbering_confirmed", "invoice_issuer_confirmed",
               "invoice_number_format", "invoice_sevdesk_last_number", "invoice_sevdesk_checked_at", "drive_invoice_only",
               "business_phone", "business_email", "business_bank", "business_bic"}
    with connect() as database:
        for key, value in values.items():
            if key in allowed:
                database.execute("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)", (key, str(value)))
    return settings()
