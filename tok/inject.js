// Experiment script for YTXTok (v6): mirrors every video onto a canvas that sits right above it.
// The video decodes (a canvas can read its frames) but the web view does not paint it, so the canvas
// shows the picture instead, and TikTok's own captions and buttons stay where they are.
(function () {
  if (window.__ytxMirV === 'v9') return;
  window.__ytxMirV = 'v9';
  ['ytx-dbg', 'ytx-paint'].forEach(function (id) { var o = document.getElementById(id); if (o) o.remove(); });
  if (window.__ytxDbgTimer) { clearInterval(window.__ytxDbgTimer); window.__ytxDbgTimer = 0; }
  var M = window.__ytxMir;
  if (M && M.stop) M.stop();
  M = window.__ytxMir = { n: 0, f: 0, err: '', alive: true };
  M.stop = function () { M.alive = false; clearInterval(M.timer); clearInterval(M.tick); [].forEach.call(document.querySelectorAll('canvas[data-ytx-mirror]'), function (c) { c.remove(); }); [].forEach.call(document.querySelectorAll('video'), function (v) { v.__ytxC = null; }); };

  function attach(v) {
    if (v.__ytxC) return;
    var c = document.createElement('canvas');
    c.setAttribute('data-ytx-mirror', '1');
    c.style.pointerEvents = 'none';
    c.style.position = 'absolute';
    c.style.boxShadow = 'inset 0 0 0 2px #0ff';
    v.__ytxC = c;
    v.parentNode.insertBefore(c, v.nextSibling);
  }
  function place(v, c) {
    var cs = getComputedStyle(v);
    var s = c.style;
    var l = v.offsetLeft + 'px', t = v.offsetTop + 'px', w = v.offsetWidth + 'px', h = v.offsetHeight + 'px';
    if (s.left !== l) s.left = l;
    if (s.top !== t) s.top = t;
    if (s.width !== w) s.width = w;
    if (s.height !== h) s.height = h;
    if (s.objectFit !== cs.objectFit) s.objectFit = cs.objectFit;
    if (s.objectPosition !== cs.objectPosition) s.objectPosition = cs.objectPosition;
    if (s.zIndex !== cs.zIndex && cs.zIndex !== 'auto') s.zIndex = cs.zIndex;
  }
  function tick() {
    if (!M.alive) return;
    try {
      var vids = document.querySelectorAll('video');
      var n = 0;
      for (var i = 0; i < vids.length; i++) {
        var v = vids[i];
        if (!v.parentNode) continue;
        if (!v.__ytxC) attach(v);
        var c = v.__ytxC;
        if (!c.isConnected) { v.parentNode.insertBefore(c, v.nextSibling); }
        n++;
        var vw = v.videoWidth, vh = v.videoHeight;
        if (!vw || !vh) continue;
        place(v, c);
        if (c.width !== vw || c.height !== vh) { c.width = vw; c.height = vh; }
        if (c.__t !== v.currentTime || !c.__d) {
          c.getContext('2d').drawImage(v, 0, 0, vw, vh);
          c.__t = v.currentTime; c.__d = true; M.f++;
        }
      }
      M.n = n;
    } catch (e) { M.err = String(e).slice(0, 60); }
  }
  M.tick = setInterval(tick, 40);
  M.raf = 0;
  (function loop() { M.raf++; if (M.alive) requestAnimationFrame(loop); })();
  // The page replaces whole video lists as it scrolls; drop canvases whose video is gone.
  M.timer = setInterval(function () {
    [].forEach.call(document.querySelectorAll('canvas[data-ytx-mirror]'), function (c) {
      var p = c.previousSibling;
      if (!p || p.tagName !== 'VIDEO') c.remove();
    });
  }, 1000);

  // ---- layout panel: where the video sits and which ancestor could be clipping it ----
  function cls(e) {
    if (!e || !e.tagName) return 'none';
    var c = (typeof e.className === 'string' ? e.className : '').split(' ')[0] || '';
    var d = e.getAttribute && e.getAttribute('data-e2e');
    return e.tagName.toLowerCase() + (e.id ? '#' + e.id.slice(0, 8) : '') + (c ? '.' + c.slice(-10) : '') + (d ? '[' + d.slice(0, 12) + ']' : '');
  }
  function st(e) {
    var s = getComputedStyle(e), r = e.getBoundingClientRect();
    return cls(e) + ' ' + Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height) +
      ' d:' + s.display + ' p:' + s.position + ' ov:' + s.overflowX + '/' + s.overflowY + ' h:' + s.height + ' mh:' + s.maxHeight +
      (e.getAttribute('style') ? ' st:' + e.getAttribute('style').slice(0, 40) : '') +
      (s.contain !== 'none' ? ' ct:' + s.contain : '') + (s.contentVisibility !== 'visible' ? ' cv:' + s.contentVisibility : '') +
      (s.clipPath !== 'none' ? ' CLIP' : '') + (s.transform !== 'none' ? ' T' : '') + (s.opacity !== '1' ? ' o:' + s.opacity : '');
  }
  function lines() {
    var L = [];
    try {
      var h = document.documentElement, b = document.body;
      L.push('html ' + st(h)); L.push('body ' + st(b));
      L.push('inner ' + innerWidth + 'x' + innerHeight + ' client ' + h.clientWidth + 'x' + h.clientHeight + ' vv ' + (window.visualViewport ? Math.round(window.visualViewport.width) + 'x' + Math.round(window.visualViewport.height) : '-'));
      var v = document.querySelector('video');
      if (v) { L.push('V ' + st(v)); var e = v.parentElement, n = 0; while (e && e !== h && n < 14) { L.push(' ^' + st(e)); e = e.parentElement; n++; } }
      var c = document.querySelector('canvas[data-ytx-mirror]');
      if (c) L.push('C ' + st(c));
    } catch (x) { L.push('err ' + x); }
    return L;
  }
  function draw() {
    var p = document.getElementById('ytx-dbg');
    if (!p) {
      p = document.createElement('div'); p.id = 'ytx-dbg';
      p.style.cssText = 'position:fixed;left:0;right:0;bottom:60px;z-index:2147483647;pointer-events:none;background:rgba(0,0,0,.9);border-top:2px solid #0f0;padding:2px';
      (document.body || document.documentElement).appendChild(p);
    }
    var html = '';
    lines().forEach(function (t) { html += '<div style="font:9px/10px monospace;color:#0f0;white-space:nowrap;overflow:hidden">' + t.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</div>'; });
    p.innerHTML = html;
  }
  window.__ytxDbgTimer = setInterval(draw, 2000);
  draw();

  var old = window.__ytxDiag;
  if (typeof old === 'function' && !old.__m) {
    var w = function () { var L = window.__ytxMir || {}; return old() + ' mir' + L.n + '/' + L.f + ' raf' + L.raf + ' vis:' + document.visibilityState + ' foc' + (document.hasFocus() ? 1 : 0) + (L.err ? ' E:' + L.err : ''); };
    w.__m = true;
    window.__ytxDiag = w;
  }
})();
