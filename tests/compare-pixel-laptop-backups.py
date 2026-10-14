"""Read-only ID/revision comparison of already-created Pixel and laptop backups."""
import argparse, json, sqlite3, tarfile, tempfile, xml.etree.ElementTree as ET
from contextlib import closing
from collections import Counter
from pathlib import Path, PurePosixPath

def pixel_state(archive_path):
    with tarfile.open(archive_path, "r:") as archive:
        members={str(PurePosixPath(m.name)):m for m in archive.getmembers() if m.isfile()}
        prefs=ET.fromstring(archive.extractfile(members["shared_prefs/haus_garten.xml"]).read())
        values={entry.attrib.get("name"):entry.text or "" for entry in prefs}
        snapshot=json.loads(values.get("drive_snapshot") or "{}")
        with tempfile.TemporaryDirectory(prefix="hausgarten-compare-") as folder:
            db=Path(folder)/"mobile.db"
            for suffix in ("", "-wal", "-shm", "-journal"):
                member=members.get("databases/haus-garten-mobile.db"+suffix)
                if member: Path(str(db)+suffix).write_bytes(archive.extractfile(member).read())
            pending=[]
            with closing(sqlite3.connect(db)) as connection:
                for client_id,kind,payload,created_at in connection.execute("SELECT id,kind,payload,created_at FROM pending ORDER BY created_at,rowid"):
                    pending.append({"client_id":client_id,"kind":kind,"payload":json.loads(payload),"created_at":created_at})
    effective={name:{str(row["id"]):dict(row) for row in snapshot.get(name,[]) if row.get("id")} for name in ("customers","orders","worklogs","photos","invoices")}
    singular={"customer":"customers","order":"orders","worklog":"worklogs","photo":"photos","invoice":"invoices"}
    for action in pending:
        collection=singular.get(action["kind"]);payload=action["payload"]
        if collection and payload.get("id"):
            effective[collection][str(payload["id"])]=dict(effective[collection].get(str(payload["id"]),{}),**payload)
    for action in pending:
        payload=action["payload"]
        if action["kind"]=="delete" and payload.get("entity") in singular:
            effective[singular[payload["entity"]]].pop(str(payload.get("id","")),None)
    return snapshot,pending,effective

def laptop_state(path):
    with closing(sqlite3.connect(f"file:{Path(path).resolve().as_posix()}?mode=ro",uri=True)) as connection:
        connection.row_factory=sqlite3.Row
        return {name:{str(row["id"]):dict(row) for row in connection.execute(f"SELECT * FROM {name}")} for name in ("customers","orders","worklogs","photos","invoices")}

def main():
    parser=argparse.ArgumentParser();parser.add_argument("pixel_tar");parser.add_argument("laptop_db");parser.add_argument("--report",required=True);args=parser.parse_args()
    snapshot,pending,pixel=pixel_state(args.pixel_tar);laptop=laptop_state(args.laptop_db)
    singular={"customer":"customers","order":"orders","worklog":"worklogs","photo":"photos","invoice":"invoices"}
    accepted={str(value) for value in snapshot.get("accepted_ids",[])}
    report={"pending_by_kind":dict(sorted(Counter(a["kind"] for a in pending).items())),"pending_receipts":{
        "accepted_in_snapshot":sum(1 for a in pending if a["client_id"] in accepted),
        "not_accepted_in_snapshot":sum(1 for a in pending if a["kind"]!="mcp_receipt" and a["client_id"] not in accepted),
        "delete_targets":[{"entity":a["payload"].get("entity",""),"id":a["payload"].get("id",""),"still_exists_on_laptop":str(a["payload"].get("id","")) in laptop.get(singular.get(a["payload"].get("entity"),""),{})} for a in pending if a["kind"]=="delete"]
    },"collections":{}}
    for name in pixel:
        snapshot_ids={str(r["id"]) for r in snapshot.get(name,[]) if r.get("id")};pixel_ids=set(pixel[name]);laptop_ids=set(laptop[name])
        changed=[];field_differences=Counter()
        for identifier in sorted(pixel_ids&laptop_ids):
            if name=="worklogs":
                keys=("customer_id","order_id","work_date","start_time","end_time","break_minutes","hours","activity","location","kilometers","material","notes","billed","customer_visible")
                different=[k for k in keys if pixel[name][identifier].get(k)!=laptop[name][identifier].get(k)]
                if different:changed.append(identifier);field_differences.update(different)
        report["collections"][name]={"pixel_snapshot":len(snapshot_ids),"pixel_effective":len(pixel_ids),"laptop":len(laptop_ids),"only_pixel":sorted(pixel_ids-laptop_ids),"only_laptop":sorted(laptop_ids-pixel_ids),"same_id_different_business_fields":changed,"different_field_counts":dict(sorted(field_differences.items())),"pending_added_or_changed":sorted(pixel_ids-snapshot_ids)}
    Path(args.report).write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf-8")
    print(json.dumps({"pending_by_kind":report["pending_by_kind"],"pending_receipts":report["pending_receipts"],"counts":{k:{x:len(v[x]) if isinstance(v[x],list) else v[x] for x in ("pixel_snapshot","pixel_effective","laptop","only_pixel","only_laptop","same_id_different_business_fields","different_field_counts","pending_added_or_changed")} for k,v in report["collections"].items()}},ensure_ascii=False))

if __name__=="__main__":main()
