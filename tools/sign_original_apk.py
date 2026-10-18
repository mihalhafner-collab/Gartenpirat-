#!/usr/bin/env python3
"""Original-key signing for the selected personal Gartenpirat Linux Cloud environment.

No key creation, CI integration, installation or update/data-retention acceptance.
Passwords reach Android/JDK tools only through their environment-password options.
All diagnostics are fixed strings; tool output and private configuration stay private.
"""
import argparse
import base64
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / 'android/original-signing-baseline.json'
VARIABLES = ('HG_ORIGINAL_KEYSTORE_BASE64', 'HG_ORIGINAL_STORE_PASSWORD',
             'HG_ORIGINAL_KEY_ALIAS', 'HG_ORIGINAL_KEY_PASSWORD', 'HG_ORIGINAL_CERT_SHA256')
MAX_APK_BYTES = 256 * 1024 * 1024
CLEANUP_ERROR = 'Temporary signing cleanup failed; no APK was published.'


class SigningError(Exception):
    """Only fixed, non-secret diagnostics may be used with this exception."""


class PrivateConfiguration:
    """Deliberately no dataclass serialization or value-bearing repr."""
    __slots__ = ('keystore', 'store_password', 'alias', 'key_password', 'certificate')

    def __init__(self, keystore, store_password, alias, key_password, certificate):
        self.keystore, self.store_password, self.alias = keystore, store_password, alias
        self.key_password, self.certificate = key_password, certificate

    def __repr__(self):
        return '<private original-signing configuration>'


def fingerprint(value):
    value = value.strip()
    if not re.fullmatch(r'(?:[a-fA-F0-9]{64}|(?:[a-fA-F0-9]{2}:){31}[a-fA-F0-9]{2})', value):
        raise SigningError('Expected certificate fingerprint is invalid.')
    return value.replace(':', '').lower()


def configuration(env):
    if any(not isinstance(env.get(name), str) or not env[name].strip() for name in VARIABLES):
        raise SigningError('All five original-signing variables are required and must be nonempty.')
    for name in VARIABLES[1:4]:
        if len(env[name]) > 4096 or any(ord(c) < 32 or ord(c) == 127 for c in env[name]):
            raise SigningError('Original-signing credentials have an invalid format.')
    encoded = env[VARIABLES[0]]
    if len(encoded) > 8 * 1024 * 1024:
        raise SigningError('Original keystore encoding exceeds the size limit.')
    decoded = None
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except Exception:
        pass
    if not decoded or base64.b64encode(decoded).decode('ascii') != encoded:
        raise SigningError('Original keystore must be nonempty canonical Base64.')
    return PrivateConfiguration(decoded, env[VARIABLES[1]], env[VARIABLES[2]],
                                env[VARIABLES[3]], fingerprint(env[VARIABLES[4]]))


def baseline():
    value = None
    try:
        value = json.loads(BASELINE.read_text())
    except Exception:
        pass
    if value != {'package': 'at.hausgarten.service', 'version_name': '1.45.0',
                 'version_code': 64, 'certificate_variable': VARIABLES[4]}:
        raise SigningError('Non-secret operative baseline is invalid.')
    return value


def public_environment(env):
    return {name: value for name, value in env.items() if name not in VARIABLES}


def tool(command, env):
    completed = None
    try:
        completed = subprocess.run(command, env=env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   check=False, timeout=90)
    except Exception:
        pass
    if completed is None or completed.returncode != 0:
        raise SigningError('Signing tool failed or timed out; private diagnostics were suppressed.')
    return completed.stdout


def private_write(path, content):
    failed = False
    try:
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0)
        descriptor = os.open(path, flags, 0o600)
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
    except Exception:
        failed = True
    if failed:
        raise SigningError('Private signing file could not be written.')


