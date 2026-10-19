"""Signing host tests with disposable keys; never access productive keys or GitHub.

Real JDK key generation, PKCS12 validation and JAR signing are exercised.
GitHub writes and Android apksigner/aapt boundary outputs are mocked explicitly.
This is NOT an Android build, APK-signing acceptance or on-device update test.
"""
import base64
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import qa_signing as signing
spec = importlib.util.spec_from_file_location("apk_privacy_guard", ROOT / "tests/apk_privacy_guard.py")
privacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(privacy)


class QaSigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("keytool") or not shutil.which("jarsigner"):
            raise RuntimeError("JDK required: signing tests were NOT run.")
        cls.fixture_temp = __import__("synthetic_fixtures").temporary_directory(prefix="qa-key-fixture-")
        cls.fixture = Path(cls.fixture_temp.name) / "synthetic-key"
        cls.contents, cls.password, cls.sha256 = signing.local_key(cls.fixture)

    @classmethod
    def tearDownClass(cls):
        cls.fixture_temp.cleanup()

    def setUp(self):
        self.temp = __import__("synthetic_fixtures").temporary_directory(prefix="qa-signing-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = {"RUNNER_TEMP": str(self.root),
                    "QA_KEYSTORE_BASE64": base64.b64encode(self.contents).decode("ascii"),
                    "QA_KEYSTORE_PASSWORD": self.password,
                    "QA_CERT_SHA256": self.sha256}

    def reject_secrets(self, **changes):
        with self.assertRaises(signing.SigningError):
            signing.validated_secrets(dict(self.env, **changes))

    def test_valid_secrets_decode(self):
        self.assertEqual(signing.validated_secrets(self.env), (self.contents, self.password, self.sha256))

    def test_each_missing_secret_fails_closed(self):
        for name in signing.SECRET_NAMES:
            with self.subTest(name=name):
                env = self.env.copy()
                del env[name]
                with self.assertRaises(signing.SigningError):
                    signing.validated_secrets(env)

    def test_blank_secret_rejected(self):
        self.reject_secrets(QA_KEYSTORE_BASE64="")

    def test_invalid_base64_rejected(self):
        self.reject_secrets(QA_KEYSTORE_BASE64="not-a-keystore!")

    def test_empty_decoded_keystore_rejected(self):
        self.reject_secrets(QA_KEYSTORE_BASE64=" \n ")

    def test_oversized_secret_rejected(self):
        self.reject_secrets(QA_KEYSTORE_BASE64="a" * (48 * 1024 + 4))

    def test_short_or_multiline_password_rejected(self):
        for value in ("android", "x" * 20 + "\n", "x" * 20 + "\x00"):
            with self.subTest(kind=len(value)):
                self.reject_secrets(QA_KEYSTORE_PASSWORD=value)

    def test_fingerprint_normalization(self):
        colon = ":".join(self.sha256[i:i+2] for i in range(0, 64, 2)).upper()
        self.assertEqual(signing.fingerprint(colon), self.sha256)

    def test_invalid_fingerprint_rejected(self):
        self.reject_secrets(QA_CERT_SHA256="missing-or-invalid")

    def test_restore_private_key_and_file_permissions(self):
        self.assertEqual(signing.restore(self.env), self.sha256)
        directory = signing.ci_directory(self.env)
        self.assertEqual((directory / signing.KEY_FILE).read_bytes(), self.contents)
        if os.name != "nt":
            self.assertEqual(directory.stat().st_mode & 0o777, 0o700)
            self.assertEqual((directory / signing.KEY_FILE).stat().st_mode & 0o777, 0o600)
        self.assertFalse((directory / signing.PASSWORD_FILE).exists())

    def test_two_runs_reuse_identical_key_and_certificate(self):
        first = signing.restore(self.env)
        original_bytes = (signing.ci_directory(self.env) / signing.KEY_FILE).read_bytes()
        signing.cleanup(self.env)
        second = signing.restore(self.env)
        self.assertEqual(first, second)
        self.assertEqual((signing.ci_directory(self.env) / signing.KEY_FILE).read_bytes(), original_bytes)

    def test_wrong_password_fails_and_removes_temp_key(self):
        with self.assertRaises(signing.SigningError):
            signing.restore(dict(self.env, QA_KEYSTORE_PASSWORD="deliberately-wrong-password"))
        self.assertFalse(signing.ci_directory(self.env).exists())

    def test_changed_certificate_fails_and_removes_temp_key(self):
        with self.assertRaises(signing.SigningError):
            signing.restore(dict(self.env, QA_CERT_SHA256="0" * 64))
        self.assertFalse(signing.ci_directory(self.env).exists())

    def test_invalid_pkcs12_fails_and_removes_temp_key(self):
        with self.assertRaises(signing.SigningError):
            signing.restore(dict(self.env, QA_KEYSTORE_BASE64=base64.b64encode(b"not-a-key").decode()))
        self.assertFalse(signing.ci_directory(self.env).exists())

    def test_no_overwrite_of_existing_runner_key(self):
        signing.restore(self.env)
        with self.assertRaises(FileExistsError):
            signing.restore(self.env)
        self.assertEqual((signing.ci_directory(self.env) / signing.KEY_FILE).read_bytes(), self.contents)

    def test_runner_path_required_and_absolute(self):
        for value in ("", "relative"):
            with self.assertRaises(signing.SigningError):
                signing.ci_directory(dict(self.env, RUNNER_TEMP=value))

    def test_private_key_in_repository_rejected(self):
        with self.assertRaises(signing.SigningError):
            signing.local_key(ROOT / ".qa-signing")

    def test_cleanup_preserves_other_files(self):
        sentinel = self.root / "productive-key-sentinel"
        sentinel.write_bytes(b"synthetic-existing-key")
        signing.restore(self.env)
        signing.cleanup(self.env)
        signing.cleanup(self.env)
        self.assertEqual(sentinel.read_bytes(), b"synthetic-existing-key")

    def test_symlinked_directory_never_followed(self):
        outside = self.root / "outside"
        outside.mkdir()
        (outside / signing.KEY_FILE).write_bytes(b"synthetic-existing-key")
        link = self.root / signing.CI_DIRECTORY
        link.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(signing.SigningError):
            signing.cleanup(self.env)
        self.assertEqual((outside / signing.KEY_FILE).read_bytes(), b"synthetic-existing-key")

    def test_no_recursive_cleanup_of_unknown_files(self):
        signing.restore(self.env)
        extra = signing.ci_directory(self.env) / "unexpected"
        extra.write_bytes(b"synthetic-do-not-delete")
        with self.assertRaises(OSError):
            signing.cleanup(self.env)
        self.assertTrue(extra.exists())

    def test_local_setup_reuses_existing_key(self):
        contents, password, digest = signing.local_key(self.fixture)
        self.assertEqual((contents, password, digest), (self.contents, self.password, self.sha256))

    def test_incomplete_backup_never_replaced(self):
        path = self.root / "incomplete"
        path.mkdir()
        (path / signing.KEY_FILE).write_bytes(b"existing-key")
        with self.assertRaises(signing.SigningError):
            signing.local_key(path)
        self.assertEqual((path / signing.KEY_FILE).read_bytes(), b"existing-key")

    def test_key_generation_refused_inside_ci(self):
        with mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}):
            with self.assertRaises(signing.SigningError):
                signing.setup(self.root / "never-created", False)
        self.assertFalse((self.root / "never-created").exists())

    def test_existing_github_secret_never_overwritten(self):
        with mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             mock.patch.object(signing.shutil, "which", return_value="available"), \
             mock.patch.object(signing, "run", return_value=b""), \
             mock.patch.object(signing, "remote_secret_names", return_value={"QA_CERT_SHA256"}), \
             mock.patch.object(signing, "local_key") as local:
            with self.assertRaises(signing.SigningError):
                signing.setup(self.root / "new-key", True)
            local.assert_not_called()

    def test_secret_upload_uses_stdin_and_fingerprint_last(self):
        with mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             mock.patch.object(signing.shutil, "which", return_value="available"), \
             mock.patch.object(signing, "remote_secret_names", side_effect=[set(), set(), set(signing.SECRET_NAMES)]), \
             mock.patch.object(signing, "local_key", return_value=(self.contents, self.password, self.sha256)), \
             mock.patch.object(signing, "run", return_value=b"") as command:
            self.assertEqual(signing.setup(self.root / "local", True), self.sha256)
        writes = [call for call in command.call_args_list if call.args[0][:3] == ["gh", "secret", "set"]]
        self.assertEqual([call.args[0][3] for call in writes], ["QA_KEYSTORE_PASSWORD", "QA_KEYSTORE_BASE64", "QA_CERT_SHA256"])
        for call in writes:
            self.assertTrue(call.kwargs["data"])
            self.assertNotIn(self.password, str(call.args))
            self.assertNotIn(self.env["QA_KEYSTORE_BASE64"], str(call.args))
            self.assertIn(signing.REPOSITORY, call.args[0])

    def test_partial_upload_never_sets_ready_fingerprint(self):
        def fail_on_blob(command, **kwargs):
            if command[:4] == ["gh", "secret", "set", "QA_KEYSTORE_BASE64"]:
                raise signing.SigningError("synthetic upload failure")
            return b""
        with mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             mock.patch.object(signing.shutil, "which", return_value="available"), \
             mock.patch.object(signing, "remote_secret_names", return_value=set()), \
             mock.patch.object(signing, "local_key", return_value=(self.contents, self.password, self.sha256)), \
             mock.patch.object(signing, "run", side_effect=fail_on_blob) as command:
            with self.assertRaises(signing.SigningError):
                signing.setup(self.root / "local", True)
        self.assertFalse(any(call.args[0][:4] == ["gh", "secret", "set", "QA_CERT_SHA256"] for call in command.call_args_list))

    def test_subprocess_diagnostics_do_not_print_secrets(self):
        failed = mock.Mock(returncode=1, stdout=self.password.encode(), stderr=self.env["QA_KEYSTORE_BASE64"].encode())
        with mock.patch.object(signing.subprocess, "run", return_value=failed):
            with self.assertRaises(signing.SigningError) as error:
                signing.run(["keytool"])
        self.assertNotIn(self.password, str(error.exception))
        self.assertNotIn(self.env["QA_KEYSTORE_BASE64"], str(error.exception))

    def test_cli_failure_contains_no_secret_values(self):
        output = io.StringIO()
        with mock.patch.dict(os.environ, dict(self.env, QA_KEYSTORE_PASSWORD="short"), clear=True), contextlib.redirect_stderr(output):
            self.assertEqual(signing.main(["check-env"]), 1)
        self.assertNotIn(self.env["QA_KEYSTORE_BASE64"], output.getvalue())
        self.assertNotIn("short", output.getvalue())

    def verify_outputs(self, certs, package=signing.QA_PACKAGE):
        with mock.patch.object(signing, "run", side_effect=[certs.encode(), ("package: name='" + package + "' versionCode='65'\n").encode()]):
            return signing.verify_apk(Path("synthetic.apk"), "mock-apksigner", "mock-aapt", self.sha256)

    def test_apk_verification_boundary_accepts_expected_qa_identity(self):
        self.assertEqual(self.verify_outputs("Signer #1 certificate SHA-256 digest: " + self.sha256 + "\n"), self.sha256)

    def test_apk_wrong_certificate_rejected(self):
        with self.assertRaises(signing.SigningError):
            self.verify_outputs("Signer #1 certificate SHA-256 digest: " + "0" * 64 + "\n")

    def test_apk_multiple_signers_rejected(self):
        with self.assertRaises(signing.SigningError):
            self.verify_outputs("Signer #1 certificate SHA-256 digest: " + self.sha256 + "\nSigner #2 certificate SHA-256 digest: " + self.sha256 + "\n")

    def test_apk_missing_signer_rejected(self):
        with self.assertRaises(signing.SigningError):
            self.verify_outputs("unexpected output\n")

    def test_apk_productive_id_rejected(self):
        with self.assertRaises(signing.SigningError):
            self.verify_outputs("Signer #1 certificate SHA-256 digest: " + self.sha256 + "\n", "at.hausgarten.service")

    def test_actual_private_key_signs_synthetic_jar(self):
        signing.restore(self.env)
        key = signing.ci_directory(self.env) / signing.KEY_FILE
        jar = self.root / "synthetic.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("test.txt", "Synthetic signing test, no customer data.")
        signing.run(["jarsigner", "-keystore", str(key), "-storetype", "PKCS12",
                     "-storepass:env", "QA_KEYSTORE_PASSWORD", "-keypass:env", "QA_KEYSTORE_PASSWORD",
                     str(jar), signing.ALIAS], env=dict(os.environ, QA_KEYSTORE_PASSWORD=self.password))
        signing.run(["jarsigner", "-verify", str(jar)])
        pem = signing.run(["keytool", "-printcert", "-jarfile", str(jar), "-rfc"]).decode("ascii")
        certificate = re.search(r"-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", pem, re.S).group(1)
        self.assertEqual(hashlib.sha256(base64.b64decode(certificate)).hexdigest(), self.sha256)

    def test_privacy_guard_rejects_signing_files_and_password_backup(self):
        for name in ("assets/private.p12", "assets/private.PFX", "assets/private.key", "assets/qa-signing-password.txt"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                privacy.scan({name: b"synthetic-only"}, set())
        privacy.scan({"assets/safe.txt": b"public content"}, set())

    def test_active_workflow_builds_only_unsigned_original_candidate(self):
        workflow = (ROOT / ".github/workflows/qa-apk.yml").read_text()
        for forbidden in ("-genkeypair", "debug.keystore", "qa_signing.py restore", "assembleDebug", "secrets.QA_"):
            self.assertNotIn(forbidden, workflow)
        self.assertIn(":app:assembleRelease", workflow)
        self.assertIn("tests/verify_app_candidate.py", workflow)
        self.assertIn("tests/apk_privacy_guard.py --apk", workflow)
        self.assertIn("outputs/apk/release/app-release-unsigned.apk", workflow)
        self.assertIn("UNSIGNED-NOT-RELEASED", workflow)
        self.assertIn("contents: read", workflow)

    def test_gradle_keeps_original_package_and_never_generates_parallel_debug_app(self):
        gradle = (ROOT / "android/app/build.gradle").read_text()
        self.assertIn("applicationId 'at.hausgarten.service'", gradle)
        self.assertNotIn("applicationIdSuffix", gradle)
        self.assertNotIn("signingConfig ", gradle)
        self.assertIn("withBuildType('debug')", gradle)
        self.assertIn("variant.enable = false", gradle)
        self.assertIn("testBuildType 'release'", gradle)
        self.assertIn("tools/sync_plant_assets.py", gradle)


if __name__ == "__main__":
    unittest.main(verbosity=2)
