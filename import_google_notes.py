from __future__ import annotations

import re
from datetime import date
from pathlib import Path

import db
import runtime_paths


SOURCE = runtime_paths.PrivatePath("imports/Google_Notizen_Export_2020.txt")


def parse_blocks(text: str):
    lines = text.replace("\r", "").splitlines()
    blocks, current, body = [], None, []
    for line in lines:
        match = re.match(r"^\s*(?:(?:Mo|Di|Mi|Do|Fr|Sa|So)\.?\s*)?(\d{1,2})(?:\.(\d{1,2}))?\.?\s*$", line)
        inline = re.match(r"^\s*(?:\w+\.?\s+)?(\d{1,2})\.(\d{1,2})(?:\s|:)", line)
        if inline:
            if current: blocks.append((current, body))
            day, month = int(inline.group(1)), int(inline.group(2))
            if not (1 <= day <= 31 and 1 <= month <= 12):
                if current: body.append(line)
                continue
            current = date(2020, month, day)
            body = [line]
        elif match and current:
            if current: blocks.append((current, body))
            day, month = int(match.group(1)), int(match.group(2) or current.month)
            current = date(2020, month, day)
            body = []
        elif current:
            body.append(line)
    if current: blocks.append((current, body))
    return blocks


def hours_for(body: list[str]) -> float:
    standalone = []
    for line in body:
        m = re.match(r"^\s*(\d+(?:[.,]\d+)?)\s*h\s*$", line, re.I)
        if m: standalone.append(float(m.group(1).replace(",", ".")))
    if standalone: return max(standalone)
    values = []
    for line in body:
        if "pause" in line.lower() or "mittag" in line.lower(): continue
        for m in re.finditer(r"(\d+(?:[.,]\d+)?)\s*h\b", line, re.I): values.append(float(m.group(1).replace(",", ".")))
    return round(sum(values), 2)


def customer_for(body: list[str], customers: list[dict]) -> dict | None:
    text = " ".join(body).lower()
    for customer in customers:
        name = str(customer.get("name", "")).strip().lower()
        if name and name in text: return customer
        parts = [p for p in re.split(r"\s+", name) if len(p) >= 5]
        if parts and any(p in text for p in parts): return customer
    return None


def main():
    db.init()
    customers = db.list_customers()
    generic = next((c for c in customers if c["name"] == "Historische Arbeitsaufzeichnungen"), None)
    if not generic:
        generic = db.save_customer({"name": "Historische Arbeitsaufzeichnungen", "notes": "Import aus Google_Notizen_Export_2020.docx"})
        customers.append(generic)
    imported = 0
    for work_date, body in parse_blocks(SOURCE.read_text(encoding="utf-8")):
        block = "\n".join(body).strip()
        hours = hours_for(body)
        if not block or hours <= 0: continue
        customer = customer_for(body, customers) or generic
        identifier = f"wrk_notes_{work_date.isoformat()}"
        db.save_worklog({"id": identifier, "customer_id": customer["id"], "work_date": work_date.isoformat(), "hours": hours, "activity": block[:4000], "notes": "Import aus Google Notizen Export 2020; bitte Zuordnung prüfen.", "source": "Google Notizen Import", "customer_visible": 1})
        imported += 1
    print(f"Importiert/aktualisiert: {imported} Arbeitsaufzeichnungen")


if __name__ == "__main__": main()
