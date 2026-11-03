// Experiment script for YTXTok. Loaded from the repo at app start and on Reload; no rebuild needed.
// v2: draws a small debug panel inside the page that says what covers the video and which parent hides it.
(function () {
  if (window.__ytxDbgTimer) clearInterval(window.__ytxDbgTimer);
  function cls(e) {
    if (!e || !e.tagName) return 'none';
    var c = (typeof e.className === 'string' ? e.className : '').split(' ')[0] || '';
    var d = e.getAttribute && e.getAttribute('data-e2e');
    return e.tagName.toLowerCase() + (c ? '.' + c.slice(-14) : '') + (d ? '[' + d.slice(0, 16) + ']' : '');
  }
  function box(e) {
    var r = e.getBoundingClientRect();
    return Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height);
  }
  function info(e) {
    var s = getComputedStyle(e);
    return cls(e) + ' ' + box(e) + ' d:' + s.display + ' v:' + s.visibility + ' o:' + s.opacity + ' p:' + s.position + ' z:' + s.zIndex +
      ' bg:' + s.backgroundColor.replace('rgba(0, 0, 0, 0)', 'none').replace(/ /g, '') + (s.transform !== 'none' ? ' T' : '') + (s.clipPath !== 'none' ? ' CLIP' : '') + (s.overflow !== 'visible' ? ' ov:' + s.overflow : '');
  }
  function report() {
    var out = [];
    try {
      var root = document.documentElement;
      out.push('html ' + (root.className || '').slice(0, 30) + ' theme:' + root.getAttribute('data-theme') + ' cs:' + getComputedStyle(root).colorScheme + ' dpr' + devicePixelRatio + ' ' + innerWidth + 'x' + innerHeight);
      var vids = [].slice.call(document.querySelectorAll('video'));
      out.push('videos ' + vids.length + ' cards ' + document.querySelectorAll('[data-e2e="video-card"]').length + ' items ' + document.querySelectorAll('[data-e2e="recommend-list-item-container"]').length);
      vids.slice(0, 2).forEach(function (v, i) {
        out.push('V' + i + ' ' + info(v) + ' ' + (v.paused ? 'paused' : 'play') + ' ' + v.videoWidth + 'x' + v.videoHeight);
        var e = v.parentElement, n = 0;
        while (e && e !== root && n < 7) { out.push(' ^' + info(e)); e = e.parentElement; n++; }
      });
      var cx = Math.round(innerWidth / 2), cy = Math.round(innerHeight / 2);
      var st = document.elementsFromPoint(cx, cy);
      out.push('stack@center ' + st.length);
      st.slice(0, 6).forEach(function (e) { out.push(' @' + info(e)); });
    } catch (x) { out.push('dbg error ' + x); }
    return out;
  }
  function draw() {
    var p = document.getElementById('ytx-dbg');
    if (!p) {
      p = document.createElement('pre');
      p.id = 'ytx-dbg';
      p.style.cssText = 'position:fixed;left:0;top:30px;right:0;margin:0;padding:4px;z-index:2147483647;pointer-events:none;font:8px/9px monospace;color:#0f0;background:rgba(0,0,0,.82);white-space:pre-wrap;word-break:break-all;max-height:60vh;overflow:hidden';
      (document.body || document.documentElement).appendChild(p);
    }
    p.textContent = report().join('\n');
  }
  window.__ytxDbgTimer = setInterval(draw, 1500);
  draw();
})();
