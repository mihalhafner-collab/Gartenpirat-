#!/usr/bin/env python3
import json
from pathlib import Path

import db


def main():
    db.init()
    target = Path(__file__).resolve().parent / "android" / "app" / "src" / "main" / "assets" / "initial-data.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    backup_target = Path(__file__).resolve().parent.parent / "outputs" / "HausGarten-Laptop.json"
    backup_target.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "format": 1,
        "exported_at": db.now(),
        "customers": db.list_customers(),
        "orders": db.list_orders(),
        "worklogs": db.list_worklogs(),
        "invoices": db.list_invoices(),
        "photos": db.mobile_photo_thumbnails(db.list_photos()),
        "dashboard": db.dashboard(),
        "accepted_ids": [],
    }
    target.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    backup_target.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    print(f"Mobiler Startbestand erstellt: {target} ({len(payload['customers'])} Kunden)")


if __name__ == "__main__":
    main()
