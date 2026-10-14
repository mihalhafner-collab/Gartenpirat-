"""Inspect a local backup, never print customer data or read native tunnel keys."""
import argparse
from contextlib import closing
import hashlib
import json
import sqlite3
import tarfile
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath


def inspect(archive_path):
    result = {"files": {}, "database_integrity": None, "master_counts": {}, "duplicate_master_ids": {}}
    with tarfile.open(archive_path, "r:") as archive:
        files = {}
        for member in archive.getmembers():
            name = str(PurePosixPath(member.name))
            if name.startswith("/") or ".." in PurePosixPath(name).parts:
                raise ValueError("Unsafe backup member")
            if not member.isfile():
                continue
            if name in files:
                raise ValueError("Repeated backup member")
            files[name] = member
            content = archive.extractfile(member).read()
            result["files"][name] = {"bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
        prefs_name = "shared_prefs/haus_garten.xml"
        if prefs_name not in files:
            raise ValueError("App preferences missing from backup")
        preferences = {entry.attrib.get("name"): entry.text or "" for entry in ET.fromstring(archive.extractfile(files[prefs_name]).read())}
        result["drive_configured"] = bool(preferences.get("drive_tree_uri"))
        snapshot = json.loads(preferences.get("drive_snapshot") or "{}")
        result["snapshot_sha256"] = hashlib.sha256(json.dumps(snapshot, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()
        for name in ("customers", "orders", "worklogs", "photos", "invoices"):
            rows = snapshot.get(name, [])
            ids = [str(row.get("id", "")) for row in rows]
            result["master_counts"][name] = len(rows)
            result["duplicate_master_ids"][name] = len(ids) - len(set(ids))
        db_name = "databases/haus-garten-mobile.db"
        if db_name not in files:
            raise ValueError("Mobile database missing from backup")
        with tempfile.TemporaryDirectory(prefix="hausgarten-backup-audit-") as temporary:
            target = Path(temporary) / "haus-garten-mobile.db"
            for suffix in ("", "-journal", "-wal", "-shm"):
                entry = files.get(db_name + suffix)
                if entry:
                    Path(str(target) + suffix).write_bytes(archive.extractfile(entry).read())
            # Recovery, if necessary, affects only this disposable copy.
            with closing(sqlite3.connect(target)) as connection:
                check = [row[0] for row in connection.execute("PRAGMA integrity_check")]
                if check != ["ok"]:
                    raise ValueError("Backup database failed integrity check")
                result["database_integrity"] = "ok"
                rows = list(connection.execute("SELECT id,kind,payload,created_at FROM pending ORDER BY id"))
                result["pending_count"] = len(rows)
                result["pending_sha256"] = hashlib.sha256(json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()
                result["invalid_pending_json"] = 0
                for row in rows:
                    try:
                        json.loads(row[2])
                    except (ValueError, TypeError):
                        result["invalid_pending_json"] += 1
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("archive")
    parser.add_argument("--report", required=True)
    parser.add_argument("--compare")
    args = parser.parse_args()
    report = inspect(args.archive)
    if args.compare:
        before = json.loads(Path(args.compare).read_text(encoding="utf-8"))
        differences = [name for name in ("files", "snapshot_sha256", "pending_sha256", "master_counts", "drive_configured") if before[name] != report[name]]
        report["unchanged_after_update"] = not differences
        report["changed_sections"] = differences
    Path(args.report).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "files"}))
    if args.compare and not report["unchanged_after_update"]:
        raise SystemExit("Backup differs after update; inspect before starting the app.")
