"""Scoped, user-confirmed repair; run only while the app server is stopped."""
import argparse
import base64
import hashlib
import json
import shutil
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

import db
import server
import runtime_paths




def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    config_path = runtime_paths.business_path(runtime_paths.private_root()/"maintenance/photo-links.json")
    config=json.loads(config_path.read_text())
    CUSTOMER=config["customer_id"]; LINKS=config["links"]
    folder = runtime_paths.business_path(db.settings()["backup_folder"])
    source = runtime_paths.business_path(folder / config["source_name"])
    original_bytes = source.read_bytes()
    source_hash = hashlib.sha256(original_bytes).hexdigest()
    if source_hash != config.get("expected_source_sha256"): raise ValueError("Quellhash muss frisch ausdrücklich bestätigt sein.")
    package = json.loads(original_bytes)
    matches = [a for a in package.get("actions", []) if a.get("client_id") in LINKS]
    if len(matches) != 3 or {a["client_id"] for a in matches} != set(LINKS):
        raise ValueError("Die drei freigegebenen Fotoaktionen sind nicht eindeutig vorhanden.")
    receipts_path = db.DATA_DIR / "drive-receipts.json"
    receipts_before = receipts_path.read_bytes()
    accepted = set(json.loads(receipts_before))
    held_path = db.DATA_DIR / "drive-held-deletions.json"
    held_before = held_path.read_bytes()
    held = set(json.loads(held_before))
    if not set(LINKS.values()).issubset(held):
        raise ValueError("Der Schutz der alten Löschaufträge fehlt.")
    prepared = []
    audit_rows = []
    with db.connect() as connection:
        customer = connection.execute("SELECT id,name FROM customers WHERE id=?", (CUSTOMER,)).fetchone()
        if customer is None:
            raise ValueError("Der vorhandene Kunde fehlt; keine Änderung vorgenommen.")
        for action in matches:
            payload = action["payload"]
            photo_id = action["client_id"]
            if (action.get("kind") != "photo" or payload.get("id") != photo_id
                    or payload.get("customer_id") != CUSTOMER or payload.get("order_id")
                    or payload.get("worklog_id") != LINKS[photo_id]):
                raise ValueError("Fotozuordnung weicht von der bestätigten Zuordnung ab.")
            if connection.execute("SELECT 1 FROM worklogs WHERE id=?", (LINKS[photo_id],)).fetchone():
                raise ValueError("Arbeitsaufzeichnung wieder vorhanden; Zuordnung erneut prüfen.")
            existing = connection.execute("SELECT * FROM photos WHERE id=?", (photo_id,)).fetchone()
            if existing and (existing["customer_id"] != CUSTOMER or existing["worklog_id"] or existing["order_id"]):
                raise ValueError("Foto wurde zwischenzeitlich anders zugeordnet; keine Überschreibung.")
            encoded = str(payload["data"])
            raw = base64.b64decode(encoded.split(",", 1)[-1], validate=True)
            digest = hashlib.sha256(raw).hexdigest()
            repaired = {**payload, "worklog_id": None, "order_id": None}
            prepared.append((repaired, digest, bool(existing)))
            audit_rows.append({
                "photo_id": photo_id, "customer_id": CUSTOMER, "customer_name": customer["name"],
                "original_worklog_id": LINKS[photo_id], "original_order_id": payload.get("order_id"),
                "new_worklog_id": None, "new_order_id": None,
                "original_name": payload.get("original_name"), "sha256": digest,
            })
    if not args.apply:
        print(json.dumps({"ready": True, "photo_count": len(audit_rows), "source_sha256": source_hash}, ensure_ascii=False))
        return
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup = db.BACKUP_DIR / ("before-confirmed-photo-links-" + stamp)
    backup.mkdir(parents=True, exist_ok=False)
    with db.connect() as connection, sqlite3.connect(backup / "haus-garten.db") as target:
        connection.backup(target)
    shutil.copy2(receipts_path, backup / receipts_path.name)
    shutil.copy2(held_path, backup / held_path.name)
    # Preserve source evidence before the phone acknowledges/removes its pending actions.
    (backup / source.name).write_bytes(original_bytes)
    audit = db.DATA_DIR / "Fotozuordnung-bestaetigt-2026-10-08.json"
    if audit.exists():
        previous = json.loads(audit.read_text(encoding="utf-8"))
        if previous.get("photos") != audit_rows:
            raise ValueError("Bestehendes Zuordnungsprotokoll weicht ab; nicht überschrieben.")
    else:
        audit.write_text(json.dumps({
            "confirmed_at": db.now(), "confirmation": "Nur dem bestehenden Kunden zuordnen",
            "source_file": source.name, "source_sha256": source_hash,
            "backup_directory": str(backup), "photos": audit_rows,
            "reason": "Die ursprünglichen Arbeitsaufzeichnungen fehlen. Nur Kundenzuordnung freigegeben.",
        }, ensure_ascii=False, indent=2), encoding="utf-8")
    for repaired, digest, existing in prepared:
        if not existing:
            server.save_photo(repaired)
        with db.connect() as connection:
            row = connection.execute("SELECT * FROM photos WHERE id=?", (repaired["id"],)).fetchone()
        assert row["customer_id"] == CUSTOMER and row["worklog_id"] is None and row["order_id"] is None
        assert hashlib.sha256((db.PHOTO_DIR / row["filename"]).read_bytes()).hexdigest() == digest
        accepted.add(repaired["id"])
    receipts_path.write_text(json.dumps(sorted(accepted), ensure_ascii=False), encoding="utf-8")
    result = server.drive_sync()
    snapshot = json.loads((folder / "HausGarten-Laptop.json").read_text(encoding="utf-8"))
    photos = {p["id"]: p for p in snapshot["photos"]}
    for photo_id in LINKS:
        assert photo_id in snapshot["accepted_ids"]
        assert photos[photo_id]["customer_id"] == CUSTOMER
        assert photos[photo_id].get("worklog_id") is None and photos[photo_id].get("order_id") is None
    assert held_path.read_bytes() == held_before
    assert source.read_bytes() == original_bytes, "Mobile Quelldatei wurde währenddessen verändert."
    assert not any(x.get("client_id") in LINKS for x in snapshot["sync_errors"])
    print(json.dumps({"repaired": 3, "source_sha256": source_hash,
                      "sync_ok": bool(result.get("ok")),
                      "snapshot_photo_count": len(photos), "held_deletions": len(held),
                      "source_unchanged": True}, ensure_ascii=False))


if __name__ == "__main__":
    main()
