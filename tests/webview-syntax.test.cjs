/* Parse every shipped JS and inline HTML script against the supported WebView grammar. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const acorn=require(process.env.HG_ACORN_MODULE||require.resolve('acorn',{paths:[process.cwd(),path.dirname(process.env.HG_PLAYWRIGHT_MODULE||process.cwd())]}));
const root=path.resolve('android/app/src/main/assets');let count=0;
for(const name of fs.readdirSync(root)){
 if(!/\.(?:js|html)$/.test(name))continue;
 const source=fs.readFileSync(path.join(root,name));
 if(name.endsWith('.js')){acorn.parse(source.toString(),{ecmaVersion:2020,sourceFile:name});count++;}
 if(name.endsWith('.html'))for(const match of source.toString().matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi)){
  if(/\bsrc\s*=|\btype\s*=\s*["'](?:application\/json|application\/ld\+json)/i.test(match[1]))continue;
  acorn.parse(match[2],{ecmaVersion:2020,sourceFile:name});count++;
 }
}
assert(count>30);console.log('PASS ES2020 grammar for '+count+' operative scripts, including deferred modules and inline scripts');