def cleanup(directory):
    failed = False
    # Remove the private key first, even if later directory cleanup fails.
    try:
        (directory / 'original.keystore').unlink(missing_ok=True)
    except Exception:
        failed = True
    try:
        shutil.rmtree(directory)
    except Exception:
        failed = True
    if failed:
        raise SigningError(CLEANUP_ERROR)


@contextmanager
def private_workspace():
    parent = Path(tempfile.gettempdir()).resolve()
    if os.name != 'posix' or parent == ROOT or ROOT in parent.parents:
        raise SigningError('A private POSIX OS-temp directory outside the repository is required.')
    directory = None
    try:
        directory = Path(tempfile.mkdtemp(prefix='hg-original-signing-', dir=parent))
        os.chmod(directory, 0o700)
        yield directory
    finally:
        if directory is not None:
            cleanup(directory)


def structural_apk(content):
    import io
    valid = False
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            entries = archive.infolist()
            names = {entry.filename for entry in entries}
            valid = (len(entries) == len(names) and
                     {'AndroidManifest.xml', 'classes.dex', 'resources.arsc'} <= names and
                     sum(entry.file_size for entry in entries) <= 1024 * 1024 * 1024 and
                     not any(entry.flag_bits & 1 for entry in entries) and archive.testzip() is None)
    except Exception:
        pass
    if not valid:
        raise SigningError('Candidate is not a structurally readable APK archive.')


def apk_metadata(path, aapt, env, expected):
    raw = tool([aapt, 'dump', 'badging', str(path)], env).decode('utf-8', 'replace')
    matches = re.findall(r"^package: name='([^']*)' versionCode='([0-9]+)' versionName='([^']*)'[^\r\n]*$", raw, re.MULTILINE)
    if len(matches) != 1:
        raise SigningError('Candidate package metadata is invalid.')
    package, code, version = matches[0]
    if package != expected['package'] or not expected['version_code'] < int(code) <= 2100000000:
        raise SigningError('Candidate package or versionCode violates the operative baseline.')
    if not re.fullmatch(r'[0-9][0-9A-Za-z.+_-]{0,63}', version):
        raise SigningError('Candidate versionName has an invalid format.')
    return {'package': package, 'version_name': version, 'version_code': int(code)}


def verify_signed(path, apksigner, env, expected):
    output = tool([apksigner, 'verify', '--verbose', '--print-certs', str(path)], env).decode('utf-8', 'replace')
    counts = re.findall(r'^Number of signers: ([0-9]+)\s*$', output, re.MULTILINE)
    certs = re.findall(r'^Signer #([0-9]+) certificate SHA-256 digest: ([a-fA-F0-9:]+)\s*$', output, re.MULTILINE)
    if counts != ['1'] or len(certs) != 1 or certs[0][0] != '1' or fingerprint(certs[0][1]) != expected:
        raise SigningError('Signed APK does not have exactly the expected single original signer.')


