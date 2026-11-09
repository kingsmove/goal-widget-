'use strict';
/* ============ utils ============ */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
const pad = n => String(n).padStart(2, '0');
const dstr = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const todayStr = () => dstr(new Date());
const parseD = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const diffDays = (a, b) => Math.round((parseD(b) - parseD(a)) / 864e5);
const WD = '일월화수목금토';
const fmtD = s => { if (!s) return '날짜 미정'; const d = parseD(s); return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WD[d.getDay()]})`; };
const fmtDs = s => { if (!s) return ''; const d = parseD(s); return `${d.getMonth() + 1}.${d.getDate()}`; };
const ico = (n, cls = 'i') => `<svg class="${cls}"><use href="#i-${n}"/></svg>`;
const num = v => { const n = parseFloat(String(v).replace(/,/g, '')); return isFinite(n) ? n : 0; };

const CUR = { USD: '$', CAD: 'C$', KRW: '₩' };
const fmtKRW = n => Math.round(n).toLocaleString('ko-KR') + '원';
const fmtShortKRW = n => n >= 1e4 ? (Math.round(n / 1e3) / 10).toLocaleString('ko-KR') + '만원' : fmtKRW(n);
const fmtCur = (a, c) => c === 'KRW' ? fmtKRW(a) : CUR[c] + Number(a).toLocaleString('en-US', { minimumFractionDigits: a % 1 ? 2 : 0, maximumFractionDigits: 2 });
const toKRW = (a, c) => c === 'KRW' ? a : a * (num(S.settings.rates[c]) || 0);

/* ============ constants ============ */
const CATS = {
  food: { n: '맛집', c: '#FF6B4A', i: 'food' },
  cafe: { n: '카페', c: '#C0874F', i: 'cup' },
  stay: { n: '숙소', c: '#7B61FF', i: 'bed' },
  see: { n: '관광', c: '#00B386', i: 'star' },
  shop: { n: '쇼핑', c: '#FF4FA3', i: 'bag' },
  move: { n: '이동', c: '#3182F6', i: 'bus' },
  work: { n: '일', c: '#F5A623', i: 'work' },
  etc: { n: '기타', c: '#8B95A1', i: 'dots' },
};
const ECATS = { food: '식비', cafe: '카페·간식', move: '교통', stay: '숙소', see: '관광', shop: '쇼핑', etc: '기타' };
const STATUS = {
  want: { n: '가고 싶음', c: '#3182F6' },
  booked: { n: '예약함', c: '#7B61FF' },
  visited: { n: '다녀옴', c: '#00B386' },
};
const BTYPES = {
  flight: { n: '항공', c: '#3182F6', i: 'plane' },
  bus: { n: '버스', c: '#00B386', i: 'bus' },
  stay: { n: '숙소', c: '#7B61FF', i: 'bed' },
  car: { n: '렌터카', c: '#F5A623', i: 'car' },
  ticket: { n: '티켓', c: '#FF4FA3', i: 'ticket' },
  etc: { n: '기타', c: '#8B95A1', i: 'dots' },
};
const MODS = {
  plan: { n: '일정', i: 'cal', c: '#3182F6' },
  map: { n: '지도', i: 'globe', c: '#0BA5EC' },
  place: { n: '장소', i: 'pin', c: '#FF6B4A' },
  money: { n: '경비', i: 'wallet', c: '#00B386' },
  photo: { n: '사진', i: 'image', c: '#FF4FA3' },
  book: { n: '예약', i: 'ticket', c: '#7B61FF' },
};

/* ============ state ============ */
const KEY = 'trip.v1';
let S;
function seed() {
  const lvcc = uid(), union = uid();
  return {
    v: 1,
    settings: { title: '미국 여행', route: 'LA · 라스베이거스 · 캐나다', start: '', end: '', rates: { USD: 1340, CAD: 970 }, budget: 0, theme: 'auto', cur: 'USD' },
    modules: ['plan', 'map', 'place', 'money', 'photo', 'book'].map(k => ({ k, on: true })),
    places: [
      { id: union, name: 'LA 유니언역 (플릭스버스)', cat: 'move', status: 'want', lat: 34.0562, lng: -118.2365, address: '800 N Alameda St, Los Angeles, CA', memo: '라스베이거스행 플릭스버스 출발지', date: '', url: '', rating: 0 },
      { id: lvcc, name: '라스베이거스 컨벤션 센터', cat: 'work', status: 'booked', lat: 36.1318, lng: -115.1513, address: '3150 Paradise Rd, Las Vegas, NV', memo: 'CES 2027', date: '2027-01-06', url: '', rating: 0 },
    ],
    plans: [
      { id: uid(), date: '', time: '', title: 'LA → 라스베이거스 플릭스버스', cat: 'move', placeId: union, memo: '약 5시간 반. 날짜 정해지면 수정', done: false },
      ...[6, 7, 8, 9].map((d, i) => ({ id: uid(), date: `2027-01-${pad(d)}`, time: '', title: `CES 근무 ${i + 1}일차`, cat: 'work', placeId: lvcc, memo: '', done: false })),
      { id: uid(), date: '2027-01-10', time: '', title: '체크아웃 → 캐나다 이동', cat: 'move', placeId: '', memo: '', done: false },
    ],
    bookings: [], expenses: [], photos: [],
  };
}
function load() {
  try { S = JSON.parse(localStorage.getItem(KEY)); } catch (e) { S = null; }
  if (!S || !S.settings) S = seed();
  const d = seed();
  S.settings = Object.assign({}, d.settings, S.settings);
  S.settings.rates = Object.assign({}, d.settings.rates, S.settings.rates);
  for (const k of ['places', 'plans', 'bookings', 'expenses', 'photos']) if (!Array.isArray(S[k])) S[k] = [];
  if (!Array.isArray(S.modules)) S.modules = d.modules;
  for (const k of Object.keys(MODS)) if (!S.modules.find(m => m.k === k)) S.modules.push({ k, on: true });
  S.modules = S.modules.filter(m => MODS[m.k]);
}
let saveT;
function save() { clearTimeout(saveT); saveT = setTimeout(() => { try { localStorage.setItem(KEY, JSON.stringify(S)); } catch (e) { toast('저장 공간이 부족해요'); } }, 120); }
const byId = (arr, id) => S[arr].find(x => x.id === id);

const ui = { tab: 'home', placeCat: 'all', placeStatus: 'all', mapCat: 'all', tipOpen: false };

/* ============ storage for images ============ */
const idb = (() => {
  let dbp;
  const db = () => dbp || (dbp = new Promise((res, rej) => {
    const r = indexedDB.open('trip-img', 1);
    r.onupgradeneeded = () => { r.result.createObjectStore('img'); r.result.createObjectStore('thumb'); };
    r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error);
  }));
  const op = async (st, mode, fn) => { const d = await db(); return new Promise((res, rej) => { const t = d.transaction(st, mode); const q = fn(t.objectStore(st)); t.oncomplete = () => res(q.result); t.onerror = () => rej(t.error); }); };
  return {
    put: (st, k, v) => op(st, 'readwrite', s => s.put(v, k)),
    get: (st, k) => op(st, 'readonly', s => s.get(k)),
    del: async k => { await op('img', 'readwrite', s => s.delete(k)); await op('thumb', 'readwrite', s => s.delete(k)); },
  };
})();
const urlCache = {};
async function imgURL(id, full) {
  const key = (full ? 'f:' : 't:') + id;
  if (urlCache[key]) return urlCache[key];
  const b = await idb.get(full ? 'img' : 'thumb', id).catch(() => null);
  if (!b) return '';
  return (urlCache[key] = URL.createObjectURL(b));
}
function hydrate(root = document) {
  $$('img[data-thumb]', root).forEach(async el => { const u = await imgURL(el.dataset.thumb); if (u) el.src = u; });
}
function scaleTo(img, max, q) {
  const r = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
  const c = document.createElement('canvas');
  c.width = Math.round(img.naturalWidth * r); c.height = Math.round(img.naturalHeight * r);
  c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
  return new Promise(res => c.toBlob(res, 'image/jpeg', q));
}
async function storeImage(file) {
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = url; });
    const id = uid();
    await idb.put('img', id, await scaleTo(img, 2048, 0.86));
    await idb.put('thumb', id, await scaleTo(img, 480, 0.8));
    return id;
  } finally { URL.revokeObjectURL(url); }
}
async function removeImage(id) { await idb.del(id).catch(() => { }); for (const p of ['f:', 't:']) if (urlCache[p + id]) { URL.revokeObjectURL(urlCache[p + id]); delete urlCache[p + id]; } }

/* ============ native bridge ============ */
const NA = window.Android;
function copyText(t) {
  if (NA && NA.copy) NA.copy(String(t));
  else if (navigator.clipboard) navigator.clipboard.writeText(String(t)).catch(() => { });
  toast('복사했어요');
}
function openURL(u) { if (NA && NA.open) NA.open(u); else window.open(u, '_blank'); }
const gmaps = p => p.lat != null && p.lat !== '' ? `https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lng}` : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent((p.name || '') + ' ' + (p.address || ''))}`;
const gdir = p => p.lat != null && p.lat !== '' ? `https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}` : `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(p.address || p.name)}`;

let toastT;
function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.add('on'); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('on'), 1900); }

