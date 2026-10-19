/* Stateless native-owner adapter. Drafts, versions and request IDs belong to each workflow. */
(()=>{
 'use strict';
 function create(fallback){
  return function call(name,args={}){
   const response=JSON.parse(AndroidData.ownerCall(name,JSON.stringify(args)));
   if(!response.ok)throw new Error(response.error?.message||fallback);
   return response.data;
  };
 }
 const clone=value=>JSON.parse(JSON.stringify(value));
 window.HGOwnerClient=Object.freeze({create,clone});
})();
