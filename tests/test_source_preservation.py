"""Synthetic Git-only handoff scenarios: no external network, device or keys."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools import source_preservation as guard
from tools import sign_original_apk as signing
from synthetic_fixtures import temporary_directory


class SourcePreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = temporary_directory(prefix='qa_source_preservation_')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / 'qa_remote.git'
        self.repo = self.root / 'qa_checkout'
        self.repo.mkdir()
        self.command(self.root, 'init', '--bare', str(self.remote))
        self.command(self.repo, 'init', '-b', 'main')
        self.git('config', 'user.name', 'QA Source Test')
        self.git('config', 'user.email', 'qa_source@example.invalid')
        self.git('config', 'commit.gpgsign', 'false')
        url = 'https://github.com/' + guard.REPOSITORY + '.git'
        self.git('remote', 'add', 'origin', url)
        # Git uses only the disposable local bare store. The recorded repository
        # identity remains realistic; no GitHub request or credential is used.
        self.git('config', 'url.' + self.remote.as_uri() + '.insteadOf', url)
        self.source = self.repo / 'qa_source.py'
        self.source.write_text('QA_VALUE = 1\n')
        self.git('add', 'qa_source.py')
        self.git('commit', '-m', 'QA initial source')
        self.git('push', 'origin', 'main')
        self.head = self.git('rev-parse', 'HEAD')

    def command(self, root, *arguments):
        result = subprocess.run(['git', *arguments], cwd=root,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, check=True)
        return result.stdout.strip()

    def git(self, *arguments):
        return self.command(self.repo, *arguments)

    def verify(self):
        return guard.verify_preserved_source(self.repo)

    def test_pushed_clean_source_is_readable_without_changing_git_state(self):
        before = self.git('status', '--porcelain')
        result = self.verify()
        self.assertEqual(result['git_sha'], self.head)
        self.assertEqual(result['remote_refs'], ['refs/heads/main'])
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual(self.git('status', '--porcelain'), before)
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.head)

    def test_modified_staged_and_untracked_work_is_not_a_source_backup(self):
        self.source.write_text('QA_VALUE = 2\n')
        with self.assertRaises(guard.SourcePreservationError):
            self.verify()
        self.git('add', 'qa_source.py')
        with self.assertRaises(guard.SourcePreservationError):
            self.verify()
        self.git('commit', '-m', 'QA next source')
        self.git('push', 'origin', 'main')
        (self.repo / 'qa_untracked.java').write_text('// QA source\n')
        with self.assertRaises(guard.SourcePreservationError):
            self.verify()

    def test_local_commit_without_push_is_rejected(self):
        self.source.write_text('QA_VALUE = 2\n')
        self.git('commit', '-am', 'QA unpushed work')
        with self.assertRaisesRegex(guard.SourcePreservationError, 'not confirmed on GitHub'):
            self.verify()

    def test_foreign_repository_is_rejected_before_remote_access(self):
        self.git('config', 'remote.origin.url', 'https://example.invalid/qa_repo.git')
        with self.assertRaisesRegex(guard.SourcePreservationError, 'Expected private GitHub'):
            self.verify()

    def test_unavailable_remote_fails_without_printing_transport_diagnostics(self):
        self.remote.rename(self.root / 'qa_unavailable.git')
        with self.assertRaises(guard.SourcePreservationError) as caught:
            self.verify()
        self.assertEqual(str(caught.exception), 'SOURCE_NOT_PRESERVED: Git verification failed.')
        self.assertNotIn(str(self.root), str(caught.exception))

    def test_published_annotated_tag_preserves_a_previous_exact_source_commit(self):
        self.git('tag', '-a', 'qa_preserved', '-m', 'QA source snapshot')
        self.git('push', 'origin', 'refs/tags/qa_preserved')
        self.source.write_text('QA_VALUE = 2\n')
        self.git('commit', '-am', 'QA later work')
        self.git('push', 'origin', 'main')
        self.git('checkout', '--detach', self.head)
        result = self.verify()
        self.assertEqual(result['git_sha'], self.head)
        self.assertIn('refs/tags/qa_preserved^{}', result['remote_refs'])

    def test_workspace_change_during_remote_read_is_rejected(self):
        original = guard.git
        def changed(root, *arguments):
            result = original(root, *arguments)
            if arguments[0] == 'ls-remote':
                self.source.write_text('QA_VALUE = 3\n')
            return result
        with mock.patch.object(guard, 'git', side_effect=changed):
            with self.assertRaisesRegex(guard.SourcePreservationError, 'changed during verification'):
                self.verify()

    def test_ignored_android_input_cannot_escape_the_checkpoint(self):
        (self.repo / '.gitignore').write_text('qa_hidden.java\n')
        self.git('add', '.gitignore')
        self.git('commit', '-m', 'QA ignore rule')
        self.git('push', 'origin', 'main')
        path = self.repo / 'app/src/main/java/qa_hidden.java'
        path.parent.mkdir(parents=True)
        path.write_text('// QA ignored build input\n')
        self.assertEqual(self.git('status', '--porcelain'), '')
        with self.assertRaisesRegex(guard.SourcePreservationError, 'Unversioned Android'):
            self.verify()

    def test_git_children_do_not_receive_original_signing_variables(self):
        original = subprocess.run
        seen = []
        def run(*arguments, **kwargs):
            seen.append(kwargs['env'])
            return original(*arguments, **kwargs)
        with mock.patch.dict(os.environ, {'HG_ORIGINAL_QA_SENTINEL': 'SYNTHETIC_NOT_A_KEY'}):
            with mock.patch.object(guard.subprocess, 'run', side_effect=run):
                self.verify()
        self.assertTrue(seen)
        self.assertTrue(all(not any(key.startswith('HG_ORIGINAL_') for key in env) for env in seen))

    def test_official_signer_stops_before_key_access_or_signing(self):
        args = ['--apk', 'qa_input.zip', '--output', 'qa_output.zip', '--keytool', 'qa_keytool',
                '--aapt', 'qa_aapt', '--zipalign', 'qa_zipalign', '--apksigner', 'qa_apksigner']
        out = io.StringIO()
        err = io.StringIO()
        with mock.patch('tools.source_preservation.verify_preserved_source',
                        side_effect=guard.SourcePreservationError('SOURCE_NOT_PRESERVED: QA blocked.')):
            with mock.patch.object(signing, 'sign') as sign, mock.patch.object(signing, 'configuration') as config:
                with mock.patch.dict(os.environ, {}, clear=True), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    self.assertEqual(signing.main(args), 1)
        sign.assert_not_called()
        config.assert_not_called()
        self.assertIn('SOURCE_NOT_PRESERVED', err.getvalue())
        self.assertEqual(out.getvalue(), '')

    def test_export_reads_the_preserved_commit_even_if_workspace_changes(self):
        spec = importlib.util.spec_from_file_location('qa_source_packager', ROOT / 'tests/package_source_copy.py')
        packager = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(packager)
        target = self.root / 'qa_source.zip'
        def change_after_checkpoint():
            self.source.unlink()
            (self.repo / 'qa_untracked.py').write_text('QA_UNTRACKED = True\n')
        with mock.patch.object(packager, 'ROOT', self.repo):
            with mock.patch.object(packager, 'source_gate', side_effect=change_after_checkpoint), mock.patch.object(packager, 'workspace_gate'):
                with mock.patch('tools.drive_source_transfer.artifact_gate', return_value={'status': 'PASS'}):
                    with mock.patch.object(sys, 'argv', ['package_source_copy.py', '--target', 'perplexity', '--output', str(target)]):
                        output = io.StringIO()
                        with contextlib.redirect_stdout(output):
                            packager.main()
        with zipfile.ZipFile(target) as archive:
            self.assertEqual(archive.namelist(), ['qa_source.py'])
            self.assertEqual(archive.read('qa_source.py'), b'QA_VALUE = 1\n')
            self.assertEqual((archive.getinfo('qa_source.py').external_attr >> 16) & 0o777, 0o644)
        self.assertEqual(json.loads(output.getvalue())['source_preservation']['git_sha'], self.head)

    def test_preserved_export_can_reuse_identical_archive_and_executable_mode(self):
        self.source.chmod(0o755)
        self.git('add', 'qa_source.py')
        self.git('commit', '-m', 'QA executable source')
        self.git('push', 'origin', 'main')
        spec = importlib.util.spec_from_file_location('qa_source_packager', ROOT / 'tests/package_source_copy.py')
        packager = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(packager)
        target = self.root / 'qa_source.zip'
        results = []
        with mock.patch.object(packager, 'ROOT', self.repo):
            with mock.patch.object(packager, 'source_gate'), mock.patch.object(packager, 'workspace_gate'):
                for extra in ([], ['--reuse-existing']):
                    with mock.patch.object(sys, 'argv', ['package_source_copy.py', '--output', str(target), *extra]):
                        output = io.StringIO()
                        with contextlib.redirect_stdout(output):
                            packager.main()
                        results.append(json.loads(output.getvalue()))
        self.assertFalse(results[0]['reused'])
        self.assertTrue(results[1]['reused'])
        self.assertEqual(results[0]['artifact']['sha256'], results[1]['artifact']['sha256'])
        self.assertEqual(results[1]['source_preservation']['git_sha'], self.git('rev-parse', 'HEAD'))
        with zipfile.ZipFile(target) as archive:
            self.assertEqual((archive.getinfo('qa_source.py').external_attr >> 16) & 0o777, 0o755)

    def test_export_rejects_a_committed_symlink_without_reading_its_target(self):
        outside = self.root / 'qa_outside.py'
        outside.write_text('QA_OUTSIDE = True\n')
        (self.repo / 'qa_link.py').symlink_to(outside)
        self.git('add', 'qa_link.py')
        self.git('commit', '-m', 'QA linked source')
        self.git('push', 'origin', 'main')
        spec = importlib.util.spec_from_file_location('qa_source_packager', ROOT / 'tests/package_source_copy.py')
        packager = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(packager)
        target = self.root / 'qa_source.zip'
        with mock.patch.object(packager, 'ROOT', self.repo):
            with mock.patch.object(packager, 'source_gate'), mock.patch.object(packager, 'workspace_gate'):
                with mock.patch.object(sys, 'argv', ['package_source_copy.py', '--output', str(target)]):
                    with self.assertRaisesRegex(ValueError, 'regular source files'):
                        packager.main()
        self.assertFalse(target.exists())


if __name__ == '__main__':
    unittest.main()
