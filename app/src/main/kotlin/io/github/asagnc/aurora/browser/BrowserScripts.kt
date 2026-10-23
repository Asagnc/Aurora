package io.github.asagnc.aurora.browser

import io.github.asagnc.aurora.core.string

internal object BrowserScripts {
    val snapshot = """(() => {
        const path = el => {
            const parts = [];
            while (el && el.nodeType === 1) {
                if (el.id) { parts.unshift('#' + CSS.escape(el.id)); break; }
                const tag = el.tagName.toLowerCase();
                const siblings = el.parentElement ? [...el.parentElement.children].filter(x => x.tagName === el.tagName) : [el];
                parts.unshift(tag + ':nth-of-type(' + (siblings.indexOf(el) + 1) + ')');
                el = el.parentElement;
            }
            return parts.join(' > ');
        };
        const all = [...document.querySelectorAll('a[href],button,input,textarea,select,[role="button"]')];
        const visible = all.filter(el => el.getClientRects().length && getComputedStyle(el).visibility !== 'hidden');
        const text = document.body?.innerText || '';
        return {url: location.href, title: document.title, text: text.slice(0,20000), textTruncated: text.length > 20000,
            elements: visible.slice(0,40).map(el => ({selector: path(el).slice(0,1024),
                tag: el.tagName.toLowerCase(), type: el.getAttribute('type'),
                label: (el.getAttribute('aria-label') || el.innerText || el.getAttribute('placeholder') || '').slice(0,160),
                href: el.tagName === 'A' ? el.href.slice(0,1024) : undefined})),
            elementsTruncated: visible.length > 40};
    })()""".trimIndent()

    fun action(selector: String, action: String, value: String) = """(() => {
        const el = document.querySelector(${string(selector)});
        if (!el || !el.isConnected || !el.getClientRects().length) throw Error('Element missing or hidden; observe again');
        if (el.disabled) throw Error('Element disabled');
        const action = ${string(action)};
        if (action === 'click') el.click();
        else if (action === 'scroll') el.scrollIntoView({block:'center', behavior:'instant'});
        else {
            if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement)) throw Error('Element is not a text field');
            const setter = Object.getOwnPropertyDescriptor(el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set;
            setter.call(el, ${string(value)});
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
        }
        return {performed:action};
    })()""".trimIndent()

    fun wrap(script: String) = io.github.asagnc.aurora.core.BrowserJavascript.synchronous(script)
}
