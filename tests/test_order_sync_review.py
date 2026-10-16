import json
import unittest
import db
import server
import sync_review
import test_mcp_integration as fixture

class OrderReviewSync(unittest.TestCase):
    setUp=fixture.MCPIntegrationTests.setUp
    tearDown=fixture.MCPIntegrationTests.tearDown
    def action(self,identifier,payload,kind='order',created='2026-10-15T08:00:00Z'):
        return dict(client_id=identifier,kind=kind,payload=payload,created_at=created)
    def pair(self):
        bad=self.action('failed-order',{**self.order,'title':'Ungeprüft'})
        corrected={**self.order,'title':'Nach Prüfung gespeichert','_sync_base':{k:self.order.get(k) for k in ('id','title','updated_at','customer_id')},'updated_at':'2026-10-15T09:00:00Z','_sync_resolves':[dict(client_id=bad['client_id'],sha256=sync_review.digest(bad))]}
        fix=self.action('review-order',corrected,created='2026-10-15T09:00:00Z')
        return bad,fix
    def journal(self,actions,name='HausGarten-Mobile-review.json'):
        (self.drive/name).write_text(json.dumps(dict(format=1,actions=actions)),encoding='utf-8')
    def test_explicit_resolution_replay_and_immutable_original(self):
        bad,fix=self.pair();self.journal([bad,fix]);before=(self.drive/'HausGarten-Mobile-review.json').read_bytes()
        one=server.drive_sync(str(self.drive));self.assertTrue(one['ok'],one)
        snapshot=json.loads((self.drive/'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        self.assertNotIn(bad['client_id'],snapshot['accepted_ids']);self.assertIn(bad['client_id'],snapshot['drive_resolved_actions'])
        self.assertEqual(next(o for o in db.list_orders() if o['id']==self.order['id'])['title'],'Nach Prüfung gespeichert')
        self.assertEqual(server.drive_sync(str(self.drive))['imported'],0)
        self.assertEqual(before,(self.drive/'HausGarten-Mobile-review.json').read_bytes())
    def test_bad_resolution_hash_retains_old_data_and_errors(self):
        bad,fix=self.pair();fix['payload']['_sync_resolves'][0]['sha256']='bad';self.journal([bad,fix])
        result=server.drive_sync(str(self.drive));self.assertFalse(result['ok']);self.assertEqual(len(result['failed']),2)
        self.assertEqual(next(o for o in db.list_orders() if o['id']==self.order['id'])['title'],self.order['title'])
    def test_parallel_change_after_review_not_overwritten(self):
        bad,fix=self.pair()
        with db.connect() as c:c.execute('UPDATE orders SET title=? WHERE id=?',('Parallel',self.order['id']))
        self.journal([bad,fix]);self.assertFalse(server.drive_sync(str(self.drive))['ok'])
        self.assertEqual(next(o for o in db.list_orders() if o['id']==self.order['id'])['title'],'Parallel')
    def test_corrupt_review_receipts_fail_closed(self):
        (db.DATA_DIR/'drive-order-review-receipts.json').write_text('broken',encoding='utf-8')
        self.journal([self.action('new',dict(id='new',customer_id=self.customer['id'],title='Kein Import',_mcp_create=True))])
        with self.assertRaises(ValueError):server.drive_sync(str(self.drive))
        self.assertFalse(any(o['id']=='new' for o in db.list_orders()))
    def test_changed_resolved_action_is_detected(self):
        bad,fix=self.pair();self.journal([bad,fix]);self.assertTrue(server.drive_sync(str(self.drive))['ok'])
        bad['payload']['title']='Mutiert';self.journal([bad,fix])
        with self.assertRaises(ValueError):server.drive_sync(str(self.drive))
    def test_global_duplicate_conflict_blocks_all_writes(self):
        bad,fix=self.pair();self.journal([bad,fix]);changed=json.loads(json.dumps(bad));changed['payload']['title']='Different'
        self.journal([changed],'HausGarten-Mobile-duplicate.json')
        with self.assertRaises(RuntimeError):server.drive_sync(str(self.drive))
        self.assertEqual(next(o for o in db.list_orders() if o['id']==self.order['id'])['title'],self.order['title'])
    def test_files_are_applied_chronologically_not_by_directory_order(self):
        bad,fix=self.pair();self.journal([fix]);self.journal([bad],'HausGarten-Mobile-old.json')
        self.assertTrue(server.drive_sync(str(self.drive))['ok'])
    def test_valid_action_cannot_be_suppressed(self):
        bad,fix=self.pair();bad['payload']['_sync_base']={k:self.order.get(k) for k in ('id','title','updated_at','customer_id')}
        fix['payload']['_sync_resolves'][0]['sha256']=sync_review.digest(bad);self.journal([bad,fix])
        result=server.drive_sync(str(self.drive));self.assertFalse(result['ok'])
        self.assertEqual(next(o for o in db.list_orders() if o['id']==self.order['id'])['title'],'Ungeprüft')

if __name__=='__main__':unittest.main()
