/* Binary-safe private backup. Never restores, clears or changes device data. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const adb = path.join(root, 'android-toolchain/sdk/platform-tools/adb.exe');
const [serial, target] = process.argv.slice(2);
const app = 'at.hausgarten.service';
if (!serial || !target || serial.startsWith('emulator-')) throw Error('A physical device serial and backup target are required.');
if(process.env.HG_TEST_DEVICE_CONFIRMED!=='1')throw Error('Explicit manual device consent required.');
const {privateRoot,businessPath}=require('./runtime-paths.cjs');
const destination = businessPath(path.resolve(target));
const backupRoot = path.join(privateRoot(), 'backups/pixel-usb') + path.sep;
if (!destination.startsWith(backupRoot) || fs.existsSync(destination)) throw Error('Refusing an existing target or a target outside the private backup folder.');
function run(args, options = {}) {
  return execFileSync(adb, ['-s', serial, ...args], {encoding:'utf8', timeout:60000, windowsHide:true, maxBuffer:16*1024*1024, ...options});
}
if (run(['get-state']).trim() !== 'device') throw Error('Device not authorized.');
if (run(['shell','getprop','ro.product.model']).trim() !== 'Pixel 10 Pro') throw Error('Unexpected phone.');
try {
  if (run(['shell','pidof',app]).trim()) throw Error('APP_RUNNING');
} catch (error) {
  if (error.message === 'APP_RUNNING' || error.status !== 1) throw error;
}
// Toybox tar streams the app sandbox, including SQLite journal and WebView state.
// No shell redirection: Windows PowerShell must not decode/re-encode these bytes.
const archive = run(['exec-out','run-as',app,'tar','-cf','-','.'], {encoding:null, maxBuffer:256*1024*1024});
if (archive.length < 1024) throw Error('Backup stream is unexpectedly empty.');
fs.mkdirSync(path.dirname(destination), {recursive:true});
fs.writeFileSync(destination, archive, {flag:'wx',mode:0o600});
console.log(JSON.stringify({backup_path_sha256:crypto.createHash('sha256').update(destination).digest('hex'), bytes:archive.length, sha256:crypto.createHash('sha256').update(archive).digest('hex')}));
