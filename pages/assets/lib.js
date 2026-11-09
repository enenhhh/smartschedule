/* 本文件与宣传片代码（smartschedule-promo/lib.js）相同，复刻的应用界面在官网和宣传片里共用。 */
/* 通用工具：缓动、文字动画、手机样机、触点、音效提示点 */
'use strict';
const TL = window.TIMELINE;                      // render.mjs 注入 timeline.json
const SCENE = Object.fromEntries(TL.scenes.map(s => [s.id, s]));
const $ = id => document.getElementById(id);
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, t) => a + (b - a) * t;
const E = {
  lin: t => t,
  outExpo: t => t >= 1 ? 1 : 1 - Math.pow(2, -10 * t),
  outCubic: t => 1 - Math.pow(1 - t, 3),
  outQuint: t => 1 - Math.pow(1 - t, 5),
  inCubic: t => t * t * t,
  inOutCubic: t => t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2,
  inOutQuint: t => t < .5 ? 16 * t ** 5 : 1 - Math.pow(-2 * t + 2, 5) / 2,
  inOutSine: t => -(Math.cos(Math.PI * t) - 1) / 2,
  outBack: t => { const c1 = 1.35, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); },
};
/** t 在 [a,b] 内的缓动进度 0..1 */
const P = (t, a, b, ease = E.outExpo) => ease(clamp((t - a) / (b - a)));
/** 多段关键帧插值：keys = [[t, v], ...] */
function K(t, keys, ease = E.inOutCubic) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [t1, v1] = keys[i], [t0, v0] = keys[i - 1];
    if (t <= t1) return lerp(v0, v1, ease(clamp((t - t0) / (t1 - t0))));
  }
  return keys[keys.length - 1][1];
}

/* ---------- 音效提示点：画面在哪一刻发生什么，audio.mjs 按这里配音 ---------- */
const CUES = [];
function cue(scene, at, type, opt = {}) { CUES.push({ t: +(SCENE[scene].start + at).toFixed(3), type, ...opt }); }
window.CUES = CUES;

/* ---------- 文字 ---------- */
function split(el) {
  const out = [];
  const walk = node => {
    [...node.childNodes].forEach(c => {
      if (c.nodeType === 3) {
        const frag = document.createDocumentFragment();
        for (const ch of c.textContent) {
          const s = document.createElement('span'); s.className = 'ch'; s.textContent = ch;
          frag.appendChild(s); out.push(s);
        }
        node.replaceChild(frag, c);
      } else if (c.nodeType === 1 && c.tagName !== 'BR') walk(c);
    });
  };
  walk(el); el._ch = out; return el;
}
/** 逐字显现：字从下方带模糊升起 */
function reveal(el, lt, delay, opt = {}) {
  if (!el._ch) split(el);
  const st = opt.stagger ?? .032, dur = opt.dur ?? 1.0, rise = opt.rise ?? .42, blur = opt.blur ?? 12;
  el._ch.forEach((c, i) => {
    const p = P(lt, delay + i * st, delay + i * st + dur, E.outExpo);
    c.style.opacity = p;
    c.style.transform = p >= 1 ? 'none' : `translateY(${(1 - p) * rise}em)`;
    c.style.filter = p > .995 ? 'none' : `blur(${(1 - p) * blur}px)`;
  });
}
function fadeUp(el, lt, delay, dur = 1.0, dist = 40, blur = 10) {
  const p = P(lt, delay, delay + dur, E.outExpo);
  el.style.opacity = p;
  el.style.transform = `translateY(${(1 - p) * dist}px)`;
  el.style.filter = p > .995 ? 'none' : `blur(${(1 - p) * blur}px)`;
  return p;
}
function setText(el, s) { if (el._t !== s) { el.textContent = s; el._t = s; } }
function setHTML(el, s) { if (el._h !== s) { el.innerHTML = s; el._h = s; } }

