"""Independent release counters and the data-preserving TEST Android migration."""
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.check_release_baseline import version_policy


class AppVersionTests(unittest.TestCase):
    def policy(self, **changes):
        values = dict(line.split('=', 1) for line in (ROOT/'app-versions.properties').read_text().splitlines()
                      if line and not line.startswith('#'))
        values.update({key: str(value) for key, value in changes.items()})
        with tempfile.TemporaryDirectory(prefix='qa_version_policy_') as folder:
            (Path(folder)/'app-versions.properties').write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
            return version_policy(folder)

    def test_test_update_cannot_advance_production(self):
        before = self.policy()
        after = self.policy(testBuildNumber=before['test_build_number']+1,
                            testAndroidVersionCode=before['test_version_code']+1, testVersionName='2.0.1')
        self.assertEqual((before['version_name'], before['version_code']), (after['version_name'], after['version_code']))

    def test_production_update_cannot_advance_test(self):
        before = self.policy()
        after = self.policy(productionPreviousVersionCode=before['version_code'],
                            productionVersionCode=before['version_code']+1, productionVersionName='1.57.4')
        for key in ('test_version_name', 'test_build_number', 'test_version_code'):
            self.assertEqual(before[key], after[key])

    def test_production_cannot_reset_to_test_start(self):
        with self.assertRaises(ValueError):
            self.policy(productionVersionCode=2)

    def test_test_android_code_cannot_downgrade_to_visible_build(self):
        with self.assertRaises(ValueError):
            self.policy(testAndroidVersionCode=self.policy()['test_build_number'])

    def test_test_version_cannot_inherit_production_version(self):
        with self.assertRaises(ValueError):
            self.policy(testVersionName=self.policy()['version_name'])
