/* 本文件与宣传片代码（smartschedule-promo/app.js）相同，复刻的应用界面在官网和宣传片里共用。 */
/*
 * 复刻的应用界面。坐标都按真实截图量出来（截图缩放到 694 宽的坐标系），
 * 颜色从截图取色，课程配色与应用 courseColor() 同一算法。
 * 每个界面返回 { root, ...更新函数 }，场景脚本按时间调用更新函数，做出可点击的交互。
 */
'use strict';

/* ---------- 搭积木的小工具 ---------- */
function div(parent, cls = '', style = '', html = '') {
  const d = document.createElement('div'); if (cls) d.className = cls;
  if (style) d.style.cssText = style; if (html) d.innerHTML = html;
  parent.appendChild(d); return d;
}
/** 文字：x 为左边（align=right 时为右边，center 时为中心），cy 为文字垂直中心 */
function T(parent, x, cy, size, text, o = {}) {
  const lh = o.lh || 1.25;
  const s = `position:absolute;top:${cy - size * lh / 2}px;font-size:${size}px;line-height:${lh};white-space:${o.wrap ? 'normal' : 'nowrap'};` +
    (o.align === 'right' ? `right:${694 - x}px;text-align:right;` : o.align === 'center' ? `left:${x - 400}px;width:800px;text-align:center;` : `left:${x}px;`) +
    (o.w ? `width:${o.w}px;` : '') + (o.color ? `color:${o.color};` : '') + (o.weight ? `font-weight:${o.weight};` : '') +
    (o.ls ? `letter-spacing:${o.ls};` : '') + (o.css || '');
  return div(parent, '', s, text);
}
function statusBar(root, time) {
  const sb = div(root, 'sb');
  div(sb, 'tm', '', time);
  div(sb, 'ic', '', STATUS_ICONS);
  return sb;
}
function navBar(root, sel) {
  const nav = div(root, 'nav');
  const items = [['今日', I.cal], ['课表', I.grid], ['作业', I.list], ['设置', I.gear]];
  const xs = [87, 239, 391, 543];
  items.forEach(([name, ic], i) => {
    div(nav, 'it' + (i === sel ? ' on' : ''), `left:${xs[i]}px`, `<div class="pl">${ic(i === sel ? '#0b1d29' : '#2a2d31', 30)}</div>${name}`);
  });
  return nav;
}
function subTopBar(root, title, sub, right = '') {
  const tb = div(root, 'topbar');
  div(tb, 'a', 'left:28px;top:96px', I.back());
  if (sub) { T(tb, 82, 102, 29, title, { weight: 600 }); T(tb, 82, 135, 15, sub, { color: '#41484d' }); }
  else T(tb, 82, 115, 29, title, { weight: 600 });
  if (right) div(tb, 'a', '', right);
  return tb;
}
function segmented(parent, x, y, w, labels, h = 62) {
  const seg = div(parent, 'seg', `left:${x}px;top:${y}px;width:${w}px;height:${h}px;border-radius:${h / 2}px`);
  const ind = div(seg, 'ind', `width:${w / labels.length}px`);
  const spans = labels.map(() => div(seg, 'o'));
  seg._set = (f /* 0..n-1 连续值 */) => {
    ind.style.transform = `translateX(${f * w / labels.length}px)`;
    const sel = Math.round(f);
    spans.forEach((s, i) => setHTML(s, (i === sel ? I.check('#191c1e', 24) : '') + labels[i]));
  };
  seg._set(0);
  return seg;
}
const fmt = m => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
const toMin = s => { const [h, m] = s.split(':').map(Number); return h * 60 + m; };

/* ---------- 真实数据（来自截图） ---------- */
const ODD = [ // 8 节 × 周一..周五（简写）
  ['数', '语', '英', '语', '化'], ['数', '语', '英', '化', '数'], ['物', '数', '数', '数', '道'], ['化', '道', '物', '体', '物'],
  ['英', '英', '体', '物', '语'], ['语', '化', '语', '道', '英'], ['班', '体', '历', '英', '历'], ['数', '英', '语', '物', '道'],
];
const EVEN_DIFF = { '2,0': '化', '3,0': '物', '3,3': '历', '2,4': '体', '6,4': '道' };  // 双周不同的格子（周一、周四、周五）
const MORNING = ['物', '语', '数', '英', '历'];
const PERIOD_START = ['08:00', '09:00', '10:15', '11:15', '14:30', '15:30', '16:45', '17:40'];
const PLAN = [['08:00', '08:45'], ['09:00', '09:45'], ['10:15', '11:00'], ['11:15', '12:00'], ['14:30', '15:15'], ['15:30', '16:15'], ['16:45', '17:30'], ['17:40', '18:40']];
const COLX = [108, 219, 329, 440, 550], COLW = 105, CELLH = 86, ROWSTEP = 92.5;

/* ======================================================================
   课表页（今日 / 课表 / 作业 / 设置 里的「课表」）
   ====================================================================== */
