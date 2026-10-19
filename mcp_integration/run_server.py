"""Hardened stdio entry point used by the Secure MCP Tunnel or a local client."""
from __future__ import annotations

import os
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEPENDENCIES = PROJECT_ROOT / ".dependencies" / "mcp"
for path in (DEPENDENCIES, PROJECT_ROOT):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))


def _refuse(message: str) -> "None":
    print(message, file=sys.stderr)
    raise SystemExit(2)


if os.environ.get("HAUSGARTEN_MCP_ENABLED") != "1":
    _refuse("Die private ChatGPT-Integration ist deaktiviert. Setze HAUSGARTEN_MCP_ENABLED=1 zum bewussten Start.")

import db  # noqa: E402  (paths are deliberately configured before importing the MCP app)

test_root = os.environ.get("HAUSGARTEN_MCP_TEST_ROOT")
if os.environ.get('HAUSGARTEN_MCP_TESTING') == '1':
    import runtime_paths
    runtime_paths.synthetic_only()
    if not test_root:_refuse('Testmodus benötigt einen ausdrücklich synthetischen Store.')
if test_root:
    if os.environ.get("HAUSGARTEN_MCP_TESTING") != "1":
        _refuse("Ein abweichender Datenordner ist ausschließlich im ausdrücklich aktivierten Testmodus erlaubt.")
    sys.path.insert(0, str(PROJECT_ROOT / "tests"))
    from synthetic_fixtures import configure_db
    root = configure_db(test_root)
    db.DATA_DIR = root / "data"
    db.DB_PATH = db.DATA_DIR / "haus-garten.db"
    db.PHOTO_DIR = db.DATA_DIR / "photos"
    db.BACKUP_DIR = db.DATA_DIR / "backups"

try:
    from mcp_integration import drive_store  # noqa: E402
    snapshot_path, channel_path = drive_store.paths()
    if not snapshot_path.is_file() or not channel_path.is_file():
        _refuse("Im konfigurierten Drive-Ordner fehlen HausGarten-Laptop.json oder HausGarten-ChatGPT.json.")
    drive_store.load_snapshot()
    drive_store.load_channel()
except SystemExit:
    raise
except Exception:
    _refuse("Der vorhandene Haus-&-Garten-Drive-Kanal ist nicht sicher lesbar. Serverstart abgebrochen.")

from mcp_integration.server_app import server  # noqa: E402


if __name__ == "__main__":
    # stdio emits only MCP protocol messages on stdout. Diagnostics go to stderr.
    server.run(transport="stdio")
