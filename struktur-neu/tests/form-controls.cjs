'use strict';
// Optional controls stay in their original form. Open their actual disclosures
// before exercising an existing workflow; required-field visibility is not relaxed.
async function revealOptional(locator){
 const disclosures=await locator.evaluate(field=>{let out=[];for(let n=field.parentElement;n;n=n.parentElement)if(n.tagName==='DETAILS'&&!n.open){if(!n.id)n.id='qa_disclosure_'+crypto.randomUUID();out.unshift(n.id);}return out;});
 for(const id of disclosures)await locator.page().locator('#'+id+'>summary').click();
}
module.exports=async function fillOptional(locator,value){await revealOptional(locator);await locator.fill(value);};
module.exports.reveal=revealOptional;