function TimetableScreen() {
  const root = div(document.body, 'app'); root.remove();
  statusBar(root, '19:11');
  T(root, 32, 112, 38, '课表', { weight: 650 });
  const sub = T(root, 32, 157, 18, '', { color: '#41484d' });
  div(root, 'circ', 'left:438px;top:94px;width:62px;height:62px', I.sofa('#0c2e40', 34));
  div(root, 'btnt', 'left:516px;top:94px;width:147px;height:62px;gap:10px', I.pen('#0c2e40', 28) + '编辑');
  const seg = segmented(root, 25, 187, 644, ['单周', '双周']);
  div(root, 'card', 'left:25px;top:272px;width:644px;height:878px');
  const days = ['一', '二', '三', '四', '五'];
  days.forEach((d, i) => {
    const on = i === 0;
    div(root, 'a', `left:${COLX[i]}px;top:285px;width:${COLW}px;height:44px;border-radius:22px;display:grid;place-items:center;font-size:21px;font-weight:600;` +
      (on ? 'background:#00668c;color:#fff' : ''), d);
  });
  div(root, 'a', 'left:56px;top:353px', I.book('#4d4a6b', 30));
  MORNING.forEach((m, i) => div(root, 'cell mr', `left:${COLX[i]}px;top:340px;width:${COLW}px;height:55px`, m));
  const cells = [];
  for (let r = 0; r < 8; r++) {
    const top = 401 + r * ROWSTEP;
    T(root, 71, top + 32, 20, String(r + 1), { align: 'center', color: '#2a2d31' });
    T(root, 71, top + 56, 15, PERIOD_START[r], { align: 'center', color: '#41484d' });
    cells.push(ODD[r].map((c, i) => {
      const d = div(root, 'cell', `left:${COLX[i]}px;top:${top}px;width:${COLW}px;height:${CELLH}px;background:${courseColor(FULL[c])}`, c);
      return d;
    }));
  }
  T(root, 34, 1180, 14.5, '点格子查看详情 · 时间方案：默认时间表', { color: '#41484d' });
  navBar(root, 1);
  /** f: 0 = 单周，1 = 双周（连续值，用于动画）；week: 当前周次 */
  function set(f, week) {
    seg._set(f);
    setText(sub, `我的课表 · 本周第 ${week} 周（${week % 2 ? '单周' : '双周'}）`);
    for (const k in EVEN_DIFF) {
      const [r, c] = k.split(',').map(Number), el = cells[r][c];
      // 翻牌：前半程旧课、后半程新课
      const ang = f * 180, showEven = f >= .5;
      const name = showEven ? EVEN_DIFF[k] : ODD[r][c];
      setText(el, name);
      el.style.background = courseColor(FULL[name]);
      el.style.transform = `perspective(500px) rotateX(${showEven ? ang - 180 : ang}deg)`;
      el.style.boxShadow = f > .02 && f < .98 ? '0 0 0 3px rgba(0,102,140,.5)' : 'none';
    }
  }
  set(0, 11);
  return { root, set, cells };
}

/* ======================================================================
   今日页：实时上课状态
   ====================================================================== */
