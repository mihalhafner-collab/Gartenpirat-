"""The P1 comparison must fail on semantic changes, not bless a new baseline."""
from copy import deepcopy
import json
import unittest
from tools.check_refactoring_contract import BASELINE, differences, approved_evolution, approved_revier_timer, approved_revier_local_workflows


class RefactoringContractTests(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads(BASELINE.read_text())['contract']

    def test_local_workflows_are_exact_additions_and_only_optional_context_extensions(self):
        delta=json.loads((BASELINE.parent/'sync-optimization-contract-v1.json').read_text())
        repair=json.loads((BASELINE.parent/'mcp-authority-schema-repair-v1.json').read_text())
        timer=json.loads((BASELINE.parent/'revier-timer-contract-v1.json').read_text())
        local=json.loads((BASELINE.parent/'revier-local-workflows-contract-v1.json').read_text())
        current=deepcopy(self.contract)
        for change in delta['changes']:
            current[change['group']][change['key']]=change['after']
        next(t for t in current['tools'] if t['name']=='update_business_settings')['inputSchema']['properties']['updates']['properties']['invoice_numbering_authority']['minLength']=0
        index=next(i for i,t in enumerate(current['tools']) if t['name']=='restore_work_session')+1
        current['tools'].insert(index,timer['added_tool'])
        current['ui_actions'].append(timer['added_ui_action'])
        current['javascript_bridge'].append(timer['added_bridge'])
        for readonly, predecessor in [(True,'inspect_backup'),(False,'update_invoice_payment')]:
            index=next(i for i,t in enumerate(current['tools']) if t['name']==predecessor)+1
            current['tools'][index:index]=[t for t in local['added_tools'] if t['annotations']['readOnlyHint']==readonly]
        for change in local['modified_tools']:
            index=next(i for i,t in enumerate(current['tools']) if t['name']==change['name'])
            current['tools'][index]=deepcopy(change['after'])
        for change in local['modified_contracts']:
            current['contracts'][change['name']]=change['after']
        current['ui_actions'].extend(local['added_ui_actions'])
        current['javascript_bridge'].extend(local['added_bridges']);current['javascript_bridge'].sort()
        self.assertEqual(len(current['tools']),161)
        self.assertEqual(len(current['ui_actions']),204)
        check=lambda value,evolution=local:approved_revier_local_workflows(self.contract,value,delta,repair,timer,evolution)
        self.assertEqual(check(current),[])
        damaged=deepcopy(current);damaged['tools'][0]['annotations']['readOnlyHint']=False
        self.assertEqual(check(damaged),['tools'])
        damaged=deepcopy(current);next(t for t in damaged['tools'] if t['name']=='start_work')['inputSchema']['required'].append('property_id')
        self.assertEqual(check(damaged),['tools'])
        damaged=deepcopy(current);damaged['tools']=[t for t in damaged['tools'] if t['name']!='request_full_backup']
        self.assertEqual(check(damaged),['tools'])
        damaged=deepcopy(current);damaged['javascript_bridge'].append('void unreviewedMutation() ')
        self.assertEqual(check(damaged),['javascript_bridge'])
        damaged=deepcopy(current);damaged['ui_actions'][0]['action']='Unreviewed replacement'
        self.assertEqual(check(damaged),['ui_actions'])
        damaged=deepcopy(current);next(t for t in damaged['tools'] if t['name']=='create_invoice_draft')['inputSchema']['properties']['positions']['items']['required'].append('source_type')
        self.assertEqual(check(damaged),['tools'])
        damaged=deepcopy(current);next(t for t in damaged['tools'] if t['name']=='request_invoice_issue')['description']='Invoice authority disabled'
        self.assertEqual(check(damaged),['tools'])
        damaged=deepcopy(current);damaged['contracts']['OfferRules']='unreviewed'
        self.assertEqual(check(damaged),['contracts'])
        invalid=deepcopy(local);invalid['modified_contracts'][0]['name']='TrashRules'
        with self.assertRaises(ValueError):check(current,invalid)
        invalid=deepcopy(local);invalid['modified_contracts'][0]['after']='unreviewed'
        with self.assertRaises(ValueError):check(current,invalid)
        invalid=deepcopy(local);invalid['added_tools'][0]['name']='arbitrary_new_tool'
        with self.assertRaises(ValueError):check(current,invalid)
        invalid=deepcopy(local);next(c for c in invalid['modified_tools'] if c['name']=='create_photo')['after']['inputSchema']['required'].append('property_id')
        with self.assertRaises(ValueError):check(current,invalid)

    def test_revier_timer_preserves_every_existing_tool_and_bridge(self):
        delta=json.loads((BASELINE.parent/'sync-optimization-contract-v1.json').read_text())
        repair=json.loads((BASELINE.parent/'mcp-authority-schema-repair-v1.json').read_text())
        evolution=json.loads((BASELINE.parent/'revier-timer-contract-v1.json').read_text())
        current=deepcopy(self.contract)
        for change in delta['changes']:
            current[change['group']][change['key']]=change['after']
        field=next(t for t in current['tools'] if t['name']=='update_business_settings')['inputSchema']['properties']['updates']['properties']['invoice_numbering_authority']
        field['minLength']=0
        current['tools'].append(evolution['added_tool'])
        current['ui_actions'].append(evolution['added_ui_action'])
        current['javascript_bridge'].append(evolution['added_bridge']);current['javascript_bridge'].sort()
        # Tools are in dispatch order: timer additions precede owner tools.
        current['tools'].remove(evolution['added_tool'])
        index=next(i for i,t in enumerate(current['tools']) if t['name']=='restore_work_session')+1
        current['tools'].insert(index,evolution['added_tool'])
        self.assertEqual(approved_revier_timer(self.contract,current,delta,repair,evolution),[])
        damaged=deepcopy(current);damaged['tools'][0]['annotations']['readOnlyHint']=False
        self.assertEqual(approved_revier_timer(self.contract,damaged,delta,repair,evolution),['tools'])
        damaged=deepcopy(current);damaged['tools'].pop(index)
        self.assertEqual(approved_revier_timer(self.contract,damaged,delta,repair,evolution),['tools'])
        damaged=deepcopy(current);damaged['javascript_bridge'].append('String unreviewedWrite() ')
        self.assertEqual(approved_revier_timer(self.contract,damaged,delta,repair,evolution),['javascript_bridge'])

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
