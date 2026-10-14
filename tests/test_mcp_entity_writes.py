"""Actual Drive importer tests with disposable business data and SQLite only."""
import json
import unittest
import db
import server
import test_mcp_integration as mcp_tests


class EntityWrites(unittest.TestCase):
    setUp = mcp_tests.MCPIntegrationTests.setUp
    tearDown = mcp_tests.MCPIntegrationTests.tearDown

    def test_custom_field_numbers_keep_numeric_equivalence_but_not_boolean_equivalence(self):
        payload = {'id': 'cus_typed_custom', 'name': 'Typed QA',
                   'custom_fields': {'number': 1.0, 'enabled': False}, '_mcp_create': True, '_new_record': True}
        self.action('mcp_typed_create', 'customer', payload)
        old = next(x for x in db.list_customers() if x['id'] == payload['id'])
        base = {'id': old['id'], 'updated_at': old['updated_at'], 'custom_fields': {'enabled': False, 'number': 1}}
        success = self.action('mcp_typed_number', 'customer', {**old, 'notes': 'Numeric representation safe', '_mcp_base': base})
        self.assertIn('mcp_typed_number', success['accepted_ids'])
        current = next(x for x in db.list_customers() if x['id'] == payload['id'])
        base = {'id': current['id'], 'updated_at': current['updated_at'], 'custom_fields': {'enabled': False, 'number': 1}}
        # Simulate a simultaneous type-changing update in disposable SQLite only.
        with db.connect() as database:
            database.execute('UPDATE customers SET custom_fields=? WHERE id=?',
                             (json.dumps({'number': 1.0, 'enabled': 1}), old['id']))
        failed = self.action('mcp_typed_boolean', 'customer', {**current, 'notes': 'Must not overwrite', '_mcp_base': base})
        self.assertNotIn('mcp_typed_boolean', failed['accepted_ids'])
        self.assertEqual(next(x['notes'] for x in db.list_customers() if x['id'] == old['id']), 'Numeric representation safe')

    def test_metadata_upgrade_never_imports_actions_or_changes_records(self):
        import hashlib
        target = self.drive / 'HausGarten-Laptop.json'
        snapshot = json.loads(target.read_text(encoding='utf-8'))
        snapshot['capabilities'] = {'worklog_compare_and_save': True, 'other_existing_feature': True}
        target.write_text(json.dumps(snapshot), encoding='utf-8')
        channel = self.drive / 'HausGarten-Mobile-metadata-test.json'
        channel.write_text(json.dumps({'actions': [{'client_id': 'do-not-import', 'kind': 'customer',
            'payload': {'id': 'never-created', 'name': 'Not imported'}}]}), encoding='utf-8')
        database_before = db.DB_PATH.read_bytes()
        channel_before = channel.read_bytes()
        result = server.refresh_drive_capabilities(hashlib.sha256(target.read_bytes()).hexdigest())
        after = json.loads(target.read_text(encoding='utf-8'))
        self.assertTrue(result['records_unchanged'])
        self.assertEqual(result['pending_actions_imported'], 0)
        self.assertEqual(db.DB_PATH.read_bytes(), database_before)
        self.assertEqual(channel.read_bytes(), channel_before)
        self.assertEqual({k: v for k, v in snapshot.items() if k != 'capabilities'},
                         {k: v for k, v in after.items() if k != 'capabilities'})
        self.assertTrue(after['capabilities']['other_existing_feature'])
        self.assertTrue(after['capabilities']['customer_custom_fields_compare_and_save'])
        self.assertEqual(json.loads(__import__('pathlib').Path(result['backup']).read_text()), snapshot)

    def test_metadata_upgrade_stale_or_corrupt_export_fails_closed(self):
        import hashlib
        target = self.drive / 'HausGarten-Laptop.json'
        original = target.read_bytes()
        with self.assertRaises(ValueError):
            server.refresh_drive_capabilities('0' * 64)
        self.assertEqual(target.read_bytes(), original)
        snapshot = json.loads(original)
        snapshot['customers'].append(snapshot['customers'][0])
        target.write_text(json.dumps(snapshot), encoding='utf-8')
        corrupt = target.read_bytes()
        with self.assertRaises(ValueError):
            server.refresh_drive_capabilities(hashlib.sha256(corrupt).hexdigest())
        self.assertEqual(target.read_bytes(), corrupt)

    def action(self, request, kind, payload):
        (self.drive / 'HausGarten-Mobile-entity-test.json').write_text(json.dumps({
            'format': 1, 'device_id': 'entity-test', 'actions': [
                {'client_id': request, 'kind': kind, 'payload': payload}]}), encoding='utf-8')
        server.drive_sync(str(self.drive))
        return json.loads((self.drive / 'HausGarten-Laptop.json').read_text(encoding='utf-8'))

    def test_customer_create_replay_update_conflict(self):
        payload = {'id': 'cus_mobile_mcp_test', 'name': 'Neuer Testkunde',
                   '_mcp_create': True, '_new_record': True}
        first = self.action('mcp_entity_create', 'customer', payload)
        self.assertIn('mcp_entity_create', first['accepted_ids'])
        self.action('mcp_entity_create', 'customer', payload)
        self.assertEqual(sum(x['id'] == payload['id'] for x in db.list_customers()), 1)
        old = next(x for x in db.list_customers() if x['id'] == payload['id'])
        base = {k: old[k] for k in ('id', 'updated_at', 'name')}
        edited = self.action('mcp_entity_edit', 'customer', {**old, 'name': 'Korrigiert', '_mcp_base': base})
        self.assertIn('mcp_entity_edit', edited['accepted_ids'])
        conflict = self.action('mcp_entity_stale', 'customer', {**old, 'name': 'Veraltet', '_mcp_base': base})
        self.assertNotIn('mcp_entity_stale', conflict['accepted_ids'])
        self.assertEqual(next(x['name'] for x in db.list_customers() if x['id'] == old['id']), 'Korrigiert')

    def test_create_collision_and_duplicate_preserve_data(self):
        collision = self.action('mcp_entity_collision', 'customer', {
            **self.customer, 'name': 'Overwrite', '_mcp_create': True})
        self.assertNotIn('mcp_entity_collision', collision['accepted_ids'])
        duplicate = self.action('mcp_entity_duplicate', 'customer', {
            **self.customer, 'id': 'cus_duplicate', '_new_record': True, '_mcp_create': True})
        self.assertNotIn('mcp_entity_duplicate', duplicate['accepted_ids'])

    def test_order_reference_and_guard(self):
        invalid = self.action('mcp_invalid_order', 'order', {'id': 'ord_invalid',
            'customer_id': 'invented', 'title': 'Pflege', '_mcp_create': True})
        self.assertNotIn('mcp_invalid_order', invalid['accepted_ids'])
        base = {k: self.order[k] for k in ('id', 'updated_at', 'title', 'customer_id')}
        updated = self.action('mcp_order_edit', 'order', {**self.order, 'title': 'Neue Pflege', '_mcp_base': base})
        self.assertIn('mcp_order_edit', updated['accepted_ids'])
        conflict = self.action('mcp_order_stale', 'order', {**self.order, 'title': 'Veraltet', '_mcp_base': base})
        self.assertNotIn('mcp_order_stale', conflict['accepted_ids'])

    def test_invoice_draft_create_update_and_position_conflict(self):
        payload = {'id': 'inv_mobile_mcp_test', 'customer_id': self.customer['id'],
            'invoice_number': 'Entwurf-inv_mobile_mcp_test', 'invoice_date': '2026-10-12',
            'status': 'Entwurf', 'positions': [{'title': 'Pflege', 'quantity': 3, 'price': 50}],
            '_mcp_create': True}
        result = self.action('mcp_invoice_create', 'invoice', payload)
        self.assertIn('mcp_invoice_create', result['accepted_ids'])
        row = next(x for x in db.list_invoices() if x['id'] == payload['id'])
        self.assertEqual(row['amount'], 150)
        base = {k: row[k] for k in ('id', 'updated_at', 'customer_id', 'status', 'invoice_number')}
        positions = row['positions']
        corrected = {**row, '_mcp_base': base, '_mcp_invoice_positions': positions,
                     'positions': [{'title': 'Pflege ergänzt', 'quantity': 4, 'price': 50}]}
        edited = self.action('mcp_invoice_edit', 'invoice', corrected)
        self.assertIn('mcp_invoice_edit', edited['accepted_ids'])
        stale = self.action('mcp_invoice_stale', 'invoice', corrected)
        self.assertNotIn('mcp_invoice_stale', stale['accepted_ids'])

    def test_capability_export(self):
        snapshot = self.action('mcp_capability', 'customer', {'id': 'cus_capability', 'name': 'Capability', '_mcp_create': True})
        self.assertIs(snapshot['capabilities']['entity_compare_and_save'], True)
        self.assertIs(snapshot['capabilities']['customer_custom_fields_compare_and_save'], True)

    def test_customer_all_fields_custom_compare_and_relation_retention(self):
        payload = {'id': 'cus_mcp_all_fields', 'name': 'QA Gartenkundin',
            'company': 'QA Betrieb', 'first_name': 'Anna', 'last_name': 'Muster',
            'street': 'Beispielweg 4', 'postal_code': '9560', 'city': 'Feldkirchen',
            'country': 'Österreich', 'phone': '+43 664 1234567', 'email': 'qa@example.test',
            'notes': 'Tor links', 'garden_area': 200.5, 'active': 1,
            'sevdesk_customer_number': 'QA-42', 'custom_fields': {'Torcode': '1234', 'Breite': 2.5},
            '_mcp_create': True, '_new_record': True}
        first = self.action('mcp_all_customer_create', 'customer', payload)
        self.assertIn('mcp_all_customer_create', first['accepted_ids'])
        old = next(x for x in db.list_customers() if x['id'] == payload['id'])
        work = db.save_worklog({'customer_id': old['id'], 'work_date': '2026-10-12',
            'hours': 2, 'activity': 'QA Pflege'})
        base = {'id': old['id'], 'updated_at': old['updated_at'], 'name': old['name'],
            'custom_fields': {'Breite': 2.5, 'Torcode': '1234'}}
        changed = {**old, '_mcp_base': base, 'notes': 'Tor rechts',
            'custom_fields': {'Breite': 2.5, 'Torcode': '4321'}}
        edited = self.action('mcp_all_customer_update', 'customer', changed)
        self.assertIn('mcp_all_customer_update', edited['accepted_ids'])
        current = next(x for x in db.list_customers() if x['id'] == old['id'])
        self.assertEqual(current['created_at'], old['created_at'])
        self.assertEqual(current['street'], old['street'])
        self.assertEqual(json.loads(current['custom_fields']), {'Breite': 2.5, 'Torcode': '4321'})
        self.assertEqual(next(x for x in db.list_worklogs() if x['id'] == work['id'])['customer_id'], old['id'])
        self.action('mcp_all_customer_update', 'customer', changed)
        self.assertEqual(sum(x['id'] == old['id'] for x in db.list_customers()), 1)
        stale = self.action('mcp_all_customer_stale', 'customer', {**changed, 'notes': 'Veraltet'})
        self.assertNotIn('mcp_all_customer_stale', stale['accepted_ids'])
        self.assertEqual(next(x['notes'] for x in db.list_customers() if x['id'] == old['id']), 'Tor rechts')

    def test_custom_fields_guard_rejects_shape_and_keeps_customer(self):
        old = self.customer
        result = self.action('mcp_bad_custom_base', 'customer', {**old,
            'name': 'Nicht überschreiben', '_mcp_base': {
                'id': old['id'], 'updated_at': old['updated_at'], 'custom_fields': '[]'}})
        self.assertNotIn('mcp_bad_custom_base', result['accepted_ids'])
        self.assertEqual(next(x['name'] for x in db.list_customers() if x['id'] == old['id']), old['name'])
