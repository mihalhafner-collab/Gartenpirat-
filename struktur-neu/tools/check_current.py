"""Run the small current source check; no build, signing, device or publication."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    (ROOT / 'outputs').mkdir(exist_ok=True)
    checks = [
        ['-c', 'import privacy_guard; print(privacy_guard.source_gate())'],
        ['tools/check_release_baseline.py'],
        ['tools/sync_offer_contract.py'],
        ['tools/verify_ui_mcp_inventory.py'],
        ['tools/sync_synthetic_fixtures.py'],
        ['tools/sync_plant_assets.py'],
        ['tools/sync_equipment_maintenance_texts.py', '--check'],
        ['-m', 'unittest', 'discover', '-s', 'tests', '-p', 'test_original_signing.py'],
        ['-m', 'unittest', 'discover', '-s', 'tests', '-p', 'test_drive_backup_only_build.py'],
    ]
    for arguments in checks:
        subprocess.run([sys.executable, '-B', *arguments], cwd=ROOT, check=True)
    print('PASS current source and selected Python checks; native/device tests not run')


if __name__ == '__main__':
    main()
