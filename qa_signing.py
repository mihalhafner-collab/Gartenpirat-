#!/usr/bin/env python3
"""Stable QA signing only; never reads the productive Android debug/release key.

CI: check-env -> restore -> Gradle -PqaPersistentSigning -> verify-apk -> cleanup.
Owner workstation: setup --publish (Python 3.9+, JDK 17, authenticated GitHub CLI).
Secrets are passed via environment/stdin, not command arguments or printed output.
"""
import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
from typing import Mapping, Optional

ROOT = Path(__file__).resolve().parent
REPOSITORY = "mihalhafner-collab/Gartenpirat-"
ALIAS = "gartenpirat-qa"
KEY_FILE = "qa-signing.p12"
PASSWORD_FILE = "qa-signing-password.txt"
FINGERPRINT_FILE = "certificate-sha256.txt"
SECRET_NAMES = ("QA_KEYSTORE_BASE64", "QA_KEYSTORE_PASSWORD", "QA_CERT_SHA256")
QA_PACKAGE = "at.hausgarten.service.qa"
CI_DIRECTORY = "gartenpirat-qa-signing"


class SigningError(Exception):
    """Safe, predetermined diagnosis; do not include subprocess output or secrets."""


def run(command: list, *, env=None, data=None) -> bytes:
    try:
        result = subprocess.run(command, input=data, env=env, capture_output=True,
                                check=False, timeout=45)
    except (OSError, subprocess.TimeoutExpired):
        raise SigningError("Signierwerkzeug nicht ausfuehrbar oder Zeitlimit erreicht.") from None
    if result.returncode:
        raise SigningError("Signierwerkzeug fehlgeschlagen. Werkzeug, Anmeldung, Schluessel und Passwort pruefen.")
    return result.stdout


def fingerprint(value: str) -> str:
    value = value.strip().replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise SigningError("QA_CERT_SHA256 fehlt oder ist kein SHA-256-Zertifikatsfingerabdruck.")
    return value


def validated_secrets(env: Mapping[str, str]) -> tuple:
    if any(not env.get(name) for name in SECRET_NAMES):
        raise SigningError("QA-Signierung nicht eingerichtet: drei Repository-Secrets fehlen oder sind leer. QA-SIGNIERUNG.md beachten.")
    password = env["QA_KEYSTORE_PASSWORD"]
    if len(password) < 16 or any(c in password for c in "\r\n\x00"):
        raise SigningError("QA-Schluesselpasswort muss mindestens 16 Zeichen ohne Zeilenumbrueche enthalten.")
    encoded = "".join(env["QA_KEYSTORE_BASE64"].split())
    if len(encoded) > 48 * 1024:
        raise SigningError("QA-Keystore ist fuer ein Repository-Secret zu gross.")
    try:
        contents = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise SigningError("QA_KEYSTORE_BASE64 ist ungueltig.") from None
    if not contents:
        raise SigningError("QA-Keystore ist leer.")
    return contents, password, fingerprint(env["QA_CERT_SHA256"])


def private_write(path: Path, data: bytes) -> None:
    # O_EXCL also prevents following an existing symlink. Never replace a key.
    fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as output:
        output.write(data)


def assert_external(directory: Path) -> Path:
    if not directory.is_absolute() or directory.is_symlink():
        raise SigningError("Signierverzeichnis muss absolut und darf kein symbolischer Verweis sein.")
    resolved = directory.resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise SigningError("Private Signierdateien duerfen nicht im Repository gespeichert werden.")
    return directory


def key_fingerprint(path: Path, password: str) -> str:
    env = dict(os.environ, QA_KEYSTORE_PASSWORD=password)
    common = ["-keystore", str(path), "-storetype", "PKCS12", "-alias", ALIAS,
              "-storepass:env", "QA_KEYSTORE_PASSWORD"]
    certificate = run(["keytool", "-exportcert", *common], env=env)
    # Export alone only proves the certificate is present. Certreq also proves
    # that the private key can actually be unlocked; the public CSR is discarded.
    run(["keytool", "-certreq", *common, "-keypass:env", "QA_KEYSTORE_PASSWORD"], env=env)
    return hashlib.sha256(certificate).hexdigest()