/* ---------- 手机样机 ---------- */
const RATIO = 1534 / 694;          // 与截图一致（2688 / 1216）
const BEZEL = 12;
/** 新建手机。content: 截图名（字符串）或 app 界面节点 */
function makePhone(parent, w, content) {
  const el = document.createElement('div'); el.className = 'phone';
  const h = Math.round(w * RATIO);
  el.style.width = (w + BEZEL * 2) + 'px'; el.style.height = (h + BEZEL * 2) + 'px';
  el.style.borderRadius = (w * 0.15) + 'px'; el.style.padding = BEZEL + 'px';
  const scr = document.createElement('div'); scr.className = 'scr'; scr.style.borderRadius = (w * 0.124) + 'px';
  if (typeof content === 'string') {
    const img = document.createElement('img'); img.className = 'shot'; img.src = `shots/${content}.jpg`; scr.appendChild(img);
  } else {
    content.style.transform = `scale(${w / 694})`;
    scr.appendChild(content);
  }
  scr.insertAdjacentHTML('beforeend', '<div class="glare"></div><div class="cut"></div>');
  el.appendChild(scr); parent.appendChild(el);
  el._w = w + BEZEL * 2; el._h = h + BEZEL * 2; el._scr = scr; el._k = w / 694;
  return el;
}
/** 以中心点 (cx, cy) 放置手机，再叠加 tf 变换 */
function place(el, cx, cy, tf = '', op = 1) {
  el.style.transform = `translate(${cx - el._w / 2}px, ${cy - el._h / 2}px) ${tf}`;
  el.style.opacity = op;
}

/* ---------- 触点 ---------- */
/**
 * 一个手指触点。keys: [{t, x, y, down?, show?}]，坐标是 app 画布坐标（694 宽）。
 * down 的时刻会播放按下 + 涟漪。
 */
function makeTouch(appEl) {
  const el = document.createElement('div'); el.className = 'touch';
  el.innerHTML = '<div class="ring"></div><div class="dot"></div>';
  appEl.appendChild(el); return el;
}
function touchAt(el, lt, keys) {
  // 位置：关键帧之间缓动移动
  let x = keys[0].x, y = keys[0].y;
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i];
    if (lt >= k.t) { x = k.x; y = k.y; }
    const n = keys[i + 1];
    if (n && lt > k.t && lt < n.t) {
      const mv = n.move ?? Math.min(.45, n.t - k.t);
      const u = E.inOutCubic(clamp((lt - (n.t - mv)) / mv));
      x = lerp(k.x, n.x, u); y = lerp(k.y, n.y, u);
    }
  }
  const first = keys[0].t, last = keys[keys.length - 1].t;
  const show = P(lt, first - .35, first, E.outCubic) * (1 - P(lt, last + .35, last + .7, E.inOutCubic));
  // 按下：每个 down 关键帧附近缩小，涟漪向外扩
  let press = 0, ring = 0, ringA = 0;
  for (const k of keys) {
    if (!k.down) continue;
    const d = lt - k.t;
    if (d > -.12 && d < .22) press = Math.max(press, 1 - Math.abs((d - .02) / .14));
    if (k.hold && lt >= k.t && lt <= k.hold) press = 1;
    if (d >= 0 && d < .6) { ring = d / .6; ringA = 1 - ring; }
  }
  el.style.transform = `translate(${x}px, ${y}px) scale(${(lerp(.6, 1, show)) * (1 - .18 * clamp(press))})`;
  el.style.opacity = show;
  const r = el.firstChild;
  r.style.opacity = ringA * .9; r.style.transform = `scale(${1 + ring * 1.6})`;
}

