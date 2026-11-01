"""Privacy acceptance uses only central temporary synthetic stores, never a Pixel."""
import importlib.util
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import unittest
from unittest.mock import patch
import zipfile

import bootstrap_contract as bootstrap
import db
import export_mobile_snapshot as exporter
import privacy_guard as privacy
import runtime_paths as paths
from synthetic_fixtures import SyntheticStore, profile, temporary_directory


class RuntimeIsolationTests(unittest.TestCase):
    def test_existing_nonempty_store_cannot_be_reclassified_as_synthetic(self):
        import tempfile
        with tempfile.TemporaryDirectory(prefix='qa_unregistered_') as folder:
            root=Path(folder);(root/'qa_sentinel').write_bytes(b'qa_sentinel')
            with self.assertRaises(paths.PrivateDataPathError):paths.register_synthetic_root(root)
            with patch.dict(os.environ,{'HG_PRIVATE_DATA_ROOT':folder}):
                self.assertEqual(paths.private_root(),root.resolve())
                if os.name=='posix':
                    root.chmod(0o755)
                    with self.assertRaises(paths.PrivateDataPathError):paths.private_root()

    def test_unconfigured_private_root_and_db_refuse_before_creation(self):
        with patch.dict(os.environ,{},clear=True):
            with self.assertRaises(paths.PrivateDataPathError):paths.private_root()
            with self.assertRaises(paths.PrivateDataPathError):paths.PrivatePath('data/qa.db').exists()
            with patch('sys.argv',['export_mobile_snapshot.py']):
                with self.assertRaises(paths.PrivateDataPathError):exporter.main()

    def test_internal_relative_parent_and_system_temp_roots_rejected(self):
        for value in [str(paths.REPOSITORY),str(paths.REPOSITORY/'ignored/data'),str(paths.REPOSITORY.parent),'data','/tmp']:
            with self.subTest(kind='unsafe_root'),patch.dict(os.environ,{'HG_PRIVATE_DATA_ROOT':value}):
                with self.assertRaises(paths.PrivateDataPathError):paths.private_root()

    def test_symlink_alias_and_child_escape_rejected(self):
        with temporary_directory() as folder:
            root=Path(folder);(root/'source-link').symlink_to(paths.REPOSITORY,target_is_directory=True)
            with patch.dict(os.environ,{'HG_PRIVATE_DATA_ROOT':str(root/'source-link')}):
                with self.assertRaises(paths.PrivateDataPathError):paths.private_root()
            (root/'data').mkdir();(root/'data/escape').symlink_to(paths.REPOSITORY,target_is_directory=True)
            with self.assertRaises(paths.PrivateDataPathError):paths.business_path(root/'data/escape/qa.db')

    def test_synthetic_store_ignores_production_root(self):
        with patch.dict(os.environ,{'HG_PRIVATE_DATA_ROOT':str(paths.REPOSITORY/'forbidden')}):
            with SyntheticStore() as store:
                self.assertEqual(db.list_customers(),[])
                row=db.save_customer(dict(name='QA Musterkunde'))
                self.assertTrue(row['id'].startswith('qa_'))
                self.assertTrue(paths.synthetic_root(db.DB_PATH))

    def test_synthetic_profiles_are_repeatable_and_related(self):
        for name in ('minimal','edge_cases','scale'):
            a=profile(name);self.assertEqual(a,profile(name))
            customers={r['id'] for r in a['customers']};orders={r['id'] for r in a['orders']}
            for key in bootstrap.COLLECTIONS:
                for row in a.get(key,[]):
                    self.assertTrue(row['id'].startswith(('qa_','test_')));self.assertIs(row['test_record'],True)
                    if row.get('customer_id'):self.assertIn(row['customer_id'],customers)
                    if row.get('order_id'):self.assertIn(row['order_id'],orders)
            self.assertTrue(a['customers'][0]['email'].endswith('@example.invalid'))
        self.assertEqual(len(profile('scale')['worklogs']),3000)

    def test_export_is_external_readonly_and_never_rewrites_bootstrap(self):
        import hashlib
        seed=paths.REPOSITORY/'app/src/main/assets/initial-data.json';before=seed.read_bytes()
        with SyntheticStore() as store:
            db.save_customer(dict(name='QA Exportkunde'))
            initial=Path(db.DB_PATH).read_bytes()
            result=exporter.export_snapshot(db.DB_PATH,store.root/'qa_export.json')
            self.assertEqual(result['counts']['customers'],1)
            self.assertEqual(initial,Path(db.DB_PATH).read_bytes())
            with self.assertRaises(paths.PrivateDataPathError):exporter.export_snapshot(db.DB_PATH,seed)
            with self.assertRaises(ValueError):exporter.export_snapshot(db.DB_PATH,store.root/'qa_export.json')
        self.assertEqual(seed.read_bytes(),before)

    def test_backup_and_media_targets_in_checkout_refused(self):
        import server,photo_storage
        with SyntheticStore():
            with self.assertRaises(paths.PrivateDataPathError):server.create_backup(str(paths.REPOSITORY/'outputs'))
            with self.assertRaises(paths.PrivateDataPathError):photo_storage.store_atomic(paths.REPOSITORY/'data/photos',b'qa',{})
            with self.assertRaises(paths.PrivateDataPathError):db.save_settings({'backup_folder':str(paths.REPOSITORY/'data')})

    def test_nested_drive_media_symlink_is_rejected_before_export(self):
        import server
        with SyntheticStore() as store:
            drive=store.root/'qa_drive';drive.mkdir()
            (drive/'HausGarten-Fotos').symlink_to(paths.REPOSITORY,target_is_directory=True)
            with self.assertRaises(paths.PrivateDataPathError):server.drive_sync(str(drive))


