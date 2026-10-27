"""No adb/device invocation: test the Cloud runner's device safety boundary."""
import importlib.util
from pathlib import Path
import unittest
import tempfile
import contextlib
import io
from types import SimpleNamespace
from unittest import mock

spec = importlib.util.spec_from_file_location('cloud_emulator_runner', Path(__file__).with_name('run_cloud_emulator_tests.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CloudEmulatorGuardTests(unittest.TestCase):
    def test_partial_passes_and_process_crashes_are_never_success(self):
        for output in ['PASS first probe\n', 'PASS first probe\nINSTRUMENTATION_RESULT: shortMsg=Process crashed.',
                       'RESULT 0 Android SQLite/MCP tests passed; acceptance failures=0;',
                       'RESULT 100 Android SQLite/MCP tests passed; acceptance failures=1;']:
            self.assertFalse(runner.successful_output(output, 'acceptance'))

    def test_complete_acceptance_and_lifecycle_require_their_own_summary(self):
        self.assertTrue(runner.successful_output('RESULT 103 Android SQLite/MCP tests passed; acceptance failures=0;', 'acceptance'))
        self.assertTrue(runner.successful_output('PASS native WebView boot, compatible JavaScript', 'ui'))
        self.assertFalse(runner.successful_output('PASS first probe', 'ui'))
        self.assertFalse(runner.successful_output('PASS native WebView boot', 'verify'))

    def test_accumulated_suite_budget_stays_bounded_and_never_bypasses_result_checks(self):
        self.assertEqual(runner.instrumentation_budget('acceptance'), 1800)
        self.assertEqual(runner.instrumentation_budget('revier_ui'), 900)
        self.assertEqual(runner.instrumentation_budget('acceptance', 3600), 3600)
        for invalid in [0, -1, 3601, float('inf'), 1800.0, True]:
            with self.subTest(invalid=invalid), mock.patch.object(runner, 'require_emulator') as device:
                with self.assertRaises(ValueError):
                    runner.run(SimpleNamespace(mode='acceptance', timeout_seconds=invalid))
                device.assert_not_called()
        self.assertFalse(runner.successful_output('PASS first probe', 'acceptance'))
        self.assertFalse(runner.successful_output('RESULT 200 Android SQLite/MCP tests passed; acceptance failures=1;', 'acceptance'))

    def test_same_emulator_cannot_run_concurrent_installs_or_tests(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            folder = Path(directory)
            with runner.emulator_lock('emulator-5580', folder):
                with self.assertRaises(ValueError):
                    with runner.emulator_lock('emulator-5580', folder):
                        self.fail('Concurrent emulator operation accepted')
            with runner.emulator_lock('emulator-5580', folder):
                pass

    def test_physical_serials_and_network_targets_never_invoke_adb(self):
        for serial in ['', 'Pixel', '0123456789ABCDEF', '192.0.2.1:5555', 'emulator-5580;id']:
            with self.subTest(serial=serial), mock.patch.object(runner.subprocess, 'run') as process:
                with self.assertRaises(ValueError):
                    runner.require_emulator('unused-adb', serial)
                process.assert_not_called()

    def test_emulator_alias_requires_qemu_and_exact_dedicated_avd(self):
        for outputs in [['0'], ['1', 'AnotherAVD\nOK'], ['1', 'GartenpiratCloud\nOK', '']]:
            with self.subTest(outputs=outputs), mock.patch.object(runner, 'command', side_effect=outputs):
                with self.assertRaises(ValueError):
                    runner.require_emulator('unused-adb', 'emulator-5580')

    def test_ready_dedicated_emulator_is_accepted(self):
        with mock.patch.object(runner, 'command', side_effect=[
                '1', 'GartenpiratCloud\nOK', '1', 'Service package: found',
                'Service activity: found', 'package:/system/framework/framework-res.apk']) as command:
            runner.require_emulator('unused-adb', 'emulator-5580')
            self.assertEqual(command.call_count, 6)

    def test_stale_boot_flag_never_allows_installation_or_instrumentation(self):
        ready = ['1', 'GartenpiratCloud\nOK', '1']
        failures = [ready + ['Service package: not found'],
                    ready + ['Service package: found', 'Service activity: not found'],
                    ready + ['Service package: found', 'Service activity: found', ''],
                    ready + ['Service package: found', 'Service activity: found', 'package:'],
                    ready + ['Service package: found', 'Service activity: found', 'qa_unexpected_output']]
        args = SimpleNamespace(adb='unused-adb', serial='emulator-5580',
                               mode='acceptance', apk=None, instrumentation_apk=None)
        for outputs in failures:
            with self.subTest(outputs=outputs), mock.patch.object(runner, 'command', side_effect=outputs) as command, \
                    mock.patch.object(runner.subprocess, 'run') as native:
                with self.assertRaisesRegex(ValueError, 'Android'):
                    runner.run(args)
                native.assert_not_called()
                self.assertFalse(any('install' in call.args[2] or 'instrument' in call.args[2]
                                     for call in command.call_args_list))

    def test_package_service_command_failure_stays_fail_closed(self):
        failure = runner.subprocess.CalledProcessError(1, ['qa_adb'])
        with mock.patch.object(runner, 'command', side_effect=[
                '1', 'GartenpiratCloud\nOK', '1', 'Service package: found',
                'Service activity: found', failure]):
            with self.assertRaises(runner.subprocess.CalledProcessError):
                runner.require_emulator('unused-adb', 'emulator-5580')

    def test_framework_death_after_readiness_is_reported_without_raw_device_output(self):
        for field in ('output', 'stderr'):
            error = runner.subprocess.CalledProcessError(255, ['qa_adb'],
                **{field: "cmd: Can't find service: package\nqa_private_diagnostic"})
            with self.subTest(field=field), mock.patch.object(runner.subprocess, 'run', side_effect=error):
                with self.assertRaisesRegex(ValueError, 'Android core service disappeared') as failure:
                    runner.command('qa_adb', 'emulator-5580', ['install', '-r', 'qa.apk'])
                self.assertNotIn('qa_private_diagnostic', str(failure.exception))

    def test_other_adb_failures_are_not_misclassified_as_framework_death(self):
        error = runner.subprocess.CalledProcessError(255, ['qa_adb'], stderr='qa_unrelated_failure')
        with mock.patch.object(runner.subprocess, 'run', side_effect=error):
            with self.assertRaises(runner.subprocess.CalledProcessError):
                runner.command('qa_adb', 'emulator-5580', ['shell', 'getprop', 'ro.kernel.qemu'])

    def test_process_recovery_kills_only_between_confirmed_phases_and_never_clears_data(self):
        args = SimpleNamespace(adb='unused-adb', serial='emulator-5580')
        operations = []
        def invoke(adb, serial, command, **kwargs):
            self.assertEqual(serial, args.serial)
            operations.append(command)
            if command[2] == 'instrument':
                mode = command[6]
                phase, fault = mode.removeprefix('process_').split('_', 1)
                return 'PASS actual process checkpoint ' + ('prepared ' if phase == 'prepare' else 'verified ') + fault
            self.assertEqual(command, ['shell', 'am', 'force-stop', 'at.hausgarten.service'])
            return ''
        with __import__('synthetic_fixtures').temporary_directory() as directory:
            with mock.patch.object(runner, 'ROOT', Path(directory)), mock.patch.object(runner, 'require_emulator') as guard, mock.patch.object(runner, 'command', side_effect=invoke):
                with contextlib.redirect_stdout(io.StringIO()):
                    runner.process_recovery(args)
            self.assertEqual(guard.call_count, 15)
        self.assertEqual([operation[2] for operation in operations], ['instrument', 'force-stop', 'instrument'] * 5)

    def test_failed_process_preparation_never_force_stops_or_runs_verification(self):
        args = SimpleNamespace(adb='unused-adb', serial='emulator-5580')
        with __import__('synthetic_fixtures').temporary_directory() as directory:
            with mock.patch.object(runner, 'ROOT', Path(directory)), mock.patch.object(runner, 'require_emulator'), mock.patch.object(runner, 'command', return_value='FAIL preparation') as command:
                with self.assertRaises(RuntimeError):
                    runner.process_recovery(args)
                self.assertEqual(command.call_count, 1)
                self.assertIn('instrument', command.call_args.args[2])
