"""Disposable test data only; no phone access."""
from contextlib import closing
import importlib.util
import io
import json
from pathlib import Path
import sqlite3
import tarfile
import tempfile
import unittest

module_path = Path(__file__).with_name("pixel-usb-backup-inspect.py")
spec = importlib.util.spec_from_file_location("pixel_backup_inspect", module_path)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class PixelBackupTests(unittest.TestCase):
    def fixture(self, root, *, invalid_json=False, unsafe=False):
        database = root / "fixture.db"
        with closing(sqlite3.connect(database)) as connection:
            connection.execute("CREATE TABLE pending (id TEXT PRIMARY KEY, kind TEXT, payload TEXT, created_at TEXT)")
            connection.execute("INSERT INTO pending VALUES (?,?,?,?)", ("test-record", "worklog", "{" if invalid_json else '{}', "2026-10-10"))
            connection.commit()
        archive = root / "fixture.tar"
        with tarfile.open(archive, "w") as target:
            prefs = b'<map><string name="drive_snapshot">{"customers":[{"id":"qa_test-customer"}]}</string></map>'
            for name, content in (("./shared_prefs/haus_garten.xml", prefs), ("./databases/haus-garten-mobile.db", database.read_bytes())):
                info = tarfile.TarInfo(name)
                info.size = len(content)
                target.addfile(info, io.BytesIO(content))
            if unsafe:
                info = tarfile.TarInfo("../outside")
                info.size = 1
                target.addfile(info, io.BytesIO(b'x'))
        return archive

    def test_sqlite_copy_is_valid_and_closed_on_windows(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            report = audit.inspect(self.fixture(Path(directory)))
            self.assertEqual(report["database_integrity"], "ok")
            self.assertEqual(report["pending_count"], 1)
            self.assertEqual(report["master_counts"]["customers"], 1)
            self.assertFalse(report["drive_configured"])
            self.assertNotIn("qa_test-customer", json.dumps(report))

    def test_repeated_inspection_preserves_bytes(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            archive = self.fixture(Path(directory))
            before = archive.read_bytes()
            self.assertEqual(audit.inspect(archive), audit.inspect(archive))
            self.assertEqual(before, archive.read_bytes())

    def test_malformed_pending_is_reported_not_changed(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            report = audit.inspect(self.fixture(Path(directory), invalid_json=True))
            self.assertEqual(report["invalid_pending_json"], 1)

    def test_unsafe_archive_name_is_rejected(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            with self.assertRaises(ValueError):
                audit.inspect(self.fixture(Path(directory), unsafe=True))


if __name__ == "__main__":
    unittest.main()
