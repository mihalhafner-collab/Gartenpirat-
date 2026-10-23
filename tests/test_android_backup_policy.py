"""Prevent partial OS restores from reviving credentials/old write authority.

This policy gate is not an OEM device-transfer execution proof.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
DOMAINS = {'root', 'file', 'database', 'sharedpref', 'external',
           'device_root', 'device_file', 'device_database', 'device_sharedpref'}


class AndroidBackupPolicyTests(unittest.TestCase):
    def test_os_backup_and_legacy_full_backup_remain_disabled(self):
        application = ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot().find('application')
        self.assertEqual(application.get(ANDROID+'allowBackup'), 'false')
        self.assertEqual(application.get(ANDROID+'fullBackupContent'), 'false')
        self.assertEqual(application.get(ANDROID+'dataExtractionRules'), '@xml/data_extraction_rules')

    def test_cloud_and_device_transfer_exclude_all_private_and_external_domains(self):
        rules = ET.parse(ROOT/'android/app/src/main/res/xml/data_extraction_rules.xml').getroot()
        self.assertEqual(rules.tag, 'data-extraction-rules')
        self.assertEqual({item.tag for item in rules}, {'cloud-backup', 'device-transfer'})
        for transport in rules:
            with self.subTest(transport=transport.tag):
                self.assertEqual(len(transport), len(DOMAINS))
                self.assertEqual({item.get('domain') for item in transport}, DOMAINS)
                self.assertTrue(all(item.tag=='exclude' and item.get('path')=='.' for item in transport))


if __name__ == '__main__':
    unittest.main()
