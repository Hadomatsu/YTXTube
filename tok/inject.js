// Experiment script for YTXTok (v11): no overlays. Only adds the measured height of 100vh to the
// diagnostic line, to confirm the page now gets a real viewport height.
(function () {
  if (window.__ytxMir && window.__ytxMir.stop) window.__ytxMir.stop();
  if (window.__ytxDbgTimer) { clearInterval(window.__ytxDbgTimer); window.__ytxDbgTimer = 0; }
  ['ytx-dbg', 'ytx-paint'].forEach(function (id) { var o = document.getElementById(id); if (o) o.remove(); });
  var old = window.__ytxDiag;
  if (typeof old !== 'function' || old.__vh) return;
  var w = function () {
    var vh = '?';
    try {
      var d = document.createElement('div');
      d.style.cssText = 'position:fixed;left:0;top:0;width:1px;height:100vh;visibility:hidden';
      document.body.appendChild(d); vh = d.offsetHeight; d.remove();
    } catch (e) {}
    return old() + ' vh' + vh + '/' + innerHeight;
  };
  w.__vh = true;
  window.__ytxDiag = w;
})();
