'use strict';
// Host-only parity with runtime_paths.py. No default business directory.
const fs=require('node:fs'),path=require('node:path');
const repository=fs.realpathSync(path.resolve(__dirname,'..'));
function canonical(value){
 if(!path.isAbsolute(value))throw Error('Absolute external private path required.');
 let p=path.resolve(value),tail=[];
 while(!fs.existsSync(p)){tail.unshift(path.basename(p));const parent=path.dirname(p);if(parent===p)throw Error('Invalid path');p=parent;}
 return path.join(fs.realpathSync(p),...tail);
}
function within(p,r){return p===r||p.startsWith(r+path.sep);}
function privateRoot(){
 const value=process.env.HG_PRIVATE_DATA_ROOT;if(!value)throw Error('HG_PRIVATE_DATA_ROOT required before device access.');
 if(within(path.resolve(value),repository))throw Error('Repository aliases forbidden.');
 const p=canonical(value);if(within(p,repository)||within(repository,p))throw Error('Repository data path forbidden.');
 if(!fs.existsSync(p)||!fs.statSync(p).isDirectory()||p===fs.realpathSync(require('node:os').tmpdir()))throw Error('Existing private directory required.');
 if(process.platform!=='win32'&&(fs.statSync(p).mode&0o077))throw Error('Owner-only private root required (0700).');
 return p;
}
function businessPath(value){if(within(path.resolve(value),repository))throw Error('Repository data path forbidden.');const p=canonical(value),r=privateRoot();if(!within(p,r))throw Error('Path outside HG_PRIVATE_DATA_ROOT.');return p;}
module.exports={privateRoot,businessPath};