function TodayScreen() {
  const root = div(document.body, 'app'); root.remove();
  const clock = statusBar(root, '10:42').firstChild;
  T(root, 32, 118, 38, '11月9日 周一', { weight: 650 });
  T(root, 32, 158, 18, '第 11 周 · 单周 · 我的课表', { color: '#41484d' });
  div(root, 'hero', 'left:25px;top:200px;width:644px;height:190px');
  const htitle = T(root, 58, 254, 30, '', { weight: 600, color: '#001e2c' });
  const hsub = T(root, 58, 295, 18, '', { color: 'rgba(0,30,44,.8)' });
  const bar = div(root, 'a', 'left:58px;top:328px;width:578px;height:34px');
  T(root, 44, 440, 18, '今日课程', { color: '#00668c', weight: 500 });
  div(root, 'card', 'left:25px;top:484px;width:644px;height:786px');
  const courses = ['数学', '数学', '物理', '化学', '英语', '语文', '班会', '数学'];
  const rows = [];
  const mk = (i, y, start, end, label, name, chip) => {
    const g = div(root, 'a', `left:25px;top:${y - 40}px;width:644px;height:80px`);
    const hl = div(g, 'a', 'left:12px;top:4px;width:620px;height:72px;border-radius:22px;background:rgba(0,102,140,.09);opacity:0');
    T(g, 24, 33, 21, start, { color: '#41484d' }); T(g, 24, 56, 15, end, { color: '#71787e' });
    const dot = div(g, 'a', 'left:103px;top:31px;width:18px;height:18px;border-radius:50%;background:#c4c9cf');
    if (i < 0) div(g, 'a', 'left:140px;top:24px', I.book('#4d4a6b', 30));
    T(g, i < 0 ? 182 : 142, 22, 14, label, { color: '#71787e' });
    const nm = T(g, i < 0 ? 182 : 142, 49, 22, name, { weight: 500 });
    if (chip) div(g, 'a', `left:572px;top:20px;width:48px;height:42px;border-radius:14px;display:grid;place-items:center;font-size:19px;background:${courseColor(name)};color:#3a3d42`, chip);
    return { g, hl, dot, nm };
  };
  rows.push(mk(-1, 536, '07:30', '08:00', '晨读', '物理', null));
  PLAN.forEach(([s, e], i) => rows.push(mk(i, 616 + i * 80.5, s, e, `第 ${i + 1} 节`, courses[i], courses[i][0])));
  T(root, 50, 1240, 14.5, '长按某一节可以临时调课（只影响今天）', { color: '#41484d' });
  T(root, 44, 1318, 18, '明天', { color: '#00668c', weight: 500 });
  div(root, 'card', 'left:25px;top:1362px;width:644px;height:200px');
  T(root, 50, 1398, 18, '周二 · 单周'); T(root, 644, 1398, 16, '11月10日', { align: 'right', color: '#41484d' });
  navBar(root, 0);

  function wave(progress) {
    const W = 578, x1 = W * progress;
    let d = 'M0 17';
    for (let x = 0; x <= x1; x += 2) d += ` L${x.toFixed(1)} ${(17 + Math.sin(x / 28 * Math.PI * 2) * 4.5).toFixed(2)}`;
    return `<svg width="${W}" height="34"><path d="${d}" stroke="#00668c" stroke-width="7" fill="none" stroke-linecap="round"/>` +
      `<line x1="${Math.min(W, x1 + 14)}" y1="17" x2="${W - 4}" y2="17" stroke="rgba(0,30,44,.15)" stroke-width="7" stroke-linecap="round"/>` +
      `<circle cx="${W - 4}" cy="17" r="4" fill="#00668c"/></svg>`;
  }
  /** nowMin：当天分钟数。按应用里同样的规则算出状态 */
  function set(nowMin) {
    setText(clock, fmt(Math.floor(nowMin)));
    const P2 = PLAN.map(([s, e]) => [toMin(s), toMin(e)]);
    let cur = -1, next = -1;
    P2.forEach(([s, e], i) => { if (nowMin >= s && nowMin < e) cur = i; });
    if (cur < 0) next = P2.findIndex(([s]) => s > nowMin);
    if (cur >= 0) {
      const [s, e] = P2[cur];
      setText(htitle, `正在上 ${courses[cur]}`);
      setText(hsub, `第 ${cur + 1} 节 · 还剩 ${Math.ceil(e - nowMin)} 分钟`);
      setHTML(bar, wave(clamp((nowMin - s) / (e - s))));
    } else if (next >= 0) {
      const done = P2.filter(([, e]) => nowMin >= e).length;
      setText(htitle, '课间休息');
      setText(hsub, `下一节 ${courses[next]}，${Math.ceil(P2[next][0] - nowMin)} 分钟后开始`);
      setHTML(bar, `<div style="position:absolute;left:0;top:14px;width:470px;height:6px;border-radius:3px;background:rgba(0,30,44,.12)"><div style="width:${done / 8 * 100}%;height:6px;border-radius:3px;background:#00668c"></div></div>` +
        `<div style="position:absolute;left:490px;top:2px;font-size:19px;color:#001e2c;font-weight:500">${done} / 8 节</div>`);
    }
    rows.forEach((r, i) => {
      const pi = i - 1;
      const past = pi < 0 ? nowMin >= toMin('08:00') : nowMin >= P2[pi][1];
      const isCur = pi === cur, isNext = pi === next;
      r.g.style.opacity = past && !isCur ? .55 : 1;
      r.hl.style.opacity = isCur ? 1 : isNext ? .55 : 0;
      r.dot.style.background = isCur ? '#00668c' : past ? '#c4c9cf' : 'transparent';
      r.dot.style.border = isCur || past ? '0' : '2.5px solid #71787e';
      r.dot.style.boxShadow = isCur ? '0 0 0 6px rgba(0,102,140,.18)' : 'none';
    });
  }
  set(10 * 60 + 42);
  return { root, set };
}

/* ======================================================================
   学期与周次：「本周是第几周」加减，开学日期自动反推
   ====================================================================== */
function SemesterScreen() {
  const root = div(document.body, 'app'); root.remove();
  statusBar(root, '19:12');
  subTopBar(root, '学期与周次', '决定单双周怎么算');
  div(root, 'hero', 'left:25px;top:180px;width:644px;height:177px');
  const h = T(root, 54, 228, 32, '', { weight: 600, color: '#001e2c' });
  const hs = T(root, 54, 266, 18, '', { color: '#22323c' });
  T(root, 54, 312, 14, '单双周按「从开学那周算起的第几周」判断：第 1、3、5… 周为单周。周日属于当周，所以周日晚上看「明天」会自动切到下一周的单双周。',
    { wrap: true, w: 590, color: '#3a4a54', lh: 1.55 });
  T(root, 44, 398, 18, '方式一：直接选开学日期', { color: '#00668c', weight: 500 });
  div(root, 'card', 'left:25px;top:433px;width:644px;height:95px');
  div(root, 'circ', 'left:50px;top:453px;width:56px;height:56px', I.cal('#00668c', 30));
  T(root, 127, 468, 20, '开学日期');
  const date = T(root, 127, 496, 15, '', { color: '#41484d' });
  div(root, 'a', 'left:612px;top:466px', I.chev());
  T(root, 44, 569, 18, '方式二：告诉我现在是第几周', { color: '#00668c', weight: 500 });
  div(root, 'card', 'left:25px;top:605px;width:644px;height:265px');
  T(root, 50, 648, 20, '本周是第几周'); T(root, 50, 676, 15, '加减后开学日期会自动反推', { color: '#41484d' });
  const minus = div(root, 'circ', 'left:434px;top:629px;width:62px;height:62px', I.minus('#191c1e', 32));
  const plus = div(root, 'circ', 'left:577px;top:629px;width:62px;height:62px', I.plus('#191c1e', 32));
  const num = T(root, 536, 660, 32, '', { align: 'center', color: '#00668c', weight: 500, css: 'font-variant-numeric:tabular-nums' });
  div(root, 'div', 'left:50px;top:716px;width:594px');
  T(root, 50, 750, 20, '本周是');
  const seg = segmented(root, 50, 787, 594, ['单周', '双周'], 60);
  T(root, 44, 910, 18, '显示', { color: '#00668c', weight: 500 });
  div(root, 'card', 'left:25px;top:946px;width:644px;height:95px');
  T(root, 50, 983, 20, '课表页显示周末'); T(root, 50, 1010, 15, '周六、周日有课时会自动显示', { color: '#41484d' });
  div(root, 'a', 'left:566px;top:969px;width:78px;height:48px;border-radius:24px;border:3px solid #71787e;background:#e3e8ee', '<div style="position:absolute;left:9px;top:9px;width:24px;height:24px;border-radius:50%;background:#71787e"></div>');
  const TODAY = Date.UTC(2026, 10, 9); // 2026-11-09 周一
  function set(week, segF) {
    const odd = week % 2 === 1;
    setText(h, `今天是第 ${week} 周 · ${odd ? '单周' : '双周'}`);
    const d = new Date(TODAY - (week - 1) * 7 * 864e5);
    const y = d.getUTCFullYear(), m = d.getUTCMonth() + 1, dd = d.getUTCDate();
    setText(hs, `开学日期：${m}月${dd}日 周一`);
    setText(date, `${y}-${String(m).padStart(2, '0')}-${String(dd).padStart(2, '0')}，选任意一天都会自动对齐到周一`);
    setText(num, String(week));
    seg._set(segF ?? (odd ? 0 : 1));
  }
  set(1);
  const touch = makeTouch(root);
  return { root, set, plus, minus, touch };
}

