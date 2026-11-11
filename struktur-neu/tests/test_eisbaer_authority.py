"""Synthetic checks for missing coverage and false Eisbär completion claims."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'tools/check_eisbaer.py'
MODULE = importlib.util.spec_from_file_location('check_eisbaer', SCRIPT)
gate = importlib.util.module_from_spec(MODULE)
MODULE.loader.exec_module(gate)


class EIsbaerAuthorityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.base = self.root / gate.SPEC_DIR
        self.base.mkdir(parents=True)
        self.spec = {
            'document': {'id': 'SYNTHETIC-SPEC', 'concept_name': 'Eisbär', 'version': '1.0.1'},
            'baseline': {'app_version_code': 158},
            'requirements': [{'id': 'REQ-001'}],
            'menus': [{'id': 'M01_HEUTE', 'primary_actions': [{'id': 'M01-ACT-01'}],
                       'steps': ['open'], 'step_ids': ['M01-STEP-01']}],
            'figures': [{'id': 'ILL-001'}], 'wireframes': [{'id': 'WF-001'}],
            'code_impact': [{'id': 'CODE-01'}], 'data_metadata': [{'id': 'DATA-01'}],
            'acceptance_tests': [{'id': 'TEST-01'}],
            'finance': {'workflows': [{'id': 'F01'}]},
            'migration_plan': ['synthetic migration'],
            'mcp': {'catalog': {'tools': [{'name': 'synthetic_read'}]}},
        }
        self.candidate = {'package_name': 'at.hausgarten.service', 'version_name': '2.0.0',
                          'version_code': 159, 'git_sha': 'a' * 40, 'source_sha256': 'b' * 64}
        self.write(self.base / 'spec.json', self.spec)
        (self.base / 'spec.pdf').write_bytes(b'%PDF-1.7\nsynthetic document')
        self.authority = {'schema': 'eisbaer.authority.v1', 'concept_name': 'Eisbär',
                          'document_id': 'SYNTHETIC-SPEC', 'version': '1.0.1',
                          'sole_authority': True, 'status': 'AUTHORITATIVE',
                          'document': self.reference('spec.pdf'),
                          'specification': self.reference('spec.json'),
                          'progress_file': 'UMSETZUNG.json'}
        checksum = {'document_id': 'SYNTHETIC-SPEC', 'concept_version': '1.0.1',
                    'pdf': copy.deepcopy(self.authority['document']),
                    'manifest': copy.deepcopy(self.authority['specification'])}
        self.write(self.base / 'checksums.json', checksum)
        self.authority['checksums'] = self.reference('checksums.json')
        self.progress = {'schema': 'eisbaer.implementation.v1', 'document_id': 'SYNTHETIC-SPEC',
                         'eisbaer_version': '1.0.1',
                         'specification_sha256': self.authority['specification']['sha256'],
                         'overall_status': 'NOT_STARTED', 'candidate': None,
                         'items': {key: {'status': 'NOT_STARTED', 'evidence': []}
                                   for key in gate.expected_items(self.spec)}, 'evidence': {}}
        self.write(self.root / 'AKTUELLER-STAND.json', {'current_task': {}})
        self.save()

    def write(self, path, value):
        path.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')

    def reference(self, name):
        return {'file': name, 'sha256': hashlib.sha256((self.base / name).read_bytes()).hexdigest()}

    def save(self):
        self.write(self.base / 'EISBAER.json', self.authority)
        self.write(self.base / 'UMSETZUNG.json', self.progress)

    def evidence(self, identity='REQ-001', kind='runtime', candidate=None):
        self.progress['candidate'] = self.candidate
        payload = {'kind': kind, 'result': 'PASS', 'candidate': candidate or self.candidate,
                   'executed_at_utc': '2026-11-11T04:00:00Z', 'command': 'synthetic runner',
                   'covers': [identity], 'observations': {'actual': 'synthetic readback'}}
        if kind == 'ci':
            payload.update(full_run=True, focused_flags=False, build_result='success',
                           emulator_result='success',
                           run_url='https://github.com/mihalhafner-collab/Gartenpirat-/actions/runs/1')
        if kind == 'pixel':
            payload['physical_device'] = True
        name = 'proof-' + str(len(self.progress['evidence'])) + '.json'
        self.write(self.base / name, payload)
        reference = self.reference(name)
        reference['kind'] = kind
        proof_id = 'proof-' + str(len(self.progress['evidence']))
        self.progress['evidence'][proof_id] = reference
        self.progress['items'][identity] = {'status': 'PASS', 'evidence': [proof_id]}
        self.save()
        return payload, proof_id, name

    def test_open_work_is_not_completion(self):
        result = gate.check(self.root)
        self.assertFalse(result['complete'])
        self.assertEqual(result['passed'], 0)
        with self.assertRaisesRegex(ValueError, 'completion blocked'):
            gate.check(self.root, completion=True)

    def test_removing_or_skipping_a_requirement_cannot_finish_it(self):
        del self.progress['items']['REQ-001']
        self.save()
        with self.assertRaisesRegex(ValueError, 'coverage'):
            gate.check(self.root)
        self.progress['items']['REQ-001'] = {'status': 'SKIPPED', 'evidence': []}
        self.save()
        with self.assertRaisesRegex(ValueError, 'skipped status'):
            gate.check(self.root)

    def test_changed_specification_is_not_silently_accepted(self):
        (self.base / 'spec.json').write_text('{}', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            gate.check(self.root)

    def test_completion_claims_automatically_enable_strict_mode(self):
        self.progress['overall_status'] = 'COMPLETE'
        self.save()
        with self.assertRaisesRegex(ValueError, 'completion blocked'):
            gate.check(self.root)
        self.progress['overall_status'] = 'NOT_STARTED'
        self.save()
        self.write(self.root / 'AKTUELLER-STAND.json', {'current_task': {'completed': True}})
        with self.assertRaisesRegex(ValueError, 'completion blocked'):
            gate.check(self.root)
        self.write(self.root / 'AKTUELLER-STAND.json',
                   {'current_task': {'status': 'COMPLETE', 'completed': False}})
        with self.assertRaisesRegex(ValueError, 'completion blocked'):
            gate.check(self.root)

    def test_a_pass_checkbox_needs_actual_matching_evidence(self):
        self.progress['items']['REQ-001']['status'] = 'PASS'
        self.save()
        with self.assertRaisesRegex(ValueError, 'without actual evidence'):
            gate.check(self.root)
        other = dict(self.candidate, source_sha256='c' * 64)
        self.evidence(candidate=other)
        with self.assertRaisesRegex(ValueError, 'different candidate'):
            gate.check(self.root)

    def test_escaping_evidence_and_changed_reports_are_rejected(self):
        _, proof_id, name = self.evidence()
        self.progress['evidence'][proof_id]['file'] = '../outside.json'
        self.save()
        with self.assertRaisesRegex(ValueError, 'traversal'):
            gate.check(self.root)
        self.progress['evidence'][proof_id]['file'] = name
        self.save()
        (self.base / name).write_text('{}', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            gate.check(self.root)

    def test_focused_ci_or_emulator_cannot_replace_full_pixel_acceptance(self):
        payload, proof_id, name = self.evidence('GATE:FULL_CI', 'ci')
        payload['emulator_result'] = 'skipped'
        self.write(self.base / name, payload)
        self.progress['evidence'][proof_id]['sha256'] = gate.digest(self.base / name)
        self.save()
        with self.assertRaisesRegex(ValueError, 'Partial, skipped'):
            gate.check(self.root)
        self.progress['evidence'] = {}
        self.progress['items']['GATE:FULL_CI'] = {'status': 'NOT_STARTED', 'evidence': []}
        payload, proof_id, name = self.evidence('GATE:PIXEL', 'pixel')
        payload['physical_device'] = False
        self.write(self.base / name, payload)
        self.progress['evidence'][proof_id]['sha256'] = gate.digest(self.base / name)
        self.save()
        with self.assertRaisesRegex(ValueError, 'physical Pixel'):
            gate.check(self.root)

    def test_completion_is_bound_to_the_actual_app_source(self):
        native = self.root / 'android/app/src/main'
        native.mkdir(parents=True)
        (native / 'Main.kt').write_text('synthetic native source', encoding='utf-8')
        build = self.root / 'android/app/build.gradle'
        build.write_text("versionCode 159\nversionName '2.0.0'", encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'source_sha256'):
            gate.actual_candidate(self.root, self.candidate, self.spec)

    def test_global_gate_requires_its_specific_evidence_kind(self):
        self.evidence('GATE:UI_MCP', 'pixel')
        with self.assertRaisesRegex(ValueError, 'requires mcp evidence'):
            gate.check(self.root)

    def test_complete_proofs_pass_only_for_the_same_app_and_a_full_current_ci(self):
        native = self.root / 'android/app/src/main'
        native.mkdir(parents=True)
        source_file = native / 'Main.kt'
        source_file.write_text('synthetic native source', encoding='utf-8')
        build = self.root / 'android/app/build.gradle'
        build.write_text("versionCode 159\nversionName '2.0.0'", encoding='utf-8')
        source_hash = hashlib.sha256()
        for path in [build, source_file]:
            source_hash.update(path.relative_to(self.root).as_posix().encode() + b'\0')
            source_hash.update(path.read_bytes() + b'\0')
        self.candidate['source_sha256'] = source_hash.hexdigest()
        for identity, kind in gate.expected_items(self.spec).items():
            self.evidence(identity, 'runtime' if kind == 'requirement' else kind)
        self.progress['overall_status'] = 'COMPLETE'
        self.save()
        self.assertTrue(gate.check(self.root)['complete'])
        with self.assertRaisesRegex(ValueError, 'focused or partial'):
            gate.check(self.root, ci_build_result='success', ci_emulator_result='success',
                       ci_full_run='false')
        self.assertTrue(gate.check(self.root, ci_build_result='success',
                                   ci_emulator_result='success', ci_full_run='true')['complete'])
        source_file.write_text('a new untested app source', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'source_sha256'):
            gate.check(self.root)


if __name__ == '__main__':
    unittest.main()
