/* 官网交互：把复刻的应用界面（app.js）装进页面里的手机，并让它们动起来、能点。
   lib.js 提供缓动 E、进度 P、关键帧 K、触点 touchAt、图标 I、appIconSVG；app.js 提供各个界面。 */
'use strict';
(function () {
  const q = s => document.querySelector(s);
  const qa = s => [...document.querySelectorAll(s)];
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const nowS = () => performance.now() / 1000;

  /* ---------- 图标 ---------- */
  qa('[data-icon]').forEach((el, i) => { el.innerHTML = appIconSVG(+el.dataset.icon, 'mk' + i); });
  qa('[data-svg]').forEach(el => { el.innerHTML = el.dataset.svg === 'cal' ? I.cal('#fff', 26) : I.clock('#fff', 28); });

  /* ---------- 标题逐字显现 + 滚动显现 ---------- */
  qa('[data-split]').forEach(el => {
    let i = 0;
    const walk = node => [...node.childNodes].forEach(c => {
      if (c.nodeType === 3) {
        const frag = document.createDocumentFragment();
        (c.textContent.match(/[A-Za-z0-9.]+|\s+|./gu) || []).forEach(t => {
          const s = document.createElement('span'); s.className = 'ch'; s.textContent = t; s.style.setProperty('--i', i++); frag.appendChild(s);
        });
        node.replaceChild(frag, c);
      } else if (c.nodeType === 1 && c.tagName !== 'BR') walk(c);
    });
    el.setAttribute('aria-label', el.textContent);
    walk(el);
  });
  const revealIO = new IntersectionObserver(es => es.forEach(e => {
    if (e.isIntersecting) { e.target.classList.add('in'); revealIO.unobserve(e.target); }
  }), { threshold: .15, rootMargin: '0px 0px -5% 0px' });
  qa('.rv,[data-split]').forEach(el => revealIO.observe(el));

  /* ---------- 把界面装进手机，按屏幕宽度缩放 ---------- */
  const fit = scr => { const k = scr.clientWidth / 694; scr.querySelectorAll(':scope > .app').forEach(a => { a.style.transform = `scale(${k})`; }); };
  const ro = new ResizeObserver(es => es.forEach(e => fit(e.target)));
  function mount(scr, ...roots) { roots.forEach(r => scr.appendChild(r)); fit(scr); ro.observe(scr); }
  /** 指针位置换算成界面坐标（694 × 1534） */
  function appXY(root, e) {
    const r = root.getBoundingClientRect();
    return { x: (e.clientX - r.left) * 694 / r.width, y: (e.clientY - r.top) * 1534 / r.height };
  }
  const hideTouch = t => { if (t) t.style.opacity = 0; };

  /* ---------- 只在可见时逐帧更新 ---------- */
  const players = [];
  function player(el, fn) { const p = { el, fn, vis: false }; players.push(p); visIO.observe(el); return p; }
  const visIO = new IntersectionObserver(es => es.forEach(e => {
    const p = players.find(x => x.el === e.target); if (!p) return;
    p.vis = e.isIntersecting;
    if (p.vis && p.onShow) p.onShow(nowS());
  }), { threshold: .2 });
  (function frame() { const t = nowS(); players.forEach(p => { if (p.vis) p.fn(t); }); requestAnimationFrame(frame); })();

  /* ================= 首屏：课表，单周 ⇄ 双周 ================= */
  {
    const scr = q('#scrTimetable'), badge = q('#weekBadge');
    const tt = TimetableScreen(); mount(scr, tt.root);
    let week = 11, f = 0, from = 0, to = 0, flipT = -9, nextAuto = 0, lastUser = -99, dirty = true;
    const label = w => { const d = new Date(Date.UTC(2026, 10, 9 + (w - 11) * 7)); return `${d.getUTCMonth() + 1}月${d.getUTCDate()}日 · 第 ${w} 周`; };
    function advance(t) {
      week = week >= 14 ? 11 : week + 1;
      from = f; to = week % 2 === 0 ? 1 : 0; flipT = t; dirty = true;
      badge.textContent = label(week);
      if (!reduce) badge.animate([{ transform: 'translateY(70%)', opacity: 0 }, { transform: 'none', opacity: 1 }], { duration: 520, easing: 'cubic-bezier(.16,1,.3,1)' });
    }
    const user = () => { const t = nowS(); lastUser = t; advance(t); };
    scr.addEventListener('click', user);
    scr.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); user(); } });
    const p = player(scr, t => {
      if (!reduce && t >= nextAuto && t - lastUser > 7) { if (nextAuto) advance(t); nextAuto = t + 3.4; }
      const u = reduce ? 1 : E.inOutCubic(clamp((t - flipT) / .6));
      if (u < 1 || dirty) { f = lerp(from, to, u); tt.set(f, week); dirty = u < 1; }
    });
    p.onShow = t => { if (!nextAuto) nextAuto = t + 2.6; };
  }

  /* ================= 首页：时间走动 ================= */
  {
    const scr = q('#scrToday'), range = q('#timeRange'), out = q('#timeOut'), clk = q('#todayClock');
    const td = TodayScreen(); mount(scr, td.root);
    let tm = 642, last = null, drag = -99, shown = -1;
    const show = () => {
      if (Math.abs(tm - shown) < .2) return;
      shown = tm; td.set(tm);
      const s = fmt(Math.floor(tm)); out.textContent = s; clk.textContent = s; range.value = Math.floor(tm);
    };
    range.addEventListener('input', () => { tm = +range.value; drag = nowS(); show(); });
    player(scr, t => {
      const dt = last === null ? 0 : Math.min(.1, t - last); last = t;
      if (!reduce && t - drag > 5) { tm += dt * 3.2; if (tm > 1119) tm = 470; }
      show();
    });
    show();
  }

  /* ================= 配置教程：三步，可以真的点 ================= */
  {
    const scr = q('#scrGuide'), hint = q('#guideHint'), redo = q('#guideRedo'), tabs = qa('.step');
    const sm = SemesterScreen(), tp = TimePlanScreen(), ed = EditorScreen();
    const screens = [sm, tp, ed];
    mount(scr, sm.root, tp.root, ed.root);
    const DEMO = [3.3, 5.3, 3.6], HOLD = 3.2;               // 每步演示时长；演示后停留多久自动进入下一步
    const HINT = ['点 ＋ / − 改周次', '拖动「每节时长」，再点「按以上规则生成」', '点常用课程，填进选中的格子'];
    let step = 0, t0 = 0, auto = true, mode = 'demo', st = {};          // mode: demo 演示中 / free 可以自己点
    const lt = () => nowS() - t0;

    /* --- 01 设定周次 --- */
    const TAPS = [1.0, 1.3, 1.55, 1.75, 1.92, 2.07, 2.2, 2.32, 2.44, 2.56];
    const KEYS1 = [{ t: .75, x: 608, y: 660 }, ...TAPS.map(t => ({ t, x: 608, y: 660, down: true })), { t: 2.75, x: 600, y: 690 }];
    function s1(lt) {
      if (mode === 'demo') {
        st.week = 1 + TAPS.filter(t => lt >= t).length;
        const tp_ = TAPS.filter(t => lt >= t).pop();
        st.flash = tp_ === undefined ? -9 : tp_;
        touchAt(sm.touch, lt, KEYS1);
      } else hideTouch(sm.touch);
      sm.set(st.week);
      sm.plus.style.background = lt - st.flash < .12 && st.flashEl !== 'minus' ? '#b8d4e8' : '#cfe3f1';
      sm.minus.style.background = lt - st.flash < .12 && st.flashEl === 'minus' ? '#b8d4e8' : '#cfe3f1';
    }
    const bump = (d, el) => { st.week = clamp(st.week + d, 1, 25); st.flash = lt(); st.flashEl = el; };
    sm.plus.addEventListener('click', () => step === 0 && bump(1, 'plus'));
    sm.minus.addEventListener('click', () => step === 0 && bump(-1, 'minus'));

    /* --- 02 生成作息 --- */
    const x45 = tp.sliderX(45), x40 = tp.sliderX(40), xMin = tp.sliderX(20), xMax = tp.sliderX(90);
    const KEYS2 = [{ t: .95, x: x45, y: tp.sliderY }, { t: 1.2, x: x45, y: tp.sliderY, down: true, hold: 2.25 },
      { t: 2.2, x: x40, y: tp.sliderY, move: .85 }, { t: 3.0, x: 347, y: 1097, down: true, move: .55 }, { t: 3.4, x: 347, y: 1110 }];
    function s2(t) {
      if (mode === 'demo') {
        const drag = P(t, 1.35, 2.2, E.inOutCubic), len = Math.round(lerp(45, 40, drag) / 5) * 5;
        Object.assign(st, { len, gT: 3.1, scFrom: 0, scTo: 600, scT: 3.9 });
        touchAt(tp.touch, t, KEYS2);
        if (t > 1.35 && t < 2.2) tp.touch.style.transform = `translate(${lerp(x45, x40, drag)}px, ${tp.sliderY}px) scale(.82)`;
      } else hideTouch(tp.touch);
      const g = st.gT === null ? 0 : P(t, st.gT, st.gT + .8, E.lin);
      const press = st.gT === null ? 0 : P(t, st.gT - .15, st.gT - .08, E.lin) * (1 - P(t, st.gT, st.gT + .15, E.lin));
      const rip = st.gT === null || t < st.gT - .1 ? -1 : P(t, st.gT - .1, st.gT + .5, E.outCubic);
      st.scroll = lerp(st.scFrom, st.scTo, E.inOutCubic(clamp((t - st.scT) / 1.1)));
      tp.set({ len: st.len, g, press, rippleT: rip, scroll: st.scroll });
    }
    const vFromX = x => Math.round(clamp(20 + (x - xMin) / (xMax - xMin) * 70, 20, 90) / 5) * 5;
    let dragging = false;
    tp.root.addEventListener('pointerdown', e => {
      if (step !== 1) return;
      const p = appXY(tp.root, e), t = lt();
      if (st.scroll > 10) { st.scFrom = st.scroll; st.scTo = 0; st.scT = t; return; }   // 列表滚上去了：点一下回到顶部
      if (Math.abs(p.y - tp.sliderY) < 48 && p.x > 30 && p.x < 664) {
        dragging = true; st.gT = null; st.len = vFromX(p.x);
        tp.root.setPointerCapture(e.pointerId); e.preventDefault();
      } else if (p.x > 49 && p.x < 645 && p.y > 1060 && p.y < 1134) {
        st.gT = t + .15; st.scFrom = 0; st.scTo = 600; st.scT = t + 1.0;
      }
    });
    tp.root.addEventListener('pointermove', e => { if (dragging) st.len = vFromX(appXY(tp.root, e).x); });
    const endDrag = () => { dragging = false; };
    tp.root.addEventListener('pointerup', endDrag); tp.root.addEventListener('pointercancel', endDrag);

    /* --- 03 点格填课 --- */
    const EMPTY = [[0, 0], [1, 0], [2, 0]];
    const chipXY = n => ({ x: ed.chipPos[n].x, y: ed.sheetTop + ed.chipPos[n].y });
    const c0 = ed.cellXY(0, 0);
    const KEYS3 = [{ t: 1.0, x: c0.x, y: c0.y }, { t: 1.3, x: c0.x, y: c0.y, down: true },
      { t: 2.3, ...chipXY('数学'), down: true, move: .6 }, { t: 3.25, ...chipXY('数学'), down: true }, { t: 3.6, ...chipXY('数学') }];
    function reset3() {
      Object.assign(st, { fills: [], sel: [], sheetFrom: 0, sheetTo: 0, sheetT: -9, typedT: -9, typed: '', cur: null });
    }
    function s3(t) {
      if (mode === 'demo') {
        st.fills = [[0, 0, '数学', 2.3], [1, 0, '数学', 3.25]].filter(f => t >= f[3] - .01);
        st.sel = [[1.3, 0, 0], [2.3, 1, 0], [3.25, 2, 0]];
        st.cur = t >= 3.25 ? [2, 0] : t >= 2.3 ? [1, 0] : [0, 0];
        Object.assign(st, { sheetFrom: 0, sheetTo: 1, sheetT: 1.35 });
        const f = st.fills.find(f => t >= f[3] && t < f[3] + .3);
        st.typed = f ? f[2] : '';
        touchAt(ed.touch, t, KEYS3);
      } else {
        hideTouch(ed.touch);
        if (t - st.typedT > .35) st.typed = '';
      }
      const sheetP = lerp(st.sheetFrom, st.sheetTo, E.outCubic(clamp((t - st.sheetT) / .5)));
      ed.set({ lt: t, fills: st.fills, selKeys: st.sel, sheetP, typed: st.typed });
      st.fills.forEach(([, , n, ft]) => { if (t >= ft - .05 && t < ft + .2) ed.chipPos[n].el.style.transform = 'scale(.92)'; });
    }
    const filled = (r, c) => st.fills.some(f => f[0] === r && f[1] === c);
    ed.root.addEventListener('click', e => {
      if (step !== 2) return;
      const p = appXY(ed.root, e), t = lt();
      const open = st.sheetTo === 1;
      if (open && p.y > ed.sheetTop) {
        const hit = Object.entries(ed.chipPos).find(([n]) => { const c = chipXY(n); return Math.abs(p.x - c.x) < 40 && Math.abs(p.y - c.y) < 28; });
        if (!hit || !st.cur) return;
        const [n] = hit;
        st.fills.push([st.cur[0], st.cur[1], n, t]); st.typed = n; st.typedT = t;
        const next = EMPTY.find(([r, c]) => !filled(r, c));
        if (next) { st.cur = next; st.sel.push([t + .15, next[0], next[1]]); }
        else { st.cur = null; st.sel.push([t + .3, -1, 0]); Object.assign(st, { sheetFrom: 1, sheetTo: 0, sheetT: t + .35 }); hint.textContent = '填好了。点「重看演示」再来一次'; }
        return;
      }
      const cell = EMPTY.find(([r, c]) => { const xy = ed.cellXY(r, c); return !filled(r, c) && Math.abs(p.x - xy.x) < 55 && Math.abs(p.y - xy.y) < 45; });
      if (cell) {
        st.cur = cell; st.sel.push([t, cell[0], cell[1]]);
        if (!open) Object.assign(st, { sheetFrom: 0, sheetTo: 1, sheetT: t });
      } else if (open) {
        st.cur = null; st.sel.push([t, -1, 0]); Object.assign(st, { sheetFrom: 1, sheetTo: 0, sheetT: t });
      } else if (EMPTY.some(([r, c]) => !filled(r, c))) hint.textContent = '点虚线框的空格子';
    });

    /* --- 调度 --- */
    function free() {                                     // 演示结束（或被打断）：停在当前状态，交给用户
      if (mode === 'free') return;
      mode = 'free'; screens.forEach(s => hideTouch(s.touch));
      if (step === 2 && !st.cur) st.cur = [0, 0];
      hint.textContent = HINT[step];
    }
    function go(i) {
      step = i; t0 = nowS(); mode = 'demo';
      st = { week: 1, flash: -9, len: 45, gT: null, scFrom: 0, scTo: 0, scT: -9, scroll: 0 }; reset3();
      screens.forEach((s, k) => s.root.classList.toggle('show', k === i));
      tabs.forEach((b, k) => { b.setAttribute('aria-selected', k === i); b.tabIndex = k === i ? 0 : -1; b.querySelector('.bar i').style.width = '0%'; });
      hint.textContent = '看演示';
    }
    // 只要用户动手（点手机、点步骤、重看演示），就不再自动切到下一步
    scr.addEventListener('pointerdown', () => { auto = false; free(); }, true);
    tabs.forEach((b, k) => b.addEventListener('click', () => { auto = false; go(k); }));
    q('.steps').addEventListener('keydown', e => {
      const d = e.key === 'ArrowDown' || e.key === 'ArrowRight' ? 1 : e.key === 'ArrowUp' || e.key === 'ArrowLeft' ? -1 : 0;
      if (!d) return; e.preventDefault(); auto = false; const n = (step + d + 3) % 3; go(n); tabs[n].focus();
    });
    redo.addEventListener('click', () => { auto = false; go(step); });
    let started = false;
    const p = player(scr, () => {
      if (!started) { started = true; go(0); }
      if (reduce && mode === 'demo') t0 = nowS() - DEMO[step];
      const t = lt();
      if (mode === 'demo' && t >= DEMO[step]) free();
      [s1, s2, s3][step](t);
      tabs[step].querySelector('.bar i').style.width = clamp(auto ? t / (DEMO[step] + HOLD) : t / DEMO[step]) * 100 + '%';
      if (auto && !reduce && t > DEMO[step] + HOLD) go((step + 1) % 3);
    });
    p.onShow = () => { if (auto && started && mode === 'free') go(step); };
    go(0); [s1, s2, s3][0](0);
  }

  /* ================= AI 导入：四步演示（与 App 里的引导动画同一套分镜） ================= */
  {
    const scr = q('#aiDemo'), steps = qa('#aiSteps li');
    const SCENE = 3.2, LOOP = SCENE * 4;
    const NAMES = ['数学', '语文', '英语', '物理', '化学', '历史', '体育', '道德与法治'];
    const cellName = (r, c) => NAMES[(r * 3 + c * 2) % NAMES.length];
    const lines = (n, ws) => Array.from({ length: n }, (_, i) => `<div class="ln" style="width:${ws[i % ws.length]}%"></div>`).join('');
    const mini = () => `<div style="width:130px;height:96px;background:#f4f1ea;padding:7px;display:grid;grid-template-columns:repeat(5,1fr);gap:3px">` +
      Array.from({ length: 20 }, (_, k) => `<i style="display:block;border-radius:3px;background:${courseColor(cellName(k / 5 | 0, k % 5))}"></i>`).join('') + '</div>';
    const ui = document.createElement('div'); ui.className = 'aid'; ui.setAttribute('aria-hidden', 'true');
    ui.innerHTML = `
      <div class="scene" data-s="0">
        <div class="hdr"><i style="background:#00668c"><svg width="14" height="14" viewBox="0 0 24 24"><path d="M10 3l1.8 5.2L17 10l-5.2 1.8L10 17l-1.8-5.2L3 10l5.2-1.8zM18 13l.9 2.1L21 16l-2.1.9L18 19l-.9-2.1L15 16l2.1-.9z" fill="#fff"/></svg></i>AI 快速导入</div>
        <div class="card" style="top:96px"><div style="font-size:12px;font-weight:600;color:#00668c">提示词</div>${lines(8, [96, 82, 90, 64, 88, 72, 93, 55])}</div>
        <div class="abtn" data-b="copy" style="top:300px">复制提示词</div>
        <div class="toast" data-t>提示词已复制</div>
      </div>
      <div class="scene" data-s="1">
        <div class="hdr"><i style="background:#4d6bfe"><svg width="14" height="14" viewBox="0 0 24 24"><path d="M10 3l1.8 5.2L17 10l-5.2 1.8L10 17l-1.8-5.2L3 10l5.2-1.8zM18 13l.9 2.1L21 16l-2.1.9L18 19l-.9-2.1L15 16l2.1-.9z" fill="#fff"/></svg></i>AI 助手</div>
        <div class="bub photo" data-u="0" style="top:100px">${mini()}</div>
        <div class="bub" data-u="1" style="top:216px;width:180px">${lines(5, [95, 80, 90, 60, 75])}</div>
        <div class="input"><span data-ph>粘贴提示词…</span><b></b></div>
      </div>
      <div class="scene" data-s="2">
        <div class="hdr"><i style="background:#4d6bfe"><svg width="14" height="14" viewBox="0 0 24 24"><path d="M10 3l1.8 5.2L17 10l-5.2 1.8L10 17l-1.8-5.2L3 10l5.2-1.8zM18 13l.9 2.1L21 16l-2.1.9L18 19l-.9-2.1L15 16l2.1-.9z" fill="#fff"/></svg></i>AI 助手</div>
        <div class="bub on" style="top:100px;width:110px;height:34px;opacity:.4"></div>
        <div class="code" data-c><div class="top">json<span class="cp" data-cp>复制</span></div>` +
      [[40, '#e6e6e6'], [86, '#9fcaff'], [70, '#9fe1cb'], [92, '#f2c38b'], [76, '#9fe1cb'], [62, '#9fcaff'], [84, '#f2c38b'], [30, '#e6e6e6']]
        .map(([w, c]) => `<div class="cl" data-w="${w}" style="background:${c}"></div>`).join('') + `</div>
      </div>
      <div class="scene" data-s="3">
        <div class="hdr"><i style="background:#00668c"><svg width="14" height="14" viewBox="0 0 24 24"><path d="M10 3l1.8 5.2L17 10l-5.2 1.8L10 17l-1.8-5.2L3 10l5.2-1.8zM18 13l.9 2.1L21 16l-2.1.9L18 19l-.9-2.1L15 16l2.1-.9z" fill="#fff"/></svg></i>AI 快速导入</div>
        <div class="field" data-f>粘贴 AI 的回复</div>
        <div class="grid">${Array.from({ length: 30 }, (_, k) => { const n = cellName(k / 5 | 0, k % 5); return `<i style="background:${courseColor(n)}">${FULL[n[0]] ? n[0] : n.slice(0, 2)}</i>`; }).join('')}</div>
        <div class="abtn" data-b="imp" style="top:440px">导入</div>
      </div>
      <div class="finger"></div>`;
    scr.appendChild(ui);
    const fitAid = () => { ui.style.transform = `scale(${scr.clientWidth / 300})`; };
    fitAid(); new ResizeObserver(fitAid).observe(scr);
    const sc = [...ui.querySelectorAll('.scene')];
    const copyBtn = ui.querySelector('[data-b=copy]'), impBtn = ui.querySelector('[data-b=imp]'), toast = ui.querySelector('[data-t]');
    const ubs = [...ui.querySelectorAll('[data-u]')], ph = ui.querySelector('[data-ph]');
    const code = ui.querySelector('[data-c]'), cls = [...ui.querySelectorAll('.cl')], cp = ui.querySelector('[data-cp]');
    const field = ui.querySelector('[data-f]'), cells = [...ui.querySelectorAll('.grid i')], finger = ui.querySelector('.finger');
    const CHK = '<svg width="14" height="14" viewBox="0 0 24 24" style="vertical-align:-2px;margin-right:4px"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" fill="#fff"/></svg>';
    const setHTMLc = (el, v) => { if (el._h !== v) { el.innerHTML = v; el._h = v; } };
    const press = (t, at) => { const d = t - at; return d < -.12 || d > .22 ? 0 : clamp(1 - Math.abs((d - .02) / .14)); };
    const between = (t, a, b) => t >= a && t < b;
    let t0 = null, offset = 0, lastScene = -1;
    function setText(el, v) { if (el.textContent !== v) el.textContent = v; }
    function draw(t) {
      const scene = Math.min(3, Math.floor(t / SCENE)), lt = t - scene * SCENE;
      if (scene !== lastScene) { sc.forEach((e, i) => e.classList.toggle('on', i === scene)); lastScene = scene; }
      steps.forEach((li, i) => {
        li.classList.toggle('on', i === scene); li.classList.toggle('done', i < scene);
        li.querySelector('.bar i').style.width = (i === scene ? clamp(lt / SCENE) * 100 : 0) + '%';
      });
      let f = null;                                     // 触点 [x, y, 按下]
      if (scene === 0) {
        const done = lt > 1.25;
        setHTMLc(copyBtn, done ? CHK + '已复制' : '复制提示词');
        copyBtn.style.background = done ? '#2bb894' : '#00668c';
        copyBtn.style.transform = `scale(${1 - .05 * press(lt, 1.05)})`;
        toast.classList.toggle('on', between(lt, 1.35, 2.8));
        if (between(lt, .4, 1.8)) f = [150, 323, press(lt, 1.05)];
      } else if (scene === 1) {
        ubs[0].classList.toggle('on', lt > .3); ubs[1].classList.toggle('on', lt > 1.3);
        setText(ph, lt < 1.15 ? '粘贴提示词…' : '');
        if (between(lt, .7, 1.7)) f = [262, 617, press(lt, 1.15)];
      } else if (scene === 2) {
        code.classList.toggle('on', lt > .15);
        cls.forEach((c, i) => { c.style.width = (lt > .4 + i * .13 ? c.dataset.w : 0) + '%'; });
        const done = lt > 2.0;
        setHTMLc(cp, done ? CHK + '已复制' : '复制'); cp.style.background = done ? '#2bb894' : 'rgba(255,255,255,.14)';
        cp.style.transform = `scale(${1 - .1 * press(lt, 1.85)})`;
        if (between(lt, 1.35, 2.6)) f = [250, 166, press(lt, 1.85)];
      } else {
        const filled = lt > .55;
        field.classList.toggle('on', filled);
        setText(field, filled ? '{ "type": "smartschedule.timetable", "days": [ …' : '粘贴 AI 的回复');
        cells.forEach((c, k) => c.classList.toggle('on', lt > .8 + ((k / 5 | 0) + k % 5) * .07));
        const done = lt > 2.05;
        setHTMLc(impBtn, done ? CHK + '导入完成' : '导入'); impBtn.style.background = done ? '#2bb894' : '#00668c';
        impBtn.style.transform = `scale(${1 - .05 * press(lt, 1.9)})`;
        if (between(lt, .1, 2.5)) f = lt < 1.2 ? [150, 118, press(lt, .45)] : [150, 463, press(lt, 1.9)];
      }
      finger.style.opacity = f ? 1 : 0;
      if (f) finger.style.transform = `translate(${f[0]}px, ${f[1]}px) scale(${1 - .18 * f[2]})`;
    }
    steps.forEach((li, i) => li.addEventListener('click', () => { offset = i * SCENE - ((nowS() - (t0 ?? nowS())) % LOOP) + offset; if (reduce) draw(i * SCENE + SCENE - .01); }));
    const p = player(scr, t => {
      if (reduce) return;
      if (t0 === null) t0 = t;
      draw((((t - t0 + offset) % LOOP) + LOOP) % LOOP);
    });
    p.onShow = t => { if (t0 === null) t0 = t; };
    draw(reduce ? LOOP - .01 : 0);
  }

  /* ================= 分享码：生成 → 复制 → 同学导入 ================= */
  {
    const me = BackupScreen({ withDialog: true }), mate = BackupScreen({ withImport: true });
    mount(q('#scrShareA'), me.root); mount(q('#scrShareB'), mate.root);
    const CHECK = '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" fill="#9fe1cb"/></svg>';
    const LOOP = 9;
    let t0 = null;
    function draw(lt) {
      me.row1hl.style.opacity = Math.sin(Math.PI * P(lt, .95, 1.5, E.lin)) * .9;
      me.setDialog(P(lt, 1.15, 1.6, E.outCubic) - P(lt, 3.4, 3.8, E.inOutCubic), P(lt, 1.3, 2.5, E.lin));
      me.copy.style.transform = `scale(${1 - .06 * Math.sin(Math.PI * P(lt, 2.78, 2.98, E.lin))})`;
      me.setSnack(P(lt, 2.9, 3.2, E.outCubic) - P(lt, 4.6, 5.0, E.inOutCubic), CHECK + '已复制分享码 825480');
      me.setRecent(P(lt, 3.5, 4.0, E.inOutCubic));
      touchAt(me.touch, lt, [{ t: .7, x: 300, y: 293 }, { t: 1.0, x: 300, y: 293, down: true }, { t: 2.8, x: 558, y: 952, down: true, move: .6 }, { t: 3.0, x: 560, y: 960 }]);
      mate.setImport(P(lt, 3.6, 4.2, E.outCubic) - P(lt, 5.25, 5.65, E.inOutCubic));
      mate.imp.style.transform = `scale(${1 - .05 * Math.sin(Math.PI * P(lt, 5.08, 5.28, E.lin))})`;
      mate.setSnack(P(lt, 5.45, 5.8, E.outCubic) - P(lt, 7.6, 8.0, E.inOutCubic), CHECK + '已导入「我的课表」');
      touchAt(mate.touch, lt, [{ t: 4.6, x: 507, y: 1469 }, { t: 5.1, x: 507, y: 1469, down: true }, { t: 5.3, x: 510, y: 1475 }]);
    }
    const p = player(q('#scrShareA'), t => {
      if (t0 === null) t0 = t;
      let lt = (t - t0) % LOOP;
      if (reduce) lt = 2.6;
      // 循环衔接：最后一刻收起「最近生成」那一行，避免跳变
      if (lt > LOOP - .8) { draw(LOOP - .8); me.setRecent(1 - P(lt, LOOP - .8, LOOP, E.inOutCubic)); return; }
      draw(lt);
    });
    p.onShow = t => { if (t0 === null) t0 = t; };
    draw(0);
  }

  /* ================= 常驻通知：下拉通知栏 ================= */
  {
    const sh = ShadeScreen(); mount(q('#scrShade'), sh.root);
    const LOOP = 8;
    let t0 = null;
    player(q('#scrShade'), t => {
      if (t0 === null) t0 = t;
      const lt = reduce ? 6 : (t - t0) % LOOP;
      const pull = P(lt, 1.0, 1.9, E.inOutCubic) - P(lt, 7.0, 7.7, E.inOutCubic);
      sh.set(pull, lt < 3.0 || lt > 6.8 ? -1 : K(lt, [[3.0, 0], [3.8, 1], [4.6, 2], [6.6, 2]], E.inOutSine));
      touchAt(sh.touch, lt, [{ t: .8, x: 347, y: 30 }, { t: 1.0, x: 347, y: 30, down: true, hold: 1.9 }, { t: 1.9, x: 347, y: 760, move: .9 }]);
      if (lt > 1.0 && lt < 1.9) sh.touch.style.transform = `translate(347px, ${lerp(30, 760, E.inOutCubic(P(lt, 1.0, 1.9, E.lin)))}px) scale(.82)`;
    });
  }

  /* ================= 2.5 MB / 开源 ================= */
  {
    const arc = q('#ringArc'), num = q('#mbNum');
    let done = false;
    new IntersectionObserver((es, o) => es.forEach(e => {
      if (!e.isIntersecting || done) return;
      done = true; o.disconnect();
      arc.style.strokeDashoffset = String(2388 * .14);
      if (reduce) return;
      const s = nowS();
      (function tick() { const k = P(nowS(), s + .2, s + 1.9, E.outExpo); num.textContent = (2.5 * k).toFixed(1); if (k < 1) requestAnimationFrame(tick); })();
    }), { threshold: .4 }).observe(arc);
    const card = q('#mitCard');
    [['数', 10, 16, 1], ['语', 89, 13, .8], ['英', 8, 84, .9], ['物', 91, 80, 1], ['化', 27, 93, .7], ['道', 73, 94, .75]].forEach(([c, x, y, s], i) => {
      const d = document.createElement('div');
      d.className = 'ctile' + (y > 50 ? ' lo' : ''); d.textContent = c; d.setAttribute('aria-hidden', 'true');
      d.style.cssText = `left:${x}%;top:${y}%;margin:-38px 0 0 -38px;background:${courseColor(FULL[c])};transform:scale(${s}) rotate(${i % 2 ? -8 : 8}deg);opacity:${.6 + .4 * s};animation-delay:${-i * 1.1}s` +
        (s < .8 ? ';filter:blur(1.5px)' : '');
      card.prepend(d);
    });
  }

  /* ================= 下载信息：取 GitHub 最新 Release，失败则保留静态默认值 ================= */
  fetch('https://api.github.com/repos/enenhhh/smartschedule/releases/latest', { headers: { Accept: 'application/vnd.github+json' } })
    .then(r => { if (!r.ok) throw 0; return r.json(); })
    .then(rel => {
      const apk = (rel.assets || []).find(a => /\.apk$/i.test(a.name));
      if (!apk) return;
      q('#dlBtn').href = apk.browser_download_url;
      q('#dlText').textContent = `下载 ${rel.tag_name} APK`;
      q('#dlVer').textContent = String(rel.tag_name).replace(/^v/, '');
      q('#dlSize').textContent = (apk.size / 1048576).toFixed(1) + ' MB';
      if (rel.published_at) { q('#dlDate').textContent = new Date(rel.published_at).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }); q('#dlDateWrap').hidden = false; }
      if (apk.digest && /^sha256:/.test(apk.digest)) { const h = q('#dlHash'); h.textContent = 'SHA-256  ' + apk.digest.slice(7); h.hidden = false; }
    }).catch(() => {});
})();
