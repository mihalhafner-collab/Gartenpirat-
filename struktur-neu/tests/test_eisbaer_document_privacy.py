"""The source-design exception must not admit invoices or runtime PDFs."""
import hashlib
from pathlib import Path
import unittest
from unittest.mock import patch
import privacy_guard


class DesignDocumentPrivacyTests(unittest.TestCase):
    def test_only_registered_source_path_and_exact_bytes_are_allowed(self):
        name = 'docs/fachregeln/eisbaer/Eisbaer-Konzept-v1.0.1.pdf'
        raw = b'%PDF-1.7\nsynthetic design document'
        policy = dict(privacy_guard.POLICY, public_design_documents={name: hashlib.sha256(raw).hexdigest()})
        with patch.object(privacy_guard, 'POLICY', policy):
            self.assertEqual([], privacy_guard.inspect_bytes(name, raw))
            for path, payload, artifact in [(name, raw+b'changed', False), ('invoice.pdf', raw, False), (name, raw, True)]:
                with self.subTest(path=path, artifact=artifact):
                    self.assertTrue(privacy_guard.inspect_bytes(path, payload, artifact=artifact))

    def test_registered_original_is_not_changed_or_packaged_as_business_data(self):
        name = 'docs/fachregeln/eisbaer/Eisbaer-Konzept-v1.0.1.pdf'
        raw = (Path(__file__).resolve().parents[1] / name).read_bytes()
        self.assertEqual([], privacy_guard.inspect_bytes(name, raw))
        self.assertTrue(privacy_guard.inspect_bytes(name, raw, artifact=True))


if __name__ == '__main__':
    unittest.main()