/* ---------- 应用图标（与启动图标一致） ---------- */
function appIconSVG(size, id = 'ig') {
  return `<svg width="${size}" height="${size}" viewBox="0 0 108 108" style="display:block">
    <defs><linearGradient id="${id}${size}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#24395f"/><stop offset="1" stop-color="#152340"/></linearGradient></defs>
    <rect width="108" height="108" rx="30" fill="url(#${id}${size})"/>
    <path d="M35,31h38a7,7 0 0 1 7,7v34a7,7 0 0 1 -7,7h-38a7,7 0 0 1 -7,-7v-34a7,7 0 0 1 7,-7z" fill="#f7f9fc"/>
    <path d="M35,31h38a7,7 0 0 1 7,7v7h-52v-7a7,7 0 0 1 7,-7z" fill="#1b2a4a"/>
    <circle cx="42.5" cy="38" r="2.2" fill="#9fe1cb"/><circle cx="65.5" cy="38" r="2.2" fill="#9fe1cb"/>
    <rect x="36" y="52" width="16" height="9" rx="4.5" fill="#006bdf"/><rect x="56" y="52" width="16" height="9" rx="4.5" fill="#9fe1cb"/>
    <rect x="36" y="65" width="16" height="9" rx="4.5" fill="#9fe1cb"/><rect x="56" y="65" width="16" height="9" rx="4.5" fill="#006bdf"/>
  </svg>`;
}

/* ---------- 课程配色：与应用 courseColor() 相同的算法 ---------- */
function courseColor(name) {
  if (!name) return '#dde4ea';
  let h = 7; for (const c of name) h = (Math.imul(h, 31) + c.charCodeAt(0)) | 0;
  const hue = ((h % 360) + 360) % 360;
  return `hsl(${hue}, 55%, 80%)`;
}
const FULL = { 数: '数学', 语: '语文', 英: '英语', 物: '物理', 化: '化学', 道: '道法', 历: '历史', 体: '体育', 班: '班会' };

