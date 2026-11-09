'use strict';
/* Offline dictionary UI. Native bridge: window.Dict (status, dicts, search, detail). */

var API = window.Dict;
var $ = function (id) { return document.getElementById(id); };
var DICTS = [], DMAP = {}, enabled = null;
var stack = ['home'];
var lastResults = [];
var searchTimer = null, curQuery = '';

function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }

function esc(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
/* {english} -> highlighted span (OVDP marks English text with braces) */
function braces(s) {
  return esc(s).replace(/\{([^{}]*)\}/g, function (m, x) { return x.trim() ? '<span class="en">' + x + '</span> ' : ''; });
}
function plain(s) { return String(s || '').replace(/\{([^{}]*)\}\s*/g, function (m, x) { return x.trim() ? x.trim() + ' · ' : ''; }).replace(/[{}]/g, '').replace(/\s+/g, ' ').trim(); }

/* ---------------- renderers ---------------- */
function renderOvdp(body, hw) {
  var lines = body.split('\n'), out = [];
  for (var i = 0; i < lines.length; i++) {
    var ln = lines[i].replace(/\s+$/, '');
    if (!ln.trim()) continue;
    var c = ln.charAt(0), rest = ln.slice(1).trim();
    if (c === '@') {
      var low = rest.toLowerCase(), h = String(hw).toLowerCase();
      if (low.indexOf(h) === 0) {
        var pr = rest.slice(hw.length).trim();
        if (pr) out.push('<div class="pron">' + braces(pr) + '</div>');
      } else {
        out.push('<div class="field">' + braces(rest) + '</div>');
      }
    } else if (c === '*') {
      out.push('<div class="pos">' + braces(rest) + '</div>');
    } else if (c === '-') {
      if (rest && rest !== '▷') out.push('<div class="m">' + braces(rest) + '</div>');
    } else if (c === '=') {
      var p = rest.indexOf('+');
      if (p >= 0) {
        out.push('<div class="ex"><span class="s">' + braces(rest.slice(0, p).replace(/^ㆍ\s*/, '')) +
          '</span> <span class="t">' + braces(rest.slice(p + 1).replace(/^\s*:\s*/, '')) + '</span></div>');
      } else out.push('<div class="ex"><span class="s">' + braces(rest.replace(/^ㆍ\s*/, '')) + '</span></div>');
    } else if (c === '!') {
      out.push('<div class="idiom">' + braces(rest) + '</div>');
    } else {
      out.push('<div class="m">' + braces(ln.trim()) + '</div>');
    }
  }
  return out.join('');
}

function wordLink(w) { return '<a class="w" data-w="' + esc(w) + '">' + esc(w) + '</a>'; }

function renderCedict(o) {
  var h = '';
  if (o.t && o.t.length) h += '<div class="hint">번체 ' + o.t.map(wordLink).join(', ') + '</div>';
  (o.r || []).forEach(function (r) {
    h += '<div class="py">' + esc(r[0]) + '</div><ol class="defs">';
    (r[1] || []).forEach(function (d) { h += '<li>' + esc(d) + '</li>'; });
    h += '</ol>';
  });
  return h;
}

var KPOS = { n: '명사', v: '동사', a: '형용사', adv: '부사', pron: '대명사', propn: '고유명사', intj: '감탄사', det: '관형사', part: '조사', num: '수사', abbrev: '약어', suf: '접미사', pref: '접두사' };
function renderKedict(o) {
  var h = o.r ? '<div class="pron">' + esc(o.r) + '</div>' : '';
  (o.s || []).forEach(function (s) {
    if (s.p) h += '<div class="pos">' + esc(KPOS[s.p] || s.p) + '</div>';
    h += '<ol class="defs">';
    (s.d || []).forEach(function (d) { h += '<li>' + esc(d) + '</li>'; });
    h += '</ol>';
    (s.e || []).forEach(function (e) {
      h += '<div class="ex"><span class="s">' + esc(e[0]) + '</span> <span class="t">' + esc(e[1]) + '</span></div>';
    });
  });
  return h;
}

function renderList(o) {
  var h = '';
  if (o.h) h += '<div class="hint">한자 ' + esc(o.h) + '</div>';
  if (o.k && o.k.length) {
    h += '<div class="items">';
    o.k.forEach(function (it) {
      h += '<div class="it">' + wordLink(it.w) + (it.p ? ' <span class="py" style="display:inline">' + esc(it.p) + '</span>' : '') +
        (it.n ? '<div class="note">' + esc(it.n) + '</div>' : '') + '</div>';
    });
    h += '</div>';
  }
  (o.x || []).forEach(function (x) { h += '<div class="m">' + esc(x) + '</div>'; });
  return h;
}

function renderBody(fmt, body, hw) {
  try {
    if (fmt === 'ovdp') return renderOvdp(body, hw);
    var o = JSON.parse(body);
    if (fmt === 'cedict') return renderCedict(o);
    if (fmt === 'kedict') return renderKedict(o);
    return renderList(o);
  } catch (e) { return '<div class="m">' + esc(body) + '</div>'; }
}

function preview(fmt, body) {
  try {
    if (fmt === 'ovdp') {
      var ms = [];
      body.split('\n').forEach(function (ln) {
        if (ln.charAt(0) === '-' && ms.length < 3) {
          var t = plain(ln.slice(1)).replace(/^\(Tech\)\s*/, '');
          if (t && t !== '▷') ms.push(t);
        }
      });
      return ms.join('; ');
    }
    var o = JSON.parse(body);
    if (fmt === 'cedict') return o.r.map(function (r) { return r[0] + ' ' + r[1].slice(0, 2).join('; '); }).join(' | ');
    if (fmt === 'kedict') return o.s.map(function (s) { return s.d.slice(0, 2).join('; '); }).join(' | ');
    var parts = (o.k || []).slice(0, 5).map(function (it) { return it.w; });
    if (!parts.length && o.x) parts = o.x.slice(0, 2);
    return parts.join(', ');
  } catch (e) { return ''; }
}

/* ---------------- dictionaries / settings ---------------- */
function enabledCsv() { return Array.from(enabled).join(','); }

function loadDicts() {
  DICTS = JSON.parse(API.dicts());
  DMAP = {};
  DICTS.forEach(function (d) { DMAP[d.id] = d; });
  var saved = store('enabled');
  enabled = new Set(saved ? JSON.parse(saved) : DICTS.map(function (d) { return d.id; }));
  var ul = $('dictList');
  ul.innerHTML = DICTS.map(function (d) {
    return '<li><label for="dc' + d.id + '"><span class="badge">' + esc(d.name) + '</span> <span class="cnt">' +
      d.cnt.toLocaleString() + '개</span></label><input type="checkbox" id="dc' + d.id + '" data-id="' + d.id + '"' +
      (enabled.has(d.id) ? ' checked' : '') + '></li>';
  }).join('');
  ul.querySelectorAll('input').forEach(function (cb) {
    cb.addEventListener('change', function () {
      var id = +cb.getAttribute('data-id');
      if (cb.checked) enabled.add(id); else enabled.delete(id);
      store('enabled', JSON.stringify(Array.from(enabled)));
    });
  });
}

function setFont(px) {
  document.documentElement.style.setProperty('--fs', px + 'px');
  store('fs', String(px));
  document.querySelectorAll('#fontSeg button').forEach(function (b) {
    b.classList.toggle('on', +b.getAttribute('data-f') === +px);
  });
}

/* ---------------- views ---------------- */
function show(view, push) {
  ['loading', 'home', 'results', 'detail', 'settings'].forEach(function (v) { $(v).classList.toggle('hidden', v !== view); });
  if (push && (view === 'detail' || stack[stack.length - 1] !== view)) stack.push(view);
  $('backBtn').classList.toggle('hidden', stack.length <= 1 || view === 'home');
  window.scrollTo(0, 0);
}

window.appBack = function () {
  if (stack.length > 1) {
    var cur = stack.pop();
    if (cur === 'detail') detailHistory.pop();
    var v = stack[stack.length - 1];
    if (v === 'detail' && detailHistory.length) renderDetail(detailHistory[detailHistory.length - 1]);
    if (v !== 'detail') detailHistory = [];
    if (v === 'settings') loadDicts();
    show(v, false);
    if (v === 'results' || v === 'home') rerunSearch();
    return true;
  }
  if ($('q').value) { $('q').value = ''; onInput(); return true; }
  return false;
};

function renderHome() {
  var hist = [];
  try { hist = JSON.parse(store('hist') || '[]'); } catch (e) {}
  $('recentWrap').classList.toggle('hidden', !hist.length);
  $('recent').innerHTML = hist.map(function (g, i) { return '<li data-i="' + i + '"><span class="hw">' + esc(g.h) + '</span></li>'; }).join('');
  $('recent').querySelectorAll('li').forEach(function (li) {
    li.addEventListener('click', function () { openGroup(hist[+li.getAttribute('data-i')]); });
  });
}

function addHist(g) {
  var hist = [];
  try { hist = JSON.parse(store('hist') || '[]'); } catch (e) {}
  hist = hist.filter(function (x) { return x.h !== g.h; });
  hist.unshift({ h: g.h, k: g.k });
  store('hist', JSON.stringify(hist.slice(0, 40)));
}

function doSearch(q) {
  curQuery = q;
  if (!q.trim()) { stack = ['home']; renderHome(); show('home', false); return; }
  var res = JSON.parse(API.search(q, enabledCsv()));
  lastResults = res;
  $('noRes').classList.toggle('hidden', res.length > 0);
  $('resList').innerHTML = res.map(function (g, i) {
    var badges = g.d.map(function (id) { return DMAP[id] ? '<span class="badge">' + esc(DMAP[id].name) + '</span>' : ''; }).join('');
    var pv = g.p && DMAP[g.p.d] ? preview(DMAP[g.p.d].fmt, g.p.b) : '';
    return '<li data-i="' + i + '"><div><span class="hw">' + esc(g.h) + '</span>' + badges + '</div>' +
      (pv ? '<div class="pv">' + esc(pv) + '</div>' : '') + '</li>';
  }).join('');
  $('resList').querySelectorAll('li').forEach(function (li) {
    li.addEventListener('click', function () { openGroup(lastResults[+li.getAttribute('data-i')]); });
  });
  stack = ['home', 'results'];
  show('results', false);
}
function rerunSearch() { if ($('q').value.trim()) doSearch($('q').value); else { renderHome(); } }

function onInput() {
  var q = $('q').value;
  $('clearBtn').classList.toggle('hidden', !q);
  clearTimeout(searchTimer);
  searchTimer = setTimeout(function () { doSearch(q); }, 120);
}

var detailHistory = [];
function renderDetail(g) {
  var rows = JSON.parse(API.detail(g.k, g.h, enabledCsv()));
  $('dHw').textContent = rows.length ? rows[0].h : g.h;
  if (!rows.length) { $('dBody').innerHTML = '<p class="empty">선택한 사전에 이 단어가 없습니다.</p>'; return; }
  $('dBody').innerHTML = rows.map(function (r) {
    var d = DMAP[r.d] || { name: '?', fmt: 'ovdp' };
    return '<div class="sec"><span class="badge">' + esc(d.name) + '</span>' + renderBody(d.fmt, r.b, r.h) + '</div>';
  }).join('');
  $('dBody').querySelectorAll('a.w').forEach(function (a) {
    a.addEventListener('click', function () { openWord(a.getAttribute('data-w')); });
  });
}

function openGroup(g) {
  if (!g) return;
  addHist(g);
  detailHistory.push(g);
  renderDetail(g);
  show('detail', true);
}

/* Jump to a word (from links in an entry): exact match opens the entry, otherwise show results. */
function openWord(w) {
  var res = JSON.parse(API.search(w, enabledCsv()));
  var lw = w.toLowerCase();
  for (var i = 0; i < res.length; i++) {
    if (res[i].h.toLowerCase() === lw) { openGroup(res[i]); return; }
  }
  $('q').value = w;
  onInput();
}

window.externalSearch = function (q) {
  $('q').value = q;
  $('clearBtn').classList.remove('hidden');
  stack = ['home'];
  openWord(q);
};

/* ---------------- start ---------------- */
function boot() {
  setFont(+(store('fs') || 17));
  $('q').addEventListener('input', onInput);
  $('q').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') { $('q').blur(); if (lastResults.length) openGroup(lastResults[0]); }
  });
  $('clearBtn').addEventListener('click', function () { $('q').value = ''; onInput(); $('q').focus(); });
  $('backBtn').addEventListener('click', function () { window.appBack(); });
  $('setBtn').addEventListener('click', function () { show('settings', true); });
  $('clearHist').addEventListener('click', function () { store('hist', '[]'); renderHome(); });
  document.querySelectorAll('#fontSeg button').forEach(function (b) {
    b.addEventListener('click', function () { setFont(+b.getAttribute('data-f')); });
  });
  poll();
}

function poll() {
  var st;
  try { st = JSON.parse(API.status()); } catch (e) { st = { state: 'error', error: String(e) }; }
  if (st.state === 'ready') {
    loadDicts();
    $('ver').textContent = '데이터 버전 ' + (st.version || '');
    renderHome();
    show('home', false);
    if (st.query) window.externalSearch(st.query);
    else $('q').focus();
    return;
  }
  if (st.state === 'error') { $('loadMsg').textContent = '오류: ' + st.error; return; }
  $('loadMsg').textContent = '사전 준비 중… ' + (st.progress || 0) + '%';
  $('barFill').style.width = (st.progress || 0) + '%';
  setTimeout(poll, 250);
}

if (typeof module !== 'undefined') {
  module.exports = { renderBody: renderBody, preview: preview };
} else {
  boot();
}
