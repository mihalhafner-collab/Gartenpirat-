"""Einmaliger, wiederholbarer Import der bereits geprüften sevDesk-Migrationsdaten."""
from __future__ import annotations

import json
from pathlib import Path

import db


ROOT = Path(__file__).resolve().parent
PLAN = ROOT.parent / "work" / "migration_plan.json"


def split_address(value: str) -> tuple[str, str, str, str]:
    lines = [line.strip() for line in (value or "").splitlines() if line.strip()]
    street = lines[0] if lines else ""
    postal, city = "", ""
    if len(lines) > 1:
        parts = lines[1].split(maxsplit=1)
        postal = parts[0] if parts else ""
        city = parts[1] if len(parts) > 1 else ""
    country = lines[2] if len(lines) > 2 else ""
    return street, postal, city, country


def main() -> None:
    db.init()
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    customer_ids: dict[str, str] = {}
    created_customers = created_invoices = skipped_customers = skipped_invoices = 0
    replacement_warnings = []

    with db.connect() as database:
        existing = {
            item["sevdesk_contact_id"]: item["id"]
            for item in database.execute(
                "SELECT id,sevdesk_contact_id FROM customers WHERE sevdesk_contact_id IS NOT NULL"
            )
        }

    for source in plan["customers"]:
        sevdesk_id = str(source["sevdesk_contact_id"])
        if sevdesk_id in existing:
            customer_ids[sevdesk_id] = existing[sevdesk_id]
            skipped_customers += 1
            continue
        fields = source["fields"]
        street, postal, city, country = split_address(fields.get("Adresse", ""))
        payload = {
            "id": f"cus_sevdesk_{sevdesk_id}",
            "name": fields.get("Kunde") or f"sevDesk Kontakt {sevdesk_id}",
            "street": street,
            "postal_code": postal,
            "city": fields.get("Ort") or city,
            "country": country,
            "email": fields.get("E-Mail", ""),
            "phone": fields.get("Telefon", ""),
            "notes": fields.get("Hinweise", ""),
            "sevdesk_contact_id": sevdesk_id,
            "sevdesk_customer_number": str(source.get("sevdesk_customer_number") or ""),
            "active": fields.get("Aktiv", True),
        }
        saved = db.save_customer(payload)
        customer_ids[sevdesk_id] = saved["id"]
        created_customers += 1
        if "�" in json.dumps(fields, ensure_ascii=False):
            replacement_warnings.append(f"Kunde {sevdesk_id}: {payload['name']}")

    for source in plan["invoices"]:
        sevdesk_id = str(source["sevdesk_invoice_id"])
        with db.connect() as database:
            exists = database.execute("SELECT 1 FROM invoices WHERE sevdesk_id=?", (sevdesk_id,)).fetchone()
        if exists:
            skipped_invoices += 1
            continue
        fields = source["fields"]
        customer_id = customer_ids.get(str(source["sevdesk_contact_id"]))
        if not customer_id:
            raise RuntimeError(f"Kundenzuordnung fehlt für Rechnung {sevdesk_id}")
        amount = fields.get("Betrag €")
        if amount is None:
            amount = next((value for key, value in fields.items() if key.startswith("Betrag")), 0)
        db.save_invoice({
            "id": f"inv_sevdesk_{sevdesk_id}",
            "customer_id": customer_id,
            "invoice_number": fields.get("Rechnungsnummer") or sevdesk_id,
            "invoice_date": fields.get("Rechnungsdatum"),
            "service_start": fields.get("Leistungsbeginn"),
            "service_end": fields.get("Leistungsende"),
            "amount": amount,
            "status": fields.get("Status") or "Entwurf",
            "notes": fields.get("Notiz", ""),
            "sevdesk_id": sevdesk_id,
        })
        created_invoices += 1
        if "�" in json.dumps(fields, ensure_ascii=False):
            replacement_warnings.append(f"Rechnung {fields.get('Rechnungsnummer', sevdesk_id)}")

    with db.connect() as database:
        customer_count = database.execute("SELECT COUNT(*) FROM customers").fetchone()[0]
        invoice_count = database.execute("SELECT COUNT(*) FROM invoices").fetchone()[0]
        linked_count = database.execute(
            "SELECT COUNT(*) FROM invoices i JOIN customers c ON c.id=i.customer_id"
        ).fetchone()[0]
        amount_sum = database.execute("SELECT ROUND(COALESCE(SUM(amount),0),2) FROM invoices").fetchone()[0]

    print(json.dumps({
        "created_customers": created_customers,
        "skipped_customers": skipped_customers,
        "created_invoices": created_invoices,
        "skipped_invoices": skipped_invoices,
        "database_customers": customer_count,
        "database_invoices": invoice_count,
        "invoices_linked_to_customer": linked_count,
        "invoice_amount_sum": amount_sum,
        "source_encoding_warnings": len(replacement_warnings),
        "warning_examples": replacement_warnings[:10],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
