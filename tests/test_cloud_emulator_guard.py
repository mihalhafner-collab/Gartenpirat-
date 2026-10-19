"""No adb/device invocation: test the Cloud runner's device safety boundary."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('cloud_emulator_runner', Path(__file__).with_name('run_cloud_emulator_tests.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CloudEmulatorGuardTests(unittest.TestCase):
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
        with mock.patch.object(runner, 'command', side_effect=['1', 'GartenpiratCloud\nOK', '1']) as command:
            runner.require_emulator('unused-adb', 'emulator-5580')
            self.assertEqual(command.call_count, 3)
