"""Run original androidTest code on the dedicated Cloud emulator, never a Pixel.

APKs must already be signed through tools/sign_original_apk.py. No key access,
test app runtime, account login, uninstall, data clearing or device auto-selection.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def require_serial(serial):
    if not re.fullmatch(r'emulator-[0-9]{4,5}', serial):
        raise ValueError('Only an explicitly named Android emulator is permitted.')


def command(adb, serial, args, timeout=90):
    require_serial(serial)
    env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
    return subprocess.run([adb, '-s', serial, *args], env=env, capture_output=True,
                          text=True, timeout=timeout, check=True).stdout.strip()


def require_emulator(adb, serial):
    require_serial(serial)
    if command(adb, serial, ['shell', 'getprop', 'ro.kernel.qemu']) != '1':
        raise ValueError('Selected target is not an Android emulator.')
    if command(adb, serial, ['emu', 'avd', 'name']).splitlines()[0] != 'GartenpiratCloud':
        raise ValueError('Only the dedicated GartenpiratCloud AVD is permitted.')
    if command(adb, serial, ['shell', 'getprop', 'sys.boot_completed']) != '1':
        raise ValueError('Wait for complete emulator startup before installing/testing.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools/adb'))
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--instrumentation-apk', type=Path)
    parser.add_argument('--mode', choices=['acceptance', 'fresh', 'prepare', 'verify', 'ui'], default='acceptance')
    args = parser.parse_args()
    require_emulator(args.adb, args.serial)
    for apk in [args.apk, args.instrumentation_apk]:
        if apk:
            if not apk.is_file():
                raise ValueError('Signed APK is missing.')
            result = command(args.adb, args.serial, ['install', '--no-streaming', '-r', str(apk)], timeout=180)
            if 'Success' not in result:
                raise RuntimeError('Emulator APK installation was not confirmed.')
    flags = [] if args.mode == 'acceptance' else ['-e', 'release_lifecycle', args.mode]
    folder = ROOT / 'outputs' / 'cloud-emulator'
    folder.mkdir(parents=True, exist_ok=True)
    log = folder / (args.mode + '.log')
    env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
    with log.open('w') as stream:
        subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', *flags,
                        'at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation'],
                       env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=900, check=True)
    output = log.read_text()
    print(output)
    if re.search(r'\bFAIL(?:ED)?\b|INSTRUMENTATION_FAILED|acceptance failures=[1-9]', output) or 'PASS' not in output:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
