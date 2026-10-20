"""Read-only synthetic emulator ledger; raw captures never reach disk or stdout.

The old release lifecycle baseline is deliberately not reset or overwritten.
Compare the actually installed source release with its in-place update instead.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess

from run_cloud_emulator_tests import ROOT, emulator_lock, require_emulator


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(',', ':')).encode()).hexdigest()


def capture(adb, serial):
    require_emulator(adb, serial)
    result = subprocess.run(
        [adb, '-s', serial, 'shell', 'am', 'instrument', '-w', '-e',
         'business_capture', 'true',
         'at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation'],
        env={k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')},
        capture_output=True, text=True, timeout=180, check=True)
    if 'BUSINESS_CAPTURE_OK' not in result.stdout:
        raise ValueError('Synthetic business capture was not confirmed; raw diagnostics suppressed.')
    chunks = [line.split('BUSINESS_CAPTURE ', 1)[1].strip()
              for line in result.stdout.splitlines() if 'BUSINESS_CAPTURE ' in line]
    state = json.loads(base64.b64decode(''.join(chunks), validate=True))
    master = state['master']
    collections = ['customers', 'orders', 'worklogs', 'invoices', 'offers', 'photos', 'appointments']
    for name in collections:
        if any(not str(row.get('id', '')).startswith(('qa_', 'qa-', 'test_', 'test-'))
               for row in master.get(name, [])):
            raise ValueError('The emulator contains non-synthetic IDs; ledger rejected.')
    business = {k: v for k, v in master.items() if k != 'capabilities'}
    # Running clocks advance. Compare all persisted identifying/session fields;
    # elapsed time, current rounded hours and pause minutes are derived at read.
    session = {k: v for k, v in state.get('work_session', {}).items()
               if k not in ('worked_ms', 'hours', 'break_minutes')}
    version = subprocess.run([adb, '-s', serial, 'shell', 'dumpsys', 'package',
                              'at.hausgarten.service'], capture_output=True,
                             text=True, check=True, timeout=30).stdout
    import re
    code = int(re.search(r'versionCode=(\d+)', version)[1])
    return {'classification': 'SYNTHETIC_TEST', 'version_code': code,
            'counts': {name: len(master.get(name, [])) for name in collections},
            'pending_count': len(state['pending']),
            'business_and_pending_sha256': digest({'master': business, 'pending': state['pending']}),
            'complete_master_sha256': digest(master), 'pending_sha256': digest(state['pending']),
            'session_persisted_fields_sha256': digest(session),
            'session_state': state.get('work_session', {}).get('state', 'IDLE'),
            'relations_sha256': digest({name: [
                {k: v for k, v in row.items() if k == 'id' or k.endswith('_id') or k.endswith('_ids')}
                for row in master.get(name, [])] for name in collections})}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default=str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'))
    parser.add_argument('--compare', action='store_true')
    args = parser.parse_args()
    path = ROOT / 'outputs/cloud-emulator/p3-update-before.json'
    with emulator_lock(args.serial):
        current = capture(args.adb, args.serial)
        if args.compare:
            before = json.loads(path.read_text())
            if before['version_code'] != 81 or current['version_code'] != 82:
                raise ValueError('Expected original in-place update 81 to 82.')
            for key in before:
                if key != 'version_code' and current[key] != before[key]:
                    raise ValueError('Update ledger mismatch: ' + key)
            target = path.with_name('p3-update-after.json')
            target.write_text(json.dumps(current, indent=2) + '\n')
            print('PASS original 81→82 update: complete master, pending actions, stable timer, counts and relations unchanged')
        else:
            if path.exists() or current['version_code'] != 81:
                raise ValueError('Do not replace an update baseline; source release must be 81.')
            path.write_text(json.dumps(current, indent=2) + '\n')
            print('PASS read-only synthetic update baseline captured: counts and digests only')
        print(json.dumps(current, sort_keys=True))


if __name__ == '__main__':
    main()
