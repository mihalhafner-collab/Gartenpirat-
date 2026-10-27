"""Run original androidTest code on the dedicated Cloud emulator, never a Pixel.

APKs must already be signed through tools/sign_original_apk.py. No key access,
test app runtime, account login, uninstall, data clearing or device auto-selection.
"""
import argparse
from contextlib import contextmanager
import fcntl
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
    try:
        return subprocess.run([adb, '-s', serial, *args], env=env, capture_output=True,
                              text=True, timeout=timeout, check=True).stdout.strip()
    except subprocess.CalledProcessError as error:
        # A framework may die after the readiness check. Expose that specific
        # system failure without dumping raw device output into source evidence.
        detail = (error.stdout or '') + '\n' + (error.stderr or '')
        if re.search(r"Can't find service: (?:package|activity)\b", detail):
            raise ValueError('Android core service disappeared; installation/testing aborted.') from None
        raise


def require_emulator(adb, serial):
    require_serial(serial)
    if command(adb, serial, ['shell', 'getprop', 'ro.kernel.qemu']) != '1':
        raise ValueError('Selected target is not an Android emulator.')
    if command(adb, serial, ['emu', 'avd', 'name']).splitlines()[0] != 'GartenpiratCloud':
        raise ValueError('Only the dedicated GartenpiratCloud AVD is permitted.')
    if command(adb, serial, ['shell', 'getprop', 'sys.boot_completed']) != '1':
        raise ValueError('Wait for complete emulator startup before installing/testing.')
    # boot_completed stays set when system_server dies and restarts. A working
    # ADB transport and that flag do not establish a usable Android framework.
    for service in ('package', 'activity'):
        if command(adb, serial, ['shell', 'service', 'check', service]) != f'Service {service}: found':
            raise ValueError('Android core services are not ready; do not install or test.')
    if not re.fullmatch(r'package:/\S+\.apk', command(adb, serial, ['shell', 'cmd', 'package', 'path', 'android'])):
        raise ValueError('Android package manager is not functional; do not install or test.')


@contextmanager
def emulator_lock(serial, folder=None):
    require_serial(serial)
    directory = folder or ROOT / 'outputs' / 'cloud-emulator'
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / (serial + '.lock')).open('a') as lock:
        try:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('Another test owns this emulator. Finish that run first.') from error
        try:
            yield
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def successful_output(output, mode):
    if re.search(r'\bFAIL(?:ED)?\b|INSTRUMENTATION_FAILED|Process crashed|acceptance failures=[1-9]', output):
        return False
    if mode in ('acceptance','estimates','provider','mcp_workflows','revier_core','revier_ui','revier_authority','revier_finance','revier_recovery','drive_backup_only','backup_setup'):
        return bool(re.search(r'RESULT [1-9][0-9]* Android SQLite/MCP tests passed; acceptance failures=0;', output))
    if mode in ('plants','plants_ui'):
        count=4 if mode=='plants' else 2
        return bool(re.search(r'RESULT '+str(count)+r' Android plant98 tests passed; acceptance failures=0', output))
    expected = {'verify': 'PASS in-place update preserves complete snapshot',
                'fresh': 'PASS fresh empty business data', 'fresh_isolated': 'PASS fresh empty business data',
                'prepare': 'PASS synthetic upgrade fixture prepared',
                'accessibility': 'PASS native overview accessibility', 'ui': 'PASS native WebView boot'}
    return expected.get(mode, '\0') in output


def instrumentation_budget(mode, requested=None):
    # Host budget for the whole accumulated suite; individual native/UI guards remain unchanged.
    default = 1800 if mode in ('acceptance', 'revier_core', 'revier_recovery') else 900
    if requested is None:
        return default
    if type(requested) is not int or requested not in (900, 1800, 3600):
        raise ValueError('Explicit bounded Cloud instrumentation budget required.')
    return requested


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools/adb'))
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--instrumentation-apk', type=Path)
    parser.add_argument('--mode', choices=['acceptance', 'fresh', 'prepare', 'verify', 'ui', 'estimates', 'provider', 'mcp_workflows', 'fresh_isolated', 'accessibility', 'plants', 'plants_ui', 'revier_core', 'revier_ui','revier_authority','revier_finance','revier_recovery','drive_backup_only','backup_setup','process_recovery'], default='acceptance')
    parser.add_argument('--timeout-seconds', type=int, choices=(900, 1800, 3600), help='Bounded host budget for one accumulated instrumentation suite; native per-case guards are unchanged.')
    args = parser.parse_args()
    with emulator_lock(args.serial):
        run(args)


def run(args):
    timeout = instrumentation_budget(args.mode, getattr(args, 'timeout_seconds', None))
    require_emulator(args.adb, args.serial)
    for apk in [args.apk, args.instrumentation_apk]:
        if apk:
            if not apk.is_file():
                raise ValueError('Signed APK is missing.')
            result = command(args.adb, args.serial, ['install', '--no-streaming', '-r', str(apk)], timeout=180)
            if 'Success' not in result:
                raise RuntimeError('Emulator APK installation was not confirmed.')
    if args.mode == 'process_recovery':
        return process_recovery(args)
    flags = [] if args.mode == 'acceptance' else ['-e', 'release_lifecycle', args.mode]
    folder = ROOT / 'outputs' / 'cloud-emulator'
    folder.mkdir(parents=True, exist_ok=True)
    log = folder / (args.mode + '.log')
    env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
    with log.open('w') as stream:
        subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', *flags,
                        'at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation'],
                       env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=timeout, check=True)
    output = log.read_text()
    print(output)
    if not successful_output(output, args.mode):
        raise SystemExit(1)


def process_recovery(args):
    """Force-stop only the locked dedicated AVD; never clear package data.

    Native preparation deliberately keeps SQLite and WebView open. Verification
    requires another PID and the same original image, draft and native context.
    """
    folder = ROOT / 'outputs' / 'cloud-emulator'
    folder.mkdir(parents=True, exist_ok=True)
    combined = []
    for fault in ('before_chunk', 'after_chunk', 'after_commit', 'editors', 'business'):
        for phase in ('prepare', 'verify'):
            require_emulator(args.adb, args.serial)
            mode = 'process_' + phase + '_' + fault
            output = command(args.adb, args.serial, ['shell', 'am', 'instrument', '-w',
                             '-e', 'release_lifecycle', mode,
                             'at.hausgarten.service.test/at.hausgarten.service.PixelMcpInstrumentation'], timeout=180)
            (folder / (mode + '.log')).write_text(output)
            expected = 'PASS actual process checkpoint ' + ('prepared ' if phase == 'prepare' else 'verified ') + fault
            if expected not in output or re.search(r'\bFAIL(?:ED)?\b|Process crashed|INSTRUMENTATION_FAILED', output):
                raise RuntimeError('Actual process recovery failed; see ' + str(folder / (mode + '.log')))
            combined.append(output)
            if phase == 'prepare':
                require_emulator(args.adb, args.serial)
                command(args.adb, args.serial, ['shell', 'am', 'force-stop', 'at.hausgarten.service'])
    (folder / 'process_recovery.log').write_text('\n'.join(combined))
    print('PASS actual OS process recovery before first chunk, after partial chunk and after native commit; original PNG and complete shared draft preserved; production inquiry/order editors and lost inquiry/order/offer commits resumed from native state after OS death')


if __name__ == '__main__':
    main()