/* ======================================================================
   编辑时间方案：拖动滑块 → 一键生成
   ====================================================================== */
function TimePlanScreen() {
  const root = div(document.body, 'app'); root.remove();
  const body = div(root, 'a', 'left:0;top:0;width:694px;height:2400px');
  statusBar(root, '19:12');
  div(body, 'field', 'left:25px;top:192px;width:644px;height:83px', '<label>方案名称</label>');
  T(body, 50, 233, 20, '默认时间表');
  T(body, 44, 320, 18, '快速生成', { color: '#00668c', weight: 500 });
  div(body, 'card', 'left:25px;top:361px;width:644px;height:789px');
  T(body, 50, 416, 20, '第一节开始');
  div(body, 'a', 'left:520px;top:386px;width:125px;height:60px;border-radius:30px;background:#d2e5f4;display:grid;place-items:center;font-size:20px', '08:00');
  const defs = [
    { label: '每节时长', min: 20, max: 90, step: 5, v: 45, unit: v => `${v} 分钟` },
    { label: '课间休息', min: 0, max: 30, step: 5, v: 10, unit: v => `${v} 分钟` },
    { label: '每天节数', min: 1, max: 14, step: 1, v: 8, unit: v => `${v} 节` },
    { label: '午休在第几节后', min: 0, max: 8, step: 1, v: 4, unit: v => `${v} 节后` },
    { label: '午休时长', min: 30, max: 180, step: 10, v: 120, unit: v => `${v} 分钟` },
  ];
  const X0 = 52, X1 = 640;
  const sliders = defs.map((d, i) => {
    const ly = 475 + i * 119, ty = 528 + i * 119;
    T(body, 50, ly, 20, d.label);
    const val = T(body, 645, ly, 18, '', { align: 'right', color: '#00668c' });
    const s = div(body, 'a', `left:0;top:${ty - 28}px;width:694px;height:56px`);
    d.val = val; d.el = s; d.frac = v => (v - d.min) / (d.max - d.min);
    return d;
  });
  function drawSlider(d, v) {
    const n = Math.round((d.max - d.min) / d.step), hx = X0 + d.frac(v) * (X1 - X0);
    let h = `<div style="position:absolute;left:${X0}px;top:15px;width:${Math.max(0, hx - 10 - X0)}px;height:26px;border-radius:13px 4px 4px 13px;background:#086a8f"></div>` +
      `<div style="position:absolute;left:${hx + 10}px;top:15px;width:${Math.max(0, X1 - hx - 10)}px;height:26px;border-radius:4px 13px 13px 4px;background:#d1e4f3"></div>` +
      `<div style="position:absolute;left:${hx - 3}px;top:0;width:6px;height:56px;border-radius:3px;background:#00668c"></div>`;
    for (let k = 0; k <= n; k++) {
      const x = X0 + 12 + k / n * (X1 - X0 - 24);
      if (Math.abs(x - hx) < 16) continue;
      h += `<div style="position:absolute;left:${x - 2.5}px;top:25.5px;width:5px;height:5px;border-radius:50%;background:${x < hx ? '#fff' : '#00668c'}"></div>`;
    }
    setHTML(d.el, h);
    setText(d.val, d.unit(v));
  }
  sliders.forEach(d => drawSlider(d, d.v));
  const btn = div(body, 'btnp', 'left:49px;top:1066px;width:596px;height:62px;overflow:hidden', I.spark('#fff', 26) + '<span>按以上规则生成 8 节</span>');
  const rip = div(btn, 'a', 'left:0;top:0;width:60px;height:60px;margin:-30px 0 0 -30px;border-radius:50%;background:rgba(255,255,255,.35);opacity:0');
  T(body, 44, 1196, 18, '节次时间（点时间可单独修改）', { color: '#00668c', weight: 500 });
  div(body, 'card', 'left:25px;top:1238px;width:644px;height:712px');
  const rows = [];
  for (let i = 0; i < 8; i++) {
    const y = 1282 + i * 88;
    T(body, 50, y, 20, `第 ${i + 1} 节`);
    const t = T(body, 583, y, 22, '', { align: 'right', color: '#00668c', css: 'font-variant-numeric:tabular-nums' });
    div(body, 'a', `left:612px;top:${y - 17}px`, I.trash('#4a4f55', 32));
    if (i < 7) div(body, 'div', `left:50px;top:${y + 43}px;width:594px`);
    const flash = div(body, 'a', `left:36px;top:${y - 40}px;width:622px;height:80px;border-radius:20px;background:rgba(0,102,140,.12);opacity:0`);
    rows.push({ t, flash });
  }
  const tb = subTopBar(root, '编辑时间方案', null);
  div(tb, 'a', 'left:614px;top:97px', I.check('#191c1e', 36));
  const touch = makeTouch(root);

  const gen = (len, brk, n, lunchAfter, lunch) => {
    const out = []; let t = 8 * 60;
    for (let i = 0; i < n; i++) { out.push([t, t + len]); t += len + (i + 1 === lunchAfter ? lunch : brk); }
    return out.map(([a, b]) => `${fmt(a)} – ${fmt(b)}`);
  };
  const OLD = PLAN.map(([a, b]) => `${a} – ${b}`);
  /** len：每节时长滑块的值；g：生成进度 0..1；press：按钮按下；scroll：内容上滚距离 */
  function set({ len, g = 0, press = 0, rippleT = -1, scroll = 0 }) {
    drawSlider(sliders[0], len);
    const NEW = gen(Math.round(len), 10, 8, 4, 120);
    rows.forEach((r, i) => {
      const k = clamp(g * 1.6 - i * .08);
      setText(r.t, k >= .5 ? NEW[i] : OLD[i]);
      r.flash.style.opacity = Math.sin(Math.PI * k) * .9;
    });
    btn.style.transform = `scale(${1 - press * .03})`;
    btn.style.filter = press ? `brightness(${1 + press * .12})` : 'none';
    if (rippleT >= 0 && rippleT < 1) { rip.style.opacity = (1 - rippleT) * .9; rip.style.transform = `translate(298px, 31px) scale(${1 + rippleT * 12})`; }
    else rip.style.opacity = 0;
    body.style.transform = `translateY(${-scroll}px)`;
  }
  set({ len: 45 });
  return { root, set, touch, sliderX: v => X0 + sliders[0].frac(v) * (X1 - X0), sliderY: 528 };
}

