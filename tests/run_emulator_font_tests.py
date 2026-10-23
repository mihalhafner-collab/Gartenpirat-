"""Actual OS font_scale1.5/2.0 acceptance on the explicitly guarded synthetic Cloud AVD.

Requires installed original97 and its original-signed instrumentation. No install,
physical device, credentials, business writes or database reset. OS setting restored.
"""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import time

from run_cloud_emulator_tests import ROOT, command, emulator_lock, require_emulator, successful_output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default=str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'))
    args = parser.parse_args()
    with emulator_lock(args.serial):
        require_emulator(args.adb, args.serial)
        package = command(args.adb, args.serial, ['shell', 'dumpsys', 'package', 'at.hausgarten.service'])
        if 'versionName=1.54.8' not in package or 'versionCode=97 ' not in package:
            raise ValueError('This acceptance requires the original1.54.8/97 candidate already installed.')
        original = command(args.adb, args.serial, ['shell', 'settings', 'get', 'system', 'font_scale'])
        display = command(args.adb, args.serial, ['shell', 'wm', 'size'])
        density = command(args.adb, args.serial, ['shell', 'wm', 'density'])
        override = re.search(r'Override size: (\d+x\d+)', display)
        original_size = (override or re.search(r'Physical size: (\d+x\d+)', display))[1]
        dpi = int((re.search(r'Override density: (\d+)', density) or re.search(r'Physical density: (\d+)', density))[1])
        target_size = f'{360*dpi//160}x{640*dpi//160}'
        results = []
        folder = ROOT / 'outputs/cloud-emulator'
        folder.mkdir(parents=True, exist_ok=True)
        env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
        try:
            command(args.adb, args.serial, ['shell', 'wm', 'size', target_size])
            for scale in ('1.0', '1.5', '2.0'):
                command(args.adb, args.serial, ['shell', 'settings', 'put', 'system', 'font_scale', scale])
                observed = command(args.adb, args.serial, ['shell', 'settings', 'get', 'system', 'font_scale'])
                if float(observed) != float(scale):
                    raise RuntimeError('Emulator font setting not confirmed.')
                time.sleep(2)
                run = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-e', 'release_lifecycle', 'accessibility',
                                      'at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation'], env=env,
                                     capture_output=True, text=True, timeout=900, check=True)
                log = folder / ('font-' + scale + '.log')
                log.write_text(run.stdout + run.stderr)
                passed = successful_output(run.stdout + run.stderr, 'accessibility')
                results.append({'font_scale': scale, 'observed': observed, 'result': 'PASS' if passed else 'FAIL',
                                'scope': 'actual Android WebView/OS fonts with synthetic read responses; no business writes'})
                print(json.dumps(results[-1]), flush=True)
                if not passed:
                    raise RuntimeError('Native font acceptance failed; see ' + str(log))
        finally:
            restore = ['shell', 'settings', 'delete', 'system', 'font_scale'] if original in ('null', '') else ['shell', 'settings', 'put', 'system', 'font_scale', original]
            command(args.adb, args.serial, restore)
            restored = command(args.adb, args.serial, ['shell', 'settings', 'get', 'system', 'font_scale'])
            command(args.adb, args.serial, ['shell', 'wm', 'size', original_size if override else 'reset'])
            display_after = command(args.adb, args.serial, ['shell', 'wm', 'size'])
            restored_size = (re.search(r'Override size: (\d+x\d+)', display_after) or re.search(r'Physical size: (\d+x\d+)', display_after))[1]
            same_display = original_size == restored_size
            effective_before = 1.0 if original in ('null', '') else float(original)
            effective_after = 1.0 if restored in ('null', '') else float(restored)
            same_font = abs(effective_before - effective_after) < 0.001
            (folder / 'font-scale-results.json').write_text(json.dumps({'classification': 'SYNTHETIC_TEST', 'results': results,
                    'original_font_scale': original, 'restored_font_scale': restored, 'original_effective_font_scale': effective_before, 'restored_effective_font_scale': effective_after, 'restored': same_font and same_display, 'logical_screen_dp': [360,640], 'original_display_size': original_size, 'tested_display_size': target_size, 'restored_display_size': restored_size}, indent=2) + '\n')
            if not same_font or not same_display:
                raise RuntimeError('Original emulator font setting could not be confirmed restored.')


if __name__ == '__main__':
    main()
