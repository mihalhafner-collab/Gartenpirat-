"""Only synthetic non-key bytes, invalid Android ZIP fixtures and mocked tools.

No real keytool, apksigner, APK, signing key, account, network or device is used.
"""
import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import traceback
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools import sign_original_apk as signing


class OriginalSigningTests(unittest.TestCase):
    def setUp(self):
        # These tests isolate signing; the source handoff guard has its own real
        # disposable-Git tests and an explicit signer rejection test.
        preservation = mock.patch('tools.source_preservation.verify_preserved_source',
                                  return_value={'status':'PASS','git_sha':'0'*40})
        preservation.start()
        self.addCleanup(preservation.stop)
        self.temp = __import__("synthetic_fixtures").temporary_directory(prefix='hg-signing-synthetic-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root/'synthetic-candidate.zip'
        # These are deliberately NOT binary Android formats or a real APK.
        with zipfile.ZipFile(self.apk, 'w') as archive:
            for name in ('AndroidManifest.xml', 'classes.dex', 'resources.arsc'):
                archive.writestr(name, b'SYNTHETIC_NOT_ANDROID_BINARY')
        self.output = self.root/'synthetic-signed.zip'
        self.fake_key = b'SYNTHETIC_PLACEHOLDER_NOT_A_KEYSTORE'
        self.fake_cert = b'SYNTHETIC_PLACEHOLDER_NOT_A_CERTIFICATE'
        self.expected = hashlib.sha256(self.fake_cert).hexdigest()
        self.env = dict(zip(signing.VARIABLES, (
            base64.b64encode(self.fake_key).decode(), 'SYNTHETIC_STORE_PASSWORD_PRIVATE',
            'synthetic-alias-private', 'SYNTHETIC_KEY_PASSWORD_PRIVATE', self.expected)))
        self.tools = dict(keytool='mock-keytool', aapt='mock-aapt', zipalign='mock-zipalign', apksigner='mock-apksigner')
        self.calls, self.directories = [], []
        self.real_rmtree = shutil.rmtree
        self.addCleanup(self.cleanup_fake_workspaces)

    def cleanup_fake_workspaces(self):
        for directory in self.directories:
            if directory.exists():
                self.real_rmtree(directory)

    def metadata(self, package='at.hausgarten.service', code=66, version='1.46.1'):
        return f"package: name='{package}' versionCode='{code}' versionName='{version}' platformBuildVersionCode='37'\n".encode()

    def verified(self, certificate=None, count=1):
        digest = certificate or self.expected
        return (f'Verifies\nNumber of signers: {count}\nSigner #1 certificate SHA-256 digest: {digest}\n').encode()

    def fake_tool(self, command, **kwargs):
        self.calls.append((command, kwargs))
        executable = command[0]
        if executable == 'mock-keytool':
            key = Path(command[command.index('-keystore')+1])
            directory = key.parent
            if directory not in self.directories:
                self.directories.append(directory)
            self.assertFalse(signing.ROOT == directory or signing.ROOT in directory.parents)
            self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(key.stat().st_mode), 0o600)
            self.assertTrue(key.read_bytes() == self.fake_key, 'Synthetic key fixture mismatch')
            stdout = self.fake_cert if command[1] == '-exportcert' else b'SYNTHETIC_CSR_NOT_REAL'
        elif executable == 'mock-aapt':
            stdout = self.metadata()
        elif executable == 'mock-zipalign':
            if '-c' not in command:
                shutil.copyfile(command[-2], command[-1])
            stdout = b''
        elif executable == 'mock-apksigner' and command[1] == 'sign':
            shutil.copyfile(command[-1], command[command.index('--out')+1])
            stdout = b''
        elif executable == 'mock-apksigner' and command[1] == 'verify':
            stdout = self.verified()
        else:
            self.fail('Unexpected tool, no real tools are allowed')
        return subprocess.CompletedProcess(command, 0, stdout, b'')

    def invoke(self, env=None, tool=None):
        with mock.patch.object(signing.subprocess, 'run', side_effect=tool or self.fake_tool):
            return signing.sign(self.apk, self.output, env=self.env if env is None else env, **self.tools)

    def test_instrumentation_is_explicit_and_restricted_to_original_runner(self):
        expected = dict(signing.baseline(), package='at.hausgarten.service.test', version_code=0, instrumentation=True)
        valid = ('E: instrumentation (line=9)\n'
                 ' A: android:name(0x01010003)="at.hausgarten.service.PixelMcpInstrumentation"\n'
                 ' A: android:targetPackage(0x01010021)="at.hausgarten.service"\n')
        for tree, accepted in [(valid, True), (valid.replace('at.hausgarten.service"', 'at.hausgarten.service.qa"'), False),
                               (valid + ' E: activity (line=20)\n', False), (valid + valid, False),
                               (valid.replace('PixelMcpInstrumentation', 'OtherRunner'), False)]:
            with self.subTest(tree=tree), mock.patch.object(signing, 'tool', side_effect=[self.metadata(package=expected['package'], code=1, version='1'), tree.encode()]):
                if accepted:
                    self.assertEqual(signing.apk_metadata(self.apk, 'mock-aapt', {}, expected)['package'], expected['package'])
                else:
                    with self.assertRaises(signing.SigningError):
                        signing.apk_metadata(self.apk, 'mock-aapt', {}, expected)
        with mock.patch.object(signing, 'tool', return_value=self.metadata(package=expected['package'], code=1, version='1')):
            with self.assertRaises(signing.SigningError):
                signing.apk_metadata(self.apk, 'mock-aapt', {}, signing.baseline())

    def assert_no_private(self, text):
        sentinels = [self.fake_key.decode(), self.fake_cert.decode(), *[self.env[name] for name in signing.VARIABLES[:4]]]
        self.assertFalse(any(value in text for value in sentinels), 'Private sentinel escaped its boundary')

    def test_ui_preview_is_explicit_and_cannot_sign_an_operative_update(self):
        expected = dict(signing.baseline(), package='at.hausgarten.service.preview', version_code=0, ui_preview=True)
        valid = self.metadata(package=expected['package'], code=88, version='1.53.1-preview1') + b"application-label:'Haus & Garten Test'\n"
        for raw, tree, accepted in [
            (valid, b'E: manifest\n', True),
            (self.metadata(), b'E: manifest\n', False),
            (valid.replace(b'Haus & Garten Test', b'Haus & Garten'), b'E: manifest\n', False),
            (valid+b"uses-permission: name='android.permission.INTERNET'\n", b'E: manifest\n', False),
            (valid, b'E: instrumentation (line=2)\n', False),
            (valid, b'A: android:sharedUserId="shared"\n', False),
        ]:
            with self.subTest(accepted=accepted, raw=raw, tree=tree), mock.patch.object(signing, 'tool', side_effect=[raw, tree]):
                if accepted:
                    self.assertEqual(signing.apk_metadata(self.apk, 'mock-aapt', {}, expected)['package'], expected['package'])
                else:
                    with self.assertRaises(signing.SigningError):
                        signing.apk_metadata(self.apk, 'mock-aapt', {}, expected)
        with mock.patch.object(signing, 'tool', return_value=valid):
            with self.assertRaises(signing.SigningError):
                signing.apk_metadata(self.apk, 'mock-aapt', {}, signing.baseline())

    def rejected(self, env=None, tool=None):
        with self.assertRaises(signing.SigningError) as caught:
            self.invoke(env=env, tool=tool)
        self.assert_no_private(''.join(traceback.format_exception(caught.exception)))
        self.assertIsNone(caught.exception.__context__)
        self.assertFalse(self.output.exists())
        self.assertTrue(all(not p.exists() for p in self.directories))
        return str(caught.exception)

    def test_every_missing_variable_fails_before_tools_or_temp_files(self):
        for name in signing.VARIABLES:
            with self.subTest(name=name):
                env = self.env.copy()
                del env[name]
                with mock.patch.object(signing.tempfile, 'mkdtemp') as create:
                    self.rejected(env=env)
                    create.assert_not_called()
                self.assertEqual(self.calls, [])

    def test_every_empty_and_whitespace_variable_is_rejected(self):
        for name in signing.VARIABLES:
            for empty in ('', '   ', '\n'):
                with self.subTest(name=name, kind='blank'):
                    self.rejected(env=dict(self.env, **{name: empty}))
        self.assertEqual(self.calls, [])

    def test_strict_base64_rejects_whitespace_noncanonical_padding_and_unicode(self):
        for encoded in ('not-base64!', 'YWJj\n', 'YWJj ', 'AB==', 'YQ===', 'é', 'YWJj===='):
            with self.subTest(kind='invalid encoding'):
                self.rejected(env=dict(self.env, HG_ORIGINAL_KEYSTORE_BASE64=encoded))
        self.assertEqual(self.calls, [])

    def test_oversized_encoding_is_rejected_before_tools(self):
        self.rejected(env=dict(self.env, HG_ORIGINAL_KEYSTORE_BASE64='A'*(8*1024*1024+4)))
        self.assertEqual(self.calls, [])

    def test_invalid_fingerprint_is_rejected(self):
        for digest in ('0'*63, 'g'*64, '00::'+'00:'*30+'00', self.expected+'x'):
            with self.subTest(kind='invalid fingerprint'):
                self.rejected(env=dict(self.env, HG_ORIGINAL_CERT_SHA256=digest))

    def test_fingerprint_normalizes_case_and_colons(self):
        digest = ':'.join(self.expected[i:i+2] for i in range(0,64,2)).upper()
        result = self.invoke(env=dict(self.env, HG_ORIGINAL_CERT_SHA256=' '+digest+' '))
        self.assertEqual(result['certificate_sha256'], self.expected)

    def test_control_characters_in_alias_and_passwords_are_rejected(self):
        for name in signing.VARIABLES[1:4]:
            for suffix in ('\n', '\r', '\x00'):
                with self.subTest(name=name, kind='control character'):
                    self.rejected(env=dict(self.env, **{name: self.env[name]+suffix}))

    def test_ci_is_forbidden_even_with_all_five_variables(self):
        for name in ('CI', 'GITHUB_ACTIONS', 'GITLAB_CI', 'TF_BUILD', 'BUILDKITE', 'CIRCLECI', 'JENKINS_URL'):
            with self.subTest(flag=name):
                self.rejected(env=dict(self.env, **{name: 'true'}))
        self.assertEqual(self.calls, [])

    def test_nonsecret_baseline_has_only_approved_fields_and_no_certificate(self):
        self.assertEqual(signing.baseline(), {'package':'at.hausgarten.service','version_name':'1.45.0',
                         'version_code':64,'certificate_variable':'HG_ORIGINAL_CERT_SHA256'})

    def test_corrupted_baseline_stops_before_any_tool(self):
        with mock.patch.object(signing, 'BASELINE', self.root/'missing-baseline.json'):
            self.rejected()
        self.assertEqual(self.calls, [])

    def test_unavailable_and_structurally_invalid_candidate_are_rejected(self):
        self.apk.unlink()
        self.rejected()
        self.apk.write_bytes(b'NOT_A_ZIP_OR_APK')
        self.rejected()
        self.assertEqual(self.calls, [])

    def test_duplicate_zip_entries_are_rejected(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.apk, 'a') as archive:
                archive.writestr('classes.dex', b'DUPLICATE_SYNTHETIC')
        self.rejected()

    def test_temp_parent_inside_repository_is_rejected_without_creating_key(self):
        with mock.patch.object(signing.tempfile, 'gettempdir', return_value=str(signing.ROOT)):
            self.rejected()
        self.assertEqual(self.calls, [])

    def test_temp_creation_failure_never_leaks_exception(self):
        with mock.patch.object(signing.tempfile, 'mkdtemp', side_effect=OSError(self.env[signing.VARIABLES[1]])):
            self.rejected()

    def test_private_file_write_failure_always_cleans_workspace(self):
        original = signing.private_write
        def write(path, content):
            if Path(path).name == 'original.keystore':
                self.directories.append(Path(path).parent)
                raise OSError(self.env[signing.VARIABLES[1]])
            original(path, content)
        with mock.patch.object(signing, 'private_write', side_effect=write):
            self.rejected()

    def test_unreadable_keystore_alias_and_password_errors_are_not_replayed(self):
        for reason,stage in (('unreadable keystore','-exportcert'),('wrong alias','-exportcert'),
                             ('wrong store password','-exportcert'),('wrong key password','-certreq')):
            with self.subTest(reason=reason):
                def failed(command, **kwargs):
                    result = self.fake_tool(command, **kwargs)
                    if command[0] == 'mock-keytool' and command[1] == stage:
                        private = (' '.join(self.env.values())).encode()
                        return subprocess.CompletedProcess(command, 1, private, private)
                    return result
                self.rejected(tool=failed)
        self.assertFalse(any(c[0][:2]==['mock-apksigner','sign'] for c in self.calls))

    def test_tool_launch_errors_and_timeouts_are_safe(self):
        for error in (OSError(self.env[signing.VARIABLES[1]]),
                      subprocess.TimeoutExpired(self.env[signing.VARIABLES[0]], 1, output=self.fake_key)):
            with self.subTest(kind=type(error).__name__):
                with mock.patch.object(signing.subprocess, 'run', side_effect=error):
                    with self.assertRaises(signing.SigningError) as caught:
                        signing.sign(self.apk,self.output,env=self.env,**self.tools)
                    self.assert_no_private(''.join(traceback.format_exception(caught.exception)))

    def test_keystore_certificate_mismatch_stops_before_private_key_use_or_signing(self):
        self.rejected(env=dict(self.env, HG_ORIGINAL_CERT_SHA256='0'*64))
        self.assertFalse(any(c[0][1] in ('-certreq','sign') for c in self.calls))

    def changed_metadata(self, *, stage='candidate.apk', **changes):
        def changed(command, **kwargs):
            result = self.fake_tool(command, **kwargs)
            if command[0]=='mock-aapt' and Path(command[-1]).name==stage:
                return subprocess.CompletedProcess(command,0,self.metadata(**changes),b'')
            return result
        return changed

    def test_candidate_package_and_nonincreasing_version_are_rejected(self):
        for changes in ({'package':'at.hausgarten.service.qa'},{'code':64},{'code':63},{'code':0},{'code':2100000001}):
            with self.subTest(changes=changes):
                self.rejected(tool=self.changed_metadata(**changes))
        self.assertFalse(any(c[0][0]=='mock-keytool' for c in self.calls))

    def test_code_65_is_strictly_above_the_operative_64_baseline(self):
        result=self.invoke(tool=lambda command,**kw: subprocess.CompletedProcess(command,0,self.metadata(code=65),b'')
                           if command[0]=='mock-aapt' else self.fake_tool(command,**kw))
        self.assertEqual(result['version_code'],65)

    def test_ambiguous_package_metadata_is_rejected(self):
        def changed(command, **kwargs):
            result=self.fake_tool(command,**kwargs)
            if command[0]=='mock-aapt':result.stdout=self.metadata()+self.metadata()
            return result
        self.rejected(tool=changed)

    def test_alignment_must_preserve_identity(self):
        self.rejected(tool=self.changed_metadata(stage='aligned.apk',code=67))
        self.assertFalse(any(c[0][:2]==['mock-apksigner','sign'] for c in self.calls))

    def test_after_signing_package_version_and_version_name_are_unchanged(self):
        for changes in ({'package':'other.package'},{'code':67},{'version':'1.46.2'}):
            with self.subTest(changes=changes):
                self.rejected(tool=self.changed_metadata(stage='signed.apk',**changes))

    def test_invalid_signature_is_rejected(self):
        def invalid(command, **kwargs):
            result=self.fake_tool(command,**kwargs)
            if command[:2]==['mock-apksigner','verify']:
                result.returncode=1;result.stderr=self.env[signing.VARIABLES[3]].encode()
            return result
        self.rejected(tool=invalid)

    def test_build_tools_37_single_v3_signer_is_verified_and_cleaned(self):
        report = (b'Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\n'
                  b'Verified using v3 scheme (APK Signature Scheme v3): true\n'
                  b'Number of signers: 1\nV3.0 Signer: certificate SHA-256 digest: '
                  + self.expected.encode() + b'\n')
        def changed(command, **kwargs):
            result = self.fake_tool(command, **kwargs)
            if command[:2] == ['mock-apksigner', 'verify']:
                result.stdout = report
            return result
        result = self.invoke(tool=changed)
        self.assertEqual(result['certificate_sha256'], self.expected)
        self.assertTrue(self.output.exists())
        self.assertTrue(all(not p.exists() for p in self.directories))

    def test_build_tools_37_foreign_extra_and_unknown_certificates_are_rejected(self):
        prefix = b'Verifies\nNumber of signers: 1\n'
        certificate = ('V3.0 Signer: certificate SHA-256 digest: ' + self.expected + '\n').encode()
        reports = [
            prefix + certificate.replace(self.expected.encode(), b'0' * 64),
            prefix + certificate + certificate,
            prefix + certificate + ('Signer #2 certificate SHA-256 digest: ' + self.expected + '\n').encode(),
            prefix + certificate + ('V3.1 Signer: certificate SHA-256 digest: ' + self.expected + '\n').encode(),
            prefix + certificate.replace(b'V3.0 Signer:', b'Unknown Signer:'),
            prefix.replace(b'signers: 1', b'signers: 2') + certificate,
        ]
        for report in reports:
            with self.subTest(kind='invalid modern signer set'):
                def changed(command, **kwargs):
                    result = self.fake_tool(command, **kwargs)
                    if command[:2] == ['mock-apksigner', 'verify']:
                        result.stdout = report
                    return result
                self.rejected(tool=changed)

    def test_different_missing_duplicate_or_multiple_final_signers_are_rejected(self):
        reports=[self.verified(certificate='0'*64),b'Verifies\n',self.verified(count=2),
                 self.verified()+f'Signer #2 certificate SHA-256 digest: {self.expected}\n'.encode(),
                 self.verified()+f'Signer #1 certificate SHA-256 digest: {self.expected}\n'.encode()]
        for report in reports:
            with self.subTest(kind='invalid signer set'):
                def changed(command, **kwargs):
                    result=self.fake_tool(command,**kwargs)
                    if command[:2]==['mock-apksigner','verify']:result.stdout=report
                    return result
                self.rejected(tool=changed)

    def test_signed_output_must_remain_structurally_readable(self):
        def broken(command, **kwargs):
            result=self.fake_tool(command,**kwargs)
            if command[:2]==['mock-apksigner','sign']:
                Path(command[command.index('--out')+1]).write_bytes(b'BROKEN_SYNTHETIC_OUTPUT')
            return result
        self.rejected(tool=broken)

    def test_success_only_reports_allowed_public_metadata_and_cleans_key_first(self):
        result=self.invoke()
        self.assertEqual(set(result),{'package','version_name','version_code','certificate_sha256','result','apk_sha256'})
        self.assert_no_private(json.dumps(result))
        self.assertEqual(result['apk_sha256'],hashlib.sha256(self.output.read_bytes()).hexdigest())
        self.assertEqual(result['certificate_sha256'],self.expected)
        self.assertEqual(result['result'],'SIGNED_NO_INSTALL_OR_UPDATE_ACCEPTANCE')
        self.assertTrue(all(not p.exists() for p in self.directories))
        self.assertEqual(stat.S_IMODE(self.output.stat().st_mode),0o600)
        for command, kwargs in self.calls:
            self.assertFalse(any(self.env[n] in ' '.join(command) for n in (signing.VARIABLES[0],signing.VARIABLES[1],signing.VARIABLES[3])))
            self.assertEqual(kwargs['stdin'],subprocess.DEVNULL)
            child=kwargs['env']
            self.assertNotIn(signing.VARIABLES[0],child)
            self.assertNotIn(signing.VARIABLES[4],child)
            if command[0] not in ('mock-keytool','mock-apksigner') or command[1]=='verify':
                self.assertFalse(any(n in child for n in signing.VARIABLES))

    def test_metadata_cannot_echo_private_values(self):
        def echoed(command,**kwargs):
            result=self.fake_tool(command,**kwargs)
            if command[0]=='mock-aapt':
                result.stdout=self.metadata(version='1.'+self.env[signing.VARIABLES[1]])
            return result
        message=self.rejected(tool=echoed)
        self.assertIn('echo private configuration',message)

    def test_cleanup_failure_deletes_key_and_prevents_output_or_secret_diagnostics(self):
        with mock.patch.object(signing.shutil,'rmtree',side_effect=OSError(self.env[signing.VARIABLES[1]])):
            with self.assertRaises(signing.SigningError) as caught:
                self.invoke()
            self.assertEqual(str(caught.exception),signing.CLEANUP_ERROR)
        self.assert_no_private(''.join(traceback.format_exception(caught.exception)))
        self.assertFalse(self.output.exists())
        self.assertTrue(all(not (p/'original.keystore').exists() for p in self.directories))

    def test_key_unlink_failure_still_attempts_directory_cleanup(self):
        with mock.patch.object(Path,'unlink',side_effect=OSError(self.env[signing.VARIABLES[3]])):
            with self.assertRaises(signing.SigningError) as caught:
                self.invoke()
            self.assertEqual(str(caught.exception),signing.CLEANUP_ERROR)
        self.assertFalse(self.output.exists())
        self.assertTrue(all(not p.exists() for p in self.directories))

    def test_cleanup_on_signing_failure(self):
        def failed(command,**kwargs):
            result=self.fake_tool(command,**kwargs)
            if command[:2]==['mock-apksigner','sign']:result.returncode=1
            return result
        self.rejected(tool=failed)

    def test_existing_file_and_symlink_are_never_overwritten(self):
        self.output.write_bytes(b'PRESERVE_EXISTING_SYNTHETIC_FILE')
        with self.assertRaises(signing.SigningError):self.invoke()
        self.assertEqual(self.output.read_bytes(),b'PRESERVE_EXISTING_SYNTHETIC_FILE')
        self.output.unlink();self.output.symlink_to(self.apk)
        with self.assertRaises(signing.SigningError):self.invoke()
        self.assertTrue(self.output.is_symlink())
        self.assertEqual(self.calls,[])

    def test_failed_final_write_removes_partial_output_after_key_cleanup(self):
        real_sync=os.fsync
        calls=0
        def sync(descriptor):
            nonlocal calls
            calls+=1
            if calls==3:raise OSError(self.env[signing.VARIABLES[1]])
            return real_sync(descriptor)
        with mock.patch.object(signing.os,'fsync',side_effect=sync):
            self.rejected()

    def test_output_race_preserves_the_other_writer(self):
        real_open=os.open
        def exclusive(path,*args,**kwargs):
            if Path(path)==self.output:
                self.output.write_bytes(b'PRESERVE_OTHER_WRITER')
                raise FileExistsError(self.env[signing.VARIABLES[1]])
            return real_open(path,*args,**kwargs)
        with mock.patch.object(signing.os,'open',side_effect=exclusive):
            with self.assertRaises(signing.SigningError) as caught:self.invoke()
        self.assert_no_private(''.join(traceback.format_exception(caught.exception)))
        self.assertEqual(self.output.read_bytes(),b'PRESERVE_OTHER_WRITER')
        self.assertTrue(all(not p.exists() for p in self.directories))

    def test_private_configuration_repr_never_contains_values(self):
        self.assert_no_private(repr(signing.configuration(self.env)))

    def test_cli_failure_stdout_stderr_and_exceptions_have_no_private_values(self):
        args=['--apk',str(self.apk),'--output',str(self.output)]
        for name,value in self.tools.items():args+=['--'+name,value]
        def failed(command,**kwargs):
            self.fake_tool(command,**kwargs)
            private=(' '.join(self.env.values())).encode()
            return subprocess.CompletedProcess(command,1,private,private)
        out,err=io.StringIO(),io.StringIO()
        with mock.patch('privacy_guard.source_gate',return_value={'status':'PASS'}),mock.patch('privacy_guard.workspace_gate',return_value={'status':'PASS'}),mock.patch.dict(os.environ,self.env,clear=True),mock.patch.object(signing.subprocess,'run',side_effect=failed),contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):
            self.assertEqual(signing.main(args),1)
        self.assertEqual(out.getvalue(),'')
        self.assert_no_private(err.getvalue())

    def test_cli_success_reports_only_public_metadata(self):
        args=['--apk',str(self.apk),'--output',str(self.output)]
        for name,value in self.tools.items():args+=['--'+name,value]
        out,err=io.StringIO(),io.StringIO()
        # Unit boundary: real gates are covered by PrivacyContractTests and release CLI.
        with mock.patch('privacy_guard.source_gate',return_value={'status':'PASS'}),mock.patch('privacy_guard.workspace_gate',return_value={'status':'PASS'}),mock.patch.dict(os.environ,self.env,clear=True),mock.patch.object(signing.subprocess,'run',side_effect=self.fake_tool),contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):
            self.assertEqual(signing.main(args),0)
        self.assertEqual(err.getvalue(),'')
        self.assert_no_private(out.getvalue())
        self.assertEqual(json.loads(out.getvalue())['version_code'],66)

    def test_unknown_cli_argument_is_not_echoed(self):
        out,err=io.StringIO(),io.StringIO()
        with contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):
            self.assertEqual(signing.main(['--unknown',self.env[signing.VARIABLES[1]]]),1)
        self.assert_no_private(out.getvalue()+err.getvalue())

    def test_cli_privacy_rejection_removes_only_own_unchanged_output(self):
        args=['--apk',str(self.apk),'--output',str(self.output)]
        for name,value in self.tools.items():args+=['--'+name,value]
        for replaced in (False,True):
            with self.subTest(replaced=replaced):
                self.output.unlink(missing_ok=True)
                def reject(*a,**kw):
                    if replaced:self.output.write_bytes(b'PRESERVE_OTHER_WRITER')
                    raise ValueError('Synthetic privacy rejection')
                out,err=io.StringIO(),io.StringIO()
                with mock.patch('privacy_guard.source_gate',return_value={'status':'PASS'}),mock.patch('privacy_guard.workspace_gate',return_value={'status':'PASS'}),mock.patch('privacy_guard.artifact_gate',side_effect=reject),mock.patch.dict(os.environ,self.env,clear=True),mock.patch.object(signing.subprocess,'run',side_effect=self.fake_tool),contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):
                    self.assertEqual(signing.main(args),1)
                if replaced:self.assertEqual(self.output.read_bytes(),b'PRESERVE_OTHER_WRITER')
                else:self.assertFalse(self.output.exists())
                self.assert_no_private(out.getvalue()+err.getvalue())


if __name__ == '__main__':
    unittest.main()