/* ======================================================================
   填课：点格子 → 选常用课程 → 自动跳到下一节
   ====================================================================== */
function EditorScreen() {
  const root = div(document.body, 'app'); root.remove();
  statusBar(root, '19:12');
  const tb = subTopBar(root, '我的课表', '点格子填课 · 自动保存');
  div(tb, 'a', 'left:552px;top:97px', I.clock('#191c1e', 38));
  div(tb, 'a', 'left:626px;top:97px', I.more('#191c1e', 38));
  const seg = segmented(root, 25, 187, 644, ['单周', '双周']);
  T(root, 32, 283, 15, '正在编辑单周（每周相同的天也在这里填）。', { color: '#41484d' });
  div(root, 'card', 'left:25px;top:312px;width:644px;height:898px');
  const alt = [true, false, false, true, true];
  ['一', '二', '三', '四', '五'].forEach((d, i) => {
    T(root, COLX[i] + COLW / 2, 346, 22, d, { align: 'center', weight: 600 });
    if (alt[i]) T(root, COLX[i] + COLW / 2, 370, 14, '单/双', { align: 'center', color: '#41484d' });
  });
  div(root, 'a', 'left:56px;top:413px', I.book('#4d4a6b', 30));
  MORNING.forEach((m, i) => div(root, 'cell mr', `left:${COLX[i]}px;top:400px;width:${COLW}px;height:55px`, m));
  const EMPTY = [[0, 0], [1, 0], [2, 0]];
  const cells = [];
  for (let r = 0; r < 8; r++) {
    const top = 461 + r * ROWSTEP;
    T(root, 71, top + 32, 20, String(r + 1), { align: 'center', color: '#2a2d31' });
    T(root, 71, top + 56, 15, PERIOD_START[r], { align: 'center', color: '#41484d' });
    cells.push(ODD[r].map((c, i) => div(root, 'cell', `left:${COLX[i]}px;top:${top}px;width:${COLW}px;height:${CELLH}px;background:${courseColor(FULL[c])}`, c)));
  }
  const sel = div(root, 'a', `left:0;top:0;width:${COLW + 10}px;height:${CELLH + 10}px;border-radius:22px;border:4px solid #00668c;opacity:0;box-shadow:0 0 0 6px rgba(0,102,140,.15)`);
  div(root, 'card', 'left:25px;top:1230px;width:644px;height:167px');
  T(root, 50, 1266, 19, '小技巧', { weight: 500 });
  ['• 点表头的「周几」：开启单双周不同、复制这一天到其它天、清空', '• 点最上面书本那一行：设置晨读', '• 填课时会自动跳到下一节，连续输入很快', '• 右上角时钟图标：更换时间方案（节数和上课时间）']
    .forEach((s, i) => T(root, 50, 1300 + i * 22, 14.5, s, { color: '#2a2d31' }));

  const scrim = div(root, 'scrim');
  const sheet = div(root, 'sheet', 'top:860px;height:700px');
  div(sheet, 'grab');
  const st = T(sheet, 36, 64, 29, '', { weight: 600 });
  div(sheet, 'field', 'left:36px;top:110px;width:622px;height:76px', '<label style="background:#dee5ed">课程名</label>');
  const ftext = T(sheet, 60, 148, 21, '');
  const caret = div(sheet, 'a', 'left:60px;top:132px;width:2.5px;height:32px;background:#00668c');
  T(sheet, 36, 222, 16, '常用课程', { color: '#41484d', weight: 500 });
  const names = ['数学', '英语', '语文', '物理', '化学', '道法', '历史', '体育', '班会'];
  const chipPos = {};
  names.forEach((n, i) => {
    const row = i < 7 ? 0 : 1, col = i < 7 ? i : i - 7;
    const x = 36 + col * 88, y = 248 + row * 62;
    const c = div(sheet, 'cchip', `position:absolute;left:${x}px;top:${y}px;width:76px;justify-content:center;padding:0;background:${courseColor(n)}`, n);
    chipPos[n] = { x: x + 38, y: y + 24, el: c };
  });
  T(sheet, 36, 412, 20, '填完自动跳到下一节');
  div(sheet, 'sw', 'left:578px;top:388px', '<i></i>');
  div(sheet, 'btnt', 'left:36px;top:462px;width:303px;height:64px', '这节没课');
  const nextBtn = div(sheet, 'btnp', 'left:355px;top:462px;width:303px;height:64px', '下一节');
  const touch = makeTouch(root);

  const cellXY = (r, c) => ({ x: COLX[c] + COLW / 2, y: 461 + r * ROWSTEP + CELLH / 2 });
  /**
   * fills: [[r, c, 名称, 时刻]]；s 为当前场景时间。selAt: [[时刻, r, c]] 选中框位置变化。
   * sheetP: 底部面板 0..1；typed: 输入框里的字
   */
  function set({ lt, fills, selKeys, sheetP, typed }) {
    EMPTY.forEach(([r, c]) => {
      const f = fills.find(x => x[0] === r && x[1] === c);
      const el = cells[r][c];
      if (f && lt >= f[3]) {
        const p = P(lt, f[3], f[3] + .45, E.outBack);
        setText(el, f[2][0]);
        el.style.background = courseColor(f[2]);
        el.style.border = '0';
        el.style.transform = `scale(${lerp(.6, 1, p)})`;
      } else {
        setText(el, '');
        el.style.background = 'rgba(220,228,234,.85)';
        el.style.border = '2px dashed rgba(113,120,126,.45)';
        el.style.transform = 'none';
      }
    });
    // 选中框：在格子之间滑动；r < 0 表示收起
    let i = -1; selKeys.forEach((k, j) => { if (lt >= k[0]) i = j; });
    if (i < 0) sel.style.opacity = 0;
    else {
      const [t, r, c] = selKeys[i];
      if (r < 0) {
        const pk = selKeys[i - 1], pos = cellXY(pk[1], pk[2]);
        sel.style.opacity = 1 - clamp((lt - t) / .25);
        sel.style.transform = `translate(${pos.x - (COLW + 10) / 2}px, ${pos.y - (CELLH + 10) / 2}px)`;
      } else {
        const to = cellXY(r, c), from = i > 0 ? cellXY(selKeys[i - 1][1], selKeys[i - 1][2]) : to;
        const u = E.outCubic(clamp((lt - t) / .35));
        sel.style.opacity = i === 0 ? clamp((lt - t) / .2) : 1;
        sel.style.transform = `translate(${lerp(from.x, to.x, u) - (COLW + 10) / 2}px, ${lerp(from.y, to.y, u) - (CELLH + 10) / 2}px)`;
        setText(st, `周一 · 第 ${r + 1} 节 · 单周`);
      }
    }
    sheet.style.transform = `translateY(${(1 - sheetP) * 720}px)`;
    scrim.style.opacity = sheetP * .28;
    setText(ftext, typed || '');
    caret.style.left = (60 + (typed ? typed.length * 21 + 3 : 0)) + 'px';
    caret.style.opacity = (Math.floor(lt * 2.2) % 2 === 0 || typed) ? 1 : 0;
    Object.values(chipPos).forEach(c => { c.el.style.outline = 'none'; c.el.style.transform = 'none'; });
  }
  return { root, set, touch, chipPos, cellXY, sheetTop: 860, nextBtn };
}

