package io.github.asagnc.aurora.core

/** Page-world code only. It never exposes a native method or authenticates page content. */
object BrowserJavascript {
    private val encode = """value => {
        const encoded = JSON.stringify(value === undefined ? null : value);
        if (typeof encoded !== 'string') throw Error('Result is not JSON serializable');
        const truncated = encoded.length > 30000;
        let prefix = truncated ? encoded.slice(0,15000) : null;
        if (prefix && /[\uD800-\uDBFF]$/.test(prefix)) prefix = prefix.slice(0,-1);
        return JSON.stringify({ok:true,value:truncated ? prefix : JSON.parse(encoded),truncated});
    }""".trimIndent()
    private val failure = """e => JSON.stringify({ok:false,error:String(e?.message || e).slice(0,500)})"""

    fun synchronous(script: String) = """(() => {
        const encode = ($encode), failure = ($failure);
        try {
            const value = (0,eval)(${string(script)});
            if (value && typeof value.then === 'function') {
                Promise.resolve(value).catch(() => {});
                throw Error('Promise returned with awaitPromise=false; execution may have started. Inspect outcome before retrying with awaitPromise=true');
            }
            return encode(value);
        } catch (e) { return failure(e); }
    })()""".trimIndent()

    fun setup(id: String, script: String, timeoutMs: Long): String {
        require(timeoutMs in 1..30000)
        return """(() => {
            const id = ${string(id)}, key = ${string(key(id))};
            const encode = ($encode), failure = ($failure);
            let port = null, done = false, started = false, timer;
            const cleanup = () => {
                done = true;
                window.removeEventListener('message', receive);
                clearTimeout(timer);
                if (port) port.close();
                delete window[key];
            };
            const send = value => {
                if (done) return;
                try { port.postMessage(value); } finally { cleanup(); }
            };
            const receive = event => {
                if (done || started || event.data !== id || event.ports.length !== 1) return;
                started = true;
                window.removeEventListener('message', receive);
                port = event.ports[0];
                Promise.resolve().then(() => (0,eval)(${string(script)}))
                    .then(value => { if (!done) send(encode(value)); })
                    .catch(error => { if (!done) send(failure(error)); });
            };
            Object.defineProperty(window,key,{value:cleanup,configurable:true});
            window.addEventListener('message',receive);
            timer = setTimeout(cleanup,${timeoutMs + 1000});
            return id;
        })()""".trimIndent()
    }

    fun cleanup(id: String) = """(() => { const key = ${string(key(id))}; if (typeof window[key] === 'function') window[key](); })()"""
    private fun key(id: String) = "__aurora_async_$id"
}
