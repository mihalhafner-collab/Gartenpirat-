"""Attachment tests use only temporary SQLite databases and synthetic images."""
import base64
import copy
import hashlib
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

import db
import photo_storage
import server
from photo_fixture import VALID_GIF, VALID_GIF_BASE64, VALID_GIF_DATA_URI


class PhotoAttachmentTests(unittest.TestCase):
    def setUp(self):
        self.previous = db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR
        self.temp = tempfile.TemporaryDirectory(prefix="hausgarten-photo-attachments-")
        root = Path(self.temp.name)
        db.DATA_DIR, db.DB_PATH = root, root / "test.db"
        db.PHOTO_DIR, db.BACKUP_DIR = root / "photos", root / "backups"
        db.init()
        self.customer = db.save_customer({"name": "Foto-Testkunde"})

    def tearDown(self):
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = self.previous
        self.temp.cleanup()

    def payload(self, **values):
        return {
            "customer_id": self.customer["id"],
            "original_name": "Gartenaufnahme.gif",
            "data": VALID_GIF_DATA_URI,
            **values,
        }

    def test_valid_photo_has_full_content_metadata_and_multiple_rows_share_blob(self):
        first = server.save_photo(self.payload(id="pho_first", role="before"))
        second = server.save_photo(self.payload(id="pho_second", role="after"))
        digest = hashlib.sha256(VALID_GIF).hexdigest()
        self.assertEqual(first["content_sha256"], digest)
        self.assertEqual(first["storage_name"], digest + ".gif")
        self.assertEqual(first["filename"], first["storage_name"])
        self.assertEqual(first["mime_type"], "image/gif")
        self.assertEqual(first["byte_size"], len(VALID_GIF))
        self.assertEqual(first["role"], "before")
        self.assertEqual(second["role"], "after")
        self.assertEqual(len(db.list_photos({"customer_id": self.customer["id"]})), 2)
        self.assertEqual([item.name for item in db.PHOTO_DIR.iterdir()], [digest + ".gif"])
        self.assertTrue(db.integrity_report()["ok"])

    def test_invalid_image_and_failed_relation_leave_no_database_or_file_residue(self):
        invalid = base64.b64encode(b"GIF89a-not-an-image").decode("ascii")
        with self.assertRaisesRegex(ValueError, "beschädigt|gelesen"):
            server.save_photo(self.payload(data="data:image/gif;base64," + invalid))
        self.assertEqual(db.list_photos(), [])
        self.assertEqual(list(db.PHOTO_DIR.iterdir()), [])

        with self.assertRaisesRegex(ValueError, "Kunde"):
            server.save_photo(self.payload(customer_id="missing-customer"))
        self.assertEqual(db.list_photos(), [])
        self.assertEqual(list(db.PHOTO_DIR.iterdir()), [])

    def test_metadata_only_drive_action_requires_and_verifies_original(self):
        metadata = photo_storage.inspect(VALID_GIF, "image/gif")
        attachment_root = db.DATA_DIR / "drive" / "HausGarten-Fotos"
        attachment_root.mkdir(parents=True)
        (attachment_root / metadata["storage_name"]).write_bytes(VALID_GIF)
        action = {
            "client_id": "photo-action",
            "kind": "photo",
            "payload": {
                "id": "pho_from_drive",
                "customer_id": self.customer["id"],
                "original_name": "Baumkontrolle.gif",
                "caption": "Vorher",
                "role": "before",
                **{key: metadata[key] for key in ("content_sha256", "mime_type", "byte_size", "storage_name")},
            },
        }
        unchanged = copy.deepcopy(action)
        saved = server.apply_sync_action(action, attachment_root)
        self.assertEqual(action, unchanged)
        self.assertEqual(saved["id"], "pho_from_drive")
        self.assertEqual(saved["content_sha256"], metadata["content_sha256"])
        self.assertNotIn("data", saved)

        missing = copy.deepcopy(action)
        missing["payload"]["id"] = "pho_missing"
        (attachment_root / metadata["storage_name"]).unlink()
        with self.assertRaisesRegex(ValueError, "fehlt"):
            server.apply_sync_action(missing, attachment_root)
        self.assertNotIn("pho_missing", {row["id"] for row in db.list_photos()})

    def test_tampered_drive_attachment_and_declared_metadata_are_rejected(self):
        metadata = photo_storage.inspect(VALID_GIF)
        attachment_root = db.DATA_DIR / "drive" / "HausGarten-Fotos"
        attachment_root.mkdir(parents=True)
        (attachment_root / metadata["storage_name"]).write_bytes(VALID_GIF + b"tampered")
        payload = {"id": "pho_tampered", "customer_id": self.customer["id"], "original_name": "x.gif", **metadata}
        with self.assertRaisesRegex(ValueError, "Prüfsumme|beschädigt|gelesen"):
            server.apply_sync_action({"client_id": "tampered", "kind": "photo", "payload": payload}, attachment_root)
        self.assertEqual(db.list_photos(), [])

        with self.assertRaisesRegex(ValueError, "Metadaten"):
            server.save_photo(self.payload(content_sha256="0" * 64))
        self.assertEqual(db.list_photos(), [])

    def test_drive_sync_imports_metadata_action_once_and_preserves_attachment(self):
        metadata = photo_storage.inspect(VALID_GIF)
        drive = db.DATA_DIR / "sync-drive"
        attachments = drive / "HausGarten-Fotos"
        attachments.mkdir(parents=True)
        attachment = attachments / metadata["storage_name"]
        attachment.write_bytes(VALID_GIF)
        action = {
            "client_id": "photo-sync-once",
            "kind": "photo",
            "payload": {
                "id": "pho_sync_once", "customer_id": self.customer["id"],
                "original_name": "Sync.gif", "role": "documentation",
                **{key: metadata[key] for key in ("content_sha256", "mime_type", "byte_size", "storage_name")},
            },
        }
        journal = drive / "HausGarten-Mobile-test.json"
        journal.write_text(json.dumps({"actions": [action]}), encoding="utf-8")
        first = server.drive_sync(str(drive))
        self.assertTrue(first["ok"])
        self.assertEqual(first["imported"], 1)
        self.assertEqual(attachment.read_bytes(), VALID_GIF)
        snapshot = json.loads((drive / "HausGarten-Laptop.json").read_text(encoding="utf-8"))
        self.assertIn("photo-sync-once", snapshot["accepted_ids"])
        self.assertNotIn("data", next(row for row in snapshot["photos"] if row["id"] == "pho_sync_once"))
        self.assertEqual(server.drive_sync(str(drive))["imported"], 0)
        self.assertEqual(len(db.list_photos()), 1)

    def test_drive_sync_keeps_action_pending_when_original_is_missing(self):
        metadata = photo_storage.inspect(VALID_GIF)
        drive = db.DATA_DIR / "missing-sync-drive"
        drive.mkdir()
        action = {
            "client_id": "photo-still-pending",
            "kind": "photo",
            "payload": {
                "id": "pho_still_pending", "customer_id": self.customer["id"],
                "original_name": "Missing.gif",
                **{key: metadata[key] for key in ("content_sha256", "mime_type", "byte_size", "storage_name")},
            },
        }
        journal = drive / "HausGarten-Mobile-test.json"
        journal.write_text(json.dumps({"actions": [action]}), encoding="utf-8")
        before = journal.read_bytes()
        result = server.drive_sync(str(drive))
        self.assertFalse(result["ok"])
        self.assertEqual(result["imported"], 0)
        self.assertEqual(journal.read_bytes(), before)
        snapshot = json.loads((drive / "HausGarten-Laptop.json").read_text(encoding="utf-8"))
        self.assertNotIn("photo-still-pending", snapshot["accepted_ids"])
        self.assertTrue(any(row.get("client_id") == "photo-still-pending" for row in snapshot["sync_errors"]))
        self.assertEqual(db.list_photos(), [])

    def test_legacy_inline_action_remains_accepted(self):
        saved = server.apply_sync_action({
            "client_id": "legacy-inline",
            "kind": "photo",
            "payload": {
                "id": "pho_legacy_inline", "customer_id": self.customer["id"],
                "original_name": "Altbestand.gif", "data": VALID_GIF_BASE64,
            },
        })
        self.assertEqual(saved["id"], "pho_legacy_inline")
        self.assertTrue((db.PHOTO_DIR / saved["storage_name"]).is_file())

    def test_legacy_schema_migration_is_additive_backed_up_and_idempotent(self):
        legacy_root = Path(self.temp.name) / "legacy"
        legacy_root.mkdir()
        db.DATA_DIR, db.DB_PATH = legacy_root, legacy_root / "legacy.db"
        db.PHOTO_DIR, db.BACKUP_DIR = legacy_root / "photos", legacy_root / "backups"
        db.PHOTO_DIR.mkdir(); db.BACKUP_DIR.mkdir()
        (db.PHOTO_DIR / "legacy-name.gif").write_bytes(VALID_GIF)
        connection = sqlite3.connect(db.DB_PATH)
        try:
            connection.execute("""CREATE TABLE photos(
                id TEXT PRIMARY KEY,customer_id TEXT NOT NULL,order_id TEXT,worklog_id TEXT,
                filename TEXT NOT NULL,original_name TEXT NOT NULL,caption TEXT DEFAULT '',
                customer_visible INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL)""")
            connection.execute(
                "INSERT INTO photos VALUES(?,?,?,?,?,?,?,?,?)",
                ("pho_legacy", "cus_legacy", None, None, "legacy-name.gif", "Original.gif", "Profilbild", 1, "2026-01-01T00:00:00Z"),
            )
            connection.commit()
        finally:
            connection.close()

        db.init()
        backups = list(db.BACKUP_DIR.glob("before-photo-attachments-*.db"))
        self.assertEqual(len(backups), 1)
        with db.connect() as migrated:
            row = dict(migrated.execute("SELECT * FROM photos WHERE id='pho_legacy'").fetchone())
        canonical_name = hashlib.sha256(VALID_GIF).hexdigest() + ".gif"
        self.assertEqual(row["filename"], "legacy-name.gif")
        self.assertEqual(row["storage_name"], canonical_name)
        self.assertEqual(row["original_name"], "Original.gif")
        self.assertEqual(row["role"], "profile")
        self.assertEqual(row["content_sha256"], hashlib.sha256(VALID_GIF).hexdigest())
        self.assertEqual(row["byte_size"], len(VALID_GIF))
        self.assertEqual(row["mime_type"], "image/gif")
        db.init()
        self.assertEqual(len(list(db.BACKUP_DIR.glob("before-photo-attachments-*.db"))), 1)
        self.assertTrue((db.PHOTO_DIR / "legacy-name.gif").is_file())
        self.assertEqual((db.PHOTO_DIR / "legacy-name.gif").read_bytes(), VALID_GIF)
        self.assertTrue((db.PHOTO_DIR / canonical_name).is_file())
        self.assertEqual((db.PHOTO_DIR / canonical_name).read_bytes(), VALID_GIF)

    def test_invalid_legacy_photo_is_not_rewritten_or_silently_blessed(self):
        legacy_root = Path(self.temp.name) / "invalid-legacy"
        legacy_root.mkdir()
        db.DATA_DIR, db.DB_PATH = legacy_root, legacy_root / "legacy.db"
        db.PHOTO_DIR, db.BACKUP_DIR = legacy_root / "photos", legacy_root / "backups"
        db.PHOTO_DIR.mkdir(); db.BACKUP_DIR.mkdir()
        damaged = b"GIF89a-this-is-not-a-complete-image"
        (db.PHOTO_DIR / "damaged.gif").write_bytes(damaged)
        connection = sqlite3.connect(db.DB_PATH)
        try:
            connection.execute("""CREATE TABLE photos(
                id TEXT PRIMARY KEY,customer_id TEXT NOT NULL,order_id TEXT,worklog_id TEXT,
                filename TEXT NOT NULL,original_name TEXT NOT NULL,caption TEXT DEFAULT '',
                customer_visible INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL)""")
            connection.execute(
                "INSERT INTO photos VALUES(?,?,?,?,?,?,?,?,?)",
                ("pho_damaged", "cus_legacy", None, None, "damaged.gif", "Damaged.gif", "", 0, "2026-01-01T00:00:00Z"),
            )
            connection.commit()
        finally:
            connection.close()

        db.init()
        with db.connect() as migrated:
            row = dict(migrated.execute("SELECT * FROM photos WHERE id='pho_damaged'").fetchone())
        self.assertIsNone(row["storage_name"])
        self.assertIsNone(row["content_sha256"])
        self.assertEqual((db.PHOTO_DIR / "damaged.gif").read_bytes(), damaged)
        self.assertIn("pho_damaged", db.integrity_report()["photos_invalid_content"])
        self.assertEqual([item.name for item in db.PHOTO_DIR.iterdir()], ["damaged.gif"])

    def test_integrity_report_detects_tamper_missing_and_orphan_without_deleting(self):
        photo = server.save_photo(self.payload(id="pho_integrity"))
        original = db.PHOTO_DIR / photo["storage_name"]
        original.write_bytes(VALID_GIF + b"changed")
        orphan = db.PHOTO_DIR / "not-referenced.gif"
        orphan.write_bytes(VALID_GIF)
        report = db.integrity_report()
        self.assertIn(photo["id"], report["photos_hash_mismatch"])
        self.assertIn("not-referenced.gif", report["photo_files_orphaned"])
        self.assertTrue(original.is_file())
        self.assertTrue(orphan.is_file())
        original.unlink()
        self.assertIn(photo["id"], db.integrity_report()["photos_missing_file"])

    def test_drive_export_never_overwrites_conflicting_existing_attachment(self):
        photo = server.save_photo(self.payload(id="pho_export"))
        drive = db.DATA_DIR / "test-drive"
        target_dir = drive / "HausGarten-Fotos"
        target_dir.mkdir(parents=True)
        target = target_dir / photo["storage_name"]
        conflicting = b"external-file-must-not-be-overwritten"
        target.write_bytes(conflicting)
        result = server.drive_sync(str(drive))
        self.assertFalse(result["ok"])
        self.assertEqual(target.read_bytes(), conflicting)
        snapshot = json.loads((drive / "HausGarten-Laptop.json").read_text(encoding="utf-8"))
        self.assertTrue(any(error.get("photo_id") == photo["id"] for error in snapshot["sync_errors"]))


if __name__ == "__main__":
    unittest.main()
