"""Immutable pre-P3 UAT observations, not a replacement application or contract."""
import hashlib
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]


class P3BaselineTests(unittest.TestCase):
    def setUp(self):
        self.before = json.loads((ROOT / 'tests/fixtures/p3-ux-baseline.json').read_text())

    def test_original_p1_contract_is_not_rewritten(self):
        original = ROOT / 'tests/fixtures/refactoring-baseline.json'
        self.assertEqual(hashlib.sha256(original.read_bytes()).hexdigest(),
                         self.before['p1_fixture_sha256'])

    def test_all_115_prior_tasks_and_17_findings_are_preserved(self):
        self.assertEqual([t['number'] for t in self.before['tasks']], list(range(1, 116)))
        self.assertEqual(set(self.before['findings']), {f'U{i:02}' for i in range(1, 18)})
        self.assertEqual(self.before['classification'], 'SYNTHETIC_TEST')
        self.assertEqual([t['number'] for t in self.before['tasks']
                          if t['before_result'].startswith('F')], [41, 81, 106])

    def test_before_sources_are_bound_to_the_recorded_main_commit(self):
        for path, expected in self.before['source_hashes'].items():
            with self.subTest(path=path):
                data = subprocess.check_output(
                    ['git', 'show', self.before['source_commit'] + ':' + path], cwd=ROOT)
                self.assertEqual(hashlib.sha256(data).hexdigest(), expected)


if __name__ == '__main__':
    unittest.main()
