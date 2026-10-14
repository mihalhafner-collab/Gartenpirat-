"""Read-only MCP transport smoke test against the configured production snapshot."""
from __future__ import annotations

import asyncio
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEPENDENCIES = ROOT / ".dependencies" / "mcp"
for path in (DEPENDENCIES, ROOT):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from mcp import ClientSession, StdioServerParameters  # noqa: E402
from mcp.client.stdio import stdio_client  # noqa: E402


def payload(result):
    if getattr(result, "structured_content", None) is not None:
        return result.structured_content
    return json.loads(next(item.text for item in result.content if getattr(item, "text", None)))


async def run() -> dict:
    env = os.environ.copy()
    env["HAUSGARTEN_MCP_ENABLED"] = "1"
    env.pop("HAUSGARTEN_MCP_TEST_ROOT", None)
    env.pop("HAUSGARTEN_MCP_TESTING", None)
    env.pop("HAUSGARTEN_MCP_DRIVE_FOLDER", None)
    env["PYTHONPATH"] = os.pathsep.join((str(DEPENDENCIES), str(ROOT), env.get("PYTHONPATH", "")))
    parameters = StdioServerParameters(
        command=sys.executable, args=[str(ROOT / "mcp_integration" / "run_server.py")],
        env=env, cwd=str(ROOT),
    )
    async with stdio_client(parameters) as (reader, writer):
        async with ClientSession(reader, writer) as session:
            await session.initialize()
            tools = await session.list_tools()
            names = {tool.name for tool in tools.tools}
            health_result = await session.call_tool("integration_health", {})
            summary_result = await session.call_tool("get_business_summary", {})
            list_result = await session.call_tool("list_work_records", {"limit": 1})
            if health_result.is_error or summary_result.is_error or list_result.is_error:
                raise AssertionError("A read-only production tool returned a transport error.")
            health, summary, listing = payload(health_result), payload(summary_result), payload(list_result)
            if not health.get("ok") or not summary.get("ok") or not listing.get("ok"):
                raise AssertionError("A read-only production tool returned an application error.")
            if any(token in name for name in names for token in ("delete", "remove", "sql", "file")):
                raise AssertionError("Unsafe tool unexpectedly exposed.")
            return {
                "executed_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
                "success": True, "transport": "official MCP Python SDK stdio client",
                "production_writes": False, "tool_count": len(names),
                "snapshot_exported_at": health["data"]["snapshot_exported_at"],
                "customers": health["data"]["customers"], "orders": health["data"]["orders"],
                "work_records": health["data"]["work_records"],
                "pending_actions": health["data"]["pending_actions"],
                "relations_valid": summary["data"]["relations_valid"],
                "one_record_read": listing["count"] == 1,
            }


if __name__ == "__main__":
    report = asyncio.run(run())
    destination = ROOT / "outputs" / "QA-MCP-Product-Readonly.json"
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
