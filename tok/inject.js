// Experiment script for YTXTok (v3): paints coloured fixed and in-flow blocks at several heights,
// so a screenshot shows which parts of the web view actually get drawn.
(function () {
  if (window.__ytxDbgTimer) clearInterval(window.__ytxDbgTimer);
  var old = document.getElementById('ytx-dbg'); if (old) old.remove();
  var old2 = document.getElementById('ytx-paint'); if (old2) old2.remove();
  var host = document.createElement('div');
  host.id = 'ytx-paint';
  var cols = ['#e11', '#1b1', '#22f', '#fc0', '#c1c'];
  var html = '';
  for (var i = 0; i < 5; i++) {
    var top = Math.round(innerHeight * (0.1 + 0.2 * i));
    html += '<div style="position:fixed;left:' + (i * 8) + 'px;width:46%;top:' + top + 'px;height:36px;background:' + cols[i] + ';color:#fff;font:bold 14px sans-serif;z-index:2147483647;pointer-events:none">fixed ' + top + 'px</div>';
    html += '<div style="position:absolute;right:' + (i * 8) + 'px;width:40%;top:' + (top + 40) + 'px;height:30px;background:' + cols[4 - i] + ';color:#fff;font:bold 12px sans-serif;z-index:2147483646;pointer-events:none">abs ' + (top + 40) + 'px</div>';
  }
  host.innerHTML = html;
  (document.body || document.documentElement).appendChild(host);
  window.__ytxDbgTimer = setInterval(function () {
    var h = document.getElementById('ytx-paint');
    if (!h) (document.body || document.documentElement).appendChild(host);
  }, 1500);
})();
