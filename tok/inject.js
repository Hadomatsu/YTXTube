// Experiment script for YTXTok. Loaded from the repo at app start and on Reload; no rebuild needed.
// Adds a second diagnostic line: what sits on top of the video, and which parent hides or fades it.
(function () {
  function cls(e) {
    if (!e) return 'none';
    var c = (typeof e.className === 'string' ? e.className : '').split(' ')[0] || '';
    return e.tagName.toLowerCase() + (c ? '.' + c.slice(0, 22) : '') + (e.getAttribute('data-e2e') ? '[' + e.getAttribute('data-e2e').slice(0, 18) + ']' : '');
  }
  function extra() {
    try {
      var out = [];
      var cx = Math.round(innerWidth / 2), cy = Math.round(innerHeight / 2);
      out.push('top@c ' + cls(document.elementFromPoint(cx, cy)));
      out.push('body bg ' + getComputedStyle(document.body).backgroundColor);
      var vids = [].slice.call(document.querySelectorAll('video')).filter(function (x) {
        var r = x.getBoundingClientRect();
        return r.height > 0 && r.top < innerHeight * 0.6 && r.bottom > innerHeight * 0.4;
      });
      var v = vids[0];
      if (v) {
        var r = v.getBoundingClientRect();
        out.push('vrect ' + Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height));
        var cs = getComputedStyle(v);
        out.push('vcss op' + cs.opacity + ' vis' + cs.visibility + ' d' + cs.display + ' fit' + cs.objectFit);
        var e = v.parentElement, bad = '';
        while (e && e !== document.documentElement) {
          var s = getComputedStyle(e);
          var rr = e.getBoundingClientRect();
          if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) < 0.05 || rr.width === 0 || rr.height === 0 || e.hasAttribute('data-ytx-wall') || e.hasAttribute('data-ytx-hide') || e.hasAttribute('data-ytx-nav')) {
            bad = 'HIDDEN by ' + cls(e) + ' d' + s.display + ' v' + s.visibility + ' o' + s.opacity + ' ' + Math.round(rr.width) + 'x' + Math.round(rr.height) + (e.hasAttribute('data-ytx-wall') ? ' WALL' : '') + (e.hasAttribute('data-ytx-nav') ? ' NAV' : '') + (e.hasAttribute('data-ytx-hide') ? ' HIDE' : '');
            break;
          }
          e = e.parentElement;
        }
        out.push(bad || 'ancestors visible');
      }
      out.push('wall ' + document.querySelectorAll('[data-ytx-wall]').length + ' nav ' + document.querySelectorAll('[data-ytx-nav]').length + ' hide ' + document.querySelectorAll('[data-ytx-hide]').length);
      return out.join(' | ');
    } catch (x) { return 'extra error ' + x; }
  }
  var old = window.__ytxDiag;
  if (typeof old !== 'function' || old.__w) return;
  var wrapped = function () { return old() + '\n' + extra(); };
  wrapped.__w = true;
  window.__ytxDiag = wrapped;
})();
