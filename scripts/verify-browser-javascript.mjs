import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { MessageChannel } from 'node:worker_threads';
import { setTimeout as wait } from 'node:timers/promises';

// Executes emitted production page-world JS. This is not a WebView origin/transfer test.
const cases = JSON.parse(readFileSync(0, 'utf8'));
globalThis.window = globalThis;
const listeners = new Set();
window.addEventListener = (name, listener) => { assert.equal(name, 'message'); listeners.add(listener); };
window.removeEventListener = (name, listener) => { assert.equal(name, 'message'); listeners.delete(listener); };
let passed = 0;
for (const item of cases) {
    if (item.mode === 'sync') {
        const raw = (0, eval)(item.code);
        assert.ok(raw.length <= 32000, `${item.name}: envelope exceeds budget`);
        const result = JSON.parse(raw);
        if (item.error) assert.equal(result.ok, false, item.name);
        else {
            assert.equal(result.ok, true, item.name);
            if (item.truncated) {
                assert.equal(result.truncated, true, item.name);
                assert.ok(!/[\uD800-\uDBFF]$/.test(result.value), item.name);
            } else assert.deepEqual(result.value, item.expected, item.name);
        }
    } else {
        const { port1, port2 } = new MessageChannel();
        const messages = [];
        port1.on('message', value => messages.push(value));
        try {
            assert.equal((0, eval)(item.code), item.id);
            assert.equal(listeners.size, 1);
            const receive = [...listeners][0];
            receive({ data: 'wrong-operation', ports: [port2] });
            assert.equal(listeners.size, 1, 'Wrong operation consumed receiver');
            receive({ data: item.id, ports: [port2] });
            receive({ data: item.id, ports: [port2] });
            assert.equal(listeners.size, 0);
            if (item.mode === 'cancel') {
                await wait(5);
                (0, eval)(item.cleanup);
                globalThis.__auroraResolveLate(42);
                await wait(20);
                assert.deepEqual(messages, [], 'Cancelled script delivered a late result');
                delete globalThis.__auroraResolveLate;
            } else if (item.mode === 'pending') {
                await wait(1150);
                assert.deepEqual(messages, []);
                assert.equal(typeof window[`__aurora_async_${item.id}`], 'undefined');
            } else {
                for (let i = 0; messages.length === 0 && i < 100; i++) await wait(5);
                assert.equal(messages.length, 1, `${item.name}: missing/duplicate completion`);
                assert.ok(messages[0].length <= 32000);
                const result = JSON.parse(messages[0]);
                assert.equal(result.ok, !item.error, item.name);
                if (!item.error) assert.deepEqual(result.value, item.expected, item.name);
            }
            assert.equal(listeners.size, 0);
            assert.equal(typeof window[`__aurora_async_${item.id}`], 'undefined');
        } finally {
            (0, eval)(item.cleanup);
            port1.close(); port2.close();
        }
    }
    console.log(`PASS ${item.name}`);
    passed++;
}
console.log(`Browser page-world JavaScript: ${passed} cases passed`);
