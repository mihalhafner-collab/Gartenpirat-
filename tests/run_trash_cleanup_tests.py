"""Execute the actual cleanup coordinator against synthetic I/O ports, never a device.

Run tools/run-followup-java.py first. Its compiled pure domain classes are reused;
Android effects doubles live only in this test's disposable OS-temp classpath.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    jar = Path(os.environ.get('HG_JSON_JAR', ROOT / '.dependencies/pixel-tunnel-tests/json-20240303.jar'))
    if hashlib.sha256(jar.read_bytes()).hexdigest() != '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':
        raise RuntimeError('JSON test dependency mismatch')
    domain = ROOT / 'outputs/integration-java/classes'
    if not (domain / 'at/hausgarten/service/TrashRules.class').is_file():
        raise RuntimeError('Run tools/run-followup-java.py before this focused test.')
    source = ROOT / 'android/app/src/main/java/at/hausgarten/service/TrashMediaCleanup.java'
    test = ROOT / 'tests/java-cleanup/at/hausgarten/service/TrashMediaCleanupTests.java'
    with tempfile.TemporaryDirectory(prefix='qa_trash_cleanup_') as temporary:
        classes = Path(temporary) / 'classes'
        classes.mkdir()
        cp = os.pathsep.join(map(str, [classes, domain, jar]))
        subprocess.run([shutil.which('javac'), '-encoding', 'UTF-8', '-implicit:none',
                        '-sourcepath', temporary, '-cp', cp, '-d', str(classes),
                        str(source), str(test)], check=True, timeout=60)
        result = subprocess.run([shutil.which('java'), '-cp', cp,
                                 'at.hausgarten.service.TrashMediaCleanupTests'],
                                capture_output=True, text=True, timeout=60)
        print(result.stdout, end='')
        if result.returncode:
            print(result.stderr)
        report = {'classification': 'SYNTHETIC_TEST', 'result': 'PASS' if result.returncode == 0 else 'FAIL',
                  'production_source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                  'scope': 'production cleanup coordinator with synthetic I/O doubles',
                  'android_sqlite_provider': 'NOT EXECUTED', 'physical_pixel': 'NOT EXECUTED',
                  'summary': result.stdout.strip()}
        output = ROOT / 'outputs/qa-trash-cleanup.json'
        output.write_text(json.dumps(report, indent=2) + '\n')
        raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