def ci_directory(env: Mapping[str, str]) -> Path:
    temporary = env.get("RUNNER_TEMP", "")
    if not temporary or not Path(temporary).is_absolute():
        raise SigningError("RUNNER_TEMP fehlt oder ist kein absoluter Runner-Pfad.")
    return assert_external(Path(temporary) / CI_DIRECTORY)


def cleanup(env: Mapping[str, str]) -> None:
    directory = ci_directory(env)
    if not directory.exists():
        return
    # Deliberately no recursive delete and no access to ~/.android/debug.keystore.
    target = directory / KEY_FILE
    if target.is_symlink() or (target.exists() and not target.is_file()):
        raise SigningError("Unerwarteter Dateityp im QA-Zwischenspeicher; keine automatische Loeschung.")
    if target.exists():
        target.unlink()
    directory.rmdir()


def restore(env: Mapping[str, str]) -> str:
    contents, password, expected = validated_secrets(env)
    directory = ci_directory(env)
    directory.mkdir(mode=0o700, parents=False, exist_ok=False)
    try:
        target = directory / KEY_FILE
        private_write(target, contents)
        actual = key_fingerprint(target, password)
        if actual != expected:
            raise SigningError("QA-Zertifikat stimmt nicht mit QA_CERT_SHA256 ueberein. Kein Build mit gewechseltem Schluessel.")
        return actual
    except Exception:
        # Only clean the directory created by this call, not a previous signing setup.
        cleanup(env)
        raise


def verify_apk(apk: Path, apksigner: str, aapt: str, expected: str) -> str:
    expected = fingerprint(expected)
    signed = run([apksigner, "verify", "--verbose", "--print-certs", str(apk)]).decode("utf-8", "replace")
    certs = re.findall(r"^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$", signed, re.MULTILINE)
    if len(certs) != 1 or fingerprint(certs[0]) != expected:
        raise SigningError("Fertige APK hat nicht das erwartete einzelne QA-Signaturzertifikat.")
    package = run([aapt, "dump", "badging", str(apk)]).decode("utf-8", "replace")
    match = re.search(r"^package: name='([^']+)'", package, re.MULTILINE)
    if not match or match.group(1) != QA_PACKAGE:
        raise SigningError("APK ist nicht die getrennte QA-App. Keine Bereitstellung.")
    return expected


def local_key(directory: Path, *, allow_create: bool = True) -> tuple:
    directory = assert_external(directory)
    key, password_path, cert = (directory / name for name in (KEY_FILE, PASSWORD_FILE, FINGERPRINT_FILE))
    if any(path.exists() or path.is_symlink() for path in (key, password_path, cert)):
        if any(path.is_symlink() or not path.is_file() for path in (key, password_path, cert)):
            raise SigningError("Lokales Schluessel-Backup ist unvollstaendig. Nicht neu erzeugen oder ueberschreiben.")
        password = password_path.read_text(encoding="utf-8").strip()
        actual = key_fingerprint(key, password)
        if actual != fingerprint(cert.read_text(encoding="ascii")):
            raise SigningError("Lokales Schluessel-Backup und gespeicherter Fingerabdruck passen nicht zusammen.")
        return key.read_bytes(), password, actual
    if not allow_create:
        raise SigningError("Vorhandenes lokales QA-Schluessel-Backup erforderlich.")
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    # A failed first setup deliberately leaves its password file for inspection;
    # a subsequent call refuses to silently replace an incomplete key set.
    password = secrets.token_urlsafe(36)
    private_write(password_path, password.encode("utf-8"))
    old_umask = os.umask(0o077)
    try:
        run(["keytool", "-genkeypair", "-noprompt", "-keystore", str(key),
             "-storetype", "PKCS12", "-alias", ALIAS, "-keyalg", "RSA",
             "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "10000",
             "-dname", "CN=Gartenpirat QA,O=Gartenpirat,C=AT",
             "-storepass:env", "QA_KEYSTORE_PASSWORD", "-keypass:env", "QA_KEYSTORE_PASSWORD"],
            env=dict(os.environ, QA_KEYSTORE_PASSWORD=password))
    finally:
        os.umask(old_umask)
    key.chmod(0o600)
    actual = key_fingerprint(key, password)
    private_write(cert, (actual + "\n").encode("ascii"))
    return key.read_bytes(), password, actual


