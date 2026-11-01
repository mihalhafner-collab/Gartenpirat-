"""Deployment policy only; no network, credentials, signing or device access."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class NetworkSecurityPolicyTests(unittest.TestCase):
    def test_operational_manifest_uses_deny_by_default_policy(self):
        manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        app = manifest.find('application')
        self.assertEqual(app.get(ANDROID+'networkSecurityConfig'), '@xml/network_security_config')
        self.assertIsNone(app.get(ANDROID+'usesCleartextTraffic'))
        policy = ET.parse(ROOT/'app/src/main/res/xml/network_security_config.xml').getroot()
        self.assertEqual(policy.find('base-config').get('cleartextTrafficPermitted'), 'false')

    def test_only_exact_local_test_hosts_can_use_cleartext(self):
        policy = ET.parse(ROOT/'app/src/main/res/xml/network_security_config.xml').getroot()
        allowed = []
        for config in policy.iter('domain-config'):
            if config.get('cleartextTrafficPermitted') == 'true':
                for domain in config.findall('domain'):
                    self.assertEqual(domain.get('includeSubdomains'), 'false')
                    allowed.append(domain.text)
        self.assertCountEqual(allowed, ['127.0.0.1', 'localhost', '10.0.2.2'])

    def test_preview_still_removes_network_permissions(self):
        manifest = ET.parse(ROOT/'app/src/preview/AndroidManifest.xml').getroot()
        permissions = {p.get(ANDROID+'name'):p.get('{http://schemas.android.com/tools}node')
                       for p in manifest.findall('uses-permission')}
        self.assertEqual(permissions.get('android.permission.INTERNET'), 'remove')
        self.assertEqual(permissions.get('android.permission.ACCESS_NETWORK_STATE'), 'remove')
