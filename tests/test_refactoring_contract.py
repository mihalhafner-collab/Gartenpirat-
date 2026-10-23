"""The P1 comparison must fail on semantic changes, not bless a new baseline."""
from copy import deepcopy
import json
import unittest
from tools.check_refactoring_contract import BASELINE, differences, approved_evolution


class RefactoringContractTests(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads(BASELINE.read_text())['contract']

    def test_advertised_empty_authority_repair_accepts_only_the_single_constraint(self):
        delta = json.loads((BASELINE.parent/'sync-optimization-contract-v1.json').read_text())
        repair = json.loads((BASELINE.parent/'mcp-authority-schema-repair-v1.json').read_text())
        current = deepcopy(self.contract)
        for change in delta['changes']:
            current[change['group']][change['key']] = change['after']
        tool = next(t for t in current['tools'] if t['name'] == 'update_business_settings')
        field = tool['inputSchema']['properties']['updates']['properties']['invoice_numbering_authority']
        self.assertEqual(field['enum'], ['', 'pixel_drive'])
        self.assertEqual(field['minLength'], 1)
        field['minLength'] = 0
        self.assertEqual(approved_evolution(self.contract, current, delta), ['tools'])
        self.assertEqual(approved_evolution(self.contract, current, delta, repair), [])
        tool['annotations']['idempotentHint'] = False
        self.assertEqual(approved_evolution(self.contract, current, delta, repair), ['tools'])
        unapproved = deepcopy(repair)
        unapproved['property'] = 'expected_record_hash'
        with self.assertRaises(ValueError):
            approved_evolution(self.contract, current, delta, unapproved)

    def test_baseline_is_complete(self):
        self.assertEqual(len(self.contract['tools']), 117)
        self.assertEqual(sum(t['annotations']['readOnlyHint'] for t in self.contract['tools']), 52)
        self.assertEqual(len(self.contract['ui_actions']), 156)
        self.assertEqual(len(self.contract['javascript_bridge']), 57)
        self.assertEqual(differences(self.contract, deepcopy(self.contract)), [])

    def test_changed_schemas_rights_ui_formats_or_observations_are_not_silent(self):
        for group in self.contract:
            with self.subTest(group=group):
                changed = deepcopy(self.contract)
                changed[group] = None
                self.assertEqual(differences(self.contract, changed), [group])
        extra = deepcopy(self.contract)
        extra['unreviewed_contract'] = True
        self.assertEqual(differences(self.contract, extra), ['unreviewed_contract'])

    def test_object_order_is_irrelevant_but_ordered_collections_are_protected(self):
        same = dict(reversed(list(self.contract.items())))
        self.assertEqual(differences(self.contract, same), [])
        changed = deepcopy(self.contract)
        changed['tools'].reverse()
        self.assertEqual(differences(self.contract, changed), ['tools'])

    def test_explicit_sync_evolution_is_narrow_and_never_refreshes_baseline(self):
        delta=json.loads((BASELINE.parent/'sync-optimization-contract-v1.json').read_text())
        updated=deepcopy(self.contract)
        for c in delta['changes']:
            updated[c['group']][c['key']]=c['after']
        self.assertEqual(approved_evolution(self.contract,updated,delta),[])
        updated['tools'].reverse()
        self.assertEqual(approved_evolution(self.contract,updated,delta),['tools'])
        updated=deepcopy(self.contract)
        self.assertEqual(approved_evolution(self.contract,updated,delta),['contracts','observations'])
        bad=deepcopy(delta);bad['changes'][0]['key']='TrashRules'
        with self.assertRaises(ValueError): approved_evolution(self.contract,updated,bad)
        bad=deepcopy(delta);bad['changes'][0]['before']='unreviewed'
        with self.assertRaises(ValueError): approved_evolution(self.contract,updated,bad)