/* ---------- 图标（Material Symbols 风格，手绘路径） ---------- */
const I = {
  back: c => `<svg width="34" height="34" viewBox="0 0 24 24"><path d="M20 11H7.8l5.6-5.6L12 4l-8 8 8 8 1.4-1.4L7.8 13H20z" fill="${c || '#191c1e'}"/></svg>`,
  check: (c, s = 26) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" fill="${c || '#191c1e'}"/></svg>`,
  cal: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M19 4h-1V2h-2v2H8V2H6v2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zm0 16H5V10h14zM7 12h5v5H7z" fill="${c}"/></svg>`,
  grid: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M3 5v14h18V5zm5 12H5V7h3zm5 0h-3V7h3zm6 0h-4V7h4z" fill="${c}"/></svg>`,
  list: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M3.5 7.5 5 9l3-3M3.5 14.5 5 16l3-3" stroke="${c}" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M11 7h10M11 15h10" stroke="${c}" stroke-width="2.2" stroke-linecap="round"/></svg>`,
  gear: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M19.1 12.9a7.4 7.4 0 0 0 0-1.8l2-1.6-2-3.4-2.4 1a7 7 0 0 0-1.6-.9L14.8 3h-4l-.4 2.6a7 7 0 0 0-1.6.9l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 1.8l-2 1.6 2 3.4 2.4-1a7 7 0 0 0 1.6.9l.4 2.6h4l.4-2.6a7 7 0 0 0 1.6-.9l2.4 1 2-3.4zM12.8 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z" fill="none" stroke="${c}" stroke-width="1.8"/></svg>`,
  share: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M18 16a3 3 0 0 0-2.1.9L8.9 12.7a3 3 0 0 0 0-1.4l7-4.1A3 3 0 1 0 15 5a3 3 0 0 0 .1.7L8.1 9.8a3 3 0 1 0 0 4.4l7 4.1a2.8 2.8 0 0 0-.1.7 3 3 0 1 0 3-3z" fill="${c}"/></svg>`,
  num: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><rect x="2" y="5" width="20" height="14" rx="2.5" fill="${c}"/><text x="12" y="15.2" font-size="7.6" text-anchor="middle" fill="#fff" font-family="GSF" font-weight="700">123</text></svg>`,
  copy: (c, s = 34) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M16 1H4a2 2 0 0 0-2 2v14h2V3h12zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11z" fill="${c}"/></svg>`,
  up: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M5 20h14v-2H5zm0-10h4v6h6v-6h4l-7-7z" fill="${c}"/></svg>`,
  down: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7zM5 18v2h14v-2z" fill="${c}"/></svg>`,
  folder: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M20 6h-8l-2-2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2zm0 12H4V8h16z" fill="${c}"/></svg>`,
  chev: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M10 6 8.6 7.4l4.6 4.6-4.6 4.6L10 18l6-6z" fill="${c || '#41484d'}"/></svg>`,
  minus: (c, s = 34) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M5 11h14v2H5z" fill="${c}"/></svg>`,
  plus: (c, s = 34) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z" fill="${c}"/></svg>`,
  spark: (c, s = 26) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M10 3l1.8 5.2L17 10l-5.2 1.8L10 17l-1.8-5.2L3 10l5.2-1.8zM18 13l.9 2.1L21 16l-2.1.9L18 19l-.9-2.1L15 16l2.1-.9z" fill="${c}"/></svg>`,
  trash: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z" fill="${c}"/></svg>`,
  clock: (c, s = 36) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="${c}" stroke-width="2"/><path d="M12 7v5.4l3.4 2" stroke="${c}" stroke-width="2" fill="none" stroke-linecap="round"/></svg>`,
  more: (c, s = 36) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><circle cx="12" cy="5" r="2" fill="${c}"/><circle cx="12" cy="12" r="2" fill="${c}"/><circle cx="12" cy="19" r="2" fill="${c}"/></svg>`,
  book: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M21 5c-1.1-.4-2.3-.5-3.5-.5-1.9 0-4 .4-5.5 1.5-1.5-1.1-3.6-1.5-5.5-1.5S2.5 4.9 1 6v14.6c1.5-1.1 3.6-1.5 5.5-1.5s4 .4 5.5 1.5c1.5-1.1 3.6-1.5 5.5-1.5 1.2 0 2.4.1 3.5.5zm0 13.5c-1.1-.3-2.3-.5-3.5-.5-1.7 0-4.2.7-5.5 1.5V8c1.3-.8 3.8-1.5 5.5-1.5 1.2 0 2.4.2 3.5.5z" fill="${c}"/></svg>`,
  sofa: (c, s = 34) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M4 9V7a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v2a2 2 0 0 0-2 2v2H6v-2a2 2 0 0 0-2-2zm17 1a2 2 0 0 0-2 2v3H5v-3a2 2 0 1 0-4 0v5a2 2 0 0 0 2 2v1h2v-1h14v1h2v-1a2 2 0 0 0 2-2v-5a2 2 0 0 0-2-2z" fill="${c}"/></svg>`,
  pen: (c, s = 30) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24"><path d="M3 17.2V21h3.8L17.8 9.9l-3.7-3.7zM20.7 7a1 1 0 0 0 0-1.4l-2.3-2.3a1 1 0 0 0-1.4 0l-1.8 1.8 3.7 3.7z" fill="${c}"/></svg>`,
};
const STATUS_ICONS = `<svg width="30" height="20" viewBox="0 0 30 20"><path d="M2 14a14 14 0 0 1 26 0M6 15.5a9.5 9.5 0 0 1 18 0M10.5 17a5 5 0 0 1 9 0" stroke="#2a2d31" stroke-width="2.4" fill="none" stroke-linecap="round"/></svg>
<svg width="34" height="20" viewBox="0 0 34 20"><g fill="#2a2d31"><rect x="1" y="14" width="5" height="5" rx="1"/><rect x="9" y="10" width="5" height="9" rx="1"/><rect x="17" y="6" width="5" height="13" rx="1"/><rect x="25" y="1" width="5" height="18" rx="1"/></g></svg>
<svg width="48" height="24" viewBox="0 0 48 24"><rect x="1.5" y="2" width="38" height="20" rx="6" fill="none" stroke="#2a2d31" stroke-width="2.2"/><rect x="42" y="8" width="3" height="8" rx="1.5" fill="#2a2d31"/><text x="20.5" y="17" font-size="14" text-anchor="middle" font-family="GSF" font-weight="600" fill="#2a2d31">70</text></svg>`;
