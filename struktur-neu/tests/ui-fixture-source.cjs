'use strict';
// Reuse existing synthetic bridges instead of introducing another business fixture.
const fs=require('node:fs'),path=require('node:path'),acorn=require(process.env.HG_ACORN_MODULE||'acorn');
module.exports=function fixtureSource(name){
 const source=fs.readFileSync(path.join(__dirname,name),'utf8'),ast=acorn.parse(source,{ecmaVersion:2022});let found;
 function visit(n){if(!n||typeof n!=='object')return;if(!found&&n.type==='CallExpression'&&n.callee?.property?.name==='addInitScript'&&['ArrowFunctionExpression','FunctionExpression'].includes(n.arguments[0]?.type))found=source.slice(n.arguments[0].start,n.arguments[0].end);for(const v of Object.values(n))if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);}
 visit(ast);if(!found)throw Error('Existing synthetic bridge missing: '+name);return '('+found+')()';
};
