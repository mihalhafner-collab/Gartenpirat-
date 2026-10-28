'use strict';
const specification=require('./fixtures/synthetic-business.json');
function profile(name='minimal'){
 if(!specification.profiles.includes(name))throw Error('Unknown synthetic profile');
 const out=JSON.parse(JSON.stringify(specification.minimal));
 if(name==='edge_cases'){out.customers[0].name='QA Musterkunde ÄÖÜ < & >';out.customers[0].custom_fields={qa_multiline:'QA\nMehrzeilig'};}
 if(name==='scale')out.worklogs=Array.from({length:specification.scale_rows},(_,i)=>({...out.worklogs[0],id:'qa_work_'+String(i).padStart(5,'0')}));
 return out;
}
module.exports={profile};
