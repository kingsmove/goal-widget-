'use strict';
/* ============ DIARY (일기) ============ */
const MOODS = ['😆', '🙂', '😐', '😮‍💨', '😢'];
const diaryOf = d => ((S.diary || {})[d]) || null;
function diaryDates() {
  const set = new Set(tripDates());
  S.photos.forEach(p => p.date && set.add(p.date));
  Object.keys(S.diary || {}).forEach(d => set.add(d));
  return [...set].sort();
}
function vDiary() {
  const t = todayStr(), ds = diaryDates();
  const written = ds.filter(d => diaryOf(d) && (diaryOf(d).text || diaryOf(d).title)).length;
  let h = hd('일기', `<button class="chip" data-a="book-make" style="padding:9px 14px">${ico('import', 'i s')} 여행 기록 만들기</button>`);
  h += `<div class="sec" style="margin-top:0"><div class="card" style="padding:16px 18px"><div class="row"><div style="flex:1"><div class="sub">쓴 일기</div><div style="font-size:20px;font-weight:700;margin-top:2px">${written} / ${ds.length}일</div></div>
    <div class="sub" style="text-align:right;line-height:1.5">여행이 끝나면 위 버튼으로<br>일기·일정·사진을 파일 하나로</div></div></div></div>`;
  if (!ds.length) return h + `<div class="sec"><div class="list">${empty('날짜가 없어요', '일정에 날짜를 넣거나 사진을 올리면 그날 일기 칸이 생겨요')}</div></div>`;
  const order = [...ds].sort((a, b) => (a <= t) === (b <= t) ? (a <= t ? b.localeCompare(a) : a.localeCompare(b)) : (a <= t ? -1 : 1));
  for (const d of order) {
    const e = diaryOf(d) || {}, dt = parseD(d), dn = dayNum(d), regs = dayRegs(d);
    const phs = S.photos.filter(p => p.date === d).slice(0, 4);
    const nPh = S.photos.filter(p => p.date === d).length;
    h += `<div class="sec"><div class="card diary ${d === t ? 'today' : ''}" data-a="diary-open" data-d="${d}">
      <div class="row"><div style="flex:1"><div style="font-size:16px;font-weight:700">${dt.getMonth() + 1}월 ${dt.getDate()}일 (${WD[dt.getDay()]})${dn > 0 ? ` <span class="sub" style="font-weight:600">Day ${dn}</span>` : ''}${d === t ? ' <span class="today" style="font-size:11px">오늘</span>' : ''}</div>
      ${regs.length ? `<div style="margin-top:6px">${rgPill(regs)}</div>` : ''}</div>${e.mood ? `<div style="font-size:26px">${e.mood}</div>` : ''}</div>
      ${e.title ? `<div style="font-weight:700;margin-top:12px">${esc(e.title)}</div>` : ''}
      ${e.text ? `<div class="dtext">${esc(e.text)}</div>` : `<div class="sub" style="margin-top:10px">${d > t ? '아직 오지 않은 날' : '눌러서 일기 쓰기'}</div>`}
      ${phs.length ? `<div class="thumbs">${phs.map(p => `<img data-thumb="${p.id}">`).join('')}${nPh > 4 ? `<span class="sub" style="align-self:center">+${nPh - 4}</span>` : ''}</div>` : ''}
    </div></div>`;
  }
  return h;
}
function diaryForm(d) {
  const e = { ...(diaryOf(d) || { title: '', text: '', mood: '' }) }, dt = parseD(d);
  const plans = expandPlans().filter(p => p.date === d).sort(planSort);
  const phs = S.photos.filter(p => p.date === d);
  sheet({
    title: `${dt.getMonth() + 1}월 ${dt.getDate()}일 (${WD[dt.getDay()]}) 일기`,
    body: `<div class="f"><label>오늘 기분</label><div class="pick" id="dMood">${MOODS.map(m => `<button data-a="pick" data-k="${m}" class="${e.mood === m ? 'on' : ''}" style="font-size:22px;padding:6px 10px">${m}</button>`).join('')}</div></div>
      <div class="f"><label>한 줄 제목</label><input class="in" id="dTitle" value="${esc(e.title)}" placeholder="예: 금문교에서 맞은 바닷바람"></div>
      <div class="f"><label>일기</label><textarea class="in" id="dText" style="min-height:200px" placeholder="오늘 있었던 일, 먹은 것, 느낀 것">${esc(e.text)}</textarea></div>
      ${plans.length ? `<div class="f"><label>이날 일정 (기록에 같이 들어가요)</label>${plans.map(p => `<div class="sub" style="padding:3px 0">${p.time ? p.time + ' ' : ''}${esc(p.title)}${p.done ? ' ✓' : ''}</div>`).join('')}</div>` : ''}
      <div class="f"><label>이날 사진 ${phs.length}장</label><div class="thumbs" style="margin-top:0">${phs.map(p => `<img data-thumb="${p.id}" data-a="photo-open" data-id="${p.id}">`).join('')}<button data-a="diary-photo" data-d="${d}" style="width:64px;height:64px;border-radius:12px;background:var(--card2);display:grid;place-items:center;color:var(--t3)">${ico('cam')}</button></div></div>`,
    foot: `<button class="btn big pri" data-a="sheet-ok">저장</button>`,
    ok: () => {
      pushUndo();
      S.diary = S.diary || {};
      const v = { title: $('#dTitle').value.trim(), text: $('#dText').value.trim(), mood: pickVal('dMood') };
      if (v.title || v.text || v.mood) S.diary[d] = v; else delete S.diary[d];
      save(); render(); toast('저장했어요');
    },
  });
}