/* ======================================================================
   备份、恢复与分享码：生成分享码对话框 / 导入预览
   ====================================================================== */
function BackupScreen({ withDialog = false, withImport = false, extraCode = false } = {}) {
  const root = div(document.body, 'app'); root.remove();
  statusBar(root, '19:13');
  T(root, 44, 205, 18, '分享码 · 把课表传给同学', { color: '#00668c', weight: 500 });
  const card1 = div(root, 'card', 'left:25px;top:240px;width:644px;height:332px');
  div(root, 'circ', 'left:49px;top:262px;width:56px;height:56px', I.share('#00668c', 28));
  T(root, 127, 280, 21, '生成分享码'); T(root, 127, 306, 14, '只上传课表、时间方案、简称和开学日期（约 1.6 KB）· 7 天内有效', { color: '#41484d' });
  const row1hl = div(root, 'a', 'left:37px;top:250px;width:620px;height:86px;border-radius:24px;background:rgba(0,102,140,.10);opacity:0');
  div(root, 'div', 'left:50px;top:340px;width:594px');
  div(root, 'circ', 'left:49px;top:342px;width:56px;height:56px', I.num('#00668c', 30));
  T(root, 127, 360, 21, '输入分享码导入'); T(root, 127, 388, 14, '导入前可以预览，不会覆盖你的设置和作业', { color: '#41484d' });
  div(root, 'a', 'left:610px;top:374px', I.chev());
  div(root, 'div', 'left:50px;top:440px;width:594px');
  T(root, 50, 470, 17, '最近生成的分享码', { color: '#2a2d31' });
  const recent = div(root, 'a', 'left:0;top:0;width:694px;height:200px');
  const newRow = div(recent, 'a', 'left:0;top:0;width:694px;height:74px;opacity:0');
  T(newRow, 50, 520, 22, '825480', { weight: 500 }); T(newRow, 160, 521, 16, '2026-11-09 19:12:59', { color: '#41484d' });
  div(newRow, 'a', 'left:590px;top:502px', I.copy('#41484d', 34));
  const oldRow = div(recent, 'a', 'left:0;top:0;width:694px;height:74px');
  T(oldRow, 50, 520, 22, '761342', { weight: 500 }); T(oldRow, 160, 521, 16, '2026-11-06 20:20:41', { color: '#41484d' });
  div(oldRow, 'a', 'left:590px;top:502px', I.copy('#41484d', 34));
  const lower = div(root, 'a', 'left:0;top:0;width:694px;height:1534px');
  T(lower, 44, 610, 18, '本地备份', { color: '#00668c', weight: 500 });
  div(lower, 'card', 'left:25px;top:647px;width:644px;height:297px');
  [['导出备份文件', '课表 + 时间方案 + 作业 + 设置，保存为一个 .json 文件', I.up], ['从备份文件恢复', '', I.down], ['从旧版备份文件夹导入', '4.x 及更早版本导出的 backup_xxx 文件夹', I.folder]].forEach(([a, b, ic], i) => {
    const y = 695 + i * 100;
    div(lower, 'circ', `left:49px;top:${y - 28}px;width:56px;height:56px`, ic('#00668c', 28));
    T(lower, 127, b ? y - 10 : y, 21, a); if (b) T(lower, 127, y + 18, 14, b, { color: '#41484d' });
    if (i < 2) div(lower, 'div', `left:50px;top:${y + 50}px;width:594px`);
  });
  T(lower, 44, 985, 18, '危险操作', { color: '#00668c', weight: 500 });
  div(lower, 'card', 'left:25px;top:1020px;width:644px;height:96px');
  div(lower, 'circ', 'left:49px;top:1040px;width:56px;height:56px;background:#f3d8d8', I.clock('#b3261e', 28));
  T(lower, 127, 1058, 21, '清空全部课表和时间方案'); T(lower, 127, 1084, 14, '作业和设置会保留；建议先导出备份', { color: '#41484d' });
  subTopBar(root, '备份、恢复与分享码', null);

  const scrim = div(root, 'scrim');
  const api = { root, row1hl };
  /** 新分享码加入「最近生成」列表，卡片变高 */
  api.setRecent = p => {
    newRow.style.opacity = p; newRow.style.transform = `translateY(${(1 - p) * -10}px)`;
    oldRow.style.transform = `translateY(${p * 74}px)`;
    card1.style.height = (332 + p * 70) + 'px';
    lower.style.transform = `translateY(${p * 74}px)`;
  };
  if (extraCode) api.setRecent(1);
  if (withDialog) {
    const dlg = div(root, 'dlg', 'left:44px;top:585px;width:606px;height:433px');
    T(dlg, 38, 52, 30, '分享码已生成', { weight: 600 });
    const digits = [...'825480'].map((d, i) => {
      const x = 92 + i * 66 + (i >= 3 ? 52 : 0);
      const box = div(dlg, 'a', `left:${x}px;top:86px;width:60px;height:84px;overflow:hidden`);
      const col = div(box, 'a', 'left:0;top:0;width:60px', [...'0123456789'].map(n => `<div style="height:84px;line-height:84px;text-align:center;font-size:66px;font-weight:450;color:#00668c">${n}</div>`).join(''));
      return { col, d: +d };
    });
    T(dlg, 38, 214, 16, '同学在「设置 › 备份、恢复与分享码 › 输入分享码导入」里输入即可。7 天后自动失效。', { wrap: true, w: 540, color: '#2a2d31', lh: 1.55 });
    div(dlg, 'div', 'left:38px;top:242px;width:530px');
    T(dlg, 56, 264, 18, '通过其它应用发送', { color: '#00668c', weight: 500 });
    T(dlg, 402, 367, 19, '关闭', { align: 'center', color: '#00668c', weight: 500 });
    const copy = div(dlg, 'btnp', 'left:459px;top:336px;width:110px;height:62px', '复制');
    api.dlg = dlg; api.copy = copy;
    /** p: 对话框出现 0..1；roll: 数字滚动 0..1 */
    api.setDialog = (p, roll) => {
      scrim.style.opacity = p * .45;
      dlg.style.opacity = clamp(p * 1.6);
      dlg.style.transform = `scale(${lerp(.86, 1, E.outBack(clamp(p)))})`;
      digits.forEach((g, i) => {
        const r = clamp(roll * 1.5 - i * .1);
        const turns = 10 + g.d;                                   // 先滚过一整圈再停到目标数字
        const pos = E.outCubic(r) * turns;
        g.col.style.transform = `translateY(${-(pos % 10) * 84}px)`;
        g.col.style.filter = r > 0 && r < .85 ? `blur(${(1 - r) * 2.5}px)` : 'none';
      });
    };
    api.setDialog(0, 0);
  }
  if (withImport) {
    const sheet = div(root, 'sheet', 'top:1010px;height:560px');
    div(sheet, 'grab');
    T(sheet, 32, 58, 30, '导入预览', { weight: 600 });
    T(sheet, 32, 90, 15, '来源：分享码 825480', { color: '#41484d' });
    [['课表', '我的课表（共 40 节课）'], ['时间方案', '默认时间表（8 节）'], ['课程简称', '9 条'], ['开学日期', '2026-08-31']].forEach(([a, b], i) => {
      T(sheet, 32, 134 + i * 32, 18, a, { color: '#41484d' }); T(sheet, 116, 134 + i * 32, 19, b);
    });
    T(sheet, 32, 277, 17, '导入方式', { color: '#2a2d31' });
    const sg = segmented(sheet, 32, 305, 630, ['合并（推荐）', '替换现有课表'], 60);
    T(sheet, 32, 394, 15, '导入的课表会追加进来，同名时间方案会被覆盖，其它保持不变。', { color: '#41484d' });
    div(sheet, 'btnt', 'left:32px;top:428px;width:308px;height:62px', '取消');
    const imp = div(sheet, 'btnp', 'left:353px;top:428px;width:309px;height:62px', '导入');
    api.imp = imp;
    api.setImport = p => { sheet.style.transform = `translateY(${(1 - p) * 580}px)`; scrim.style.opacity = p * .45; };
    api.setImport(0);
  }
  const snack = div(root, 'snack', 'top:1300px;opacity:0', '');
  api.setSnack = (p, html) => { setHTML(snack, html); snack.style.opacity = p; snack.style.transform = `translateY(${(1 - p) * 40}px)`; };
  api.touch = makeTouch(root);
  return api;
}

