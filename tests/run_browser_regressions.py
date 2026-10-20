"""Run existing source UI regressions with synthetic Android bridges; no device access.

On managed Cloud Chromium use HG_TEST_LOCAL_ASSETS=1, HG_BROWSER_EXECUTABLE_PATH
and HG_PLAYWRIGHT_MODULE. The local asset route models Android asset delivery,
not an installed WebView. Web service-worker tests require a separate synthetic
HTTP server and are intentionally reported separately.
"""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
LOGS = ROOT/'outputs/integration-ui'
NAMES = ['p3-customer-hub.test.cjs', 'p3-business-actions.test.cjs', 'business-feedback.test.cjs', 'current-data-ui-smoke.cjs', 'planning-ui.test.cjs', 'invoice-editor.test.js', 'invoice-flow.test.js', 'billing-ui.test.cjs',
         'architecture-workflow.test.cjs', 'daily-usability.test.cjs', 'design-concept.test.cjs',
         'simple-design.test.cjs', 'worklog-window.test.cjs', 'pixel-tunnel-ui.test.js',
         'work-location.test.cjs', 'timer-completion.test.cjs', 'design-v139.test.cjs',
         'gartenpirat-removal.test.cjs', 'customer-duplicate-ui.test.cjs', 'gartenpirat-plants.test.cjs',
         'gartenpirat-photo.test.cjs', 'garden-followup.test.cjs', 'customer-photos.test.cjs',
         'work-entry-photo-failure.test.cjs', 'camera-timer-static.test.cjs', 'personal-payment.test.cjs',
         'access-integrity.test.cjs', 'sync-customer-gallery.test.cjs', 'offers-photo-library.test.cjs', 'sync-trash.test.cjs', 'worklog-batch.test.cjs', 'runtime-compat.test.cjs', 'owner-client-contract.test.cjs']


if __name__ == '__main__':
    LOGS.mkdir(parents=True, exist_ok=True)
    results = []
    for name in NAMES:
        try:
            completed = subprocess.run(['node', str(ROOT/'tests'/name)], cwd=ROOT, env=os.environ,
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=150)
            output, code = completed.stdout, completed.returncode
        except subprocess.TimeoutExpired as error:
            output, code = error.stdout or b'', 'TIMEOUT'
        (LOGS/(name+'.log')).write_bytes(output)
        results.append({'suite': name, 'exit_code': code})
        print(name, code, flush=True)
        (LOGS/'results.json').write_text(json.dumps(results, indent=2))
    raise SystemExit(0 if all(row['exit_code'] == 0 for row in results) else 1)