/* ============ TRAVEL BOOK: one self-contained HTML file ============ */
function bookSheet() {
  const ds = diaryDates();
  sheet({
    title: '여행 기록 만들기',
    body: `<div class="sub" style="line-height:1.6">일기·일정·사진·지도 경로·경비를 <b>HTML 파일 하나</b>로 묶어요. 사진까지 파일 안에 들어가서 어디서든 열리고, 브라우저에서 인쇄 → 'PDF로 저장'하면 PDF가 돼요.</div>
      <div class="f two"><div><label>시작</label><input class="in" type="date" id="bFrom" value="${ds[0] || ''}"></div><div><label>끝</label><input class="in" type="date" id="bTo" value="${ds[ds.length - 1] || ''}"></div></div>
      <div class="f"><label>제목</label><input class="in" id="bTitle" value="${esc(S.settings.title || '여행')}"></div>
      <div class="f"><label>넣을 것</label><div class="pick" id="bInc">${[['diary', '일기'], ['plan', '일정'], ['photo', '사진'], ['map', '지도 경로'], ['money', '경비 요약']].map(([k, n]) => `<button data-a="btog" data-k="${k}" class="on">${n}</button>`).join('')}</div></div>
      <div class="f"><label>사진 화질</label><div class="seg" id="bQ">${[['s', '가볍게'], ['m', '보통'], ['l', '선명하게']].map(([k, n]) => `<button class="${k === 'm' ? 'on' : ''}" data-a="bq" data-k="${k}">${n}</button>`).join('')}</div>
        <div class="coord" id="bEst"></div></div>`,
    foot: `<button class="btn big pri" data-a="book-go">파일 만들기</button>`,
    mount: () => { const est = () => { const n = S.photos.filter(p => p.date >= $('#bFrom').value && p.date <= $('#bTo').value).length, kb = { s: 90, m: 180, l: 380 }[$$('#bQ button.on')[0].dataset.k]; $('#bEst').textContent = `사진 ${n}장 · 예상 크기 약 ${Math.max(1, Math.round(n * kb / 1024))}MB`; }; ui.bookEst = est; ['bFrom', 'bTo'].forEach(i => $('#' + i).addEventListener('input', est)); est(); },
  });
}
const BQ = { s: [1000, 0.72], m: [1400, 0.8], l: [2000, 0.86] };
async function photoData(id, q) {
  const b = await idb.get('img', id).catch(() => null) || await idb.get('thumb', id).catch(() => null);
  if (!b) return '';
  const url = URL.createObjectURL(b);
  try {
    const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = url; });
    const blob = await scaleTo(img, BQ[q][0], BQ[q][1]);
    return await new Promise(res => { const r = new FileReader(); r.onload = () => res(r.result); r.readAsDataURL(blob); });
  } catch (e) { return ''; } finally { URL.revokeObjectURL(url); }
}
// simple route map as SVG: country outlines + places + arrows, longitudes shifted so the Pacific isn't split
function routeSVG(from, to) {
  const segs = routeSegments().filter(g => g.plan.date >= from && g.plan.date <= to);
  const pts = new Map();
  segs.forEach(g => { pts.set(g.from.id, g.from); pts.set(g.to.id, g.to); });
  expandPlans().filter(p => p.date >= from && p.date <= to && p.placeId).forEach(p => { const x = byId('places', p.placeId); if (x && x.lat !== '' && x.lat != null) pts.set(x.id, x); });
  const P = [...pts.values()]; if (!P.length) return '';
  const lngs0 = P.map(p => +p.lng), spread = Math.max(...lngs0) - Math.min(...lngs0);
  const shift = spread > 180 ? (l => l < 0 ? l + 360 : l) : (l => l);
  const L = P.map(p => shift(+p.lng)), A = P.map(p => +p.lat);
  let x0 = Math.min(...L), x1 = Math.max(...L), y0 = Math.min(...A), y1 = Math.max(...A);
  const pad = Math.max(3, (x1 - x0) * 0.12, (y1 - y0) * 0.2); x0 -= pad; x1 += pad; y0 -= pad; y1 += pad;
  if ((x1 - x0) / (y1 - y0) < 1.6) { const c = (x0 + x1) / 2, w = (y1 - y0) * 1.6 / 2; x0 = c - w; x1 = c + w; }
  else { const c = (y0 + y1) / 2, hh = (x1 - x0) / 1.6 / 2; y0 = c - hh; y1 = c + hh; }
  const W = 1000, H = 625, k = W / (x1 - x0);
  const X = l => ((shift(l) - x0) * k).toFixed(1), Y = a => ((y1 - a) * k).toFixed(1);
  let land = '';
  const ring = (r, off) => { let d = '', last = null; for (const [lg, la] of r) { const xx = ((lg + off - x0) * k), yy = ((y1 - la) * k); if (last && Math.abs(xx - last[0]) < 1.2 && Math.abs(yy - last[1]) < 1.2) continue; d += (d ? 'L' : 'M') + xx.toFixed(1) + ' ' + yy.toFixed(1); last = [xx, yy]; } return d + 'Z'; };
  for (const f of (window.WORLD || { features: [] }).features) {
    const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
    for (const poly of polys) for (const off of spread > 180 ? [0, 360] : [0]) {
      let r = poly[0], a = 1e9, b = -1e9, c = 1e9, e = -1e9;
      const box = () => { a = 1e9; b = -1e9; c = 1e9; e = -1e9; for (const [lg, la] of r) { a = Math.min(a, lg + off); b = Math.max(b, lg + off); c = Math.min(c, la); e = Math.max(e, la); } };
      box();
      if (b - a > 300) { r = r.map(([lg, la]) => [lg < 0 ? lg + 360 : lg, la]); box(); } // outline crossing the date line (e.g. Russia): unwrap
      if (b < x0 || a > x1 || e < y0 || c > y1 || c < -60 || b - a > 300) continue;
      land += ring(r, off);
    }
  }
  const order = []; segs.forEach(g => { [g.from, g.to].forEach(p => { if (!order.includes(p.id)) order.push(p.id); }); });
  P.forEach(p => { if (!order.includes(p.id)) order.push(p.id); });
  const arcs = segs.map(g => {
    const ax = +X(g.from.lng), ay = +Y(g.from.lat), bx = +X(g.to.lng), by = +Y(g.to.lat);
    const mx = (ax + bx) / 2, my = (ay + by) / 2, dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy) || 1, kk = Math.min(0.18, 40 / len);
    return `<path d="M${ax} ${ay}Q${(mx - dy * kk).toFixed(1)} ${(my + dx * kk).toFixed(1)} ${bx} ${by}" class="arc" marker-end="url(#ah)"/>`;
  }).join('');
  const boxes = [];
  const marks = order.map((id, i) => {
    const p = pts.get(id), x = +X(p.lng), y = +Y(p.lat), name = p.code || p.name, w = name.length * 15 + 20;
    const free = !boxes.some(b => x + 15 < b[0] + b[2] && x + 15 + w > b[0] && Math.abs(y - b[1]) < 22) && !order.slice(0, i).some(j => { const q = pts.get(j); return Math.hypot(+X(q.lng) - x, +Y(q.lat) - y) < 18; });
    if (free) boxes.push([x + 15, y, w]);
    return `<g transform="translate(${x} ${y})"><circle r="11" class="pt"/><text y="4" class="num">${i + 1}</text>${free ? `<text x="15" y="5" class="lbl">${esc(name)}</text>` : ''}</g>`;
  }).join('');
  const legend = `<div class="legend">${order.map((id, i) => `<span><b>${i + 1}</b>${esc(pts.get(id).name)}</span>`).join('')}</div>`;
  return `<svg viewBox="0 0 ${W} ${H}" class="map"><defs><marker id="ah" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#3182F6"/></marker></defs>
    <rect width="${W}" height="${H}" class="sea"/><path d="${land}" class="land"/>${arcs}${marks}</svg>${legend}`;
}
async function buildBook(o, progress) {
  const days = []; for (let d = o.from; d <= o.to; d = addDays(d, 1)) days.push(d);
  const inc = k => o.inc.includes(k);
  const st = S.settings, plansAll = expandPlans();
  const phAll = S.photos.filter(p => p.date >= o.from && p.date <= o.to).sort((a, b) => a.date.localeCompare(b.date) || a.id.localeCompare(b.id));
  const exps = S.expenses.filter(e => e.date >= o.from && e.date <= o.to);
  const spent = exps.reduce((a, e) => a + toKRW(num(e.amount), e.cur), 0);
  const cities = [...new Set(days.flatMap(d => dayRegs(d).map(r => r.name)))];
  const fmtLong = d => { const x = parseD(d); return `${x.getFullYear()}년 ${x.getMonth() + 1}월 ${x.getDate()}일 (${WD[x.getDay()]})`; };
  const parts = [];
  parts.push(`<!DOCTYPE html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(o.title)}</title><style>${BOOK_CSS}</style></head><body>`);
  parts.push(`<header class="cover"><div class="k">TRAVEL RECORD</div><h1>${esc(o.title)}</h1><div class="when">${fmtLong(o.from)} — ${fmtLong(o.to)}</div>
    ${cities.length ? `<div class="route">${cities.map(c => `<span>${esc(c)}</span>`).join('<i>→</i>')}</div>` : (st.route ? `<div class="route"><span>${esc(st.route)}</span></div>` : '')}
    <div class="stats"><div><b>${days.length}</b>일</div><div><b>${cities.length || '-'}</b>곳</div><div><b>${phAll.length}</b>장</div>${inc('money') ? `<div><b>${fmtShortKRW(spent)}</b></div>` : ''}</div></header>`);
  if (inc('map')) { const m = routeSVG(o.from, o.to); if (m) parts.push(`<section class="mapbox"><h2>지나온 길</h2>${m}</section>`); }
  let done = 0;
  for (const d of days) {
    const e = diaryOf(d), plans = plansAll.filter(p => p.date === d).sort(planSort), phs = phAll.filter(p => p.date === d);
    const dx = exps.filter(x => x.date === d), dsum = dx.reduce((a, x) => a + toKRW(num(x.amount), x.cur), 0);
    if (!(e && (e.text || e.title)) && !(inc('plan') && plans.length) && !(inc('photo') && phs.length)) continue;
    const regs = dayRegs(d), dn = dayNum(d);
    let h = `<section class="day"><div class="dh"><div><div class="dn">${dn > 0 ? 'DAY ' + dn : ''}</div><h2>${fmtLong(d)}</h2></div>
      <div class="rg">${regs.map(r => `<span style="background:${r.color}">${esc(r.name)}</span>`).join('')}${e && e.mood ? `<em>${e.mood}</em>` : ''}</div></div>`;
    if (inc('diary') && e && (e.title || e.text)) h += `<article>${e.title ? `<h3>${esc(e.title)}</h3>` : ''}${e.text ? `<p>${esc(e.text).replace(/\n/g, '<br>')}</p>` : ''}</article>`;
    if (inc('plan') && plans.length) h += `<ol class="tl">${plans.map(p => { const tr = isTrip(p) ? tripInfo(p) : null, pl = p.placeId && !tr ? byId('places', p.placeId) : null;
      return `<li class="${p.done ? 'ok' : ''}"><span class="t">${p.time ? p.time + `<small>${TZS[tzOf(p)].s}</small>` : '·'}</span><span class="c">${tr && tr.mode ? tr.mode.n + ' · ' : ''}<b>${esc(p.title)}</b>${pl ? ` <small>${esc(pl.name)}</small>` : ''}${tr && tr.arr ? ` <small>${esc(tr.arr)}${tr.dur ? ' · ' + tr.dur : ''}</small>` : ''}${p.memo ? `<small class="m">${esc(p.memo)}</small>` : ''}</span></li>`; }).join('')}</ol>`;
    parts.push(h);
    if (inc('photo') && phs.length) {
      parts.push(`<div class="ph n${Math.min(phs.length, 3)}">`);
      for (const p of phs) {
        const src = await photoData(p.id, o.q); done++; progress && progress(done, phAll.length);
        if (src) parts.push(`<figure><img src="${src}">${p.caption ? `<figcaption>${esc(p.caption)}</figcaption>` : ''}</figure>`);
      }
      parts.push(`</div>`);
    }
    parts.push(`${inc('money') && dsum ? `<div class="spent">이날 쓴 돈 ${fmtKRW(dsum)}</div>` : ''}</section>`);
  }
  if (inc('money') && exps.length) {
    const byCat = {}; exps.forEach(x => byCat[x.cat] = (byCat[x.cat] || 0) + toKRW(num(x.amount), x.cur));
    parts.push(`<section class="money"><h2>경비 요약</h2><div class="total">${fmtKRW(spent)}</div><div class="bars">${Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([k, v]) => `<div><span>${ECATS[k] || '기타'}</span><i style="width:${(v / spent * 100).toFixed(1)}%;background:${(CATS[k] || CATS.etc).c}"></i><b>${fmtKRW(v)}</b></div>`).join('')}</div></section>`);
  }
  parts.push(`<footer>WSG로 만든 여행 기록 · ${todayStr()}</footer></body></html>`);
  return parts;
}
const BOOK_CSS = `
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:Pretendard,-apple-system,'Apple SD Gothic Neo','Malgun Gothic','Noto Sans KR',sans-serif;background:#F2F4F6;color:#191F28;line-height:1.6;letter-spacing:-.2px}
.cover{min-height:92vh;display:flex;flex-direction:column;justify-content:flex-end;padding:56px 28px 44px;background:linear-gradient(160deg,#1B64DA,#3182F6 55%,#7AB5FF);color:#fff}
.cover .k{font-size:12px;letter-spacing:4px;font-weight:700;opacity:.8}
.cover h1{font-size:44px;line-height:1.15;letter-spacing:-1.5px;margin:10px 0 8px}
.cover .when{opacity:.9}
.route{display:flex;flex-wrap:wrap;gap:6px;align-items:center;margin-top:18px}
.route span{background:rgba(255,255,255,.2);padding:6px 12px;border-radius:999px;font-weight:700;font-size:14px}
.route i{font-style:normal;opacity:.7}
.stats{display:flex;gap:26px;margin-top:30px;padding-top:18px;border-top:1px solid rgba(255,255,255,.3)}
.stats b{font-size:26px;margin-right:2px}
section{max-width:760px;margin:16px auto;background:#fff;border-radius:24px;padding:28px 24px;break-inside:avoid-page}
h2{font-size:22px;letter-spacing:-.5px}
.mapbox h2{margin-bottom:14px}
.map{width:100%;height:auto;border-radius:16px;display:block}
.map .sea{fill:#DCEBFA}.map .land{fill:#FBF9F4;stroke:#CFC6B5;stroke-width:.8}
.map .arc{fill:none;stroke:#3182F6;stroke-width:3;stroke-linecap:round}
.map .pt{fill:#191F28;stroke:#fff;stroke-width:3}.map .num{fill:#fff;font-size:12px;font-weight:700;text-anchor:middle}
.legend{display:flex;flex-wrap:wrap;gap:8px 16px;margin-top:14px;font-size:13.5px;color:#4E5968}.legend span{display:inline-flex;align-items:center;gap:6px}.legend b{width:20px;height:20px;border-radius:50%;background:#191F28;color:#fff;font-size:11px;display:grid;place-items:center}
.map .lbl{font-size:15px;font-weight:700;fill:#191F28;paint-order:stroke;stroke:#fff;stroke-width:4px}
.dh{display:flex;justify-content:space-between;align-items:flex-end;gap:12px;padding-bottom:14px;border-bottom:1px solid #E5E8EB}
.dn{font-size:12px;font-weight:800;letter-spacing:2px;color:#3182F6}
.rg{display:flex;gap:6px;align-items:center;flex-wrap:wrap;justify-content:flex-end}
.rg span{color:#fff;font-size:12px;font-weight:700;padding:4px 10px;border-radius:999px}.rg em{font-style:normal;font-size:24px}
article{margin-top:20px}article h3{font-size:19px;margin-bottom:6px}article p{font-size:16px;color:#333D4B;white-space:normal}
.tl{list-style:none;margin-top:20px;border-left:2px solid #E5E8EB;padding-left:16px}
.tl li{display:flex;gap:12px;padding:7px 0;position:relative}
.tl li::before{content:"";position:absolute;left:-22px;top:15px;width:10px;height:10px;border-radius:50%;background:#B0B8C1}
.tl li.ok::before{background:#3182F6}
.tl .t{width:58px;flex:none;font-weight:700;font-size:14px;color:#4E5968}.tl .t small{display:block;font-size:10px;color:#8B95A1;font-weight:700}
.tl .c{font-size:15px}.tl small{color:#8B95A1;font-size:13px}.tl small.m{display:block}
.ph{display:grid;gap:8px;margin-top:20px}.ph.n1{grid-template-columns:1fr}.ph.n2{grid-template-columns:1fr 1fr}.ph.n3{grid-template-columns:1fr 1fr 1fr}
.ph figure{break-inside:avoid}.ph img{width:100%;border-radius:12px;display:block;aspect-ratio:1;object-fit:cover}.ph.n1 img{aspect-ratio:auto}
.ph figcaption{font-size:12.5px;color:#6B7684;margin-top:4px}
.spent{margin-top:16px;font-size:13px;color:#8B95A1;text-align:right}
.money .total{font-size:32px;font-weight:800;margin:6px 0 16px}
.bars div{display:grid;grid-template-columns:90px 1fr 110px;gap:10px;align-items:center;padding:5px 0;font-size:14px}
.bars i{height:10px;border-radius:5px;display:block}.bars b{text-align:right}
footer{text-align:center;color:#8B95A1;font-size:12px;padding:30px 0 50px}
@media print{body{background:#fff}.cover{min-height:auto;height:96vh;-webkit-print-color-adjust:exact;print-color-adjust:exact}section{border-radius:0;margin:0 auto 6mm;padding:6mm 0;border-bottom:1px solid #E5E8EB;break-inside:avoid}.mapbox{page-break-before:always}.money{break-before:auto}*{-webkit-print-color-adjust:exact;print-color-adjust:exact}.ph img{aspect-ratio:auto;max-height:70vh;object-fit:contain}}
@media(max-width:600px){.cover h1{font-size:34px}section{margin:12px;padding:22px 18px}}`;

