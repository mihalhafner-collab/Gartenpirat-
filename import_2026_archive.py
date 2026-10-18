from __future__ import annotations
import re
from datetime import date
from pathlib import Path
import db

SOURCE = Path(__file__).resolve().parent / "data" / "Arbeitsaufzeichnungen_2026_Notizenarchiv.md"
MONTHS = {"januar":1,"februar":2,"märz":3,"maerz":3,"april":4,"mai":5,"juni":6,"juli":7,"august":8,"september":9,"oktober":10,"november":11,"dezember":12}

def parse_date(value):
    m = re.search(r"(\d{1,2})\.(\d{1,2})\.(?:20)?(\d{2,4})", value)
    if m: return date(int(m.group(3)) if len(m.group(3)) == 4 else 2000 + int(m.group(3)), int(m.group(2)), int(m.group(1)))
    m = re.search(r"(\d{1,2})\.(\d{1,2})\.?", value)
    if m: return date(2026, int(m.group(2)), int(m.group(1)))
    m = re.search(r"(\d{1,2})\.\s*([A-Za-zÄÖÜäöü]+)(?:\s+(20\d{2}))?", value)
    if m and m.group(2).lower() in MONTHS: return date(int(m.group(3) or 2026), MONTHS[m.group(2).lower()], int(m.group(1)))
    return None

def norm(value): return re.sub(r"[^a-z0-9äöüß]", "", value.lower())

def find_customer(name, customers, generic):
    clean = re.sub(r"\*|⚠", "", name).strip()
    if not clean or clean in {"–", "-", "offen", "Kunde offen"}: return generic
    n = norm(clean)
    matches = []
    for c in customers:
        cn = norm(c["name"])
        if n and n == cn: matches.append(c)
    if matches: return matches[0] if len(matches) == 1 else generic
    for c in customers:
        cn = norm(c["name"])
        parts = [p for p in re.split(r"[ ,/]+", clean) if len(p) >= 5]
        if n and (n in cn or cn in n) or any(norm(p) in cn for p in parts): matches.append(c)
    return matches[0] if len(matches) == 1 else generic

def main():
    db.init(); customers = db.list_customers()
    generic = next((c for c in customers if c["name"] == "Notizenarchiv – ungeklärte Zuordnung"), None)
    if not generic:
        generic = db.save_customer({"name":"Notizenarchiv – ungeklärte Zuordnung", "notes":"Automatisch angelegt für Archivzeilen ohne eindeutige Kundenzuordnung."}); customers.append(generic)
    counts={}; imported=0
    for line in SOURCE.read_text(encoding="utf-8").splitlines():
        if not line.lstrip().startswith("|") or line.count("|") < 5: continue
        cells=[x.strip() for x in line.strip().strip("|").split("|")]
        if len(cells) < 5 or cells[0].lower().startswith(("datum", "---")): continue
        day=parse_date(cells[0]);
        if not day: continue
        hm=re.search(r"(\d+(?:[.,]\d+)?)\s*h", cells[3], re.I)
        if not hm: continue
        hours=float(hm.group(1).replace(",","."));
        if hours <= 0: continue
        customer=find_customer(cells[1],customers,generic); key=f"{day.isoformat()}_{norm(cells[1])}"; counts[key]=counts.get(key,0)+1
        identifier=f"wrk_archive_{day.isoformat()}_{norm(cells[1])[:24]}_{counts[key]}"
        activity=cells[4] if len(cells)>4 else "Arbeitsaufzeichnung aus Notizenarchiv"
        db.save_worklog({"id":identifier,"customer_id":customer["id"],"work_date":day.isoformat(),"hours":hours,"activity":activity,"notes":"Import aus Arbeitsaufzeichnungen_2026_Notizenarchiv; Zuordnung und Abrechnungsstatus bitte prüfen.","source":"Google Notizen Archiv 2026","customer_visible":1})
        imported += 1
    print(f"Archiv importiert/aktualisiert: {imported} Arbeitsaufzeichnungen")

if __name__ == "__main__": main()
