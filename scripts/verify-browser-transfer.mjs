import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { MessageChannel } from 'node:worker_threads';
import { setTimeout as wait } from 'node:timers/promises';

// Production page scripts, actual Node File/Blob/Response/streams and MessageChannel.
// DOM classes below are fixtures, not proof of WebView input/form compatibility.
const cases = JSON.parse(readFileSync(0, 'utf8'));
globalThis.window = globalThis;
const listeners = new Set();
window.addEventListener = (_, fn) => listeners.add(fn);
window.removeEventListener = (_, fn) => listeners.delete(fn);
class Input { type='file'; disabled=false; multiple=true; accept=''; files=null; events=[];
  dispatchEvent(event) { this.events.push(event.type); return true; }
}
globalThis.HTMLInputElement = Input;
globalThis.DataTransfer = class { files=[]; items={add:file=>this.files.push(file)}; };
let passed = 0;
for (const item of cases) {
  const input = new Input(); input.accept=item.accept || ''; input.multiple=item.multiple !== false;
  globalThis.document = {querySelector:()=>input};
  const {port1,port2}=new MessageChannel();
  const queue=[];let pending;
  port1.on('message',value=>{if(pending){const resolve=pending;pending=null;resolve(value);}else queue.push(value);});
  const next=async()=>{
    let timer;
    try{return await Promise.race([queue.length ? Promise.resolve(queue.shift()) : new Promise(resolve=>{pending=resolve;}),
      new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('Missing protocol message')),2000);})]);}
    finally{clearTimeout(timer);}
  };
  const verifyError=value=>{
    assert.deepEqual(Object.keys(value).sort(),['code','kind']);
    const expected={ 'oversize response':'download_budget_exceeded','HTTP failure':'response_unreadable',
      'script rejection':'page_script','wrong result type':'invalid_download_source',
      'reader rejection':'stream_read','unexpected disk acknowledgement':'unexpected_ack',
      'accept mismatch':'input_accept_rejected' };
    assert.equal(value.code,expected[item.name],item.name);
  };
  const control=async kind=>{const value=JSON.parse(await next());assert.equal(value.kind,kind,item.name);
    if(kind==='error')verifyError(value);return value;};
  try {
    assert.equal((0,eval)(item.code),item.id);
    const receive=[...listeners][0]; receive({data:'wrong',ports:[port2]});assert.equal(listeners.size,1);
    receive({data:item.id,ports:[port2]});receive({data:item.id,ports:[port2]});assert.equal(listeners.size,0);
    if(item.mode==='upload') {
      if(item.error)await control('error');
      else {
        await control('ready');
        for(const size of item.sizes){
          for(let offset=0;offset<size;offset+=65536){
            const bytes=Uint8Array.from({length:Math.min(65536,size-offset)},(_,i)=>(offset+i)%251);
            port1.postMessage(bytes.buffer,[bytes.buffer]);await control('ack');
          }
          port1.postMessage(JSON.stringify({kind:'fileEnd'}));await control('ack');
        }
        port1.postMessage(JSON.stringify({kind:'finish'}));assert.equal((await control('complete')).files,item.sizes.length);
        assert.deepEqual(input.events,['input','change']);assert.equal(input.files.length,item.sizes.length);
        for(let i=0;i<input.files.length;i++){
          const bytes=new Uint8Array(await input.files[i].arrayBuffer());
          assert.deepEqual(bytes,Uint8Array.from({length:item.sizes[i]},(_,i)=>i%251));
        }
      }
    } else if(item.mode==='cancel') {
      await control('ready'); // Deliberately withhold ACK, then cancel locally.
      (0,eval)(item.cleanup);await wait(15);assert.equal(queue.length,0);
    } else if(item.mode==='badAck') {
      await control('ready');port1.postMessage('wrong');await control('error');
    } else {
      const first=await next();const ready=JSON.parse(first);
      if(ready.kind==='error'){assert.equal(item.error,true);verifyError(ready);}
      else {
        assert.equal(ready.kind,'ready');
        assert.deepEqual(Object.keys(ready).sort(),['disposition','kind','mime']);
        assert.ok(ready.mime.length<=128 && ready.disposition.length<=512);
        if(item.name==='response metadata'){
          assert.equal(ready.mime,'application/pdf');assert.equal(ready.disposition,'attachment; filename=report.pdf');
        }
        if(item.name==='oversize metadata discarded'){
          assert.equal(ready.mime,'');assert.equal(ready.disposition,'');
        }
        port1.postMessage('ack');
        const chunks=[];
        while(true){
          const message=await next();
          if(message instanceof ArrayBuffer){
            assert.ok(message.byteLength>0 && message.byteLength<=65536);chunks.push(Buffer.from(message));
            await wait(2);assert.equal(queue.length,0,'No second chunk before disk ACK');port1.postMessage('ack');
          } else {
            const result=JSON.parse(message);
            if(item.error){assert.equal(result.kind,'error');verifyError(result);}
            else {assert.equal(result.kind,'complete');assert.equal(result.bytes,item.size);
              assert.deepEqual(Buffer.concat(chunks),Buffer.from(Uint8Array.from({length:item.size},(_,i)=>i%251)));port1.postMessage('ack');}
            break;
          }
        }
      }
    }
    await wait(10);assert.equal(typeof window[`__aurora_transfer_${item.id}`],'undefined');
    assert.equal(listeners.size,0);console.log(`PASS ${item.name}`);passed++;
  } finally {(0,eval)(item.cleanup);port1.close();port2.close();}
}
console.log(`Browser binary JavaScript: ${passed} cases passed`);
