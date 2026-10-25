// Shared helpers for the Home Assistant detail screens. Data comes from ha_detail.json, which Home
// Assistant writes over ADB; window.Ha is the launcher's read-only bridge (HaDetailActivity.Bridge).
(function () {
  var STALE_MS = 15 * 60 * 1000;
  var NAMES = { energy: 'Energie', weather: 'Weer', map: 'Locatie' };

  function read() {
    try {
      if (window.Ha) {
        var t = Ha.data();
        return t ? JSON.parse(t) : null;
      }
      return window.SAMPLE || null;   // opened in a desktop browser for a preview
    } catch (e) {
      return null;
    }
  }

  function age() {
    try { return window.Ha ? Ha.age() : 0; } catch (e) { return -1; }
  }

  function nav(el) {
    var list = ['energy', 'weather', 'map'], on = document.body.dataset.screen;
    try {
      if (window.Ha) {
        var p = Ha.screens().split(';');
        list = p[0].split(',');
      }
    } catch (e) { }
    var h = '<span class="arrow">◀</span>';
    list.forEach(function (s) { h += '<span class="tab' + (s === on ? ' on' : '') + '">' + (NAMES[s] || s) + '</span>'; });
    el.innerHTML = h + '<span class="arrow">▶</span>';
  }

  // Dutch number: 1234.5 -> "1.234,5"
  function num(v, dec) {
    if (v === null || v === undefined || isNaN(v)) return '–';
    var s = Number(v).toFixed(dec || 0).split('.');
    s[0] = s[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return s.join(',');
  }

  function watts(w) {
    w = Math.abs(w || 0);
    return w >= 1000 ? num(w / 1000, w >= 10000 ? 0 : 1) + '<small>kW</small>' : num(w) + '<small>W</small>';
  }

  function time(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d)) return '';
    return ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
  }

  /** Calls render(data) now and every 30 s; shows the empty and stale notices. */
  function start(render) {
    var empty = document.querySelector('.empty'), stale = document.querySelector('.stale');
    function tick() {
      var d = read(), a = age();
      if (empty) empty.classList.toggle('show', !d);
      if (stale) stale.classList.toggle('show', !!d && a > STALE_MS);
      if (d) {
        try { render(d); } catch (e) { console.error(e); }
      }
    }
    tick();
    setInterval(tick, 30000);
  }

  window.HA = { read: read, nav: nav, num: num, watts: watts, time: time, start: start };
})();
