"""The P1 comparison must fail on semantic changes, not bless a new baseline."""
from copy import deepcopy
import json
import unittest
from tools.check_refactoring_contract import BASELINE, differences


class RefactoringContractTests(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads(BASELINE.read_text())['contract']

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
