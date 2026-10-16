// Static regression guards for camera/timer integration; no customer data or device needed.
const fs=require('node:fs');
const assert=require('node:assert/strict');
const source=fs.readFileSync('android/app/src/main/assets/workday-ui.js','utf8');
const html=fs.readFileSync('android/app/src/main/assets/mobile.html','utf8');
assert.match(html,/id="photo"[^>]*type="file"[^>]*accept="image\/\*"[^>]*capture="environment"/);
assert.match(source,/function configureWorkPhotoCamera\(\)/);
assert.match(source,/id='work-photo-camera-shortcut'/);
assert.match(source,/if\(!get\('customer'\)\?\.value\)/);
assert.match(source,/input\.click\(\)/);
assert.match(source,/if\(action==='photo'\)/);
assert.match(source,/if\(session\.customer_id&&customer/);
assert.match(source,/if\(!customer\?\.value\)/);
assert.match(source,/get\('work-photo-camera-shortcut'\)\?\.click\(\)/);
assert.match(source,/active\|\|paused\?'<button type="button" class="timer-secondary" data-session-action="photo"/);
assert.match(source,/if\(action==='pause'\)next=sessionCall\('pauseWorkSession'/);
assert.match(source,/if\(action==='stop'\)next=sessionCall\('stopWorkSession'/);
console.log('Camera/timer static regression guards: PASS (not a device/UI test)');
