"""Isolated existing Android-journal -> web-import -> same Drive snapshot regressions."""
import json
import unittest
import db
import server
import test_mcp_integration as fixture

class PixelPrimaryDrive(unittest.TestCase):
    setUp = fixture.MCPIntegrationTests.setUp
    tearDown = fixture.MCPIntegrationTests.tearDown

    def journal(self, actions, name='HausGarten-Mobile-isolated.json'):
        path=self.drive/name
        path.write_text(json.dumps({'format':1,'actions':actions}),encoding='utf-8')
        return path

    def action(self, identifier, kind, payload):
        return {'client_id':identifier,'kind':kind,'payload':payload}

    def test_pixel_manual_customer_guard_and_revision_preserved(self):
        old=self.customer
        stamp='2026-10-12T10:10:01.123456Z'
        payload={**old,'notes':'Pixel Ergänzung','updated_at':stamp,
                 '_sync_base':{k:old[k] for k in ('id','updated_at','notes','name')}}
        self.journal([self.action('pixel-edit','customer',payload)])
        result=server.drive_sync(str(self.drive))
        self.assertTrue(result['ok'])
        row=next(r for r in db.list_customers() if r['id']==old['id'])
        self.assertEqual(row['notes'],'Pixel Ergänzung')
        self.assertEqual(row['updated_at'],stamp)
        self.assertEqual(row['created_at'],old['created_at'])
        self.assertFalse(result['cloud_confirmed'])

    def test_parallel_web_customer_edit_not_overwritten(self):
        old=self.customer
        with db.connect() as connection:
            connection.execute('UPDATE customers SET notes=? WHERE id=?',('Web Ergänzung',old['id']))
        self.journal([self.action('pixel-stale','customer',{**old,'notes':'Stale',
                      '_sync_base':{k:old[k] for k in ('id','updated_at','notes','name')}})])
        # The export guard may also stop the conflicting replica export. Either path retains data.
        try: result=server.drive_sync(str(self.drive))
        except ValueError: result={'ok':False}
        self.assertFalse(result['ok'])
        self.assertEqual(next(r['notes'] for r in db.list_customers() if r['id']==old['id']),'Web Ergänzung')

    def test_two_files_repeated_action_preserve_one_worklog_id(self):
        payload={'id':'wrk_pixel_same','customer_id':self.customer['id'],'work_date':'2026-10-12',
                 'activity':'Isolierter Heckenschnitt','hours':3,'updated_at':'2026-10-12T09:00:00Z','_mcp_create':True}
        action=self.action('pixel-repeat','worklog',payload)
        self.journal([action])
        self.journal([action], 'HausGarten-Mobile-isolated (1).json')
        first=server.drive_sync(str(self.drive));second=server.drive_sync(str(self.drive))
        self.assertTrue(first['ok'] and second['ok'])
        self.assertEqual(first['imported'],1);self.assertEqual(second['imported'],0)
        self.assertEqual(len([r for r in db.list_worklogs() if r['id']==payload['id']]),1)

    def test_offline_customer_order_work_chain_same_relations(self):
        c={'id':'cus_offline','name':'Einzigartiger Offline-Kunde','updated_at':'2026-10-12T09:00:00Z','_mcp_create':True}
        o={'id':'ord_offline','customer_id':c['id'],'title':'Test Hecke','updated_at':'2026-10-12T09:01:00Z','_mcp_create':True}
        w={'id':'wrk_offline','customer_id':c['id'],'order_id':o['id'],'work_date':'2026-10-12','hours':5.5,
           'start_time':'08:00','end_time':'14:30','break_minutes':60,'activity':'Test Thuja','updated_at':'2026-10-12T09:02:00Z','_mcp_create':True}
        self.journal([self.action('c-new','customer',c),self.action('o-new','order',o),self.action('w-new','worklog',w)])
        self.assertTrue(server.drive_sync(str(self.drive))['ok'])
        row=next(r for r in db.list_worklogs() if r['id']==w['id'])
        self.assertEqual((row['customer_id'],row['order_id'],row['hours']),(c['id'],o['id'],5.5))
        self.assertEqual(row['updated_at'],w['updated_at'])

    def test_missing_remote_customer_blocks_whole_snapshot_overwrite(self):
        path=self.drive/'HausGarten-Laptop.json'
        remote=json.loads(path.read_text(encoding='utf-8'));remote['customers'].append({'id':'only-on-drive','name':'Remote protected'})
        path.write_text(json.dumps(remote),encoding='utf-8');before=path.read_bytes()
        result=server.drive_sync(str(self.drive))
        self.assertFalse(result['ok']);self.assertIn('fehlen',result['failed'][-1]['error'])
        self.assertEqual(path.read_bytes(),before)

    def test_newer_remote_revision_blocks_stale_replica(self):
        path=self.drive/'HausGarten-Laptop.json';remote=json.loads(path.read_text(encoding='utf-8'))
        row=next(r for r in remote['customers'] if r['id']==self.customer['id'])
        row['notes']='Neue Drive Notiz';row['updated_at']='2099-01-01T00:00:00Z'
        path.write_text(json.dumps(remote),encoding='utf-8');before=path.read_bytes()
        result=server.drive_sync(str(self.drive))
        self.assertFalse(result['ok']);self.assertIn('widersprüchlichen',result['failed'][-1]['error'])
        self.assertEqual(path.read_bytes(),before)

    def test_invalid_revision_does_not_write_business_row(self):
        payload={**self.customer,'notes':'Must not save','updated_at':'invalid',
                 '_sync_base':{'id':self.customer['id'],'updated_at':self.customer['updated_at']}}
        self.journal([self.action('bad-stamp','customer',payload)])
        result=server.drive_sync(str(self.drive));self.assertFalse(result['ok'])
        self.assertEqual(next(r['notes'] for r in db.list_customers() if r['id']==self.customer['id']),self.customer['notes'])

    def test_backup_and_technical_unknown_metadata_preserved(self):
        path=self.drive/'HausGarten-Laptop.json';remote=json.loads(path.read_text(encoding='utf-8'))
        remote['capabilities']['unknown_existing_flag']=True;remote['existing_metadata']={'keep':True}
        path.write_text(json.dumps(remote),encoding='utf-8');before=path.read_bytes()
        self.assertTrue(server.drive_sync(str(self.drive))['ok'])
        after=json.loads(path.read_text(encoding='utf-8'));self.assertTrue(after['capabilities']['unknown_existing_flag'])
        self.assertEqual(after['existing_metadata'],remote['existing_metadata'])
        self.assertTrue(any(p.read_bytes()==before for p in db.BACKUP_DIR.glob('Drive-before-web-export-*.json')))

    def test_unguarded_legacy_update_is_not_blindly_imported(self):
        old=self.customer
        self.journal([self.action('legacy-edit','customer',{**old,'notes':'Unsafe old edit'})])
        result=server.drive_sync(str(self.drive));self.assertFalse(result['ok'])
        self.assertEqual(next(r['notes'] for r in db.list_customers() if r['id']==old['id']),old['notes'])

    def test_legacy_identical_repeat_is_read_only(self):
        self.journal([self.action('legacy-repeat','customer',dict(self.customer))])
        self.assertTrue(server.drive_sync(str(self.drive))['ok'])
        self.assertEqual(next(r['updated_at'] for r in db.list_customers() if r['id']==self.customer['id']),self.customer['updated_at'])

    def test_delete_action_is_retained_for_review_not_executed(self):
        self.journal([self.action('review-delete','delete',{'entity':'worklog','id':self.record['id']})])
        result=server.drive_sync(str(self.drive));self.assertFalse(result['ok'])
        self.assertEqual(result['failed'][0]['code'],'DELETE_REVIEW_REQUIRED')
        self.assertTrue(any(r['id']==self.record['id'] for r in db.list_worklogs()))

    def test_existing_mcp_correction_replay_keeps_same_record_and_request(self):
        from mcp_integration import service
        args={'request_id':'qa-legacy-correct-replay','work_record_id':self.record['id'],'updates':{'notes':'Sicherer Nachtrag'}}
        one=service.update_work_record(**args)['data'];self.assertTrue(server.drive_sync(str(self.drive))['ok'])
        repeated=service.update_work_record(**args);self.assertEqual(one['client_id'],repeated['data']['client_id'])
        self.assertTrue(repeated['replayed']);self.assertEqual(server.drive_sync(str(self.drive))['imported'],0)
        self.assertEqual(next(r['notes'] for r in db.list_worklogs() if r['id']==self.record['id']),'Sicherer Nachtrag')

if __name__=='__main__':unittest.main()