def _sign(apk, output, *, keytool, aapt, zipalign, apksigner, env=None):
    env = dict(os.environ if env is None else env)
    private = configuration(env)  # Missing values fail before any tool or temp-file operation.
    ci_names = ('CI', 'GITHUB_ACTIONS', 'GITLAB_CI', 'TF_BUILD', 'BUILDKITE', 'CIRCLECI', 'JENKINS_URL')
    if any(env.get(name, '').lower() not in ('', '0', 'false') for name in ci_names):
        raise SigningError('Original signing is forbidden in CI; use the selected personal Cloud environment.')
    expected = baseline()
    output = Path(output)
    if output.exists() or output.is_symlink():
        raise SigningError('Output already exists; no existing file will be overwritten.')
    content = None
    try:
        if Path(apk).stat().st_size <= MAX_APK_BYTES:
            content = Path(apk).read_bytes()
    except Exception:
        pass
    if not content or len(content) > MAX_APK_BYTES:
        raise SigningError('Candidate APK is unavailable or exceeds the size limit.')
    structural_apk(content)
    public = public_environment(env)
    credentials = dict(public, HG_ORIGINAL_STORE_PASSWORD=private.store_password,
                       HG_ORIGINAL_KEY_PASSWORD=private.key_password)
    with private_workspace() as directory:
        candidate, aligned, signed = (directory / name for name in ('candidate.apk', 'aligned.apk', 'signed.apk'))
        private_write(candidate, content)
        before = apk_metadata(candidate, aapt, public, expected)
        store = directory / 'original.keystore'
        private_write(store, private.keystore)
        common = ['-keystore', str(store), '-alias', private.alias,
                  '-storepass:env', VARIABLES[1]]
        certificate = tool([keytool, '-exportcert', *common], credentials)
        if hashlib.sha256(certificate).hexdigest() != private.certificate:
            raise SigningError('Original keystore certificate does not match the configured fingerprint.')
        # Unlock the existing private key; discard the public CSR. Never generates a key.
        tool([keytool, '-certreq', *common, '-keypass:env', VARIABLES[3]], credentials)
        tool([zipalign, '-P', '16', '4', str(candidate), str(aligned)], public)
        if apk_metadata(aligned, aapt, public, expected) != before:
            raise SigningError('APK identity changed during alignment.')
        tool([apksigner, 'sign', '--ks', str(store), '--ks-key-alias', private.alias,
              '--ks-pass', 'env:' + VARIABLES[1], '--key-pass', 'env:' + VARIABLES[3],
              '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
              '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
              '--out', str(signed), str(aligned)], credentials)
        verify_signed(signed, apksigner, public, private.certificate)
        if apk_metadata(signed, aapt, public, expected) != before:
            raise SigningError('APK identity changed after signing.')
        tool([zipalign, '-c', '-P', '16', '4', str(signed)], public)
        signed_content = signed.read_bytes()
        structural_apk(signed_content)
        result = {**before, 'result': 'SIGNED_NO_INSTALL_OR_UPDATE_ACCEPTANCE',
                  'apk_sha256': hashlib.sha256(signed_content).hexdigest()}
        # Tool-derived metadata must never echo a private configuration value.
        metadata_text = json.dumps(result, sort_keys=True)
        if any(env[name] in metadata_text for name in VARIABLES[:4]):
            raise SigningError('Public APK metadata would echo private configuration; no output is permitted.')
        result['certificate_sha256'] = private.certificate
    # Publish only after verification AND successful cleanup of temporary signing material.
    created = False
    failed = False
    try:
        descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0), 0o600)
        created = True
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(signed_content)
            stream.flush()
            os.fsync(stream.fileno())
    except Exception:
        failed = True
    if failed:
        if created:
            try:
                output.unlink()
            except Exception:
                raise SigningError('Output cleanup failed; signing was not accepted.') from None
        raise SigningError('Verified APK could not be published to a new output file.')
    return result


def sign(apk, output, *, keytool, aapt, zipalign, apksigner, env=None):
    failure = None
    try:
        return _sign(apk, output, keytool=keytool, aapt=aapt, zipalign=zipalign,
                     apksigner=apksigner, env=env)
    except SigningError as error:
        failure = str(error)
    except Exception:
        failure = 'Signing could not complete; private diagnostics were suppressed.'
    # Outside the except block: do not retain a secret-bearing exception context.
    raise SigningError(failure)


class SafeParser(argparse.ArgumentParser):
    def error(self, message):
        raise SigningError('Invalid signing command arguments; use --help.')


def main(argv=None):
    try:
        parser = SafeParser(description=__doc__)
        for name in ('apk', 'output', 'keytool', 'aapt', 'zipalign', 'apksigner'):
            parser.add_argument('--' + name, required=True)
        result = sign(**vars(parser.parse_args(argv)))
    except SigningError as error:
        print('ORIGINAL_SIGNING_FAILED: ' + str(error), file=sys.stderr)
        return 1
    except Exception:
        print('ORIGINAL_SIGNING_FAILED: Signing could not complete; private diagnostics were suppressed.', file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