def remote_secret_names() -> set:
    # Values are never read (GitHub secrets are write-only through this interface).
    data = run(["gh", "secret", "list", "--repo", REPOSITORY, "--json", "name"])
    try:
        return {row["name"] for row in json.loads(data)}
    except (KeyError, TypeError, ValueError):
        raise SigningError("GitHub-Secretliste konnte nicht sicher ausgewertet werden.") from None


def setup(directory: Path, publish: bool) -> str:
    if os.environ.get("GITHUB_ACTIONS") == "true":
        raise SigningError("Einmalige Schluesselerzeugung ist nur lokal erlaubt, niemals im QA-Build.")
    if not shutil.which("keytool"):
        raise SigningError("JDK 17 / keytool fehlt. QA-SIGNIERUNG.md beachten.")
    if publish:
        if not shutil.which("gh"):
            raise SigningError("GitHub CLI fehlt. Zuerst gh installieren und gh auth login ausfuehren.")
        run(["gh", "auth", "status", "--hostname", "github.com"])
        if remote_secret_names().intersection(SECRET_NAMES):
            raise SigningError("QA-Secrets existieren bereits. Keine automatische Ueberschreibung oder Schluesselrotation; QA-SIGNIERUNG.md beachten.")
    contents, password, actual = local_key(directory)
    if publish:
        # A second check catches intervening setup; initialization must not run concurrently.
        if remote_secret_names().intersection(SECRET_NAMES):
            raise SigningError("QA-Secrets wurden zwischenzeitlich angelegt. Upload gestoppt.")
        values = {"QA_KEYSTORE_PASSWORD": password,
                  "QA_KEYSTORE_BASE64": base64.b64encode(contents).decode("ascii"),
                  "QA_CERT_SHA256": actual}
        for name, value in values.items():
            # Fingerprint is uploaded last: partial initialization fails closed in CI.
            # gh encrypts the stdin value for GitHub; no secrets appear in argv or stdout.
            run(["gh", "secret", "set", name, "--repo", REPOSITORY], data=value.encode("utf-8"))
        if not set(SECRET_NAMES).issubset(remote_secret_names()):
            raise SigningError("GitHub-Einrichtung unvollstaendig. Lokales Backup erhalten; QA-SIGNIERUNG.md beachten.")
    return actual


def main(argv: Optional[list] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("check-env", "restore", "cleanup"):
        commands.add_parser(command)
    create = commands.add_parser("setup")
    create.add_argument("--directory", type=Path, default=Path.home() / ".gartenpirat-signing" / "qa")
    create.add_argument("--publish", action="store_true", help="Einmalig als Secrets im bestehenden Repository hinterlegen.")
    verify = commands.add_parser("verify-apk")
    verify.add_argument("--apk", type=Path, required=True)
    verify.add_argument("--apksigner", required=True)
    verify.add_argument("--aapt", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "check-env":
            validated_secrets(os.environ)
            print("QA-Secrets vorhanden und formal gueltig; Schluesselpruefung folgt vor dem Build.")
        elif args.command == "restore":
            restore(os.environ)
            print("Fester QA-Schluessel und Zertifikatsfingerabdruck verifiziert.")
        elif args.command == "cleanup":
            cleanup(os.environ)
            print("Temporaeres QA-Signiermaterial entfernt.")
        elif args.command == "setup":
            actual = setup(args.directory, args.publish)
            print("QA-Schluessel lokal gesichert" + (" und Repository-Secrets hinterlegt." if args.publish else "."))
            print("Oeffentlicher Zertifikatsfingerabdruck (SHA-256): " + actual)
            print("Schluesselverzeichnis getrennt und verschluesselt sichern. Niemals ins Repository oder in einen Chat kopieren.")
        else:
            verify_apk(args.apk, args.apksigner, args.aapt, os.environ.get("QA_CERT_SHA256", ""))
            print("APK-Signatur und getrennte QA-Application-ID verifiziert.")
        return 0
    except SigningError as error:
        print("QA_SIGNING_FAILED: " + str(error), file=sys.stderr)
    except (OSError, ValueError):
        print("QA_SIGNING_FAILED: Signierdateien nicht verfuegbar oder ungueltig; keine automatische Neuerzeugung.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
