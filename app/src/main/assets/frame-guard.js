/* Bundled app policy: embedded documents are unsupported. This is not an XSS sanitizer. */
(()=>{'use strict';
 if(window.HGFrameGuard)return;
 const forbidden=new Set(['iframe','frame','frameset','object','embed','portal','fencedframe','base']);
 const create=Document.prototype.createElement,remove=Node.prototype.removeChild;
 const inner=Object.getOwnPropertyDescriptor(Element.prototype,'innerHTML');
 const reject=()=>{throw new DOMException('Embedded documents are disabled in this app.','SecurityError');};
 const blocked=node=>node?.nodeType===1&&forbidden.has(String(node.localName).toLowerCase());
 function clean(node){
  if(blocked(node))reject();
  for(const child of [...node.childNodes||[]]){if(blocked(child))remove.call(node,child);else clean(child);}
  if(node.nodeType===1&&String(node.localName).toLowerCase()==='template')clean(node.content);
  return node;
 }
 function html(value){const template=create.call(document,'template');inner.set.call(template,String(value));clean(template.content);return inner.get.call(template);}
 function method(proto,name,transform){if(!proto||typeof proto[name]!=='function')return;const original=proto[name];Object.defineProperty(proto,name,{configurable:true,writable:true,value:function(...args){return Reflect.apply(original,this,transform.call(this,args));}});}
 method(Document.prototype,'createElement',args=>{if(forbidden.has(String(args[0]).toLowerCase()))reject();return args;});
 method(Document.prototype,'createElementNS',args=>{if(forbidden.has(String(args[1]).split(':').pop().toLowerCase()))reject();return args;});
 for(const name of ['appendChild','insertBefore','replaceChild'])method(Node.prototype,name,args=>{if(args[0]&&typeof args[0]==='object')clean(args[0]);return args;});
 for(const proto of [Element.prototype,Document.prototype,DocumentFragment.prototype,window.CharacterData?.prototype,window.DocumentType?.prototype])for(const name of ['append','prepend','replaceChildren','before','after','replaceWith'])method(proto,name,args=>args.map(value=>value&&typeof value==='object'?clean(value):value));
 for(const name of ['importNode','adoptNode'])method(Document.prototype,name,args=>{if(args[0])clean(args[0]);return args;});
 const clone=Node.prototype.cloneNode;Node.prototype.cloneNode=function(...args){return clean(Reflect.apply(clone,this,args));};
 for(const [proto,name]of [[Element.prototype,'innerHTML'],[Element.prototype,'outerHTML'],[window.ShadowRoot?.prototype,'innerHTML']]){if(!proto)continue;const original=Object.getOwnPropertyDescriptor(proto,name);if(!original?.set)continue;Object.defineProperty(proto,name,{...original,set(value){return Reflect.apply(original.set,this,[html(value)]);}});}
 method(Element.prototype,'insertAdjacentHTML',args=>{args[1]=html(args[1]);return args;});
 if(window.Range){method(Range.prototype,'insertNode',args=>{if(args[0])clean(args[0]);return args;});method(Range.prototype,'surroundContents',args=>{if(args[0])clean(args[0]);return args;});const fragment=Range.prototype.createContextualFragment;if(fragment)Range.prototype.createContextualFragment=function(...args){return clean(Reflect.apply(fragment,this,args));};}
 if(window.DOMParser){const parse=DOMParser.prototype.parseFromString;DOMParser.prototype.parseFromString=function(...args){return clean(Reflect.apply(parse,this,args));};}
 for(const proto of [Element.prototype,window.ShadowRoot?.prototype])for(const name of ['setHTML','setHTMLUnsafe'])method(proto,name,args=>{args[0]=html(args[0]);return args;});
 if(typeof Document.parseHTMLUnsafe==='function'){const parse=Document.parseHTMLUnsafe;Document.parseHTMLUnsafe=function(...args){return clean(Reflect.apply(parse,this,args));};}
 // Replacement documents could discard the guard before new parser content executes.
 for(const name of ['open','write','writeln'])if(typeof Document.prototype[name]==='function')Document.prototype[name]=reject;
 for(const constructor of [window.HTMLIFrameElement,window.HTMLFrameElement,window.HTMLObjectElement,window.HTMLEmbedElement]){if(!constructor)continue;for(const name of ['srcdoc','src','data']){const original=Object.getOwnPropertyDescriptor(constructor.prototype,name);if(original?.set)Object.defineProperty(constructor.prototype,name,{...original,set:reject});}}
 method(Element.prototype,'setAttribute',function(args){if(blocked(this))reject();return args;});method(Element.prototype,'setAttributeNS',function(args){if(blocked(this))reject();return args;});
 window.HGFrameGuard=Object.freeze({version:1,policy:'no_embedded_documents'});
})();
