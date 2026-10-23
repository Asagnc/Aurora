package io.github.asagnc.aurora.core

import kotlinx.serialization.json.JsonObject

/** Binary transport only. File/Blob creation still has whole-file storage cost in the renderer. */
object BrowserTransferJavascript {
    fun setup(id: String, request: JsonObject, timeoutMs: Long): String {
        require(timeoutMs in 1..270000)
        return """
        (()=>{
          const id=${string(id)}, key='__aurora_transfer_'+id, request=$request;
          let port=null, reader=null, timer=null, stopped=false, resolveAck=null, rejectAck=null;
          const abort=new AbortController();
          const cleanup=()=>{
            if(stopped)return; stopped=true; clearTimeout(timer); window.removeEventListener('message',start);
            abort.abort(); if(reader)Promise.resolve(reader.cancel()).then(()=>reader.releaseLock()).catch(()=>{});
            parts=[];files=[];if(port)port.onmessage=null;
            if(rejectAck)rejectAck(Error('Transfer closed')); if(port)port.close(); delete window[key];
          };
          const send=value=>{if(stopped)throw Error('Transfer closed');port.postMessage(JSON.stringify(value));};
          const wait=()=>new Promise((resolve,reject)=>{resolveAck=resolve;rejectAck=reject;});
          const acknowledged=async value=>{const pending=wait();send(value);await pending;};
          const fail=error=>{try{send({kind:'error',error:String(error).slice(0,500)});}finally{cleanup();}};
          async function download(){
            const signal=abort.signal;
            const value=await (async()=>eval(request.script))();
            if(stopped)return;
            let source;
            if(value instanceof Response){
              if(!value.ok || value.type==='opaque')throw Error('Response is not readable or successful');
              source=value.body || new ReadableStream({start(controller){controller.close();}});
            }else if(value instanceof Blob){source=value.stream();}
            else throw Error('Script must return Response or Blob');
            reader=source.getReader();
            await acknowledged({kind:'ready'});
            let total=0;
            while(!stopped){
              const item=await reader.read(); if(item.done)break;
              if(!(item.value instanceof Uint8Array))throw Error('Expected byte stream');
              for(let offset=0;offset<item.value.byteLength;offset+=65536){
                const size=Math.min(65536,item.value.byteLength-offset);
                if(total+size>request.maxBytes)throw Error('Download byte budget exceeded');
                const chunk=item.value.slice(offset,offset+size);
                const pending=wait();port.postMessage(chunk.buffer,[chunk.buffer]);await pending;total+=size;
              }
            }
            await acknowledged({kind:'complete',bytes:total});cleanup();
          }
          let files=[],parts=[],index=0,total=0;
          async function uploadMessage(data){
            if(stopped)return;
            const item=request.files[index];
            if(data instanceof ArrayBuffer){
              if(!item || data.byteLength<1 || data.byteLength>65536 || total+data.byteLength>item.size)throw Error('Invalid upload chunk');
              total+=data.byteLength;parts.push(data);send({kind:'ack'});return;
            }
            if(typeof data!=='string' || data.length>1024)throw Error('Invalid upload control');
            const message=JSON.parse(data);
            if(message.kind==='fileEnd'){
              if(!item || total!==item.size)throw Error('Incomplete upload file');
              files.push(new File(parts,item.name,{type:item.mime}));parts=[];total=0;index++;send({kind:'ack'});return;
            }
            if(message.kind!=='finish' || index!==request.files.length || parts.length)throw Error('Invalid upload completion');
            const input=uploadTarget();
            const transfer=new DataTransfer();for(const file of files)transfer.items.add(file);
            input.files=transfer.files;input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));
            send({kind:'complete',files:files.length});files=[];cleanup();
          }
          function uploadTarget(){
            const input=document.querySelector(request.selector);
            if(!(input instanceof HTMLInputElement) || input.type!=='file' || input.disabled)throw Error('Target is not an enabled file input');
            if(request.files.length>1 && !input.multiple)throw Error('Input does not allow multiple files');
            const accept=input.accept.split(',').map(x=>x.trim().toLowerCase()).filter(Boolean);
            for(const item of request.files){
              if(accept.length && !accept.some(type=>type.startsWith('.') ? item.name.toLowerCase().endsWith(type) :
                type.endsWith('/*') ? item.mime.toLowerCase().startsWith(type.slice(0,-1)) : item.mime.toLowerCase()===type))throw Error('File does not match input accept');
            }
            return input;
          }
          function start(event){
            if(stopped || port || event.data!==id || !event.ports || event.ports.length!==1)return;
            window.removeEventListener('message',start);port=event.ports[0];
            if(request.mode==='download'){
              port.onmessage=event=>{
                if(event.data!=='ack' || !resolveAck){fail(Error('Unexpected acknowledgement'));return;}
                const resolve=resolveAck;resolveAck=null;rejectAck=null;resolve();
              };port.start();download().catch(error=>{if(!stopped)fail(error);});
            }else{
              port.onmessage=event=>{uploadMessage(event.data).catch(error=>{if(!stopped)fail(error);});};
              port.start();try{uploadTarget();send({kind:'ready'});}catch(error){fail(error);}
            }
          }
          Object.defineProperty(window,key,{value:cleanup,configurable:true});
          window.addEventListener('message',start);timer=setTimeout(cleanup,$timeoutMs+1000);return id;
        })()
        """.trimIndent()
    }

    fun cleanup(id: String) = "(()=>{const close=window[${string("__aurora_transfer_$id")}];if(close)close();})()"
}
