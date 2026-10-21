"""Read-only search regressions with explicit synthetic records."""
import copy
import unittest
from unittest.mock import patch

import search_order
from mcp_integration import service


class SearchOrderingTests(unittest.TestCase):
    def fixture(self):
        rows = [dict(id='qa_10', name='Ähren 10 QA', active=1, test_record=True),
                dict(id='qa_2', name='Ähren 2 QA', active=1, test_record=True),
                dict(id='qa_m', name='Müller QA', active=1, street='QA Grauenrain',
                     phone='+43 (664) 123-4567', test_record=True),
                dict(id='qa_off', name='Aachen QA', active=False, test_record=True)]
        order = dict(id='qa_o', customer_id='qa_m', title='QA Rasen', test_record=True)
        return dict(customers=rows, orders=[order], worklogs=[],
                    _indexes=dict(customers={r['id']: r for r in rows}, orders={'qa_o': order}))

    def test_alias_multiword_phone_and_literal_wildcards(self):
        self.assertTrue(search_order.matches('mueller grauenrain', ['Müller QA', 'QA Grauenrain']))
        self.assertTrue(search_order.matches('STRASSE', ['Straße']))
        self.assertTrue(search_order.matches('436641234567', ['+43 (664) 123-4567']))
        self.assertFalse(search_order.matches('QA missing', ['QA Müller']))
        self.assertFalse(search_order.matches('%', ['QA Müller']))

    def test_mcp_customer_order_limit_and_new_account(self):
        snapshot = self.fixture()
        with patch.object(service, '_snapshot', return_value=snapshot):
            self.assertEqual([r['id'] for r in service.search_customers('QA', limit=2)['data']], ['qa_2', 'qa_10'])
            self.assertEqual(service.search_customers('mueller grauenrain')['data'][0]['id'], 'qa_m')
            self.assertEqual(service.search_customers('436641234567')['data'][0]['id'], 'qa_m')
            snapshot['customers'].append(dict(id='qa_new', name='Abel QA', active=1, test_record=True))
            self.assertEqual(service.search_customers('QA', limit=1)['data'][0]['id'], 'qa_new')
            self.assertEqual(service.search_customers('Aachen')['count'], 0)
            self.assertEqual(service.search_customers('Aachen', include_inactive=True)['count'], 1)

    def test_global_and_order_queries_do_not_mutate_snapshot(self):
        snapshot = self.fixture()
        before = copy.deepcopy(snapshot)
        with patch.object(service, '_snapshot', return_value=snapshot):
            self.assertEqual(service.search_orders('mueller rasen')['data'][0]['id'], 'qa_o')
            self.assertEqual(service.search_business_data('mueller grauenrain')['data']['customers'][0]['id'], 'qa_m')
        self.assertEqual(snapshot, before)
