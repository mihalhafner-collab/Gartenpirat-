'use strict';
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const window={},document={addEventListener(){}};vm.runInNewContext(fs.readFileSync('android/app/src/main/assets/business-feedback.js','utf8'),{window,document,Date});
const f=window.HGBusinessFeedback;
for(const [e,text] of [['SQLITE_FULL','Nicht gespeichert'],['WRITE_CONFLICT expected_record_hash','inzwischen'],['HTTP 401','Freigabe'],['READ_ERROR provider_readback','nicht abgeschlossen']])assert.match(f.error(e),new RegExp(text,'i'));
assert.equal(f.diagnostics().length,4);assert.equal(f.error('Pflichtangabe fehlt: business_street'),'Bitte Geschäftsanschrift ergänzen.');assert.match(f.diagnostics().join(' '),/expected_record_hash/);
assert.match(f.sync({configured:true,last_error:'IOException'}).title,/nicht möglich/);
assert.doesNotMatch(JSON.stringify(f.sync({configured:true,last_error:'IOException',user_action_required:true})),/Wir versuchen/);
assert.match(f.sync({configured:true,review:7}).title,/Prüfung/);
assert.match(f.sync({configured:true,last_sync:'2026-10-20',confirmation_scope:'provider_readback'}).detail,/vollständige Sicherung.*nicht bestätigt/);
assert.match(f.sync({configured:true,cloud_confirmed:true,last_sync:'2026-10-20'}).title,/abgeglichen/);
assert.doesNotMatch(JSON.stringify(f.sync({configured:true,cloud_confirmed:false})),/gesichert/);
assert.match(f.sync({configured:false}).title,/nicht verbunden/);
assert.match(f.sync({configured:true,pending:2}).title,/gespeichert/);
assert.match(f.connection({running:true,state:'ready'}),/verbunden/);assert.match(f.connection({running:true,state:'reconnecting'}),/geprüft/);assert.match(f.connection({state:'auth_error'}),/freigeben/);
console.log('PASS P3 business feedback: evidence distinctions, friendly errors, retained diagnostics');

assert.doesNotMatch(f.error('UNRECOGNIZED_BACKEND_STATE action_hash '+ 'a'.repeat(64)),/UNRECOGNIZED|action_hash|aaaa/);
assert.match(f.diagnostics().at(-1),/UNRECOGNIZED_BACKEND_STATE/);
assert.doesNotMatch(JSON.stringify(f.sync({configured:true,cloud_confirmed:true,pending:1})),/abgeglichen|gesichert/);

for(const field of ['request_id','price_source','client_id','provider_readback']){assert.doesNotMatch(f.error('Ungültiges Feld: '+field),new RegExp(field));assert.match(f.diagnostics().at(-1),new RegExp(field));}

// The final local storage contract never treats the old business queue as backup proof.
const local={drive_role:'backup_only',storage_contract_version:3,pending:999,review:12,syncing:true,configured:true,cloud_confirmed:true,last_sync:'2027-05-18T19:00:00Z'};
assert.equal(f.backupOnly(local),true);assert.equal(f.backupOnly({storage_contract_version:3}),true);assert.doesNotMatch(JSON.stringify(f.sync(local)),/999|12|Synchronisierung|abgeglichen|Drive verbinden|gesichert/);assert.match(f.sync(local).detail,/keine.*bestätigt/);
assert.match(f.sync({...local,full_backup:{mode:'PIXEL_PLAINTEXT_ZIP'}}).title,/ausstehend/);
assert.match(f.sync({...local,full_backup:{jobs:[{phase:'LOCAL_VERIFIED'}]}}).detail,/Noch keine vollständig gespeicherte/);
assert.match(f.sync({...local,full_backup:{jobs:[{phase:'FAILED'}]}}).title,/prüfen/);
const proof={last_local_verified_at:'2027-05-18T19:00:00Z',last_local_captured_at:'2027-05-17T18:00:00Z'};
assert.match(f.sync({...local,full_backup:proof}).title,/Pixel geprüft/);assert.match(f.sync({...local,full_backup:proof}).detail,/enthaltener Datenstand/);
assert.match(f.sync({...local,full_backup:{...proof,clock_review_required:true}}).title,/Sicherungszeit prüfen/);
assert.doesNotMatch(f.sync({...local,full_backup:{last_local_verified_at:'not a date'}}).title,/Pixel geprüft/);
assert.match(f.sync({...local,full_backup:{last_external_verified_at:'2027-05-18T19:00:00Z',last_external_captured_at:'2027-05-18T19:00:00Z'}}).title,/ausstehend/);
assert.doesNotMatch(JSON.stringify(f.sync({...local,full_backup:proof})),/Drive|Upload|Passwort/);
console.log('PASS local ZIP presentation: historical cloud flags are not local save proof; readback/date and pending/failure separated');
