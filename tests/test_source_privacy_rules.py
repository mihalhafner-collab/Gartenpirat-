import unittest
from source_privacy_rules import (anonymize, sanitize_snapshot_verifier,
                                  sanitize_device_tools, scan_cloud_metadata)


class SourcePrivacyRulesTest(unittest.TestCase):

    def test_device_export_removes_path_and_serial_and_keeps_adb_selection(self):
        prefix = 'C:/' + 'Users/' + 'SyntheticUser/'
        for assignment in ("const serial=" + "'fixture_device';",
                           "const serial=process.argv[2]||'fixture_device';"):
            source = ("'use strict';\nconst {chromium}=require('" + prefix
                      + "runtime/node_modules/playwright');\n" + assignment
                      + "\nconst args=['-s',serial];\n").encode()
            out = sanitize_device_tools(source, 'tests/pixel-start-tunnel.cjs')
            self.assertNotIn(prefix.encode(), out)
            self.assertNotIn(b'fixture_device', out)
            self.assertIn(b"require(process.env.HG_PLAYWRIGHT_MODULE || 'playwright')", out)
            self.assertIn(b"HG_TEST_DEVICE_CONFIRMED !== '1'", out)
            self.assertIn(b"const args=['-s',serial]", out)
            scan_cloud_metadata({'tests/pixel-start-tunnel.cjs': out})
            self.assertEqual(out, sanitize_device_tools(out, 'tests/pixel-start-tunnel.cjs'))

    def test_device_export_unknown_structure_fails_closed(self):
        with self.assertRaises(ValueError):
            sanitize_device_tools(b'unknown()', 'tests/pixel-start-tunnel.cjs')

    def test_metadata_scan_rejects_personal_paths_and_physical_defaults(self):
        home = ('C:/' + 'Users/' + 'SyntheticUser/runtime').encode()
        with self.assertRaises(ValueError):
            scan_cloud_metadata({'test.cjs': home})
        default = b"const serial=" + b"'fixture_device';"
        with self.assertRaises(ValueError):
            scan_cloud_metadata({'test.cjs': default})
        scan_cloud_metadata({'test.py': b"SERIAL='emulator-5554'"})

    def test_device_update_guard_precedes_any_device_work(self):
        source = b"import os\ndef main(serial):\n    raise AssertionError('device touched')\n"
        out = sanitize_device_tools(source, 'tests/pixel_update_release.py')
        namespace = {}
        exec(out, namespace)  # hardcoded synthetic program; no adb or device
        import os
        from unittest.mock import patch
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, 'explicit user consent'):
                namespace['main']('fixture-device')

    def test_snapshot_totals_removed_but_integrity_checks_preserved(self):
        # Deliberately fabricated counters, not a production import snapshot.
        source = b'''expected = {
    "customers": 7, "invoices": 11, "paid_invoices": 8,
    "open_invoices": 3, "invoice_amount_sum": 987.65,
    "duplicate_customer_source_ids": 0, "duplicate_invoice_source_ids": 0,
    "invoices_without_customer": 0,
}
checks = {key: result[key] == value for key, value in expected.items()}
'''
        sanitized = sanitize_snapshot_verifier(source)
        self.assertNotIn(b'987.65', sanitized)
        self.assertNotIn(b'"customers":', sanitized)
        for corrupt in (False, True):
            values = {'duplicate_customer_source_ids': 0,
                      'duplicate_invoice_source_ids': 0,
                      'invoices_without_customer': int(corrupt)}
            namespace = {'result': values}
            exec(sanitized, namespace)  # only the hardcoded synthetic source above
            self.assertEqual(not corrupt, all(namespace['checks'].values()))

    def test_unknown_verifier_rule_is_not_silently_removed(self):
        with self.assertRaises(ValueError):
            sanitize_snapshot_verifier(b'expected = {"unknown_rule": 42}')

    def test_email_retains_shape_and_consistent_case(self):
        original = 'qa_914b2c6ec31a@example.invalid'
        out = anonymize((original + ' ' + original.upper()).encode(), {original}, '.py').decode()
        first, second = out.split()
        self.assertEqual(first.upper(), second)
        self.assertIn('@example.invalid', first)
        self.assertNotIn(original, out.lower())

    def test_phone_stays_numeric(self):
        out = anonymize(b'+43 1234567890', {'+43 1234567890'}, '.java').decode()
        self.assertRegex(out, r'^\+\d+$')
        self.assertNotEqual('+431234567890', out)

    def test_name_duplicate_variations_remain_equal(self):
        out = anonymize(b'Test Sample|TEST   SAMPLE', {'Test Sample'}, '.java').decode().split('|')
        self.assertEqual(' '.join(out[0].lower().split()), ' '.join(out[1].lower().split()))
        self.assertIn('   ', out[1])

    def test_valid_id_expression_has_no_embedded_tunnel_id(self):
        value = b'("tunnel_" + "a5bf9a3347f9414c601003f7fc7d23a7")'
        out = anonymize(value, set(), '.py').decode()
        resolved = eval(out, {'__builtins__': {}})  # only this hardcoded test expression
        self.assertRegex(resolved, r'^tunnel_[a-f0-9]{32}$')
        self.assertNotRegex(out, r'tunnel_[a-f0-9]{32}')

    def test_iban_synthetic_checksum(self):
        out = anonymize(b'AT00 1111111111111111', {'AT00 1111111111111111'}, '.py').decode()
        rotated = out[4:] + ''.join(str(ord(c)-55) for c in out[:2]) + out[2:4]
        self.assertEqual(1, int(rotated) % 97)

    def test_no_change_to_non_private_source(self):
        code = b'customer_id = request.customer_id'
        self.assertEqual(code, anonymize(code, {'Private Customer'}, '.py'))

    def test_partial_name_ambiguity_is_retained(self):
        code = b"a='Alice Fiction'; b='Bob Fiction'; query='Fiction'"
        out = anonymize(code, {'Alice Fiction'}, '.py').decode()
        namespace = {}
        exec(out, {'__builtins__': {}}, namespace)  # only hardcoded synthetic code
        self.assertIn(namespace['query'], namespace['a'])
        self.assertIn(namespace['query'], namespace['b'])
