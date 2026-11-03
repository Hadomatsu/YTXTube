// Experiment script for YTXTok (v6): mirrors every video onto a canvas that sits right above it.
// The video decodes (a canvas can read its frames) but the web view does not paint it, so the canvas
// shows the picture instead, and TikTok's own captions and buttons stay where they are.
(function () {
  if (window.__ytxMirV === 'v8') return;
  window.__ytxMirV = 'v8';
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

  var old = window.__ytxDiag;
  if (typeof old === 'function' && !old.__m) {
    var w = function () { var L = window.__ytxMir || {}; return old() + ' mir' + L.n + '/' + L.f + ' raf' + L.raf + ' vis:' + document.visibilityState + ' foc' + (document.hasFocus() ? 1 : 0) + (L.err ? ' E:' + L.err : ''); };
    w.__m = true;
    window.__ytxDiag = w;
  }
})();
