"""Synthetic replacement for the historical production-reading smoke test.

No owner store, private root, real customer or productive Drive channel is used.
"""
import asyncio,json
from pathlib import Path
from mcp_stdio_e2e import run

if __name__=='__main__':
    report=asyncio.run(run())
    target=Path(__file__).resolve().parents[1]/'outputs/QA-MCP-Synthetic-Smoke.json'
    target.write_text(json.dumps(report,indent=2))
    print(json.dumps({'result':'PASS' if report.get('success') else 'FAIL','data_source':'central synthetic OS-temp store'}))
