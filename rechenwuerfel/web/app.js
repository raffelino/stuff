/* Rechenwürfel – Oberfläche, Animationen und Sound (Web-Version). */
(function () {
  "use strict";
  const C = window.RechenCore;
  const $ = (id) => document.getElementById(id);
  const params = new URLSearchParams(location.search);
  const FAST = params.get("fast") === "1"; // verkürzte Animationen (für automatische Tests)
  const T = { roll: FAST ? 120 : 1200, settle: FAST ? 40 : 260, step: FAST ? 40 : 380 };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  // ------------------------------------------------------------ Speicher
  const store = {
    get(key, fallback) {
      try {
        const v = localStorage.getItem("rechenwuerfel." + key);
        return v === null ? fallback : JSON.parse(v);
      } catch (_) { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem("rechenwuerfel." + key, JSON.stringify(value)); } catch (_) { /* egal */ }
    },
  };

  // ------------------------------------------------------------ Audio (synthetisch, Web Audio API)
  const audio = {
    ctx: null,
    music: store.get("music", true),
    sfx: store.get("sfx", true),
    musicSession: 0,
    musicTimer: null,
    ensure() {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return null;
        this.ctx = new AC();
      }
      if (this.ctx.state === "suspended") this.ctx.resume().catch(() => {});
      return this.ctx;
    },
    tone({ freq, freqEnd, dur, type = "sine", vol = 0.3, at = 0, attack = 0.005, release = 0.05, dest }) {
      const ctx = this.ctx;
      const t0 = ctx.currentTime + at;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, t0);
      if (freqEnd) osc.frequency.exponentialRampToValueAtTime(Math.max(20, freqEnd), t0 + dur);
      gain.gain.setValueAtTime(0.0001, t0);
      gain.gain.linearRampToValueAtTime(vol, t0 + attack);
      gain.gain.setValueAtTime(vol, t0 + Math.max(attack, dur - release));
      gain.gain.linearRampToValueAtTime(0.0001, t0 + dur);
      osc.connect(gain).connect(dest || ctx.destination);
      osc.start(t0);
      osc.stop(t0 + dur + 0.02);
    },
    noise({ dur, vol = 0.3, at = 0, lowpass = 4000, dest }) {
      const ctx = this.ctx;
      const t0 = ctx.currentTime + at;
      const len = Math.max(1, Math.floor(ctx.sampleRate * dur));
      const buf = ctx.createBuffer(1, len, ctx.sampleRate);
      const data = buf.getChannelData(0);
      for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1;
      const src = ctx.createBufferSource();
      src.buffer = buf;
      const filter = ctx.createBiquadFilter();
      filter.type = "lowpass";
      filter.frequency.value = lowpass;
      const gain = ctx.createGain();
      gain.gain.setValueAtTime(vol, t0);
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
      src.connect(filter).connect(gain).connect(dest || ctx.destination);
      src.start(t0);
    },
    play(name) {
      if (!this.sfx || !this.ensure()) return;
      const n = (name) => 440 * Math.pow(2, (name - 69) / 12);
      switch (name) {
        case "click": this.tone({ freq: 700, dur: 0.05, vol: 0.25 }); break;
        case "tick": this.tone({ freq: 1500, dur: 0.05, type: "square", vol: 0.12 }); break;
        case "step": this.tone({ freq: 880, freqEnd: 1100, dur: 0.08, vol: 0.3 }); break;
        case "dice":
          for (let i = 0, t = 0; i < 9; i++, t += 0.06 + i * 0.02) {
            this.noise({ dur: 0.05, vol: 0.45 - i * 0.03, at: t, lowpass: 3000 });
            this.tone({ freq: [900, 1200, 1500, 1800][i % 4], dur: 0.04, type: "triangle", vol: 0.15, at: t });
          }
          break;
        case "correct":
          [72, 76, 79, 84].forEach((m, i) => {
            this.tone({ freq: n(m), dur: i === 3 ? 0.35 : 0.14, type: "square", vol: 0.12, at: i * 0.09 });
            this.tone({ freq: n(m), dur: i === 3 ? 0.35 : 0.14, type: "sine", vol: 0.2, at: i * 0.09 });
          });
          break;
        case "wrong":
          this.tone({ freq: 220, freqEnd: 180, dur: 0.25, type: "sawtooth", vol: 0.2 });
          this.tone({ freq: 170, freqEnd: 120, dur: 0.4, type: "sawtooth", vol: 0.2, at: 0.28, release: 0.1 });
          break;
        case "timeout":
          this.tone({ freq: 330, freqEnd: 200, dur: 0.5, type: "square", vol: 0.12 });
          this.tone({ freq: 331, freqEnd: 201, dur: 0.5, type: "triangle", vol: 0.2 });
          this.tone({ freq: 160, freqEnd: 90, dur: 0.4, type: "sawtooth", vol: 0.2, at: 0.55, release: 0.15 });
          break;
        case "win": {
          const seq = [[72, .15], [72, .15], [72, .15], [76, .45], [74, .15], [74, .15], [74, .15], [79, .45], [76, .15], [79, .15], [84, .6]];
          let t = 0;
          for (const [m, d] of seq) {
            this.tone({ freq: n(m), dur: d, type: "square", vol: 0.12, at: t });
            this.tone({ freq: n(m) / 2, dur: d, type: "triangle", vol: 0.18, at: t });
            t += d + 0.02;
          }
          break;
        }
        case "joker":
          [84, 88, 91, 96, 100].forEach((m, i) => this.tone({ freq: n(m), dur: 0.25, vol: 0.18, at: i * 0.07, release: 0.15 }));
          this.tone({ freq: n(91), dur: 0.5, type: "triangle", vol: 0.12, at: 0.35, release: 0.3 });
          break;
        case "skip": this.tone({ freq: 500, freqEnd: 180, dur: 0.8, type: "triangle", vol: 0.25, release: 0.2 }); break;
      }
    },
    // Hintergrundmusik: 16 Takte Chiptune, endlos geplant
    startMusic() {
      if (!this.music || this.musicTimer || !this.ensure()) return;
      const ctx = this.ctx;
      const session = ++this.musicSession;
      const master = ctx.createGain();
      master.gain.value = 0.35;
      master.connect(ctx.destination);
      this.musicMaster = master;
      const bpm = 132, beat = 60 / bpm, eighth = beat / 2;
      const n = (name) => {
        const m = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 }[name[0]] + 12 * (parseInt(name.slice(1), 10) + 1);
        return 440 * Math.pow(2, (m - 69) / 12);
      };
      const melody = [
        "E5 G5 E5 C5 D5 E5 D5:2", "D5 F5 D5 B4 G4:2 B4:2", "C5 E5 C5 A4 B4 C5 B4:2", "A4 C5 A4 F4 G4:2 A4:2",
        "E5 G5 E5 C5 G5:2 E5:2", "D5 F5 D5 B4 D5:2 G5:2", "A5 G5 F5 E5 D5 C5 D5:2", "B4 C5 D5 E5 D5:4",
        "C5 E5 G5 C6 B5:2 G5:2", "B4 D5 G5 B5 A5:2 G5:2", "A4 C5 E5 A5 G5:2 E5:2", "F5 E5 D5 C5 A4:2 C5:2",
        "E5 G5 C6 G5 E5:2 C5:2", "D5 G5 B5 G5 D5:2 B4:2", "A4 C5 F5 A5 G5 F5 E5 D5", "D5:2 B4:2 C5:4",
      ];
      const chords = ["C", "G", "Am", "F", "C", "G", "F", "G", "C", "G", "Am", "F", "C", "G", "F", "G"];
      const roots = { C: "C3", G: "G2", Am: "A2", F: "F2" }, thirds = { C: "E3", G: "B2", Am: "C3", F: "A2" };
      const barLen = 4 * beat, loopLen = melody.length * barLen;
      let nextBar = 0, barStart = ctx.currentTime + 0.1;
      const scheduleBar = (bar, t0) => {
        let t = 0;
        for (const tok of melody[bar].split(" ")) {
          const [name, l] = tok.split(":");
          const dur = (l ? parseInt(l, 10) : 1) * eighth;
          if (name !== "R") {
            const f = n(name);
            this.tone({ freq: f, dur: dur * 0.9, type: "square", vol: 0.08, at: t0 + t - ctx.currentTime, attack: 0.01, dest: master });
            this.tone({ freq: f, dur: dur * 0.9, type: "triangle", vol: 0.07, at: t0 + t - ctx.currentTime, attack: 0.01, dest: master });
          }
          t += dur;
        }
        const ch = chords[bar];
        const pattern = [n(roots[ch]), n(roots[ch]) * 2, n(roots[ch]) * 1.5, n(thirds[ch])];
        pattern.forEach((f, i) => {
          this.tone({ freq: f, dur: beat * 0.8, type: "triangle", vol: 0.16, at: t0 + i * beat - ctx.currentTime, dest: master });
          if (i % 2 === 0) this.tone({ freq: 150, freqEnd: 45, dur: 0.12, vol: 0.3, at: t0 + i * beat - ctx.currentTime, dest: master });
          else this.noise({ dur: 0.09, vol: 0.12, at: t0 + i * beat - ctx.currentTime, lowpass: 5000, dest: master });
        });
        for (let i = 0; i < 8; i++) this.noise({ dur: 0.025, vol: i % 2 ? 0.035 : 0.05, at: t0 + i * eighth - ctx.currentTime, lowpass: 9000, dest: master });
      };
      const tick = () => {
        if (session !== this.musicSession) return;
        while (barStart < ctx.currentTime + 0.6) {
          scheduleBar(nextBar % melody.length, barStart);
          nextBar++;
          barStart += barLen;
        }
      };
      tick();
      this.musicTimer = setInterval(tick, 200);
      void loopLen;
    },
    stopMusic() {
      this.musicSession++;
      if (this.musicTimer) { clearInterval(this.musicTimer); this.musicTimer = null; }
      if (this.musicMaster && this.ctx) {
        const g = this.musicMaster;
        g.gain.setTargetAtTime(0, this.ctx.currentTime, 0.05);
        setTimeout(() => { try { g.disconnect(); } catch (_) { /* egal */ } }, 400);
        this.musicMaster = null;
      }
    },
    setMusic(on) { this.music = on; store.set("music", on); if (on) this.startMusic(); else this.stopMusic(); },
    setSfx(on) { this.sfx = on; store.set("sfx", on); },
  };
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) audio.stopMusic(); else audio.startMusic();
  });

  // ------------------------------------------------------------ Bildschirme
  const screens = { menu: $("screen-menu"), setup: $("screen-setup"), game: $("screen-game") };
  let currentScreen = "menu";
  function showScreen(name) {
    for (const [k, el] of Object.entries(screens)) el.hidden = k !== name;
    currentScreen = name;
    if (name === "game") board.resize();
    window.scrollTo(0, 0);
  }
  const click = () => audio.play("click");

  // ------------------------------------------------------------ Hauptmenü
  $("btn-play").addEventListener("click", () => { click(); audio.startMusic(); showScreen("setup"); });
  $("btn-rules").addEventListener("click", () => { click(); audio.startMusic(); $("dlg-rules").showModal(); });
  $("btn-rules-ok").addEventListener("click", () => { click(); $("dlg-rules").close(); });
  $("btn-options").addEventListener("click", () => {
    click(); audio.startMusic();
    $("opt-music").checked = audio.music;
    $("opt-sfx").checked = audio.sfx;
    $("dlg-options").showModal();
  });
  $("opt-music").addEventListener("change", (e) => audio.setMusic(e.target.checked));
  $("opt-sfx").addEventListener("change", (e) => { audio.setSfx(e.target.checked); if (e.target.checked) click(); });
  $("btn-options-ok").addEventListener("click", () => { click(); $("dlg-options").close(); });

  // ------------------------------------------------------------ Setup
  const setup = {
    count: Math.min(4, Math.max(1, store.get("playerCount", 2))),
    names: store.get("names", ["Spieler 1", "Spieler 2", "Spieler 3", "Spieler 4"]),
    figures: store.get("figures", C.FIGURES.slice(0, 4)),
    rows: [],
  };
  // doppelte Figuren aus alten Einstellungen auflösen
  for (let i = 0; i < 4; i++) {
    if (!C.FIGURES.includes(setup.figures[i]) || setup.figures.slice(0, i).includes(setup.figures[i])) {
      setup.figures[i] = C.FIGURES.find((f) => !setup.figures.slice(0, i).includes(f));
    }
  }
  function buildPlayerRows() {
    const host = $("players");
    host.innerHTML = "";
    setup.rows = [];
    for (let i = 0; i < 4; i++) {
      const card = document.createElement("div");
      card.className = "card player";
      card.dataset.player = String(i);
      const head = document.createElement("div");
      head.className = "head";
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.style.background = C.PLAYER_COLORS[i];
      badge.textContent = `Spieler ${i + 1}`;
      const name = document.createElement("input");
      name.type = "text";
      name.id = `name-${i}`;
      name.maxLength = 16;
      name.placeholder = "Name";
      name.setAttribute("aria-label", `Name Spieler ${i + 1}`);
      name.value = setup.names[i] || `Spieler ${i + 1}`;
      head.append(badge, name);
      const bar = document.createElement("div");
      bar.className = "figures";
      const buttons = C.FIGURES.map((f, fi) => {
        const b = document.createElement("button");
        b.className = "figure";
        b.type = "button";
        b.textContent = f;
        b.setAttribute("aria-label", `Figur ${fi + 1}`);
        b.addEventListener("click", () => {
          const takenBy = setup.figures.findIndex((x, pi) => x === f && pi < setup.count);
          if (takenBy !== -1 && takenBy !== i) setup.figures[takenBy] = setup.figures[i]; // tauschen
          setup.figures[i] = f;
          click();
          refreshFigures();
        });
        bar.append(b);
        return b;
      });
      card.append(head, bar);
      host.append(card);
      setup.rows.push({ card, name, buttons });
    }
  }
  function refreshFigures() {
    setup.rows.forEach((row, pi) => {
      row.buttons.forEach((b, fi) => {
        const f = C.FIGURES[fi];
        b.setAttribute("aria-pressed", String(setup.figures[pi] === f));
        const takenByOther = setup.figures.some((x, i) => i !== pi && i < setup.count && x === f);
        b.classList.toggle("taken", takenByOther);
      });
    });
  }
  function setPlayerCount(count) {
    setup.count = count;
    document.querySelectorAll("#player-count .chip").forEach((b) => b.setAttribute("aria-pressed", String(+b.dataset.count === count)));
    setup.rows.forEach((row, i) => { row.card.hidden = i >= count; });
    refreshFigures();
  }
  document.querySelectorAll("#player-count .chip").forEach((b) => b.addEventListener("click", () => { click(); setPlayerCount(+b.dataset.count); }));
  document.querySelectorAll(".chip[data-set]").forEach((b) => b.addEventListener("click", () => {
    click();
    $("set-" + b.dataset.set).value = b.dataset.value;
  }));
  const stepInputFor = { PLUS: "set-steps-plus", MINUS: "set-steps-minus", TIMES: "set-steps-times", DIVIDE: "set-steps-divide" };
  function refreshOperationInputs() {
    for (const op of C.OPERATION_KEYS) $(stepInputFor[op]).disabled = !$("op-" + op).checked;
    $("ops-error").hidden = C.OPERATION_KEYS.some((op) => $("op-" + op).checked);
  }
  for (const op of C.OPERATION_KEYS) $("op-" + op).addEventListener("change", () => { click(); refreshOperationInputs(); });
  function loadSettingsForm() {
    const s = C.sanitizeSettings(store.get("settings", C.DEFAULT_SETTINGS));
    for (const op of C.OPERATION_KEYS) $("op-" + op).checked = C.isEnabled(s, op);
    refreshOperationInputs();
    $("set-range").value = s.numberRange;
    $("set-seconds").value = s.secondsPerTask;
    $("set-fields").value = s.boardFields;
    $("set-steps-plus").value = s.stepsPlus;
    $("set-steps-minus").value = s.stepsMinus;
    $("set-steps-times").value = s.stepsTimes;
    $("set-steps-divide").value = s.stepsDivide;
  }
  function readSettingsForm() {
    return C.sanitizeSettings({
      numberRange: $("set-range").value,
      secondsPerTask: $("set-seconds").value,
      boardFields: $("set-fields").value,
      stepsPlus: $("set-steps-plus").value,
      stepsMinus: $("set-steps-minus").value,
      stepsTimes: $("set-steps-times").value,
      stepsDivide: $("set-steps-divide").value,
      operations: C.OPERATION_KEYS.filter((op) => $("op-" + op).checked),
    });
  }
  $("btn-setup-back").addEventListener("click", () => { click(); showScreen("menu"); });
  $("btn-start").addEventListener("click", () => {
    click();
    if (!C.OPERATION_KEYS.some((op) => $("op-" + op).checked)) {
      $("ops-error").hidden = false;
      $("ops-error").scrollIntoView({ block: "center" });
      return;
    }
    const settings = readSettingsForm();
    store.set("settings", settings);
    const names = setup.rows.slice(0, setup.count).map((r, i) => r.name.value.trim() || `Spieler ${i + 1}`);
    setup.names = setup.rows.map((r, i) => r.name.value.trim() || `Spieler ${i + 1}`);
    store.set("playerCount", setup.count);
    store.set("names", setup.names);
    store.set("figures", setup.figures);
    game.start(C.createPlayers(names, setup.figures.slice(0, setup.count)), settings);
  });
  buildPlayerRows();
  setPlayerCount(setup.count);
  loadSettingsForm();

  // ------------------------------------------------------------ Spielbrett (Canvas)
  const board = {
    canvas: $("board"),
    fields: 30,
    players: [],
    animPos: [],
    moving: -1,
    current: 0,
    layout: null,
    colors: ["#FFF59D", "#B3E5FC", "#F8BBD0", "#C8E6C9", "#FFE0B2"],
    setup(fields, players) {
      this.fields = fields;
      this.players = players;
      this.animPos = players.map((p) => p.position);
      this.moving = -1;
      this.resize();
    },
    resize() {
      const wrap = this.canvas.parentElement;
      const w = wrap.clientWidth, h = wrap.clientHeight;
      if (!w || !h) return;
      const dpr = window.devicePixelRatio || 1;
      this.canvas.width = Math.round(w * dpr);
      this.canvas.height = Math.round(h * dpr);
      this.ctx = this.canvas.getContext("2d");
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.layout = C.boardLayout(this.fields, w - 8, h - 8);
      this.layout.offX += 4;
      this.layout.offY += 4;
      this.draw();
    },
    draw() {
      const ctx = this.ctx, L = this.layout;
      if (!ctx || !L) return;
      const w = this.canvas.width / (window.devicePixelRatio || 1), h = this.canvas.height / (window.devicePixelRatio || 1);
      ctx.clearRect(0, 0, w, h);
      const cell = L.cell;
      // Straße
      ctx.beginPath();
      for (let i = 0; i < this.fields; i++) {
        const c = C.cellCenter(L, i);
        if (i === 0) ctx.moveTo(c.x, c.y); else ctx.lineTo(c.x, c.y);
      }
      ctx.lineWidth = cell * 0.55;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue("--board-road").trim() || "rgba(255,255,255,.4)";
      ctx.stroke();
      // Felder
      const half = cell * 0.43, r = cell * 0.18;
      ctx.font = `700 ${Math.max(9, cell * 0.2)}px ${getComputedStyle(document.body).fontFamily}`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      for (let i = 0; i < this.fields; i++) {
        const c = C.cellCenter(L, i);
        ctx.fillStyle = i === 0 ? "#81C784" : i === this.fields - 1 ? "#FFD54F" : this.colors[i % this.colors.length];
        ctx.beginPath();
        ctx.roundRect(c.x - half, c.y - half, half * 2, half * 2, r);
        ctx.fill();
        ctx.strokeStyle = "rgba(0,0,0,.2)";
        ctx.lineWidth = Math.max(1, cell * 0.02);
        ctx.stroke();
        ctx.fillStyle = "rgba(0,0,0,.65)";
        const label = i === 0 ? "START" : i === this.fields - 1 ? "🏁 ZIEL" : String(i);
        ctx.fillText(label, c.x, c.y - half + cell * 0.16);
      }
      // Figuren
      const radius = cell * 0.2;
      const order = this.players.map((_, i) => i).filter((i) => i !== this.moving);
      if (this.moving >= 0) order.push(this.moving);
      for (const idx of order) {
        const pos = this.animPos[idx];
        const p = C.pathPoint(L, pos);
        const same = this.players.map((_, i) => i).filter((i) => i !== this.moving && this.animPos[i] === pos);
        const slot = same.indexOf(idx);
        let dx = 0, dy = 0;
        if (idx !== this.moving && same.length > 1) [dx, dy] = this.offsetFor(slot, same.length);
        const cx = p.x + dx * cell, cy = p.y + (dy + 0.06) * cell;
        if (idx === this.current) {
          ctx.beginPath();
          ctx.arc(cx, cy, radius * 1.25, 0, Math.PI * 2);
          ctx.lineWidth = Math.max(2, cell * 0.05);
          ctx.strokeStyle = "#FFC107";
          ctx.stroke();
        }
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.fillStyle = this.players[idx].color;
        ctx.fill();
        ctx.lineWidth = Math.max(2, cell * 0.035);
        ctx.strokeStyle = "#fff";
        ctx.stroke();
        ctx.font = `${radius * 1.35}px "Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", sans-serif`;
        ctx.fillStyle = "#000";
        ctx.fillText(this.players[idx].figure, cx, cy + radius * 0.05);
      }
    },
    offsetFor(slot, count) {
      if (count === 2) return slot === 0 ? [-0.17, 0.05] : [0.17, 0.05];
      if (count === 3) return [[-0.18, -0.08], [0.18, -0.08], [0, 0.18]][slot];
      return [[-0.17, -0.12], [0.17, -0.12], [-0.17, 0.18], [0.17, 0.18]][slot] || [0, 0];
    },
    animateMove(idx, from, to, onStep) {
      return new Promise((resolve) => {
        if (to <= from) { resolve(); return; }
        this.moving = idx;
        const duration = Math.min(3000, T.step * (to - from));
        const start = performance.now();
        let lastStep = from;
        const ease = (t) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2);
        const frame = (now) => {
          const t = Math.min(1, (now - start) / duration);
          const v = from + (to - from) * ease(t);
          this.animPos[idx] = v;
          const reached = Math.floor(v + 0.001);
          while (lastStep < reached) { lastStep++; onStep && onStep(lastStep); }
          this.draw();
          if (t < 1) requestAnimationFrame(frame);
          else { this.animPos[idx] = to; this.moving = -1; this.draw(); resolve(); }
        };
        requestAnimationFrame(frame);
      });
    },
  };
  if (window.ResizeObserver) new ResizeObserver(() => board.resize()).observe(board.canvas.parentElement);
  else window.addEventListener("resize", () => board.resize());

  // ------------------------------------------------------------ Würfel
  const dice = {
    el: $("dice"),
    rolling: false,
    faces: C.DICE_FACES,
    show(face) {
      this.el.textContent = face.symbol;
      this.el.style.setProperty("--face-color", face.color);
      this.el.classList.toggle("emoji", !face.operation);
    },
    async roll(result) {
      this.rolling = true;
      const start = performance.now();
      while (performance.now() - start < T.roll) {
        const elapsed = performance.now() - start;
        this.show(this.faces[Math.floor(Math.random() * this.faces.length)]);
        this.el.style.transform = `rotate(${Math.random() * 50 - 25}deg) scale(${0.85 + Math.random() * 0.25})`;
        await sleep(60 + elapsed / 6);
      }
      this.show(result);
      this.el.style.transform = "";
      this.el.classList.remove("settle");
      void this.el.offsetWidth;
      this.el.classList.add("settle");
      await sleep(T.settle);
      this.rolling = false;
    },
  };

  // ------------------------------------------------------------ Spiel
  const panels = ["roll", "joker", "task", "message", "win"].map((n) => $("panel-" + n));
  function showPanel(name) {
    for (const p of panels) p.hidden = p.id !== "panel-" + name;
  }
  const game = {
    players: [], settings: null, current: 0, task: null, answer: "", busy: false,
    timer: null, timeLeft: 0, timerPausedAt: null, maxDigits: 3, phase: "idle",
    start(players, settings) {
      this.players = players;
      this.settings = settings;
      this.current = 0;
      this.task = null;
      this.answer = "";
      this.busy = false;
      this.maxDigits = String(settings.numberRange).length + 1;
      $("steps-hint").textContent = settings.operations
        .map((op) => `${C.OPERATIONS[op].symbol} ${C.stepsFor(settings, op)}`).join(" · ") + " Felder";
      // Abgewählte Rechenarten stehen auch beim Joker nicht zur Wahl
      document.querySelectorAll("#panel-joker .key").forEach((b) => { b.hidden = !C.isEnabled(settings, b.dataset.op); });
      dice.faces = C.allowedFaces(settings);
      showScreen("game");
      board.setup(settings.boardFields, players);
      board.current = 0;
      this.updateHeader();
      this.showRoll();
      updateAudioButtons();
    },
    get player() { return this.players[this.current]; },
    updateHeader() {
      const p = this.player;
      $("turn").textContent = `${p.figure} ${p.name} ist dran`;
      $("turn").style.color = p.color;
      board.current = this.current;
      board.draw();
    },
    showRoll() {
      this.phase = "roll";
      $("btn-roll").disabled = false;
      $("dice-hint").textContent = "Tippe auf „Würfeln“";
      showPanel("roll");
    },
    async rollDice() {
      if (this.busy || dice.rolling) return;
      this.busy = true;
      $("btn-roll").disabled = true;
      $("dice-hint").textContent = "Der Würfel rollt …";
      audio.play("dice");
      const override = window.Rechenwuerfel.diceOverride;
      const allowed = C.allowedFaces(this.settings);
      const face = override
        ? C.DICE_FACES.find((f) => f.key === (typeof override === "function" ? override() : override))
        : allowed[Math.floor(Math.random() * allowed.length)];
      await dice.roll(face);
      $("dice-hint").textContent = face.label;
      if (face.key === "SKIP") {
        audio.play("skip");
        this.showMessage(`💤 ${this.player.name} muss leider aussetzen.`, true);
      } else if (face.key === "JOKER") {
        audio.play("joker");
        this.phase = "joker";
        showPanel("joker");
      } else {
        this.startTask(face.operation);
      }
    },
    startTask(op) {
      this.task = C.generateTask(op, this.settings.numberRange);
      this.answer = "";
      $("question").textContent = this.task.question;
      $("answer").textContent = "_";
      const steps = C.stepsFor(this.settings, op);
      $("task-info").textContent = `${C.OPERATIONS[op].label}: richtig = ${steps} ${steps === 1 ? "Feld" : "Felder"} vor`;
      this.phase = "task";
      showPanel("task");
      this.startTimer(this.settings.secondsPerTask * 1000);
    },
    startTimer(remainingMs) {
      this.stopTimer();
      const total = this.settings.secondsPerTask * 1000;
      const endAt = performance.now() + remainingMs;
      let lastSecond = -1;
      const tick = () => {
        const left = Math.max(0, endAt - performance.now());
        this.timeLeft = left;
        $("time-fill").style.width = `${(left / total) * 100}%`;
        const secs = Math.ceil(left / 1000);
        $("time-left").textContent = `${secs} s`;
        if (secs >= 1 && secs <= 5 && secs !== lastSecond) { lastSecond = secs; audio.play("tick"); }
        if (left <= 0) { this.timer = null; this.onTimeout(); }
        else this.timer = setTimeout(tick, 50);
      };
      tick();
    },
    stopTimer() { if (this.timer) { clearTimeout(this.timer); this.timer = null; } },
    pauseTimer() { if (this.task && this.timer) { this.stopTimer(); this.timerPausedAt = this.timeLeft; } },
    resumeTimer() { if (this.task && !this.timer && this.timerPausedAt != null) { this.startTimer(Math.max(1000, this.timerPausedAt)); this.timerPausedAt = null; } },
    onDigit(d) {
      if (!this.task || this.answer.length >= this.maxDigits) return;
      click();
      if (this.answer === "0") this.answer = "";
      this.answer += d;
      $("answer").textContent = this.answer;
    },
    onBackspace() {
      if (!this.task || !this.answer) return;
      click();
      this.answer = this.answer.slice(0, -1);
      $("answer").textContent = this.answer || "_";
    },
    submit() {
      if (!this.task || !this.answer) return;
      const task = this.task;
      this.stopTimer();
      this.task = null;
      if (parseInt(this.answer, 10) === task.result) this.onCorrect(task);
      else {
        audio.play("wrong");
        this.showMessage(`❌ Leider falsch.\nRichtig wäre: ${task.solution}\nDie Figur bleibt stehen.`, true);
      }
    },
    onTimeout() {
      const task = this.task;
      if (!task) return;
      this.task = null;
      audio.play("timeout");
      this.showMessage(`⏰ Die Zeit ist abgelaufen!\nRichtig wäre: ${task.solution}\nDie Figur bleibt stehen.`, true);
    },
    async onCorrect(task) {
      audio.play("correct");
      const steps = C.stepsFor(this.settings, task.operation);
      const idx = this.current;
      const from = this.players[idx].position;
      const to = C.movePosition(from, steps, this.settings.boardFields);
      this.showMessage(`✅ Richtig! Die Figur zieht ${steps} ${steps === 1 ? "Feld" : "Felder"} vor.`, false);
      await board.animateMove(idx, from, to, () => audio.play("step"));
      this.players[idx].position = to;
      if (to >= this.settings.boardFields - 1) this.onWin(this.players[idx]);
      else $("btn-next").hidden = false;
    },
    showMessage(text, showNext) {
      this.phase = "message";
      $("message").textContent = text;
      $("btn-next").hidden = !showNext;
      showPanel("message");
    },
    nextPlayer() {
      this.busy = false;
      this.current = (this.current + 1) % this.players.length;
      this.updateHeader();
      this.showRoll();
    },
    onWin(winner) {
      this.phase = "win";
      audio.play("win");
      $("winner").textContent = `🏆 ${winner.figure} ${winner.name} hat gewonnen!`;
      $("winner").style.color = winner.color;
      showPanel("win");
    },
    restart() {
      this.stopTimer();
      this.task = null;
      this.busy = false;
      for (const p of this.players) p.position = 0;
      board.setup(this.settings.boardFields, this.players);
      this.current = Math.floor(Math.random() * this.players.length);
      this.updateHeader();
      this.showRoll();
    },
    quit() {
      this.stopTimer();
      this.task = null;
      this.busy = false;
      this.phase = "idle";
      showScreen("menu");
    },
  };

  function updateAudioButtons() {
    $("btn-music").textContent = audio.music ? "🎵" : "🔇";
    $("btn-music").setAttribute("aria-pressed", String(audio.music));
    $("btn-sfx").textContent = audio.sfx ? "🔊" : "🔈";
    $("btn-sfx").setAttribute("aria-pressed", String(audio.sfx));
  }
  $("btn-roll").addEventListener("click", () => game.rollDice());
  $("btn-next").addEventListener("click", () => { click(); game.nextPlayer(); });
  document.querySelectorAll("#panel-joker .key").forEach((b) => b.addEventListener("click", () => { click(); game.startTask(b.dataset.op); }));
  $("keypad").addEventListener("click", (e) => {
    const key = e.target.closest("[data-key]");
    if (!key) return;
    const k = key.dataset.key;
    if (k === "back") game.onBackspace();
    else if (k === "ok") game.submit();
    else game.onDigit(k);
  });
  document.addEventListener("keydown", (e) => {
    if (currentScreen !== "game" || game.phase !== "task") return;
    if (/^[0-9]$/.test(e.key)) { game.onDigit(e.key); e.preventDefault(); }
    else if (e.key === "Backspace") { game.onBackspace(); e.preventDefault(); }
    else if (e.key === "Enter") { game.submit(); e.preventDefault(); }
  });
  $("btn-music").addEventListener("click", () => { audio.setMusic(!audio.music); updateAudioButtons(); });
  $("btn-sfx").addEventListener("click", () => { audio.setSfx(!audio.sfx); updateAudioButtons(); click(); });
  $("btn-again").addEventListener("click", () => { click(); game.restart(); });
  $("btn-menu").addEventListener("click", () => { click(); game.quit(); });
  $("btn-quit").addEventListener("click", () => {
    click();
    if (game.phase === "win" || game.phase === "idle") { game.quit(); return; }
    game.pauseTimer();
    $("dlg-quit").showModal();
  });
  $("btn-quit-no").addEventListener("click", () => { click(); $("dlg-quit").close(); game.resumeTimer(); });
  $("btn-quit-yes").addEventListener("click", () => { click(); $("dlg-quit").close(); game.quit(); });
  $("dlg-quit").addEventListener("cancel", () => game.resumeTimer());
  document.addEventListener("visibilitychange", () => { if (document.hidden) game.pauseTimer(); else game.resumeTimer(); });

  // Öffentliche Schnittstelle (für Tests): Würfel steuern und Zustand auslesen
  window.Rechenwuerfel = {
    diceOverride: null,
    state() {
      return {
        screen: currentScreen,
        phase: game.phase,
        current: game.current,
        positions: game.players.map((p) => p.position),
        names: game.players.map((p) => p.name),
        task: game.task ? { ...game.task } : null,
        settings: game.settings,
        music: audio.music,
        sfx: audio.sfx,
      };
    },
  };
})();