class PrivacyContractTests(unittest.TestCase):
    def test_empty_generator_preserves_nonempty_content_and_never_follows_links(self):
        from tools.empty_bootstrap import generate
        with temporary_directory() as folder:
            root=Path(folder);target=root/'initial-data.json'
            generate(target);before=target.read_bytes()
            with patch.object(Path,'read_bytes',return_value=json.dumps(profile()).encode()):
                with self.assertRaises(ValueError):generate(target)
            self.assertEqual(target.read_bytes(),before)
            target.unlink();sentinel=root/'qa_sentinel.json';sentinel.write_text(json.dumps(bootstrap.BOOTSTRAP)+'  ')
            before=sentinel.read_bytes();target.symlink_to(sentinel)
            with self.assertRaises(ValueError):generate(target)
            self.assertEqual(sentinel.read_bytes(),before)

    def test_nested_git_named_directory_is_not_a_workspace_privacy_escape(self):
        with temporary_directory() as folder:
            root=Path(folder);hidden=root/'qa_nested'/'.git';hidden.mkdir(parents=True)
            (hidden/'qa.sqlite').write_bytes(b'SQLite format 3\0qa')
            with self.assertRaises(privacy.PrivacyFailure):privacy.workspace_gate(root)

    def test_individual_runtime_records_are_blocked_outside_snapshot_collections(self):
        with self.assertRaises(privacy.PrivacyFailure):
            privacy.scan_files({'record.json':json.dumps({'id':'not_qa','customer_id':'not_qa_customer','title':'not_production_fixture'}).encode()})
        privacy.scan_files({'record.json':json.dumps(profile()['worklogs'][0]).encode()})

    def test_retired_markers_are_also_detected_in_compiled_binary_payloads(self):
        import hashlib
        marker='qa retired literal'
        with patch.object(privacy,'RETIRED',{hashlib.sha256(marker.encode()).hexdigest()}),patch.object(privacy,'RETIRED_LENGTHS',{(2,7,7)}):
            with self.assertRaises(privacy.PrivacyFailure):privacy.scan_files({'classes.dex':b'dex\n035\0\xff\0'+marker.encode()+b'\0'})

    def test_manual_node_diagnostics_stay_external_and_contain_no_business_values(self):
        with temporary_directory() as folder:
            module=paths.REPOSITORY/'tests/private-diagnostics.cjs'
            script="const p=require(process.argv[1]);const fs=require('node:fs');const f=p.writeFile(process.argv[2],JSON.stringify({success:true,counts:{customers:1},customer:{name:'QA Never-in-report',email:'qa_hidden@example.invalid'}}));const text=fs.readFileSync(f,'utf8');if(text.includes('Never-in-report')||text.includes('qa_hidden'))throw Error('Data in report');console.log('PASS');"
            target=paths.REPOSITORY/'outputs/qa_forbidden_private_report.json'
            env={**os.environ,'HG_PRIVATE_DATA_ROOT':folder,'HG_TEST_DEVICE_CONFIRMED':'1'}
            result=subprocess.run(['node','-e',script,str(module),str(target)],capture_output=True,text=True,env=env)
            self.assertEqual(result.returncode,0);self.assertFalse(target.exists())

    def test_renamed_private_settings_and_unclassified_archive_photo_fail(self):
        with temporary_directory() as folder:
            root=Path(folder);(root/'qa.blob').write_text(json.dumps({'business_settings':{'owner_name':'NOT_A_REAL_PERSON_UNIT_FIXTURE'}}))
            with self.assertRaises(privacy.PrivacyFailure):privacy.workspace_gate(root)
            p=root/'qa.zip'
            with zipfile.ZipFile(p,'w') as z:z.writestr('photos/qa.jpg',b'qa-unclassified-image')
            with self.assertRaises(privacy.PrivacyFailure):privacy.artifact_gate(p)

    def test_bootstrap_is_exact_empty_contract(self):
        self.assertTrue(bootstrap.verify_bootstrap(json.dumps(bootstrap.BOOTSTRAP)))
        for name in bootstrap.COLLECTIONS:
            value=dict(bootstrap.BOOTSTRAP,**{name:[dict(id='qa_injected',test_record=True)]})
            with self.assertRaises(ValueError):bootstrap.verify_bootstrap(json.dumps(value))
        with self.assertRaises(ValueError):bootstrap.verify_bootstrap(json.dumps(dict(bootstrap.BOOTSTRAP,business_settings={'owner_name':'QA'})))

    def test_direct_and_aliased_seed_writer_detected(self):
        for source in ["p=Path('initial-data.json');p.write_text(payload)","p=Path('assets')/'initial-data.json';q=p;q.write_bytes(raw)","p='initial-data.json';open(p,'w').write(raw)","dest=Path('initial-data.json');shutil.copy2(source,dest)","source.replace(Path('initial-data.json'))"]:
            self.assertTrue(privacy.bootstrap_writers(source))
        self.assertFalse(privacy.bootstrap_writers("p=Path('initial-data.json');p.read_text()"))

    def test_ignored_db_snapshot_renamed_sqlite_and_backup_fail_workspace(self):
        for suffix,raw in [('.db',b'qa'),('.json',json.dumps({'customers':[{'id':'not_synthetic'}]}).encode()),('.blob',b'SQLite format 3\0'+b'qa'),('.tar',b'qa')]:
            with temporary_directory() as folder:
                p=Path(folder);(p/'.gitignore').write_text('*');(p/('qa'+suffix)).write_bytes(raw)
                with self.assertRaises(privacy.PrivacyFailure):privacy.workspace_gate(p)

    def test_secret_diagnostics_contain_no_value(self):
        value=b'sk-'+bytes([120])*35
        with self.assertRaises(privacy.PrivacyFailure) as result:privacy.scan_files({'qa.py':value})
        self.assertNotIn(value.decode(),str(result.exception))
        self.assertIn('SECRET',str(result.exception))

    def test_source_gate_scans_untracked_bootstrap_writer(self):
        with temporary_directory() as folder:
            root=Path(folder);seed=root/'app/src/main/assets';seed.mkdir(parents=True)
            __import__('tools.empty_bootstrap',fromlist=['generate']).generate(seed/'initial-data.json')
            (root/'qa_writer.py').write_text("p=Path('initial-data.json');p.write_text(payload)")
            with self.assertRaises(privacy.PrivacyFailure):privacy.source_gate(root,['qa_writer.py'])

    def test_zip_privacy_and_zero_apk_seed(self):
        with temporary_directory() as folder:
            p=Path(folder)/'qa.apk'
            with zipfile.ZipFile(p,'w') as z:z.writestr('assets/initial-data.json',json.dumps(bootstrap.BOOTSTRAP))
            self.assertEqual(privacy.artifact_gate(p)['operational_seed_rows'],0)
            with zipfile.ZipFile(p,'w') as z:z.writestr('assets/initial-data.json',json.dumps(dict(bootstrap.BOOTSTRAP,customers=[{'id':'private_record'}])))
            with self.assertRaises(privacy.PrivacyFailure):privacy.artifact_gate(p)
            p=Path(folder)/'qa_review.zip'
            with zipfile.ZipFile(p,'w') as z:z.writestr('fixtures.json',json.dumps(profile()))
            self.assertEqual(privacy.artifact_gate(p)['status'],'PASS')

    def test_unclassified_image_and_symlink_fail(self):
        with temporary_directory() as folder:
            root=Path(folder);(root/'qa.jpg').write_bytes(b'qa_image')
            with self.assertRaises(privacy.PrivacyFailure):privacy.workspace_gate(root)
            (root/'qa.jpg').unlink();(root/'qa_link').symlink_to(paths.REPOSITORY,target_is_directory=True)
            with self.assertRaises(privacy.PrivacyFailure):privacy.workspace_gate(root)

    def test_update_audit_reports_hashes_and_counts_only(self):
        spec=importlib.util.spec_from_file_location('qa_private_audit',Path(__file__).with_name('audit_readonly.py'))
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        with SyntheticStore():
            db.save_customer(dict(name='QA Never-in-report',email='qa_hidden@example.invalid'))
            result=module.audit(db.DB_PATH);text=json.dumps(result)
            self.assertEqual(result['counts']['customers'],1);self.assertTrue(result['database_file_unchanged'])
            self.assertNotIn('Never-in-report',text);self.assertNotIn('qa_hidden',text)