// save a large text in pieces (Android: stream to the chosen file; browser: download)
function saveLarge(name, parts, mime) {
  if (NA && NA.beginSave) {
    window.onSaveReady = ok => {
      if (!ok) { toast('저장을 취소했어요'); return; }
      try { for (const p of parts) for (let i = 0; i < p.length; i += 512000) NA.writeChunk(p.slice(i, i + 512000)); NA.endSave(); }
      catch (e) { toast('저장 중 문제가 생겼어요'); }
    };
    NA.beginSave(name, mime);
    return;
  }
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob(parts, { type: mime })); a.download = name; document.body.appendChild(a); a.click(); a.remove();
  toast('파일을 저장했어요');
}

Object.assign(A, {
  'diary-open': el => diaryForm(el.dataset.d),
  'diary-photo': el => { const d = el.dataset.d; closeSheet(); pickFiles({ kind: 'photo', date: d }); },
  'book-make': () => bookSheet(),
  btog: el => el.classList.toggle('on'),
  bq: el => { $$('#bQ button').forEach(b => b.classList.toggle('on', b === el)); ui.bookEst && ui.bookEst(); },
  'book-go': async () => {
    const o = { from: $('#bFrom').value, to: $('#bTo').value, title: $('#bTitle').value.trim() || '여행', inc: $$('#bInc button.on').map(b => b.dataset.k), q: $$('#bQ button.on')[0].dataset.k };
    if (!o.from || !o.to || o.to < o.from) { toast('기간을 확인해주세요'); return; }
    closeSheet();
    const box = document.createElement('div'); box.className = 'bookprog'; box.innerHTML = '<b>여행 기록 만드는 중…</b><span>사진 정리 중</span><i><u></u></i>'; document.body.appendChild(box);
    try {
      const parts = await buildBook(o, (n, all) => { box.querySelector('span').textContent = `사진 ${n} / ${all}`; box.querySelector('u').style.width = (n / all * 100) + '%'; });
      box.remove();
      saveLarge(`${o.title.replace(/[\\/:*?"<>|]/g, '')}_여행기록.html`, parts, 'text/html');
    } catch (e) { box.remove(); toast('만들지 못했어요'); }
  },
});
