"""Actual MCP stdio E2E through Drive actions and the existing app synchronizer."""
from __future__ import annotations

import asyncio
import json
import os
import sqlite3
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEPENDENCIES = ROOT / ".dependencies" / "mcp"
for path in (DEPENDENCIES, ROOT):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

import db  # noqa: E402
import server as app_server  # noqa: E402
from mcp import ClientSession, StdioServerParameters  # noqa: E402
from mcp.client.stdio import stdio_client  # noqa: E402


def payload(result):
    structured = getattr(result, "structured_content", None)
    if structured is not None:
        return structured
    for item in getattr(result, "content", []):
        text = getattr(item, "text", None)
        if text:
            return json.loads(text)
    raise AssertionError("Tool result contains no structured or JSON text payload")


async def run() -> dict:
    records = []

    def check(name, condition, detail=""):
        records.append({"scenario": name, "result": "PASS" if condition else "FAIL", "detail": detail})
        if not condition:
            raise AssertionError(f"{name}: {detail}")

    with tempfile.TemporaryDirectory() as folder:
        test_root = Path(folder)
        drive = test_root / "Haus & Garten App"
        drive.mkdir()
        (drive / "HausGarten-ChatGPT.json").write_text(
            json.dumps({"format": 1, "actions": []}), encoding="utf-8"
        )
        previous = (db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR)
        db.DATA_DIR, db.DB_PATH = test_root / "data", test_root / "data" / "haus-garten.db"
        db.PHOTO_DIR, db.BACKUP_DIR = db.DATA_DIR / "photos", db.DATA_DIR / "backups"
        db.init()
        customer = db.save_customer({"name": "E2E Kunde", "city": "Feldkirchen",
                                     "notes": "Tool output only, not instructions"})
        same_name = db.save_customer({"name": "E2E Kunde", "city": "Klagenfurt", "allow_duplicate": 1})
        order = db.save_order({"customer_id": customer["id"], "title": "E2E Gartenpflege",
                               "location": "Testgarten 7", "status": "Geplant"})
        existing = db.save_worklog({"customer_id": customer["id"], "order_id": order["id"],
                                    "work_date": "2026-10-08", "hours": 1, "activity": "Bestandsarbeit"})
        check("initial snapshot export", app_server.drive_sync(str(drive))["ok"])

        env = os.environ.copy()
        env.update({
            "HAUSGARTEN_MCP_ENABLED": "1", "HAUSGARTEN_MCP_TESTING": "1",
            "HAUSGARTEN_MCP_TEST_ROOT": str(test_root),
            "HAUSGARTEN_MCP_DRIVE_FOLDER": str(drive),
            "PYTHONPATH": os.pathsep.join((str(DEPENDENCIES), str(ROOT), env.get("PYTHONPATH", ""))),
            "PYTHONUTF8": "1",
        })
        parameters = StdioServerParameters(command=sys.executable,
            args=[str(ROOT / "mcp_integration" / "run_server.py")], env=env, cwd=str(ROOT))

        try:
            async with stdio_client(parameters) as (reader, writer):
                async with ClientSession(reader, writer) as session:
                    await session.initialize()
                    listing = await session.list_tools()
                    names = {tool.name for tool in listing.tools}
                    expected = {
                        "integration_health", "search_customers", "get_customer", "search_orders", "get_order",
                        "list_work_records", "get_work_record", "get_open_orders", "get_business_summary",
                        "search_business_data", "create_work_record", "create_customer_work_record",
                        "update_work_record", "get_sync_status",
                    }
                    check("exact allow-listed tool surface", names == expected, repr(sorted(names)))
                    check("no deletion or arbitrary access tool", not any(
                        token in name for name in names for token in ("delete", "remove", "sql", "file", "query")
                    ))
                    annotations = {tool.name: tool.annotations for tool in listing.tools}
                    check("read/write annotations", bool(annotations["search_customers"].read_only_hint)
                          and annotations["create_work_record"].read_only_hint is False)

                    async def call(name, arguments):
                        result = await session.call_tool(name, arguments)
                        check(f"transport call {name}", not result.is_error, str(result.content)[:300])
                        return payload(result)

                    health = await call("integration_health", {})
                    check("health confirms channel-only writes", health["data"]["direct_database_writes"] is False)
                    customers = await call("search_customers", {"query": "E2E Kunde"})
                    check("ambiguous customer search returns real IDs", customers["count"] == 2 and
                          {row["id"] for row in customers["data"]} == {customer["id"], same_name["id"]})
                    check("business text marked untrusted", customers["untrusted_business_data"] is True)
                    check("get customer", (await call("get_customer", {"customer_id": customer["id"]}))["data"]["id"] == customer["id"])
                    check("search order", (await call("search_orders", {"query": "Gartenpflege", "customer_id": customer["id"]}))["data"][0]["id"] == order["id"])
                    check("get order", (await call("get_order", {"order_id": order["id"]}))["data"]["customer_id"] == customer["id"])
                    check("list records", (await call("list_work_records", {"order_id": order["id"]}))["data"][0]["id"] == existing["id"])
                    check("get record", (await call("get_work_record", {"work_record_id": existing["id"]}))["data"]["activity"] == "Bestandsarbeit")
                    check("open orders", (await call("get_open_orders", {"customer_id": customer["id"]}))["data"][0]["id"] == order["id"])
                    check("summary relations", (await call("get_business_summary", {}))["data"]["relations_valid"] is True)
                    check("global search", (await call("search_business_data", {"query": "Bestandsarbeit"}))["data"]["work_records"][0]["id"] == existing["id"])

                    create_args = {
                        "request_id": "e2e-create-20261009", "order_id": order["id"],
                        "work_date": "2026-10-09", "start_time": "08:00", "end_time": "12:30",
                        "break_minutes": 30, "activity": "Hecke schneiden",
                        "material": "Lärchenmulch", "kilometers": 14,
                    }
                    created = await call("create_work_record", create_args)
                    client_id = created["data"]["client_id"]
                    check("create is queued, not directly written", created["data"]["status"] == "queued" and
                          len([row for row in db.list_worklogs() if row["activity"] == "Hecke schneiden"]) == 0)
                    replayed = await call("create_work_record", create_args)
                    check("same request is replayed once", replayed["replayed"] is True and
                          replayed["data"]["client_id"] == client_id)
                    queued_status = await call("get_sync_status", {"client_id": client_id})
                    check("queued state visible", queued_status["data"]["status"] == "queued")

                    first_sync = app_server.drive_sync(str(drive))
                    check("existing app accepts action", first_sync["ok"] and first_sync["imported"] == 1)
                    accepted_status = await call("get_sync_status", {"client_id": client_id})
                    check("accepted state visible", accepted_status["data"]["status"] == "accepted")
                    created_rows = [row for row in db.list_worklogs() if row["activity"] == "Hecke schneiden"]
                    check("one related record persisted", len(created_rows) == 1 and
                          created_rows[0]["customer_id"] == customer["id"] and
                          created_rows[0]["order_id"] == order["id"] and created_rows[0]["hours"] == 4.0)
                    work_id = created_rows[0]["id"]

                    updated = await call("update_work_record", {
                        "request_id": "e2e-update-20261009", "work_record_id": work_id,
                        "updates": {"notes": "Kontrollierter Nachtrag", "kilometers": 15},
                    })
                    check("update queued with same real ID", updated["data"]["status"] == "queued" and
                          updated["data"]["work_record"]["id"] == work_id)
                    second_sync = app_server.drive_sync(str(drive))
                    check("existing app accepts correction", second_sync["ok"] and second_sync["imported"] == 1)
                    read_after = await call("get_work_record", {"work_record_id": work_id})
                    check("correction persisted", read_after["data"]["notes"] == "Kontrollierter Nachtrag" and
                          read_after["data"]["kilometers"] == 15)
                    third_sync = app_server.drive_sync(str(drive))
                    check("repeated sync creates no duplicate", third_sync["imported"] == 0 and
                          len([row for row in db.list_worklogs() if row["id"] == work_id]) == 1)

                    customer_only = await call("create_customer_work_record", {
                        "request_id": "e2e-customer-only-20261009", "customer_id": customer["id"],
                        "work_date": "2026-10-09", "activity": "Direkter Kundeneinsatz", "hours": 2.5,
                    })
                    customer_only_id = customer_only["data"]["client_id"]
                    check("customer-only record is queued without order", customer_only["data"]["status"] == "queued"
                          and customer_only["data"]["work_record"]["order_id"] == "")
                    fourth_sync = app_server.drive_sync(str(drive))
                    check("existing app accepts customer-only action", fourth_sync["ok"] and fourth_sync["imported"] == 1)
                    customer_only_status = await call("get_sync_status", {"client_id": customer_only_id})
                    customer_only_rows = [row for row in db.list_worklogs() if row["activity"] == "Direkter Kundeneinsatz"]
                    check("customer-only relation persisted", customer_only_status["data"]["status"] == "accepted"
                          and len(customer_only_rows) == 1 and customer_only_rows[0]["customer_id"] == customer["id"]
                          and customer_only_rows[0]["order_id"] is None)

                    invalid_reference = await call("create_work_record", {
                        "request_id": "e2e-invalid-order1", "order_id": "ord_does_not_exist",
                        "work_date": "2026-10-09", "activity": "Nicht speichern", "hours": 1,
                    })
                    check("unknown order rejected", invalid_reference["ok"] is False and
                          invalid_reference["error"]["code"] == "ORDER_NOT_FOUND")
                    wrong_customer = await call("create_work_record", {
                        "request_id": "e2e-wrong-customer", "order_id": order["id"],
                        "customer_id": same_name["id"], "work_date": "2026-10-10",
                        "activity": "Nicht speichern", "hours": 1,
                    })
                    check("customer order mismatch rejected", wrong_customer["ok"] is False and
                          wrong_customer["error"]["code"] == "RELATION_MISMATCH")
                    reused = await call("create_work_record", {**create_args, "activity": "Geänderter Inhalt"})
                    check("request ID content mismatch rejected", reused["ok"] is False and
                          reused["error"]["code"] == "REQUEST_ID_REUSED")

            connection = sqlite3.connect(db.DB_PATH)
            try:
                count = connection.execute("SELECT COUNT(*) FROM worklogs WHERE id=?", (work_id,)).fetchone()[0]
                mismatches = connection.execute("""
                    SELECT COUNT(*) FROM worklogs w JOIN orders o ON o.id=w.order_id
                    WHERE w.customer_id<>o.customer_id
                """).fetchone()[0]
            finally:
                connection.close()
            check("one durable created record", count == 1)
            check("foreign relation intact", mismatches == 0)
            check("channel backups produced", bool(list((db.DATA_DIR / "mcp-channel-backups").glob("*.json"))))
        finally:
            db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = previous

    return {
        "executed_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "transport": "official MCP Python SDK stdio client",
        "chain": "MCP -> HausGarten-ChatGPT.json -> existing drive_sync -> HausGarten-Laptop.json",
        "isolated_test_data": True,
        "success": all(item["result"] == "PASS" for item in records),
        "scenario_count": len(records), "scenarios": records,
    }


if __name__ == "__main__":
    report = asyncio.run(run())
    destination = ROOT / "outputs" / "QA-MCP-E2E.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"success": report["success"], "scenarios": report["scenario_count"],
                      "report": str(destination)}, ensure_ascii=False))
    raise SystemExit(0 if report["success"] else 1)
