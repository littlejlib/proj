package littlejlib.proj.report;

/**
 * The page's only script: single-key navigation, the side panel, and highlighting an item's neighbours in the graph.
 * It reads the graph from the SVG itself ({@code data-from}/{@code data-to} on each edge), so no data is embedded twice.
 */
final class ReportScript {

    private ReportScript() { }

    static final String JS = """
            (function(){
            const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
            const side = document.createElement('aside'); side.id = 'side'; side.hidden = true; document.body.appendChild(side);
            const ins = {}, outs = {};
            for (const e of $$('path.edge')) { const a = e.dataset.from, b = e.dataset.to; (ins[b] = ins[b] || []).push(a); (outs[a] = outs[a] || []).push(b); }
            const reach = (k, m, seen = new Set()) => { for (const x of m[k] || []) if (!seen.has(x)) { seen.add(x); reach(x, m, seen); } return seen; };
            const domId = k => k.replace(/[^A-Za-z0-9_-]/g, '-');
            let current = null;
            function rows() { return $$('.list li[data-k]').filter(r => r.offsetParent !== null); }
            function mark(r) { $$('.current').forEach(x => x.classList.remove('current')); if (r) { r.classList.add('current'); r.focus(); r.scrollIntoView({block: 'nearest'}); } }
            function highlight(k) {
              const near = k ? new Set([k, ...reach(k, ins), ...reach(k, outs)]) : null;
              for (const n of $$('g.node')) { n.classList.toggle('dim', !!near && !near.has(n.dataset.k)); n.classList.toggle('hot', n.dataset.k === k); }
              for (const e of $$('path.edge')) { const on = near && near.has(e.dataset.from) && near.has(e.dataset.to); e.classList.toggle('hot', !!on); e.classList.toggle('dim', !!near && !on); }
            }
            function open(k) {
              const d = document.getElementById('d-' + domId(k)); if (!d) return;
              current = k; side.innerHTML = ''; const c = d.cloneNode(true); c.hidden = false; c.removeAttribute('id'); side.appendChild(c); side.hidden = false;
              highlight(k); history.replaceState(null, '', '#' + domId(k));
            }
            function close() { side.hidden = true; current = null; highlight(null); history.replaceState(null, '', location.pathname + location.search); }
            function press(key) { const b = side.hidden ? null : side.querySelector('[data-key="' + key + '"]'); if (!b) return false; if (b.tagName === 'TEXTAREA') b.focus(); else b.click(); return true; }
            document.addEventListener('click', e => {
              if (e.target.closest('form, a, button, input, textarea, select')) return;
              const r = e.target.closest('[data-k]'); if (r) open(r.dataset.k);
            });
            document.addEventListener('keydown', e => {
              if (e.ctrlKey || e.altKey || e.metaKey) return;
              const t = e.target, typing = t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT';
              if (e.key === 'Escape') { if (typing) { t.blur(); side.focus && side.focus(); } else if (!$('#new').hidden) $('#new').hidden = true; else close(); e.preventDefault(); return; }
              if (typing) return;
              const all = rows(), at = all.indexOf(document.activeElement.closest ? document.activeElement.closest('li[data-k]') : null);
              const k = e.key;
              if (k === 'j' || k === 'k') { const i = at < 0 ? 0 : Math.max(0, Math.min(all.length - 1, at + (k === 'j' ? 1 : -1))); mark(all[i]); e.preventDefault(); return; }
              if (k === 'Enter') { const r = document.activeElement.closest && document.activeElement.closest('[data-k]'); if (r) { open(r.dataset.k); e.preventDefault(); } return; }
              const jump = {w: 'w', s: 's', p: 'p', g: 'g', l: 'l'}[k];
              if (jump && !current) { const first = $('[data-panel="' + jump + '"] li[data-k]'); if (first) { mark(first); e.preventDefault(); } return; }
              if (k === '/') { const f = $('#f-with'); f.focus(); f.select(); e.preventDefault(); return; }
              if (k === 'n') { const n = $('#new'); if (n) { n.hidden = false; $('#new-title').focus(); e.preventDefault(); } return; }
              if (k === 'r') { location.reload(); return; }
              if (k === '?') { $('#help').scrollIntoView(); return; }
              if (press(k)) e.preventDefault();
            });
            const h = location.hash.slice(1); if (h) { const r = $$('[data-k]').find(x => domId(x.dataset.k) === h); if (r) { open(r.dataset.k); mark($$('.list li[data-k]').find(x => x.dataset.k === r.dataset.k)); } }
            })();
            """;
}
