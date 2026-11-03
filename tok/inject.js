// Experiment script for YTXTok (v4): lists, in big readable lines on a solid panel, what the page holds
// and why the feed is not visible (hidden parents, covering elements, text colours).
(function () {
  if (window.__ytxDbgTimer) clearInterval(window.__ytxDbgTimer);
  ['ytx-dbg', 'ytx-paint'].forEach(function (id) { var o = document.getElementById(id); if (o) o.remove(); });
  function cls(e) {
    if (!e || !e.tagName) return 'none';
    var c = (typeof e.className === 'string' ? e.className : '').split(' ')[0] || '';
    var d = e.getAttribute && e.getAttribute('data-e2e');
    return e.tagName.toLowerCase() + (c ? '.' + c.slice(-12) : '') + (d ? '[' + d.slice(0, 14) + ']' : '');
  }
  function box(e) {
    var r = e.getBoundingClientRect();
    return Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height);
  }
  function st(e) {
    var s = getComputedStyle(e);
    return cls(e) + ' ' + box(e) + ' d:' + s.display + ' v:' + s.visibility + ' o:' + s.opacity + ' p:' + s.position +
      ' bg:' + s.backgroundColor.replace('rgba(0, 0, 0, 0)', '-').replace(/ /g, '') + ' c:' + s.color.replace(/ /g, '') +
      (s.filter !== 'none' ? ' F' : '') + (s.transform !== 'none' ? ' T' : '');
  }
  function lines() {
    var L = [];
    try {
      var b = document.body, h = document.documentElement;
      L.push('html ' + st(h));
      L.push('body ' + st(b));
      L.push('kids ' + [].slice.call(b.children).slice(0, 6).map(cls).join(' '));
      var root = b.querySelector('#app,#root,#__next,[id^="app"]') || b.firstElementChild;
      if (root) { L.push('root ' + st(root)); [].slice.call(root.children).slice(0, 4).forEach(function (c) { L.push(' >' + st(c)); }); }
      L.push('text: ' + (b.innerText || '').replace(/\s+/g, ' ').slice(0, 90));
      var vids = [].slice.call(document.querySelectorAll('video'));
      L.push('videos ' + vids.length);
      if (vids[0]) {
        L.push('V ' + st(vids[0]));
        var e = vids[0].parentElement, n = 0;
        while (e && e !== h && n < 6) { L.push(' ^' + st(e)); e = e.parentElement; n++; }
      }
      var cx = Math.round(innerWidth / 2), cy = Math.round(innerHeight / 2);
      L.push('stack@center:');
      document.elementsFromPoint(cx, cy).slice(0, 5).forEach(function (x) { L.push(' @' + st(x)); });
    } catch (x) { L.push('error ' + x); }
    return L;
  }
  function draw() {
    var p = document.getElementById('ytx-dbg');
    if (!p) {
      p = document.createElement('div');
      p.id = 'ytx-dbg';
      p.style.cssText = 'position:fixed;left:0;right:0;bottom:90px;z-index:2147483647;pointer-events:none;background:#000;border-top:2px solid #0f0;padding:2px';
      (document.body || document.documentElement).appendChild(p);
    }
    var html = '';
    lines().forEach(function (t) {
      html += '<div style="font:10px/11px monospace;color:#0f0;white-space:nowrap;overflow:hidden;text-overflow:clip">' + t.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</div>';
    });
    p.innerHTML = html;
  }
  window.__ytxDbgTimer = setInterval(draw, 2000);
  draw();
})();
