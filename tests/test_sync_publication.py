"""Disposable SQLite and exchange fixtures; no Google or operational writes."""
import hashlib
import json
import unittest
from pathlib import Path
from unittest.mock import patch
import db,owner_journal,sync_protocol,sync_review,drive_publication,server
import test_daily_scenarios as fixtures

class SyncPublicationTests(unittest.TestCase):
    setUp=fixtures.DailyScenarioTests.setUp
    tearDown=fixtures.DailyScenarioTests.tearDown

    def customer_by_id(self,identifier):return next(c for c in db.list_customers() if c['id']==identifier)

    def action(self,identifier,kind,payload):return {'client_id':identifier,'kind':kind,'payload':payload,'created_at':'2026-10-18T12:00:00Z'}

    def test_original_receipt_survives_disjoint_merge_and_replay(self):
        c=self.customer;base={k:c.get(k) for k in ('id','name','notes','updated_at','custom_fields')}
        db.save_customer({'id':c['id'],'notes':'Remote note'})
        action=self.action('merge-001','customer',{**base,'name':'QA changed name','_sync_base':base,'_sync_field_merge':True})
        result=owner_journal.run_action(action);self.assertEqual(result['notes'],'Remote note');self.assertEqual(result['name'],'QA changed name')
        db.save_customer({'id':c['id'],'phone':'new phone'})
        owner_journal.run_action(action)
        self.assertEqual(self.customer_by_id(c['id'])['phone'],'new phone')
        with db.connect() as database:
            rows=list(database.execute('SELECT sha256 FROM owner_journal_receipts WHERE action_id=?',('merge-001',)))
            self.assertEqual(len(rows),1);self.assertEqual(rows[0]['sha256'],sync_review.digest(action))

    def test_same_field_conflict_preserves_both_and_has_no_success_receipt(self):
        c=self.customer;base={k:c.get(k) for k in ('id','name','notes','updated_at')}
        db.save_customer({'id':c['id'],'notes':'Remote'})
        action=self.action('merge-002','customer',{**base,'notes':'Local','_sync_base':base,'_sync_field_merge':True})
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):owner_journal.run_action(action)
        self.assertEqual(self.customer_by_id(c['id'])['notes'],'Remote');self.assertEqual(action['payload']['notes'],'Local')
        with db.connect() as database:self.assertIsNone(database.execute('SELECT 1 FROM owner_journal_receipts WHERE action_id=?',('merge-002',)).fetchone())

    def test_dependency_order_uses_relations_and_predecessors_not_clock(self):
        c=self.action('create-c','customer',{'id':'new-c','name':'QA','_mcp_create':True,'updated_at':'t1'})
        edit=self.action('edit-c','customer',{'id':'new-c','name':'QA2','_sync_base':{'id':'new-c','updated_at':'t1'}})
        w=self.action('work','worklog',{'id':'new-w','customer_id':'new-c'})
        self.assertEqual([a['client_id'] for a in sync_protocol.ordered([edit,w,c])],['create-c','edit-c','work'])
        c['payload']['_sync_predecessors']=['edit-c']
        with self.assertRaisesRegex(ValueError,'DEPENDENCY_CYCLE'):sync_protocol.ordered([edit,w,c])

    def test_garden_validation_keeps_unknown_and_additional_information(self):
        c=db.save_customer({'id':self.customer['id'],'garden_area':'','custom_fields':{'Unrelated':'preserve','Rasenfläche (m²)':'12,5'}})
        self.assertIsNone(c['garden_area']);self.assertEqual(json.loads(c['custom_fields'])['Rasenfläche (m²)'],12.5)
        for bad in (-1,float('inf'),'invalid'):
            with self.assertRaises(ValueError):db.save_customer({'id':c['id'],'garden_area':bad})
        with self.assertRaises(ValueError):db.save_customer({'id':c['id'],'garden_area':10})
        self.assertEqual(json.loads(self.customer_by_id(c['id'])['custom_fields'])['Unrelated'],'preserve')

    def publish(self,root,data,sequence=1,publisher='synthetic-pixel'):
        raw=sync_review.canonical(data).encode();sha=hashlib.sha256(raw).hexdigest();name='HausGarten-State-'+sha+'.json';(root/name).write_bytes(raw)
        manifest={'format':'hausgarten.drive.commit.v1','publisher_id':publisher,'sequence':sequence,'state_sha256':sha,'state_name':name,'receipts':{},'media_complete':True}
        body=sync_review.canonical(manifest).encode();path=root/('HausGarten-Commit-'+hashlib.sha256(body).hexdigest()+'.json');path.write_bytes(body);return path,root/name

    def test_publication_read_never_claims_cloud_from_a_mounted_file(self):
        root=Path(self.temp.name);self.publish(root,{'format':1,'customers':[]})
        data=drive_publication.latest(root);self.assertFalse(data['_publication']['cloud_confirmed']);self.assertEqual(data['_publication']['confirmation_scope'],'local_publication_read')

    def test_tampered_incomplete_and_forked_publications_are_not_silently_selected(self):
        root=Path(self.temp.name);manifest,state=self.publish(root,{'format':1,'customers':[]});state.write_text('{}')
        with self.assertRaises(ValueError):drive_publication.latest(root)
        manifest.unlink();state.unlink();self.publish(root,{'customers':[]});self.publish(root,{'customers':[{'id':'c'}]})
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):drive_publication.latest(root)

    def test_two_primary_devices_require_explicit_resolution(self):
        root=Path(self.temp.name);self.publish(root,{'format':1},publisher='first');self.publish(root,{'format':1},publisher='second')
        with self.assertRaisesRegex(ValueError,'primäre Pixel'):drive_publication.latest(root)

    def test_explicit_legacy_delete_review_archives_without_losing_original(self):
        c=self.customer;prior=self.action('legacy-delete','delete',{'entity':'customer','id':c['id']})
        action=self.action('review-delete','conflict_resolution',{'client_id':'legacy-delete','resolution':'apply_proposed','_original_action':prior,'expected_action_hash':sync_review.digest(prior),'_current_base':owner_journal.customer_guard(c)})
        owner_journal.run_action(action);owner_journal.run_action(action)
        self.assertEqual(self.customer_by_id(c['id'])['active'],0)
        metadata=owner_journal.snapshot_metadata();self.assertEqual(metadata['owner_conflict_history']['legacy-delete']['original_action'],prior)
        self.assertEqual(metadata['owner_archive']['customer:'+c['id']]['record']['name'],c['name'])
        with db.connect() as connection:self.assertTrue(owner_journal.effects(connection,action))

    def test_independent_actions_continue_after_customer_conflict(self):
        root=Path(self.temp.name)/'drive';root.mkdir();db.save_settings({'backup_folder':str(root)})
        actions=[self.action('bad-c','customer',{'id':self.customer['id'],'name':'Conflict','_mcp_create':True}),self.action('good-c','customer',{'id':'independent-qa','name':'Independent QA','_mcp_create':True}),self.action('dependent','order',{'id':'dependent-o','customer_id':self.customer['id'],'title':'Wait','_sync_predecessors':['bad-c']})]
        (root/'HausGarten-Mobile-synthetic.json').write_text(json.dumps({'format':1,'actions':actions}))
        result=server.drive_sync();self.assertEqual(self.customer_by_id('independent-qa')['name'],'Independent QA')
        self.assertTrue((root/'HausGarten-Mobile-synthetic.json').exists())
        snapshot=json.loads((root/'HausGarten-Laptop.json').read_text());self.assertIn('good-c',snapshot['accepted_ids']);self.assertNotIn('bad-c',snapshot['accepted_ids']);self.assertNotIn('dependent',snapshot['accepted_ids'])

if __name__=='__main__':unittest.main()