/* ======================================================================
   通知栏：常驻通知
   ====================================================================== */
function ShadeScreen() {
  const root = div(document.body, 'app'); root.remove();
  root.style.background = '#cfdbe8';
  // 壁纸：用真实截图做重度模糊，只留下颜色（同时把其它应用的通知完全糊掉）
  div(root, 'a', 'left:-60px;top:-60px;width:814px;height:1654px;background:url(shots/11_notification.jpg) center/cover;filter:blur(36px) saturate(1.15);opacity:.95');
  const home = div(root, 'a', 'left:0;top:0;width:694px;height:1534px');
  T(home, 347, 330, 120, '19:13', { align: 'center', weight: 300, color: 'rgba(255,255,255,.95)', css: 'text-shadow:0 2px 20px rgba(0,40,80,.2)' });
  T(home, 347, 430, 26, '11月9日 星期一', { align: 'center', color: 'rgba(255,255,255,.95)' });
  const shade = div(root, 'a', 'left:0;top:0;width:694px;height:1534px;background:rgba(232,240,248,.42)');
  const sb = div(shade, 'a', 'left:0;top:0;width:694px;height:70px');
  T(sb, 38, 30, 14, '中国电信<br>中国联通', { lh: 1.3, color: '#2a2d31' });
  div(sb, 'a', 'right:30px;top:22px;display:flex;gap:12px', STATUS_ICONS);
  T(shade, 38, 135, 34, '19:13', { color: '#2a2d31', weight: 400 });
  T(shade, 140, 135, 22, '11月9日星期一', { color: '#2a2d31' });
  const card = div(shade, 'a', 'left:19px;top:187px;width:656px;height:131px;border-radius:34px;background:rgba(206,229,242,.78);border:1.5px solid rgba(255,255,255,.75);box-shadow:0 10px 30px -14px rgba(20,60,100,.35)');
  div(card, 'a', 'left:18px;top:36px;width:58px;height:58px;border-radius:16px;overflow:hidden', appIconSVG(58, 'nf'));
  T(card, 86, 29, 19, '智能课表 · 第 11 周单周', { color: '#20262c', weight: 500 });
  div(card, 'a', 'left:534px;top:17px;padding:1px 9px;border:1.5px solid #4f86c6;border-radius:8px;font-size:13px;color:#2f6bb3', '已置顶');
  T(card, 622, 29, 15, '刚刚', { align: 'right', color: '#5f6a73' });
  const lines = ['今日: 物 | 数 数 物 化 英 语 班 数', '明日: 语 | 语 语 数 道 英 化 体 英', '紧急作业: 听写错一改三'].map((s, i) =>
    T(card, 86, 58 + i * 22, 17, s, { color: '#33414c', ls: '.14em' }));
  div(card, 'a', 'left:600px;top:84px;width:36px;height:36px;border-radius:50%;background:rgba(255,255,255,.35);display:grid;place-items:center',
    '<svg width="20" height="20" viewBox="0 0 24 24"><path d="M7 14l5-5 5 5" stroke="#4a5560" stroke-width="2.2" fill="none" stroke-linecap="round"/></svg>');
  const touch = makeTouch(root);
  /** pull: 下拉进度 0..1；hlLine: 高亮第几行（小数做过渡） */
  function set(pull, hl = -1) {
    home.style.opacity = 1 - clamp(pull * 1.4);
    home.style.filter = `blur(${pull * 10}px)`;
    shade.style.opacity = clamp(pull * 1.3);
    shade.style.transform = `translateY(${(1 - E.outCubic(clamp(pull))) * -260}px)`;
    lines.forEach((l, i) => {
      const a = hl < 0 ? 0 : Math.max(0, 1 - Math.abs(hl - i) * 1.4);
      l.style.color = a > .01 ? `rgb(${lerp(51, 0, a)},${lerp(65, 90, a)},${lerp(76, 150, a)})` : '#33414c';
      l.style.fontWeight = a > .5 ? '500' : '400';
    });
  }
  set(0);
  return { root, set, touch, card: { x: 19, y: 187, w: 656, h: 131 } };
}