/* ============ theme ============ */
function applyTheme() {
  const t = S.settings.theme;
  const dark = t === 'dark' || (t === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  if (NA && NA.setBars) NA.setBars(dark ? '#101013' : '#F2F4F6', !dark);
  if (map) setMapStyle();
}

/* ============ trip range ============ */
function tripRange() {
  const ds = S.plans.map(p => p.date).filter(Boolean).sort();
  return { start: S.settings.start || ds[0] || '', end: S.settings.end || ds[ds.length - 1] || '' };
}
function dayNum(date) { const { start } = tripRange(); return start && date ? diffDays(start, date) + 1 : null; }

/* ============ navigation ============ */
const enabled = () => S.modules.filter(m => m.on).map(m => m.k);
function go(tab) {
  if (tab !== 'home' && !enabled().includes(tab)) tab = 'home';
  ui.tab = tab;
  if (tab !== 'map') setPinMode(false);
  render();
  $('#screen').scrollTop = 0;
}
function renderTabs() {
  const tabs = ['home', ...enabled().slice(0, 4)];
  $('#tabs').innerHTML = tabs.map(k => {
    const m = k === 'home' ? { n: '홈', i: 'home' } : MODS[k];
    return `<button data-a="tab" data-k="${k}" class="${ui.tab === k ? 'on' : ''}">${ico(m.i)}${m.n}</button>`;
  }).join('');
}
function render() {
  renderTabs();
  const isMap = ui.tab === 'map';
  $('#mapScreen').classList.toggle('on', isMap);
  $('#screen').style.display = isMap ? 'none' : '';
  if (isMap) { ensureMap(); return; }
  const fn = { home: vHome, plan: vPlan, place: vPlace, money: vMoney, photo: vPhoto, book: vBook }[ui.tab] || vHome;
  $('#screen').innerHTML = fn();
  hydrate($('#screen'));
  if (ui.tab === 'money') tipCalc();
}
const fab = a => `<button class="fab" data-a="${a}">${ico('plus')}</button>`;
const hd = (t, extra = '') => `<div class="hd"><h1>${esc(t)}</h1>${extra}</div>`;
const empty = (b, s) => `<div class="empty"><b>${b}</b>${s}</div>`;
const catIco = (cat, set = CATS) => { const c = set[cat] || set.etc; return `<div class="ico" style="background:${c.c}">${ico(c.i)}</div>`; };
const tag = (txt, color) => `<span class="tag" style="color:${color};background:${color}1F">${esc(txt)}</span>`;

/* ============ HOME ============ */
function vHome() {
  const st = S.settings, { start, end } = tripRange(), t = todayStr();
  let k = '여행까지', big = '—';
  if (start) {
    const d = diffDays(t, start);
    if (d > 0) { k = st.start ? '출발까지' : '첫 일정까지'; big = `D-${d}`; }
    else if (!end || diffDays(t, end) >= 0) { k = '여행 중'; big = `Day ${1 - d}`; }
    else { k = '여행 끝'; big = '수고했어요'; }
  }
  const rng = start ? `${start.replace(/-/g, '.')}${end && end !== start ? ' – ' + fmtDs(end) : ''}` : '날짜는 설정에서 정할 수 있어요';
  const route = (st.route || '').split(/[·,>\/]/).map(s => s.trim()).filter(Boolean);
  const en = enabled();
  let h = hd(st.title || '여행', `<button class="ib" data-a="settings">${ico('set')}</button>`);
  h += `<div class="hero"><svg class="globe" viewBox="0 0 24 24"><use href="#i-globe"/></svg>
    <div class="k">${k}</div><div class="big">${big}</div><div class="rng">${esc(rng)}</div>
    ${route.length ? `<div class="route">${route.map(r => `<span>${esc(r)}</span>`).join('')}</div>` : ''}</div>`;

  const total = S.expenses.reduce((a, e) => a + toKRW(num(e.amount), e.cur), 0);
  const stats = [];
  if (en.includes('money')) stats.push(['money', '쓴 돈', fmtShortKRW(total)]);
  if (en.includes('place')) stats.push(['place', '저장한 장소', S.places.length + '곳']);
  if (en.includes('photo')) stats.push(['photo', '사진', S.photos.length + '장']);
  if (en.includes('book') && stats.length < 3) stats.push(['book', '예약', S.bookings.length + '건']);
  if (stats.length) h += `<div class="stats" style="grid-template-columns:repeat(${stats.length},1fr)">${stats.map(s => `<button class="stat" data-a="tab" data-k="${s[0]}" style="text-align:left"><div class="sub">${s[1]}</div><div class="v">${s[2]}</div></button>`).join('')}</div>`;

  if (en.includes('plan')) {
    const up = S.plans.filter(p => p.date && p.date >= t && !p.done).sort(planSort).slice(0, 3);
    const und = S.plans.filter(p => !p.date && !p.done);
    const list = up.length ? up : und.slice(0, 3);
    h += `<div class="lbl">${up.length ? '다가오는 일정' : '아직 날짜 없는 일정'}</div>`;
    h += list.length ? `<div class="sec" style="margin-top:0"><div class="list">${list.map(p => planLi(p, true)).join('')}</div></div>`
      : `<div class="sec" style="margin-top:0"><div class="list">${empty('일정이 없어요', '일정 탭에서 추가해보세요')}</div></div>`;
  }
  if (en.includes('photo') && S.photos.length) {
    const ph = [...S.photos].sort((a, b) => (b.date + b.id).localeCompare(a.date + a.id)).slice(0, 12);
    h += `<div class="lbl">최근 사진</div><div class="strip">${ph.map(p => `<img data-thumb="${p.id}" data-a="photo-open" data-id="${p.id}">`).join('')}</div>`;
  }
  h += `<div class="lbl">기능</div><div class="grid">${en.map(k => `<button class="tile" data-a="tab" data-k="${k}"><div class="ico" style="background:${MODS[k].c}">${ico(MODS[k].i)}</div>${MODS[k].n}</button>`).join('')}
    <button class="tile" data-a="settings"><div class="ico" style="background:var(--t4)">${ico('set')}</div>편집</button></div>`;
  return h;
}

/* ============ PLAN ============ */
const planSort = (a, b) => (a.date || '9999').localeCompare(b.date || '9999') || (a.time || '99').localeCompare(b.time || '99');
function planLi(p, withDate) {
  const pl = p.placeId ? byId('places', p.placeId) : null;
  const c = CATS[p.cat] || CATS.etc;
  const sub = [withDate && p.date ? fmtD(p.date) : '', pl ? pl.name : '', p.memo].filter(Boolean).join(' · ');
  return `<div class="li ${p.done ? 'done' : ''}" data-a="plan-edit" data-id="${p.id}">
    <button class="chk ${p.done ? 'on' : ''}" data-a="plan-done" data-id="${p.id}">${p.done ? ico('check', 'i s') : ''}</button>
    ${p.time ? `<div class="time">${esc(p.time)}</div>` : ''}
    <div class="m"><div class="t">${esc(p.title)}</div>${sub ? `<div class="d">${esc(sub)}</div>` : ''}</div>
    ${tag(c.n, c.c)}</div>`;
}
function vPlan() {
  let h = hd('일정');
  if (!S.plans.length) return h + `<div class="sec">${empty('일정이 비어 있어요', '+ 버튼으로 첫 일정을 넣어보세요')}</div>` + fab('plan-add');
  const groups = {};
  [...S.plans].sort(planSort).forEach(p => (groups[p.date || ''] ||= []).push(p));
  const t = todayStr();
  for (const [d, arr] of Object.entries(groups)) {
    const dn = d ? dayNum(d) : null;
    h += `<div class="day"><span class="n">${d ? fmtD(d) : '날짜 미정'}</span>${dn && dn > 0 ? `<span class="s">Day ${dn}</span>` : ''}${d === t ? '<span class="today">오늘</span>' : ''}</div>
      <div class="sec" style="margin-top:0"><div class="list">${arr.map(p => planLi(p)).join('')}</div></div>`;
  }
  return h + fab('plan-add');
}
function planForm(p) {
  const isNew = !p;
  p = p ? { ...p } : { id: uid(), date: ui.lastPlanDate || '', time: '', title: '', cat: 'see', placeId: '', memo: '', done: false };
  const opts = `<option value="">연결 안 함</option>` + S.places.map(x => `<option value="${x.id}" ${x.id === p.placeId ? 'selected' : ''}>${esc(x.name)}</option>`).join('');
  sheet({
    title: isNew ? '일정 추가' : '일정 수정',
    body: `<div class="f"><label>무엇을 하나요</label><input class="in" id="fTitle" value="${esc(p.title)}" placeholder="예: 인앤아웃 점심"></div>
      <div class="f"><label>종류</label>${pick('fCat', CATS, p.cat)}</div>
      <div class="f two"><div><label>날짜</label><input class="in" type="date" id="fDate" value="${esc(p.date)}"></div><div><label>시간</label><input class="in" type="time" id="fTime" value="${esc(p.time)}"></div></div>
      <div class="f"><label>장소 연결</label><select class="in" id="fPlace">${opts}</select></div>
      <div class="f"><label>메모</label><textarea class="in" id="fMemo" placeholder="예약 시간, 준비물 등">${esc(p.memo)}</textarea></div>`,
    foot: `${isNew ? '' : `<button class="btn big red" data-a="plan-del" data-id="${p.id}" style="flex:0 0 90px">삭제</button>`}<button class="btn big pri" data-a="sheet-ok">저장</button>`,
    ok: () => {
      const title = $('#fTitle').value.trim(); if (!title) { toast('내용을 입력해주세요'); return false; }
      Object.assign(p, { title, cat: pickVal('fCat'), date: $('#fDate').value, time: $('#fTime').value, placeId: $('#fPlace').value, memo: $('#fMemo').value.trim() });
      ui.lastPlanDate = p.date;
      upsert('plans', p); toast(isNew ? '일정을 추가했어요' : '저장했어요');
    },
  });
}

/* ============ PLACES ============ */
function vPlace() {
  let h = hd('장소', `<button class="ib" data-a="tab" data-k="map">${ico('globe')}</button>`);
  const cats = Object.keys(CATS).filter(k => S.places.some(p => p.cat === k));
  h += `<div class="chips"><button class="chip ${ui.placeCat === 'all' ? 'on' : ''}" data-a="pcat" data-k="all">전체 ${S.places.length}</button>${cats.map(k => `<button class="chip ${ui.placeCat === k ? 'on' : ''}" data-a="pcat" data-k="${k}"><span class="dot" style="background:${CATS[k].c}"></span>${CATS[k].n} ${S.places.filter(p => p.cat === k).length}</button>`).join('')}</div>`;
  h += `<div class="sec"><div class="seg">${[['all', '전체'], ...Object.entries(STATUS).map(([k, v]) => [k, v.n])].map(([k, n]) => `<button class="${ui.placeStatus === k ? 'on' : ''}" data-a="pstat" data-k="${k}">${n}</button>`).join('')}</div></div>`;
  const list = S.places.filter(p => (ui.placeCat === 'all' || p.cat === ui.placeCat) && (ui.placeStatus === 'all' || p.status === ui.placeStatus))
    .sort((a, b) => (a.date || '9999').localeCompare(b.date || '9999') || a.name.localeCompare(b.name));
  h += `<div class="sec">${list.length ? `<div class="list">${list.map(placeLi).join('')}</div>` : `<div class="list">${empty('저장한 장소가 없어요', '가고 싶은 식당, 묵을 숙소를 모아두면<br>지도에서 한눈에 볼 수 있어요')}</div>`}</div>`;
  return h + fab('place-add');
}
function placeLi(p) {
  const st = STATUS[p.status] || STATUS.want;
  const sub = [p.date ? fmtD(p.date) : '', p.address || p.city || '', p.memo].filter(Boolean).join(' · ');
  const nph = S.photos.filter(x => x.placeId === p.id).length;
  return `<div class="li" data-a="place-open" data-id="${p.id}">${catIco(p.cat)}
    <div class="m"><div class="t">${esc(p.name)}</div><div class="d">${tag(st.n, st.c)} ${esc(sub)}</div></div>
    ${nph ? `<span class="sub">${ico('image', 'i s')}</span>` : ''}<span class="chev">${ico('chev', 'i s')}</span></div>`;
}
function placeDetail(id) {
  const p = byId('places', id); if (!p) return;
  const st = STATUS[p.status] || STATUS.want, c = CATS[p.cat] || CATS.etc;
  const phs = S.photos.filter(x => x.placeId === p.id);
  const plans = S.plans.filter(x => x.placeId === p.id).sort(planSort);
  const exps = S.expenses.filter(x => x.placeId === p.id);
  const spent = exps.reduce((a, e) => a + toKRW(num(e.amount), e.cur), 0);
  sheet({
    title: p.name,
    body: `<div class="row" style="gap:6px;margin-top:2px">${tag(c.n, c.c)}${tag(st.n, st.c)}${p.rating ? tag('★'.repeat(p.rating), '#F5A623') : ''}</div>
      ${p.address ? `<div class="f"><label>주소</label><div class="row"><div style="flex:1;line-height:1.45">${esc(p.address)}</div><button class="ib" style="color:var(--t3)" data-a="copy" data-t="${esc(p.address)}">${ico('copy', 'i s')}</button></div></div>` : ''}
      ${p.date ? `<div class="f"><label>날짜</label>${fmtD(p.date)}</div>` : ''}
      ${p.memo ? `<div class="f"><label>메모</label><div style="white-space:pre-wrap;line-height:1.5">${esc(p.memo)}</div></div>` : ''}
      ${p.url ? `<div class="f"><label>링크</label><button style="color:var(--blue);text-align:left;word-break:break-all" data-a="url" data-u="${esc(p.url)}">${esc(p.url)}</button></div>` : ''}
      <div class="f"><label>상태</label><div class="seg">${Object.entries(STATUS).map(([k, v]) => `<button class="${p.status === k ? 'on' : ''}" data-a="place-status" data-id="${p.id}" data-k="${k}">${v.n}</button>`).join('')}</div></div>
      ${plans.length ? `<div class="f"><label>연결된 일정</label>${plans.map(x => `<div style="padding:4px 0">${x.date ? fmtDs(x.date) + ' ' : ''}${esc(x.title)}</div>`).join('')}</div>` : ''}
      ${exps.length ? `<div class="f"><label>여기서 쓴 돈</label>${fmtKRW(spent)}</div>` : ''}
      ${phs.length ? `<div class="f"><label>사진 ${phs.length}</label><div class="thumbs">${phs.map(x => `<img data-thumb="${x.id}" data-a="photo-open" data-id="${x.id}" data-place="${p.id}">`).join('')}</div></div>` : ''}
      <div class="btns">${p.lat !== '' && p.lat != null ? `<button class="btn" data-a="place-onmap" data-id="${p.id}">${ico('globe', 'i s')}지도</button>` : ''}<button class="btn" data-a="url" data-u="${esc(gdir(p))}">${ico('nav', 'i s')}길찾기</button><button class="btn" data-a="url" data-u="${esc(gmaps(p))}">구글맵</button></div>`,
    foot: `<button class="btn big red" data-a="place-del" data-id="${p.id}" style="flex:0 0 90px">삭제</button><button class="btn big pri" data-a="place-edit" data-id="${p.id}">수정</button>`,
  });
}
let pending = null; // place draft waiting for a map tap
function placeForm(draft, isNew) {
  const p = draft ? { ...draft } : { id: uid(), name: '', cat: 'food', status: 'want', lat: '', lng: '', address: '', memo: '', date: '', url: '', rating: 0 };
  if (isNew === undefined) isNew = !S.places.find(x => x.id === p.id);
  const hasLoc = p.lat !== '' && p.lat != null;
  sheet({
    title: isNew ? '장소 추가' : '장소 수정',
    body: `<div class="f"><label>이름</label><input class="in" id="fName" value="${esc(p.name)}" placeholder="예: In-N-Out Burger"></div>
      <div class="f"><label>위치 찾기</label><div class="row"><input class="in" id="fLoc" placeholder="이름이나 주소로 검색" enterkeyhint="search" style="flex:1"><button class="btn" style="flex:0 0 52px;height:52px" data-a="loc-search">${ico('search', 'i s')}</button></div>
        <div id="fLocRes"></div>
        <div class="coord" id="fCoord">${hasLoc ? `${ico('pin', 'i s')} 위치 저장됨 (${(+p.lat).toFixed(4)}, ${(+p.lng).toFixed(4)})` : '위치가 없으면 지도에 안 보여요'}</div>
        <div class="btns" style="margin-top:8px"><button class="btn" data-a="loc-pin">${ico('globe', 'i s')}지도에서 찍기</button><button class="btn" data-a="loc-me">${ico('nav', 'i s')}현재 위치</button></div></div>
      <div class="f"><label>종류</label>${pick('fCat', CATS, p.cat)}</div>
      <div class="f"><label>상태</label>${pick('fStat', STATUS, p.status)}</div>
      <div class="f"><label>주소</label><input class="in" id="fAddr" value="${esc(p.address)}"></div>
      <div class="f"><label>갈 날짜</label><input class="in" type="date" id="fDate" value="${esc(p.date)}"></div>
      <div class="f"><label>별점 (다녀온 뒤)</label><div class="pick" id="fRate">${[1, 2, 3, 4, 5].map(n => `<button data-a="rate" data-n="${n}" class="${p.rating >= n ? 'on' : ''}">★</button>`).join('')}</div></div>
      <div class="f"><label>링크</label><input class="in" id="fUrl" value="${esc(p.url)}" placeholder="구글맵, 예약 페이지 등" inputmode="url"></div>
      <div class="f"><label>메모</label><textarea class="in" id="fMemo" placeholder="추천 메뉴, 가격대, 체크인 시간 등">${esc(p.memo)}</textarea></div>`,
    foot: `<button class="btn big pri" data-a="sheet-ok">저장</button>`,
    mount: () => {
      $('#fLoc').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); locSearch(); } });
      ui.draft = p;
    },
    ok: () => {
      const name = $('#fName').value.trim(); if (!name) { toast('이름을 입력해주세요'); return false; }
      Object.assign(p, collectPlace());
      p.name = name;
      upsert('places', p); toast(isNew ? '장소를 저장했어요' : '저장했어요');
      if (map) drawMarkers();
    },
  });
}
function collectPlace() {
  const p = ui.draft;
  return Object.assign(p, {
    name: $('#fName').value.trim(), cat: pickVal('fCat'), status: pickVal('fStat'), address: $('#fAddr').value.trim(),
    date: $('#fDate').value, url: $('#fUrl').value.trim(), memo: $('#fMemo').value.trim(),
    rating: $$('#fRate button.on').length,
  });
}
function setDraftLoc(lat, lng, addr, name) {
  const p = ui.draft; if (!p) return;
  p.lat = +(+lat).toFixed(6); p.lng = +(+lng).toFixed(6);
  $('#fCoord').innerHTML = `${ico('pin', 'i s')} 위치 저장됨 (${p.lat.toFixed(4)}, ${p.lng.toFixed(4)})`;
  if (addr && !$('#fAddr').value) $('#fAddr').value = addr;
  if (name && !$('#fName').value) $('#fName').value = name;
}
/* geocoding via Photon (OpenStreetMap) */
function photonLabel(pr) { return [pr.housenumber && pr.street ? pr.housenumber + ' ' + pr.street : pr.street, pr.city || pr.county, pr.state, pr.country].filter(Boolean).join(', '); }
async function photon(q, near) {
  let u = `https://photon.komoot.io/api/?limit=7&q=${encodeURIComponent(q)}`;
  if (near) u += `&lat=${near.lat}&lon=${near.lng}`;
  const r = await fetch(u); if (!r.ok) throw new Error('geo');
  const j = await r.json();
  return j.features.map(f => ({ name: f.properties.name || photonLabel(f.properties), addr: photonLabel(f.properties), lat: f.geometry.coordinates[1], lng: f.geometry.coordinates[0] }));
}
async function reverseGeo(lat, lng) {
  try { const r = await fetch(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lng}`); const j = await r.json(); const f = j.features[0]; return f ? photonLabel(f.properties) : ''; } catch (e) { return ''; }
}
const searchNear = () => { const t = S.places.find(p => p.lat !== '' && p.lat != null); return map ? { lat: map.getCenter().lat, lng: map.getCenter().lng } : t ? { lat: t.lat, lng: t.lng } : { lat: 36.1, lng: -115.2 }; };
let locRes = [];
async function locSearch() {
  const q = $('#fLoc').value.trim() || $('#fName').value.trim(); if (!q) return;
  const box = $('#fLocRes'); box.innerHTML = '<div class="coord">찾는 중…</div>';
  try {
    locRes = await photon(q, searchNear());
    box.innerHTML = locRes.length ? `<div class="sres">${locRes.map((r, i) => `<div class="li" data-a="loc-pick" data-i="${i}"><div class="m"><div class="t" style="font-size:15px">${esc(r.name)}</div><div class="d">${esc(r.addr)}</div></div></div>`).join('')}</div>` : '<div class="coord">결과가 없어요. 영어로 검색해보세요</div>';
  } catch (e) { box.innerHTML = '<div class="coord">인터넷 연결이 필요해요</div>'; }
}

/* ============ MONEY ============ */
function vMoney() {
  const st = S.settings;
  let h = hd('경비');
  const total = S.expenses.reduce((a, e) => a + toKRW(num(e.amount), e.cur), 0);
  const byCur = {}; S.expenses.forEach(e => byCur[e.cur] = (byCur[e.cur] || 0) + num(e.amount));
  const byCat = {}; S.expenses.forEach(e => byCat[e.cat] = (byCat[e.cat] || 0) + toKRW(num(e.amount), e.cur));
  const cats = Object.entries(byCat).sort((a, b) => b[1] - a[1]);
  const card = S.expenses.filter(e => e.pay !== 'cash').reduce((a, e) => a + toKRW(num(e.amount), e.cur), 0);
  h += `<div class="sec"><div class="card"><div class="sub">총 지출</div><div class="money-big">${fmtKRW(total)}</div>
    <div class="sub" style="margin-top:4px">${Object.entries(byCur).map(([c, v]) => fmtCur(Math.round(v * 100) / 100, c)).join(' + ') || '아직 기록이 없어요'}</div>
    ${st.budget > 0 ? `<div class="bar"><i style="width:${Math.min(100, total / st.budget * 100)}%;background:${total > st.budget ? 'var(--red)' : 'var(--blue)'}"></i></div><div class="sub" style="margin-top:8px">예산 ${fmtKRW(st.budget)} 중 ${Math.round(total / st.budget * 100)}% · ${total > st.budget ? fmtKRW(total - st.budget) + ' 초과' : fmtKRW(st.budget - total) + ' 남음'}</div>` : ''}
    ${cats.length ? `<div class="bar">${cats.map(([k, v]) => `<i style="width:${v / total * 100}%;background:${(CATS[k] || CATS.etc).c}"></i>`).join('')}</div>
    <div class="legend">${cats.map(([k, v]) => `<div class="row"><span class="dot" style="background:${(CATS[k] || CATS.etc).c}"></span><span class="x">${ECATS[k] || '기타'}</span><b>${fmtKRW(v)}</b><span class="p">${Math.round(v / total * 100)}%</span></div>`).join('')}</div>
    <div class="sub" style="margin-top:14px">카드 ${fmtKRW(card)} · 현금 ${fmtKRW(total - card)} · 환율 $1=${num(st.rates.USD).toLocaleString()}원</div>` : ''}
  </div></div>`;
  // tip calculator
  h += `<div class="sec"><div class="card" style="padding:16px 18px">
    <button class="row" data-a="tip-toggle" style="width:100%"><div class="ico" style="background:#F5A623;width:36px;height:36px;border-radius:11px">${ico('tip')}</div><div style="flex:1;text-align:left;font-weight:600">팁 계산기</div><span class="chev">${ico(ui.tipOpen ? 'up' : 'down', 'i s')}</span></button>
    ${ui.tipOpen ? `<div class="f"><input class="in amt" id="tipBill" inputmode="decimal" placeholder="$ 계산서 금액" value="${esc(ui.tipBill || '')}"></div>
      <div class="f"><div class="seg" id="tipPct">${[15, 18, 20, 22].map(p => `<button class="${(ui.tipPct || 18) === p ? 'on' : ''}" data-a="tip-pct" data-p="${p}">${p}%</button>`).join('')}</div></div>
      <div id="tipOut"></div>` : ''}
  </div></div>`;
  if (!S.expenses.length) return h + `<div class="sec"><div class="list">${empty('지출 기록이 없어요', '+ 버튼으로 쓴 돈을 기록하면<br>원화로 자동 환산돼요')}</div></div>` + fab('exp-add');
  const groups = {};
  [...S.expenses].sort((a, b) => (b.date || '').localeCompare(a.date || '') || b.id.localeCompare(a.id)).forEach(e => (groups[e.date || ''] ||= []).push(e));
  for (const [d, arr] of Object.entries(groups)) {
    const sum = arr.reduce((a, e) => a + toKRW(num(e.amount), e.cur), 0);
    h += `<div class="day"><span class="n">${fmtD(d)}</span><span class="s" style="margin-left:auto">${fmtKRW(sum)}</span></div>
      <div class="sec" style="margin-top:0"><div class="list">${arr.map(e => {
      const pl = e.placeId ? byId('places', e.placeId) : null;
      return `<div class="li" data-a="exp-edit" data-id="${e.id}">${catIco(e.cat)}<div class="m"><div class="t">${esc(e.memo || ECATS[e.cat] || '지출')}</div><div class="d">${[ECATS[e.cat], e.pay === 'cash' ? '현금' : '카드', pl ? pl.name : ''].filter(Boolean).map(esc).join(' · ')}</div></div>
      <div><div class="r">${fmtCur(num(e.amount), e.cur)}</div>${e.cur !== 'KRW' ? `<div class="d" style="text-align:right;font-size:12px;color:var(--t3);margin-top:2px">${fmtKRW(toKRW(num(e.amount), e.cur))}</div>` : ''}</div></div>`;
    }).join('')}</div></div>`;
  }
  return h + fab('exp-add');
}
function tipCalc() {
  const el = $('#tipOut'); if (!el) return;
  const b = num($('#tipBill').value); ui.tipBill = $('#tipBill').value;
  const p = ui.tipPct || 18, tip = Math.round(b * p) / 100, tot = Math.round((b + tip) * 100) / 100;
  el.innerHTML = b ? `<div class="f"><div class="row" style="justify-content:space-between"><span class="sub">팁 ${p}%</span><b>${fmtCur(tip, 'USD')}</b></div>
    <div class="row" style="justify-content:space-between;margin-top:8px"><span class="sub">합계</span><b style="font-size:20px">${fmtCur(tot, 'USD')}</b></div>
    <div class="row" style="justify-content:space-between;margin-top:4px"><span class="sub"></span><span class="sub">${fmtKRW(toKRW(tot, 'USD'))}</span></div>
    <button class="btn pri" style="width:100%;margin-top:14px" data-a="tip-add" data-v="${tot}">이 금액 경비로 기록</button></div>` : '';
}
function expForm(e, preset) {
  const isNew = !e;
  e = e ? { ...e } : Object.assign({ id: uid(), date: todayStr(), amount: '', cur: S.settings.cur || 'USD', cat: 'food', pay: 'card', memo: '', placeId: '' }, preset || {});
  const CURS = { USD: { n: '$ 달러' }, CAD: { n: 'C$ 캐나다' }, KRW: { n: '₩ 원' } };
  const ECO = Object.fromEntries(Object.entries(ECATS).map(([k, n]) => [k, { n, c: CATS[k].c, i: CATS[k].i }]));
  sheet({
    title: isNew ? '지출 기록' : '지출 수정',
    body: `<div class="f"><input class="in amt" id="fAmt" inputmode="decimal" placeholder="0" value="${esc(e.amount)}"><div class="coord" id="fKRW"></div></div>
      <div class="f">${pick('fCur', CURS, e.cur)}</div>
      <div class="f"><label>분류</label>${pick('fCat', ECO, e.cat)}</div>
      <div class="f"><label>결제</label>${pick('fPay', { card: { n: '카드' }, cash: { n: '현금' } }, e.pay)}</div>
      <div class="f"><label>내용</label><input class="in" id="fMemo" value="${esc(e.memo)}" placeholder="예: 인앤아웃 더블더블"></div>
      <div class="f two"><div><label>날짜</label><input class="in" type="date" id="fDate" value="${esc(e.date)}"></div>
      <div><label>장소</label><select class="in" id="fPlace"><option value="">없음</option>${S.places.map(x => `<option value="${x.id}" ${x.id === e.placeId ? 'selected' : ''}>${esc(x.name)}</option>`).join('')}</select></div></div>`,
    foot: `${isNew ? '' : `<button class="btn big red" data-a="exp-del" data-id="${e.id}" style="flex:0 0 90px">삭제</button>`}<button class="btn big pri" data-a="sheet-ok">저장</button>`,
    mount: () => {
      const upd = () => { const a = num($('#fAmt').value), c = pickVal('fCur'); $('#fKRW').textContent = a && c !== 'KRW' ? '≈ ' + fmtKRW(toKRW(a, c)) : ''; };
      $('#fAmt').addEventListener('input', upd); $('#fCur').addEventListener('click', () => setTimeout(upd)); upd();
      if (isNew && !preset) setTimeout(() => $('#fAmt').focus(), 300);
    },
    ok: () => {
      const a = num($('#fAmt').value); if (!a) { toast('금액을 입력해주세요'); return false; }
      Object.assign(e, { amount: a, cur: pickVal('fCur'), cat: pickVal('fCat'), pay: pickVal('fPay'), memo: $('#fMemo').value.trim(), date: $('#fDate').value, placeId: $('#fPlace').value });
      S.settings.cur = e.cur;
      upsert('expenses', e); toast(isNew ? '기록했어요' : '저장했어요');
    },
  });
}

/* ============ PHOTOS ============ */
function photoSorted() { return [...S.photos].sort((a, b) => (a.date || '').localeCompare(b.date || '') || a.id.localeCompare(b.id)); }
function vPhoto() {
  let h = hd('사진', `<span class="sub" style="margin-right:6px">${S.photos.length}장</span>`);
  if (!S.photos.length) return h + `<div class="sec"><div class="list">${empty('사진이 없어요', '+ 버튼으로 갤러리에서 여러 장 한 번에 올릴 수 있어요.<br>장소와 연결하면 지도에서도 볼 수 있어요')}</div></div>` + fab('photo-add');
  const groups = {};
  photoSorted().reverse().forEach(p => (groups[p.date || ''] ||= []).push(p));
  for (const [d, arr] of Object.entries(groups)) {
    const dn = d ? dayNum(d) : null;
    h += `<div class="day"><span class="n">${fmtD(d)}</span>${dn && dn > 0 ? `<span class="s">Day ${dn}</span>` : ''}<span class="s" style="margin-left:auto">${arr.length}</span></div>
      <div class="pgrid">${arr.map(p => `<div><img data-thumb="${p.id}" data-a="photo-open" data-id="${p.id}"></div>`).join('')}</div>`;
  }
  return h + fab('photo-add');
}
let fileTarget = null;
function pickFiles(target) { fileTarget = target; const f = $('#fileIn'); f.value = ''; f.click(); }
async function onFiles(files) {
  files = [...files]; if (!files.length) return;
  const tgt = fileTarget; fileTarget = null;
  toast(`${files.length}장 올리는 중…`);
  let ok = 0;
  for (const f of files) {
    try {
      const id = await storeImage(f);
      if (tgt && tgt.kind === 'book') { tgt.draft.imgs.push(id); tgt.fresh.push(id); }
      else S.photos.push({ id, date: f.lastModified ? dstr(new Date(f.lastModified)) : todayStr(), caption: '', placeId: (tgt && tgt.placeId) || '' });
      ok++;
    } catch (e) { }
  }
  save();
  if (tgt && tgt.kind === 'book') { renderBookImgs(tgt.draft); }
  else render();
  toast(ok === files.length ? `${ok}장 추가했어요` : `${ok}장 추가 · ${files.length - ok}장은 열 수 없는 형식이에요`);
}
function photoEdit(id) {
  const p = byId('photos', id); if (!p) return;
  sheet({
    title: '사진 정보',
    body: `<div class="f"><label>설명</label><textarea class="in" id="fCap" placeholder="이 순간에 대해 한 줄">${esc(p.caption)}</textarea></div>
      <div class="f"><label>날짜</label><input class="in" type="date" id="fDate" value="${esc(p.date)}"></div>
      <div class="f"><label>장소 연결</label><select class="in" id="fPlace"><option value="">없음</option>${S.places.map(x => `<option value="${x.id}" ${x.id === p.placeId ? 'selected' : ''}>${esc(x.name)}</option>`).join('')}</select></div>`,
    foot: `<button class="btn big pri" data-a="sheet-ok">저장</button>`,
    ok: () => { Object.assign(p, { caption: $('#fCap').value.trim(), date: $('#fDate').value, placeId: $('#fPlace').value }); save(); viewerShow(); },
  });
}

/* ============ BOOKINGS ============ */
function vBook() {
  let h = hd('예약');
  if (!S.bookings.length) return h + `<div class="sec"><div class="list">${empty('예약이 없어요', '항공권, 플릭스버스, 숙소 확인번호와<br>캡처 화면을 한곳에 모아두세요')}</div></div>` + fab('book-add');
  const list = [...S.bookings].sort((a, b) => (a.date || '9999').localeCompare(b.date || '9999') || (a.time || '').localeCompare(b.time || ''));
  h += list.map(b => {
    const t = BTYPES[b.type] || BTYPES.etc;
    return `<div class="sec"><div class="card">
      <div class="row">${catIco(b.type, BTYPES)}<div style="flex:1;min-width:0"><div style="font-size:17px;font-weight:700">${esc(b.title || t.n)}</div><div class="sub" style="margin-top:2px">${[t.n, b.date ? fmtD(b.date) : '', b.time].filter(Boolean).join(' · ')}</div></div>
      <button class="ib" style="color:var(--t3);width:36px;height:36px;display:grid;place-items:center" data-a="book-edit" data-id="${b.id}">${ico('edit', 'i s')}</button></div>
      ${b.code ? `<div class="row" style="margin-top:16px;background:var(--card2);border-radius:14px;padding:12px 14px"><div style="flex:1"><div class="sub">확인번호</div><div class="code">${esc(b.code)}</div></div><button class="btn" style="flex:0 0 auto;padding:0 14px;height:40px" data-a="copy" data-t="${esc(b.code)}">${ico('copy', 'i s')}복사</button></div>` : ''}
      ${b.address ? `<button class="row" style="margin-top:12px;width:100%;text-align:left;color:var(--t2);font-size:14px" data-a="url" data-u="${esc(gmaps({ name: b.title, address: b.address }))}">${ico('pin', 'i s')}<span style="flex:1">${esc(b.address)}</span></button>` : ''}
      ${b.memo ? `<div style="margin-top:10px;font-size:14px;color:var(--t2);white-space:pre-wrap;line-height:1.5">${esc(b.memo)}</div>` : ''}
      ${b.imgs && b.imgs.length ? `<div class="thumbs">${b.imgs.map(i => `<img data-thumb="${i}" data-a="book-img" data-id="${b.id}" data-img="${i}">`).join('')}</div>` : ''}
    </div></div>`;
  }).join('');
  return h + fab('book-add');
}
function renderBookImgs(d) {
  const el = $('#fImgs'); if (!el) return;
  el.innerHTML = d.imgs.map(i => `<div style="position:relative"><img data-thumb="${i}"><button data-a="bimg-rm" data-img="${i}" style="position:absolute;top:-6px;right:-6px;width:24px;height:24px;border-radius:50%;background:var(--t1);color:var(--bg);display:grid;place-items:center">${ico('x', 'i s')}</button></div>`).join('')
    + `<button data-a="bimg-add" style="width:64px;height:64px;border-radius:12px;background:var(--card2);display:grid;place-items:center;color:var(--t3)">${ico('cam')}</button>`;
  hydrate(el);
}
function bookForm(b) {
  const isNew = !b;
  const d = b ? { ...b, imgs: [...(b.imgs || [])] } : { id: uid(), type: 'flight', title: '', date: '', time: '', code: '', address: '', memo: '', imgs: [] };
  const ctx = { kind: 'book', draft: d, fresh: [], removed: [] };
  ui.bookCtx = ctx;
  sheet({
    title: isNew ? '예약 추가' : '예약 수정',
    body: `<div class="f"><label>종류</label>${pick('fType', BTYPES, d.type)}</div>
      <div class="f"><label>이름</label><input class="in" id="fTitle" value="${esc(d.title)}" placeholder="예: 대한항공 KE017 인천→LA"></div>
      <div class="f two"><div><label>날짜</label><input class="in" type="date" id="fDate" value="${esc(d.date)}"></div><div><label>시간</label><input class="in" type="time" id="fTime" value="${esc(d.time)}"></div></div>
      <div class="f"><label>확인번호</label><input class="in" id="fCode" value="${esc(d.code)}" placeholder="예약번호, PNR" autocapitalize="characters" style="font-family:ui-monospace,monospace;letter-spacing:1px"></div>
      <div class="f"><label>주소</label><input class="in" id="fAddr" value="${esc(d.address)}"></div>
      <div class="f"><label>메모</label><textarea class="in" id="fMemo" placeholder="체크인 시간, 좌석, 수하물 등">${esc(d.memo)}</textarea></div>
      <div class="f"><label>티켓·캡처 사진</label><div class="thumbs" id="fImgs" style="margin-top:0"></div></div>`,
    foot: `${isNew ? '' : `<button class="btn big red" data-a="book-del" data-id="${d.id}" style="flex:0 0 90px">삭제</button>`}<button class="btn big pri" data-a="sheet-ok">저장</button>`,
    mount: () => renderBookImgs(d),
    ok: () => {
      Object.assign(d, { type: pickVal('fType'), title: $('#fTitle').value.trim(), date: $('#fDate').value, time: $('#fTime').value, code: $('#fCode').value.trim(), address: $('#fAddr').value.trim(), memo: $('#fMemo').value.trim() });
      if (!d.title && !d.code) { toast('이름이나 확인번호를 입력해주세요'); return false; }
      ctx.removed.forEach(removeImage); ctx.saved = true;
      upsert('bookings', d); toast('저장했어요');
    },
    close: () => { if (!ctx.saved) ctx.fresh.forEach(removeImage); },
  });
}

/* ============ generic helpers ============ */
function pick(id, set, val) {
  return `<div class="pick" id="${id}">${Object.entries(set).map(([k, v]) => `<button data-a="pick" data-k="${k}" class="${k === val ? 'on' : ''}">${v.c ? `<span class="dot" style="background:${v.c}"></span>` : ''}${esc(v.n)}</button>`).join('')}</div>`;
}
const pickVal = id => { const b = $(`#${id} button.on`); return b ? b.dataset.k : ''; };
function upsert(arr, obj) { const i = S[arr].findIndex(x => x.id === obj.id); if (i >= 0) S[arr][i] = obj; else S[arr].push(obj); save(); render(); }
function confirmSheet(msg, sub, yes, onYes) {
  sheet({ title: msg, body: `<div class="sub" style="font-size:15px;line-height:1.5;margin-top:4px">${sub}</div>`, foot: `<button class="btn big" data-a="sheet-close">취소</button><button class="btn big pri" style="background:var(--red)" data-a="sheet-ok">${yes}</button>`, ok: onYes });
}

/* ============ sheet ============ */
const sheets = [];
function sheet(o) {
  const lay = document.createElement('div'); lay.className = 'lay';
  lay.innerHTML = `<div class="dim" data-a="sheet-close"></div><div class="sheet"><div class="grab"></div><div class="sh-h"><h2>${esc(o.title)}</h2><button class="ib" style="color:var(--t3);width:36px;height:36px;display:grid;place-items:center" data-a="sheet-close">${ico('x')}</button></div><div class="sh-b">${o.body}</div>${o.foot ? `<div class="sh-f">${o.foot}</div>` : ''}</div>`;
  $('#ov').appendChild(lay);
  o.el = lay; sheets.push(o);
  requestAnimationFrame(() => requestAnimationFrame(() => lay.classList.add('on')));
  hydrate(lay);
  if (o.mount) o.mount(lay);
  // swipe down to close
  const sh = $('.sheet', lay), body = $('.sh-b', lay); let y0 = null, dy = 0;
  sh.addEventListener('touchstart', e => { if (body.scrollTop <= 0 && !e.target.closest('input,textarea,select')) { y0 = e.touches[0].clientY; dy = 0; } }, { passive: true });
  sh.addEventListener('touchmove', e => { if (y0 == null) return; dy = e.touches[0].clientY - y0; if (dy > 0) { sh.style.transition = 'none'; sh.style.transform = `translateY(${dy}px)`; } }, { passive: true });
  sh.addEventListener('touchend', () => { if (y0 == null) return; sh.style.transition = ''; sh.style.transform = ''; if (dy > 110) closeSheet(); y0 = null; });
}
function closeSheet(all) {
  do {
    const o = sheets.pop(); if (!o) return;
    if (o.close) o.close();
    o.el.classList.remove('on');
    setTimeout(() => o.el.remove(), 300);
  } while (all && sheets.length);
}

/* ============ settings ============ */
function settingsSheet() {
  const st = S.settings;
  const modRows = () => S.modules.map((m, i) => `<div class="li" style="padding:10px 14px"><div class="ico" style="background:${MODS[m.k].c};width:34px;height:34px;border-radius:11px">${ico(MODS[m.k].i, 'i s')}</div><div class="m"><div class="t" style="font-size:15px">${MODS[m.k].n}</div></div>
    <button class="mv" data-a="mod-mv" data-i="${i}" data-d="-1" ${i === 0 ? 'disabled' : ''}>${ico('up', 'i s')}</button><button class="mv" data-a="mod-mv" data-i="${i}" data-d="1" ${i === S.modules.length - 1 ? 'disabled' : ''}>${ico('down', 'i s')}</button>
    <button class="sw ${m.on ? 'on' : ''}" data-a="mod-sw" data-i="${i}"></button></div>`).join('');
  sheet({
    title: '설정',
    body: `<div class="f"><label>여행 이름</label><input class="in" data-set="title" value="${esc(st.title)}"></div>
      <div class="f"><label>경로 (· 로 구분)</label><input class="in" data-set="route" value="${esc(st.route)}"></div>
      <div class="f two"><div><label>시작일</label><input class="in" type="date" data-set="start" value="${esc(st.start)}"></div><div><label>종료일</label><input class="in" type="date" data-set="end" value="${esc(st.end)}"></div></div>
      <div class="f two"><div><label>1달러 = 원</label><input class="in" inputmode="decimal" data-rate="USD" value="${esc(st.rates.USD)}"></div><div><label>1캐나다달러 = 원</label><input class="in" inputmode="decimal" data-rate="CAD" value="${esc(st.rates.CAD)}"></div></div>
      <div class="f"><label>총 예산 (원)</label><input class="in" inputmode="numeric" data-set="budget" value="${st.budget || ''}" placeholder="예: 2000000"></div>
      <div class="f"><label>기능 켜기·끄기·순서 <span style="font-weight:400">(앞의 4개가 아래 탭에 나와요)</span></label><div class="list" id="modList" style="background:var(--card2)">${modRows()}</div></div>
      <div class="f"><label>화면</label><div class="seg">${[['auto', '자동'], ['light', '라이트'], ['dark', '다크']].map(([k, n]) => `<button class="${st.theme === k ? 'on' : ''}" data-a="theme" data-k="${k}">${n}</button>`).join('')}</div></div>
      <div class="f"><label>데이터</label><div class="btns" style="margin-top:0"><button class="btn" data-a="export">백업 내보내기</button><button class="btn" data-a="import">백업 불러오기</button></div>
        <div class="coord">백업 파일엔 사진이 빠져요. 사진은 폰 갤러리에 원본이 남아 있어요.</div>
        <button class="btn red" style="width:100%;margin-top:10px" data-a="reset">전체 초기화</button></div>
      <div class="coord" style="justify-content:center;margin-top:22px">지도 © OpenStreetMap · OpenFreeMap · 장소 검색 Photon</div>`,
    mount: lay => {
      lay.addEventListener('input', e => {
        const t = e.target;
        if (t.dataset.set) { S.settings[t.dataset.set] = t.dataset.set === 'budget' ? num(t.value) : t.value; save(); render(); }
        if (t.dataset.rate) { S.settings.rates[t.dataset.rate] = num(t.value); save(); render(); }
      });
      ui.modRows = modRows;
    },
  });
}
function exportData() {
  const name = `trip-backup-${todayStr()}.json`, txt = JSON.stringify(S, null, 1);
  if (NA && NA.save) NA.save(name, txt);
  else { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([txt], { type: 'application/json' })); a.download = name; a.click(); }
}
window.onLoadFile = txt => {
  try {
    const d = JSON.parse(txt); if (!d || !d.settings || !Array.isArray(d.plans)) throw 0;
    const keepPhotos = S.photos;
    S = d; load2(); if (!S.photos.length) S.photos = keepPhotos;
    save(); closeSheet(true); applyTheme(); render(); if (map) drawMarkers(); toast('불러왔어요');
  } catch (e) { toast('백업 파일이 아니에요'); }
};
function load2() { localStorage.setItem(KEY, JSON.stringify(S)); load(); }
function importData() {
  if (NA && NA.load) return NA.load();
  const i = document.createElement('input'); i.type = 'file'; i.accept = 'application/json,.json';
  i.onchange = () => { const f = i.files[0]; if (f) f.text().then(window.onLoadFile); }; i.click();
}

/* ============ MAP ============ */
let map = null, markers = [], meMarker = null, tmpMarker = null, pinMode = false, mapStyleKind = '';
const ONLINE_STYLE = 'https://tiles.openfreemap.org/styles/liberty';
let onlineStyleCache = null;
function localStyle() {
  const dark = document.documentElement.dataset.theme === 'dark';
  return {
    version: 8, projection: { type: 'globe' },
    sources: { w: { type: 'geojson', data: window.WORLD } },
    layers: [
      { id: 'bg', type: 'background', paint: { 'background-color': dark ? '#132235' : '#BFDCF5' } },
      { id: 'land', type: 'fill', source: 'w', paint: { 'fill-color': dark ? '#2A2F38' : '#F5F2EA' } },
      { id: 'bd', type: 'line', source: 'w', paint: { 'line-color': dark ? '#4A505C' : '#CFC6B5', 'line-width': 0.7 } },
    ],
    sky: { 'atmosphere-blend': ['interpolate', ['linear'], ['zoom'], 0, 1, 5, 1, 7, 0] },
  };
}
async function getOnlineStyle() {
  if (onlineStyleCache) return onlineStyleCache;
  if (!navigator.onLine) throw new Error('offline');
  const ctl = new AbortController(); const to = setTimeout(() => ctl.abort(), 7000);
  try {
    const r = await fetch(ONLINE_STYLE, { signal: ctl.signal }); if (!r.ok) throw new Error('style');
    const s = await r.json();
    s.projection = { type: 'globe' };
    s.sky = { 'atmosphere-blend': ['interpolate', ['linear'], ['zoom'], 0, 1, 5, 1, 7, 0] };
    // prefer Korean labels where OSM has them
    s.layers.forEach(l => { if (l.layout && l.layout['text-field'] && l.type === 'symbol' && /place|country|state|city|town/.test(l.id)) l.layout['text-field'] = ['coalesce', ['get', 'name:ko'], ['get', 'name:latin'], ['get', 'name']]; });
    return (onlineStyleCache = s);
  } finally { clearTimeout(to); }
}
async function setMapStyle() {
  let st, kind;
  try { st = await getOnlineStyle(); kind = 'online'; } catch (e) { st = localStyle(); kind = 'local'; }
  if (kind === 'online' && mapStyleKind === 'online') return;
  mapStyleKind = kind;
  map.setStyle(st, { diff: false });
  if (kind === 'local') toast('오프라인 지구본으로 보여요');
}
async function ensureMap() {
  renderMapChips();
  if (map) { map.resize(); drawMarkers(); return; }
  if (!window.maplibregl) { $('#mapEl').innerHTML = '<div class="empty" style="color:#fff">지도를 불러올 수 없어요</div>'; return; }
  let st;
  try { st = await getOnlineStyle(); mapStyleKind = 'online'; } catch (e) { st = localStyle(); mapStyleKind = 'local'; }
  map = new maplibregl.Map({ container: 'mapEl', style: st, center: [-112, 37], zoom: 1.7, attributionControl: { compact: true }, maxPitch: 60 });
  map.on('load', () => { drawMarkers(); const pts = placesWithLoc(); if (pts.length) setTimeout(() => fitAll(), 600); });
  map.on('click', async e => {
    if (!pinMode) { hideMcard(); return; }
    const { lat, lng } = e.lngLat;
    setPinMode(false);
    const d = pending || { id: uid(), name: '', cat: 'food', status: 'want', lat: '', lng: '', address: '', memo: '', date: '', url: '', rating: 0 };
    const isNew = !S.places.find(x => x.id === d.id);
    pending = null;
    d.lat = +lat.toFixed(6); d.lng = +lng.toFixed(6);
    if (!d.address) d.address = await reverseGeo(lat, lng);
    placeForm(d, isNew);
  });
  map.on('error', e => {
    if (mapStyleKind === 'online' && !map.isStyleLoaded() && !map.__fell) { map.__fell = true; mapStyleKind = 'local'; map.setStyle(localStyle()); toast('오프라인 지구본으로 보여요'); }
  });
  if (mapStyleKind === 'local') toast('오프라인 지구본으로 보여요 (인터넷 연결 시 상세 지도)');
}
const placesWithLoc = () => S.places.filter(p => p.lat !== '' && p.lat != null && isFinite(p.lat));
function renderMapChips() {
  const cats = Object.keys(CATS).filter(k => S.places.some(p => p.cat === k));
  if (ui.mapCat !== 'all' && !cats.includes(ui.mapCat)) ui.mapCat = 'all';
  $('#mchips').innerHTML = `<button class="chip ${ui.mapCat === 'all' ? 'on' : ''}" data-a="mcat" data-k="all">전체</button>` + cats.map(k => `<button class="chip ${ui.mapCat === k ? 'on' : ''}" data-a="mcat" data-k="${k}"><span class="dot" style="background:${CATS[k].c}"></span>${CATS[k].n}</button>`).join('');
}
function drawMarkers() {
  if (!map) return;
  markers.forEach(m => m.remove()); markers = [];
  placesWithLoc().filter(p => ui.mapCat === 'all' || p.cat === ui.mapCat).forEach(p => {
    const c = CATS[p.cat] || CATS.etc;
    const el = document.createElement('div');
    el.innerHTML = `<div class="pin${p.status === 'visited' ? ' v' : ''}" style="background:${c.c}"><svg><use href="#i-${c.i}"/></svg></div>`;
    el.addEventListener('click', ev => { ev.stopPropagation(); showMcard(p.id); });
    markers.push(new maplibregl.Marker({ element: el, anchor: 'bottom', offset: [0, -4] }).setLngLat([p.lng, p.lat]).addTo(map));
  });
}
function fitAll() {
  const pts = placesWithLoc().filter(p => ui.mapCat === 'all' || p.cat === ui.mapCat);
  if (!map) return;
  if (!pts.length) { map.flyTo({ center: [-112, 37], zoom: 3 }); return; }
  if (pts.length === 1) { map.flyTo({ center: [pts[0].lng, pts[0].lat], zoom: 13 }); return; }
  const b = new maplibregl.LngLatBounds(); pts.forEach(p => b.extend([p.lng, p.lat]));
  map.fitBounds(b, { padding: { top: 150, bottom: 90, left: 50, right: 70 }, maxZoom: mapStyleKind === 'local' ? 5 : 14, duration: 1400 });
}
function showMcard(id) {
  const p = byId('places', id); if (!p) return;
  const c = CATS[p.cat] || CATS.etc, st = STATUS[p.status] || STATUS.want;
  const ph = S.photos.filter(x => x.placeId === p.id).slice(0, 6);
  const el = $('#mcard');
  el.innerHTML = `<div class="row">${catIco(p.cat)}<div style="flex:1;min-width:0"><div style="font-size:17px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(p.name)}</div><div style="margin-top:4px">${tag(c.n, c.c)} ${tag(st.n, st.c)} ${p.date ? `<span class="sub">${fmtD(p.date)}</span>` : ''}</div></div><button style="color:var(--t3)" data-a="mcard-x">${ico('x')}</button></div>
    ${p.address ? `<div class="sub" style="margin-top:10px">${esc(p.address)}</div>` : ''}
    ${ph.length ? `<div class="thumbs">${ph.map(x => `<img data-thumb="${x.id}" data-a="photo-open" data-id="${x.id}" data-place="${p.id}">`).join('')}</div>` : ''}
    <div class="btns"><button class="btn" data-a="place-open" data-id="${p.id}">상세</button><button class="btn pri" data-a="url" data-u="${esc(gdir(p))}">${ico('nav', 'i s')}길찾기</button></div>`;
  el.classList.add('on'); $('#mbtns').style.display = 'none';
  hydrate(el);
  map.flyTo({ center: [p.lng, p.lat], zoom: Math.max(map.getZoom(), 14), offset: [0, -60] });
}
function hideMcard() { $('#mcard').classList.remove('on'); $('#mbtns').style.display = ''; if (tmpMarker) { tmpMarker.remove(); tmpMarker = null; } }
function setPinMode(on) {
  pinMode = on;
  $('#pinBtn').classList.toggle('on', on);
  $('#mhint').classList.toggle('on', on);
  if (map) map.getCanvas().style.cursor = on ? 'crosshair' : '';
  if (on) hideMcard();
}
let mres = [];
async function mapSearch() {
  const q = $('#mq').value.trim(); if (!q) return;
  const box = $('#mres'); box.style.display = ''; box.innerHTML = '<div class="li"><div class="d">찾는 중…</div></div>';
  try {
    mres = await photon(q, searchNear());
    box.innerHTML = mres.length ? mres.map((r, i) => `<div class="li" data-a="mres-pick" data-i="${i}">${ico('pin', 'i s')}<div class="m"><div class="t" style="font-size:15px">${esc(r.name)}</div><div class="d">${esc(r.addr)}</div></div></div>`).join('') : '<div class="li"><div class="d">결과가 없어요. 영어로 검색해보세요</div></div>';
  } catch (e) { box.innerHTML = '<div class="li"><div class="d">인터넷 연결이 필요해요</div></div>'; }
}
function mresPick(i) {
  const r = mres[i]; if (!r) return;
  $('#mres').style.display = 'none'; $('#mq').blur();
  if (tmpMarker) tmpMarker.remove();
  tmpMarker = new maplibregl.Marker({ color: '#191F28' }).setLngLat([r.lng, r.lat]).addTo(map);
  map.flyTo({ center: [r.lng, r.lat], zoom: 15, offset: [0, -60] });
  const el = $('#mcard');
  el.innerHTML = `<div class="row"><div style="flex:1;min-width:0"><div style="font-size:17px;font-weight:700">${esc(r.name)}</div><div class="sub" style="margin-top:4px">${esc(r.addr)}</div></div><button style="color:var(--t3)" data-a="mcard-x">${ico('x')}</button></div>
    <div class="btns"><button class="btn" data-a="url" data-u="${esc(gmaps(r))}">구글맵</button><button class="btn pri" data-a="mres-save" data-i="${i}">${ico('plus', 'i s')}장소로 저장</button></div>`;
  el.classList.add('on'); $('#mbtns').style.display = 'none';
}
function locateMe(cb) {
  if (!navigator.geolocation) { toast('위치를 쓸 수 없어요'); return; }
  toast('현재 위치 찾는 중…');
  navigator.geolocation.getCurrentPosition(pos => cb(pos.coords.latitude, pos.coords.longitude), () => toast('위치 권한을 확인해주세요'), { enableHighAccuracy: true, timeout: 12000 });
}

/* ============ viewer ============ */
const V = { list: [], i: 0, ctx: null, s: 1, x: 0, y: 0 };
function openViewer(list, i, ctx) { V.list = list; V.i = i; V.ctx = ctx; $('#viewer').classList.add('on'); viewerShow(); }
function closeViewer() { $('#viewer').classList.remove('on'); $('#vImg').src = ''; }
async function viewerShow() {
  if (!V.list.length) { closeViewer(); return; }
  V.i = Math.max(0, Math.min(V.i, V.list.length - 1));
  const id = V.list[V.i]; V.s = 1; V.x = 0; V.y = 0; vApply();
  const img = $('#vImg'); img.src = await imgURL(id, true) || await imgURL(id);
  const p = V.ctx === 'photo' ? byId('photos', id) : null;
  $('#vEdit').style.display = p ? '' : 'none';
  const pl = p && p.placeId ? byId('places', p.placeId) : null;
  $('#vTitle').textContent = p ? (p.date ? fmtD(p.date) : '날짜 없음') : '티켓·캡처';
  $('#vSub').textContent = `${V.i + 1} / ${V.list.length}${pl ? ' · ' + pl.name : ''}`;
  $('#vCap').textContent = p ? p.caption : ''; $('#vCap').style.display = p && p.caption ? '' : 'none';
}
function vApply() { $('#vImg').style.transform = `translate(${V.x}px,${V.y}px) scale(${V.s})`; }
(function viewerGestures() {
  const st = document.getElementById('vStage');
  let t0 = null, d0 = 0, s0 = 1, x0 = 0, y0 = 0, moved = false, lastTap = 0;
  const dist = t => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
  st.addEventListener('touchstart', e => {
    moved = false;
    if (e.touches.length === 2) { d0 = dist(e.touches); s0 = V.s; }
    else { t0 = { x: e.touches[0].clientX, y: e.touches[0].clientY }; x0 = V.x; y0 = V.y; }
  }, { passive: true });
  st.addEventListener('touchmove', e => {
    moved = true;
    if (e.touches.length === 2 && d0) { V.s = Math.max(1, Math.min(5, s0 * dist(e.touches) / d0)); vApply(); }
    else if (t0 && e.touches.length === 1) {
      const dx = e.touches[0].clientX - t0.x, dy = e.touches[0].clientY - t0.y;
      if (V.s > 1) { V.x = x0 + dx; V.y = y0 + dy; } else { V.x = dx; }
      vApply();
    }
  }, { passive: true });
  st.addEventListener('touchend', e => {
    if (e.touches.length) return;
    if (V.s <= 1.02 && t0) {
      const dx = V.x;
      if (dx < -70 && V.i < V.list.length - 1) { V.i++; viewerShow(); }
      else if (dx > 70 && V.i > 0) { V.i--; viewerShow(); }
      else { V.s = 1; V.x = 0; V.y = 0; vApply(); }
    }
    if (!moved) { const n = Date.now(); if (n - lastTap < 280) { V.s = V.s > 1 ? 1 : 2.5; V.x = 0; V.y = 0; vApply(); } lastTap = n; }
    if (V.s <= 1) { V.s = 1; V.x = 0; V.y = 0; vApply(); }
    t0 = null; d0 = 0;
  });
})();

/* ============ actions ============ */
const A = {
  tab: el => go(el.dataset.k),
  settings: () => settingsSheet(),
  'sheet-close': () => closeSheet(),
  'sheet-ok': () => { const o = sheets[sheets.length - 1]; if (o && o.ok && o.ok() === false) return; closeSheet(); },
  pick: el => { $$('button', el.parentNode).forEach(b => b.classList.toggle('on', b === el)); },
  rate: el => { const n = +el.dataset.n, cur = $$('#fRate button.on').length; $$('#fRate button').forEach((b, i) => b.classList.toggle('on', cur !== n && i < n)); },
  copy: el => copyText(el.dataset.t),
  url: el => openURL(el.dataset.u),
  // plans
  'plan-add': () => planForm(),
  'plan-edit': el => planForm(byId('plans', el.dataset.id)),
  'plan-done': el => { const p = byId('plans', el.dataset.id); p.done = !p.done; save(); render(); },
  'plan-del': el => confirmSheet('일정을 삭제할까요?', '되돌릴 수 없어요', '삭제', () => { S.plans = S.plans.filter(x => x.id !== el.dataset.id); save(); closeSheet(); render(); }),
  // places
  pcat: el => { ui.placeCat = el.dataset.k; render(); },
  pstat: el => { ui.placeStatus = el.dataset.k; render(); },
  'place-add': () => placeForm(),
  'place-open': el => placeDetail(el.dataset.id),
  'place-edit': el => { closeSheet(); placeForm(byId('places', el.dataset.id)); },
  'place-status': el => { const p = byId('places', el.dataset.id); p.status = el.dataset.k; save(); closeSheet(); render(); placeDetail(p.id); if (map) drawMarkers(); },
  'place-del': el => confirmSheet('장소를 삭제할까요?', '연결된 일정·사진·지출은 남고 연결만 풀려요', '삭제', () => {
    const id = el.dataset.id; S.places = S.places.filter(x => x.id !== id);
    ['plans', 'photos', 'expenses'].forEach(k => S[k].forEach(x => { if (x.placeId === id) x.placeId = ''; }));
    save(); closeSheet(true); render(); if (map) { drawMarkers(); hideMcard(); }
  }),
  'place-onmap': el => { const id = el.dataset.id; closeSheet(true); go('map'); setTimeout(() => showMcard(id), map ? 50 : 1500); },
  'loc-search': () => locSearch(),
  'loc-pick': el => { const r = locRes[+el.dataset.i]; setDraftLoc(r.lat, r.lng, r.addr, r.name); $('#fLocRes').innerHTML = ''; toast('위치를 넣었어요'); },
  'loc-pin': () => { if (!enabled().includes('map')) { toast('지도 기능을 켜주세요'); return; } pending = collectPlace(); const o = sheets[sheets.length - 1]; o.close = null; closeSheet(true); go('map'); setPinMode(true); },
  'loc-me': () => locateMe(async (la, ln) => { setDraftLoc(la, ln, await reverseGeo(la, ln)); toast('현재 위치를 넣었어요'); }),
  // map
  mcat: el => { ui.mapCat = el.dataset.k; renderMapChips(); drawMarkers(); },
  'map-fit': () => fitAll(),
  'map-globe': () => map && map.flyTo({ center: map.getCenter(), zoom: 1.6, pitch: 0, bearing: 0, duration: 1600 }),
  'map-me': () => locateMe((la, ln) => {
    if (!map) return; if (meMarker) meMarker.remove();
    const el = document.createElement('div'); el.style.cssText = 'width:18px;height:18px;border-radius:50%;background:#3182F6;border:3px solid #fff;box-shadow:0 0 0 6px rgba(49,130,246,.25)';
    meMarker = new maplibregl.Marker({ element: el }).setLngLat([ln, la]).addTo(map); map.flyTo({ center: [ln, la], zoom: 15 });
  }),
  'map-pinmode': () => { pending = null; setPinMode(!pinMode); },
  'mcard-x': () => hideMcard(),
  'mres-pick': el => mresPick(+el.dataset.i),
  'mres-save': el => { const r = mres[+el.dataset.i]; hideMcard(); placeForm({ id: uid(), name: r.name, cat: 'food', status: 'want', lat: +r.lat.toFixed(6), lng: +r.lng.toFixed(6), address: r.addr, memo: '', date: '', url: '', rating: 0 }, true); },
  // money
  'exp-add': () => expForm(),
  'exp-edit': el => expForm(byId('expenses', el.dataset.id)),
  'exp-del': el => confirmSheet('지출을 삭제할까요?', '되돌릴 수 없어요', '삭제', () => { S.expenses = S.expenses.filter(x => x.id !== el.dataset.id); save(); closeSheet(true); render(); }),
  'tip-toggle': () => { ui.tipOpen = !ui.tipOpen; render(); if (ui.tipOpen) tipCalc(); },
  'tip-pct': el => { ui.tipPct = +el.dataset.p; $$('#tipPct button').forEach(b => b.classList.toggle('on', b === el)); tipCalc(); },
  'tip-add': el => expForm(null, { amount: +el.dataset.v, cur: 'USD', cat: 'food', memo: '' }),
  // photos
  'photo-add': () => pickFiles({ kind: 'photo' }),
  'photo-open': el => {
    let list = photoSorted();
    if (el.dataset.place) list = list.filter(p => p.placeId === el.dataset.place);
    const ids = list.map(p => p.id); openViewer(ids, ids.indexOf(el.dataset.id), 'photo');
  },
  'v-close': () => closeViewer(),
  'v-edit': () => photoEdit(V.list[V.i]),
  'v-del': () => {
    const id = V.list[V.i];
    confirmSheet('사진을 삭제할까요?', '앱에서만 지워지고 갤러리 원본은 그대로예요', '삭제', () => {
      if (V.ctx === 'photo') S.photos = S.photos.filter(p => p.id !== id);
      else { const b = byId('bookings', V.ctx); if (b) b.imgs = b.imgs.filter(i => i !== id); }
      removeImage(id); save(); V.list.splice(V.i, 1); render(); viewerShow();
    });
  },
  // bookings
  'book-add': () => bookForm(),
  'book-edit': el => bookForm(byId('bookings', el.dataset.id)),
  'book-del': el => confirmSheet('예약을 삭제할까요?', '첨부한 사진도 함께 지워져요', '삭제', () => { const b = byId('bookings', el.dataset.id); (b.imgs || []).forEach(removeImage); S.bookings = S.bookings.filter(x => x.id !== b.id); if (ui.bookCtx) ui.bookCtx.saved = true; save(); closeSheet(true); render(); }),
  'book-img': el => { const b = byId('bookings', el.dataset.id); openViewer([...b.imgs], b.imgs.indexOf(el.dataset.img), b.id); },
  'bimg-add': () => pickFiles(ui.bookCtx),
  'bimg-rm': el => { const c = ui.bookCtx, i = el.dataset.img; c.draft.imgs = c.draft.imgs.filter(x => x !== i); if (c.fresh.includes(i)) { c.fresh = c.fresh.filter(x => x !== i); removeImage(i); } else c.removed.push(i); renderBookImgs(c.draft); },
  // settings
  'mod-sw': el => { const m = S.modules[+el.dataset.i]; if (m.on && enabled().length <= 1) { toast('최소 1개는 켜두어야 해요'); return; } m.on = !m.on; save(); $('#modList').innerHTML = ui.modRows(); render(); },
  'mod-mv': el => { const i = +el.dataset.i, j = i + +el.dataset.d; [S.modules[i], S.modules[j]] = [S.modules[j], S.modules[i]]; save(); $('#modList').innerHTML = ui.modRows(); render(); },
  theme: el => { S.settings.theme = el.dataset.k; $$('button', el.parentNode).forEach(b => b.classList.toggle('on', b === el)); save(); applyTheme(); render(); },
  export: () => exportData(),
  import: () => importData(),
  reset: () => confirmSheet('전체 초기화할까요?', '일정, 장소, 경비, 예약, 사진이 모두 지워져요', '초기화', async () => {
    for (const p of S.photos) await removeImage(p.id);
    for (const b of S.bookings) for (const i of b.imgs || []) await removeImage(i);
    S = seed(); save(); closeSheet(true); applyTheme(); render(); if (map) drawMarkers(); toast('초기화했어요');
  }),
};
document.addEventListener('click', e => {
  const el = e.target.closest('[data-a]'); if (!el) return;
  const f = A[el.dataset.a]; if (!f) return;
  e.stopPropagation(); f(el, e);
});
document.addEventListener('input', e => { if (e.target.id === 'tipBill') tipCalc(); });
$('#fileIn').addEventListener('change', e => onFiles(e.target.files));
$('#mq').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); mapSearch(); } });
$('#mq').addEventListener('input', e => { $('#mqx').style.display = e.target.value ? '' : 'none'; if (!e.target.value) $('#mres').style.display = 'none'; });
$('#mqx').addEventListener('click', () => { $('#mq').value = ''; $('#mqx').style.display = 'none'; $('#mres').style.display = 'none'; hideMcard(); });
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { if (S.settings.theme === 'auto') { applyTheme(); render(); } });

/* back button (called from Android) */
window.onBack = () => {
  if ($('#viewer').classList.contains('on')) { closeViewer(); return true; }
  if (sheets.length) { closeSheet(); return true; }
  if (ui.tab === 'map') {
    if ($('#mres').style.display !== 'none') { $('#mres').style.display = 'none'; return true; }
    if ($('#mcard').classList.contains('on')) { hideMcard(); return true; }
    if (pinMode) { setPinMode(false); pending = null; return true; }
  }
  if (ui.tab !== 'home') { go('home'); return true; }
  return false;
};

load(); applyTheme(); render();
