"""Owner writes/receipts on isolated SQLite and Drive fixtures, no operational data."""
import json
import unittest
from unittest.mock import patch
import db, server, invoicing, owner_journal, sync_review
import test_daily_scenarios as fixtures

class OwnerProtocolTests(unittest.TestCase):
    setUp=fixtures.DailyScenarioTests.setUp
    tearDown=fixtures.DailyScenarioTests.tearDown
    work=fixtures.DailyScenarioTests.work
    draft=fixtures.DailyScenarioTests.draft

    def test_shared_transaction_rolls_back_nested_savers_and_receipt(self):
        count=len(db.list_customers())
        with self.assertRaises(ValueError):
            with db.atomic() as database:
                db.save_customer({'name':'QA rollback customer'})
                db.save_worklog({'customer_id':'qa_missing','activity':'Invalid','hours':1})
        self.assertEqual(len(db.list_customers()),count)

    def test_archive_restore_preserves_original_id_and_action_receipt(self):
        c=self.customer;stamp=db.now()
        p={'entity':'customer','id':c['id'],'_record_base':c,'_archive_record':c,'updated_at':stamp}
        action={'client_id':'archive-qa','kind':'archive','payload':p,'created_at':stamp}
        first=server.apply_sync_action(action);self.assertEqual(first,server.apply_sync_action(action))
        archived=owner_journal.snapshot_metadata()['owner_archive']['customer:'+c['id']]
        self.assertEqual(next(r for r in db.list_customers() if r['id']==c['id'])['active'],0)
        restore={'client_id':'restore-qa','kind':'restore','payload':{'entity':'customer','id':c['id'],'expected_record_hash':sync_review.digest(archived)}}
        server.apply_sync_action(restore)
        self.assertEqual(next(r for r in db.list_customers() if r['id']==c['id'])['active'],1)
        with self.assertRaisesRegex(ValueError,'ACTION_ID_CONFLICT'):server.apply_sync_action({**action,'payload':{**p,'updated_at':'changed'}})

    def test_conflict_keep_current_retains_both_versions_and_replays(self):
        current=self.customer;proposed={**current,'name':'QA other proposal','_mcp_create':True}
        original={'client_id':'collision-qa','kind':'customer','payload':proposed}
        action={'client_id':'resolve-qa','kind':'conflict_resolution','payload':{'client_id':'collision-qa','_original_action':original,'expected_action_hash':sync_review.digest(original),'_current_base':current,'resolution':'keep_current'}}
        server.apply_sync_action(action);server.apply_sync_action(action)
        state=owner_journal.snapshot_metadata();self.assertEqual(state['owner_conflict_history']['collision-qa']['original_action'],original)
        self.assertEqual(next(r for r in db.list_customers() if r['id']==current['id'])['name'],current['name'])

    def test_stale_conflict_decision_cannot_overwrite_new_version(self):
        current=self.customer;db.save_customer({'id':current['id'],'notes':'Parallel'})
        original={'client_id':'collision-qa','kind':'customer','payload':{**current,'name':'Proposed','_mcp_create':True}}
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):
            server.apply_sync_action({'client_id':'stale-resolution','kind':'conflict_resolution','payload':{'client_id':'collision-qa','_original_action':original,'expected_action_hash':sync_review.digest(original),'_current_base':current,'resolution':'apply_proposed'}})
        self.assertEqual(owner_journal.snapshot_metadata()['owner_conflict_history'],{})

    def test_invoice_issue_requires_the_same_reviewed_draft(self):
        draft=self.draft(self.work());db.save_invoice({'id':draft['id'],'notes':'Changed'})
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):invoicing.issue(draft['id'],True,draft)
        self.assertEqual(db.list_invoices()[0]['status'],'Entwurf')

    def test_cancellation_creates_separate_draft_then_immutable_credit_pdf(self):
        draft=self.draft(self.work());invoicing.issue(draft['id'],True)
        with db.connect() as database:original=owner_journal.record(database,'invoice',draft['id'])
        original_pdf=invoicing.issued_pdf(original['id'])
        action={'client_id':'correction-qa','kind':'invoice_correction','payload':{'id':original['id'],'_expected_invoice':original,'correction_type':'cancellation','reason':'QA versehentliche Ausstellung'}}
        correction=server.apply_sync_action(action);again=server.apply_sync_action(action)
        self.assertEqual(correction['id'],again['id']);self.assertEqual(correction['status'],'Entwurf');self.assertLess(correction['gross_total'],0)
        self.assertEqual(next(r for r in db.list_invoices() if r['id']==original['id'])['status'],'Gestellt')
        invoicing.issue(correction['id'],True)
        self.assertEqual(next(r for r in db.list_invoices() if r['id']==original['id'])['status'],'Storniert')
        self.assertEqual(invoicing.issued_pdf(original['id']),original_pdf)
        self.assertTrue(invoicing.issued_pdf(correction['id']).startswith(b'%PDF'))
        self.assertTrue(db.list_worklogs()[0]['billed'])

    def test_failed_cancellation_pdf_keeps_original_and_draft(self):
        draft=self.draft(self.work());invoicing.issue(draft['id'],True)
        with db.connect() as database:original=owner_journal.record(database,'invoice',draft['id'])
        correction=invoicing.create_correction({'id':draft['id'],'_expected_invoice':original,'correction_type':'cancellation','reason':'QA','_action_id':'failed-credit'})
        with patch.object(invoicing,'render_pdf',side_effect=OSError('synthetic')):
            with self.assertRaises(OSError):invoicing.issue(correction['id'],True)
        self.assertEqual(next(r for r in db.list_invoices() if r['id']==original['id'])['status'],'Gestellt')
        self.assertEqual(next(r for r in db.list_invoices() if r['id']==correction['id'])['status'],'Entwurf')

    def test_drive_archive_export_and_crash_replay_keep_exact_receipt(self):
        work=self.work();folder=db.DATA_DIR/'owner-drive';folder.mkdir()
        self.assertTrue(server.drive_sync(str(folder))['ok'])
        action={'client_id':'archive-work','kind':'archive','created_at':db.now(),'payload':{'entity':'worklog','id':work['id'],'_record_base':work,'_archive_record':work,'updated_at':db.now()}}
        journal=folder/'HausGarten-Mobile-qa.json';journal.write_text(json.dumps({'actions':[action]}))
        result=server.drive_sync(str(folder));self.assertTrue(result['ok'],result)
        snapshot=json.loads((folder/'HausGarten-Laptop.json').read_text());self.assertFalse(snapshot['worklogs']);self.assertIn('worklog:'+work['id'],snapshot['owner_archive'])
        self.assertTrue(server.drive_sync(str(folder))['ok'])
        changed={**action,'payload':{**action['payload'],'updated_at':'mutated'}};journal.write_text(json.dumps({'actions':[changed]}))
        with self.assertRaisesRegex(ValueError,'ACTION_ID_CONFLICT'):server.drive_sync(str(folder))

    def test_drive_recovery_after_export_failure_uses_atomic_receipt(self):
        folder=db.DATA_DIR/'owner-drive-crash';folder.mkdir();server.drive_sync(str(folder))
        action={'client_id':'atomic-customer','kind':'customer','payload':{'id':'qa_cus_atomic','name':'QA durable replay','_mcp_create':True}}
        (folder/'HausGarten-Mobile-qa.json').write_text(json.dumps({'actions':[action]}))
        write=server.write_json_atomic
        def fail_receipt(path,value):
            if path.name=='drive-order-review-receipts.json':raise OSError('simulated after DB commit')
            return write(path,value)
        with patch.object(server,'write_json_atomic',side_effect=fail_receipt):
            self.assertFalse(server.drive_sync(str(folder))['ok'])
        self.assertEqual(sum(r['id']=='qa_cus_atomic' for r in db.list_customers()),1)
        self.assertTrue(server.drive_sync(str(folder))['ok'])
        self.assertEqual(sum(r['id']=='qa_cus_atomic' for r in db.list_customers()),1)

    def test_invoice_queued_performance_version_is_checked(self):
        work=self.work();draft=self.draft(work);db.save_worklog({'id':work['id'],'notes':'Changed after request'})
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):invoicing.issue(draft['id'],True,draft,{work['id']:work})
        self.assertEqual(db.list_invoices()[0]['status'],'Entwurf')

    def test_business_settings_exclude_credentials_and_compare_versions(self):
        original=owner_journal.business_settings()
        action={'client_id':'business-settings','kind':'business_settings','payload':{'expected_record_hash':sync_review.digest(original),'updates':{'owner_name':'QA neuer Aussteller','invoice_prefix':'QA','invoice_issuer_confirmed':True}}}
        server.apply_sync_action(action);server.apply_sync_action(action)
        self.assertEqual(db.settings()['owner_name'],'QA neuer Aussteller')
        self.assertNotIn('sevdesk_api_token',owner_journal.business_settings())
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):server.apply_sync_action({**action,'client_id':'stale-settings'})
        bad={'client_id':'bad-settings','kind':'business_settings','payload':{'expected_record_hash':sync_review.digest(owner_journal.business_settings()),'updates':{'sevdesk_api_token':'synthetic-forbidden'}}}
        with self.assertRaises(ValueError):server.apply_sync_action(bad)
        self.assertNotEqual(db.settings().get('sevdesk_api_token'),'synthetic-forbidden')

    def test_changed_legacy_invoice_positions_are_retained_for_review(self):
        draft=self.draft(self.work())
        with self.assertRaisesRegex(ValueError,'Vergleichsstand'):
            server.apply_sync_action({'client_id':'legacy-draft-change','kind':'invoice','payload':{'id':draft['id'],'customer_id':draft['customer_id'],'positions':[{'title':'Geändert','quantity':8,'price':50}]}})
        self.assertEqual(db.list_invoices()[0]['positions'][0]['quantity'],2)

    def test_owner_invoice_namespace_requires_baseline_and_has_durable_replay(self):
        work=self.work();draft=self.draft(work)
        action={'client_id':'owner-finalization','kind':'owner_invoice_issue','payload':{'id':draft['id'],'confirmed':True,'_expected_invoice':draft,'_expected_worklogs':{work['id']:work}}}
        with self.assertRaises(ValueError):server.apply_sync_action({**action,'payload':{'id':draft['id'],'confirmed':True}})
        first=owner_journal.run_action(action);self.assertEqual(first,owner_journal.run_action(action))
        self.assertEqual(db.list_invoices()[0]['status'],'Gestellt')
        with self.assertRaisesRegex(ValueError,'ACTION_ID_CONFLICT'):owner_journal.run_action({**action,'payload':{**action['payload'],'confirmed':False}})
        self.assertIn(('worklogs',work['id']),owner_journal.verified_effects(action))

if __name__=='__main__':unittest.main()
