/*
 * Rechenwürfel – Spiellogik ohne Oberfläche.
 * Läuft im Browser (window.RechenCore) und in Node (module.exports) und ist damit
 * mit `node --test` testbar.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  root.RechenCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  /** Die vier Rechenarten. */
  const OPERATIONS = {
    PLUS: { key: "PLUS", symbol: "+", label: "Plus", color: "#2E7D32" },
    MINUS: { key: "MINUS", symbol: "−", label: "Minus", color: "#C62828" },
    TIMES: { key: "TIMES", symbol: "×", label: "Mal", color: "#1565C0" },
    DIVIDE: { key: "DIVIDE", symbol: "÷", label: "Geteilt", color: "#6A1B9A" },
  };

  /** Die sechs Würfelseiten. */
  const DICE_FACES = [
    { key: "PLUS", symbol: "+", label: "Plus", operation: "PLUS", color: "#2E7D32" },
    { key: "MINUS", symbol: "−", label: "Minus", operation: "MINUS", color: "#C62828" },
    { key: "TIMES", symbol: "×", label: "Mal", operation: "TIMES", color: "#1565C0" },
    { key: "DIVIDE", symbol: "÷", label: "Geteilt", operation: "DIVIDE", color: "#6A1B9A" },
    { key: "JOKER", symbol: "🃏", label: "Joker", operation: null, color: "#EF6C00" },
    { key: "SKIP", symbol: "💤", label: "Aussetzen", operation: null, color: "#616161" },
  ];

  const FIGURES = ["🐸", "🦊", "🐼", "🦁", "🐙", "🦄", "🐢", "🚀", "🐝", "🐬", "🦖", "🤖"];
  const PLAYER_COLORS = ["#E53935", "#1E88E5", "#43A047", "#FB8C00"];

  const DEFAULT_SETTINGS = Object.freeze({
    numberRange: 100,
    secondsPerTask: 30,
    boardFields: 30,
    stepsPlus: 1,
    stepsMinus: 2,
    stepsTimes: 3,
    stepsDivide: 4,
  });

  const LIMITS = Object.freeze({
    numberRange: [5, 10000],
    secondsPerTask: [3, 600],
    boardFields: [6, 120],
    stepsPlus: [1, 20],
    stepsMinus: [1, 20],
    stepsTimes: [1, 20],
    stepsDivide: [1, 20],
  });

  function clamp(v, lo, hi) {
    return Math.min(hi, Math.max(lo, v));
  }

  /** Begrenzt alle Einstellungen auf sinnvolle Bereiche; ungültige Werte werden durch Standardwerte ersetzt. */
  function sanitizeSettings(input) {
    const out = {};
    for (const key of Object.keys(DEFAULT_SETTINGS)) {
      const raw = input && input[key];
      const n = typeof raw === "number" ? raw : parseInt(raw, 10);
      const value = Number.isFinite(n) ? n : DEFAULT_SETTINGS[key];
      const [lo, hi] = LIMITS[key];
      out[key] = clamp(Math.round(value), lo, hi);
    }
    return out;
  }

  function stepsFor(settings, operationKey) {
    switch (operationKey) {
      case "PLUS": return settings.stepsPlus;
      case "MINUS": return settings.stepsMinus;
      case "TIMES": return settings.stepsTimes;
      case "DIVIDE": return settings.stepsDivide;
      default: throw new Error("Unbekannte Rechenart: " + operationKey);
    }
  }

  /** Zufallszahl in [lo, hi] (beide inklusive). */
  function randInt(rng, lo, hi) {
    return lo + Math.floor(rng() * (hi - lo + 1));
  }

  function factorLimit(n) {
    if (n < 10) return Math.max(2, n);
    if (n <= 100) return 10;
    if (n <= 400) return 12;
    return 20;
  }

  /**
   * Erzeugt eine Aufgabe im Zahlenraum 0..range. Minus wird nie negativ,
   * Geteilt hat nie einen Rest.
   */
  function generateTask(operationKey, range, rng) {
    rng = rng || Math.random;
    const n = Math.max(range, 2);
    let a, b, result;
    switch (operationKey) {
      case "PLUS":
        a = randInt(rng, 1, n - 1);
        b = randInt(rng, 1, n - a);
        result = a + b;
        break;
      case "MINUS":
        a = randInt(rng, 2, n);
        b = randInt(rng, 1, a - 1);
        result = a - b;
        break;
      case "TIMES": {
        const maxFactor = factorLimit(n);
        a = randInt(rng, 1, maxFactor);
        b = randInt(rng, 1, Math.max(1, Math.min(maxFactor, Math.floor(n / a))));
        result = a * b;
        break;
      }
      case "DIVIDE": {
        const maxFactor = factorLimit(n);
        b = randInt(rng, 1, maxFactor);
        result = randInt(rng, 1, Math.max(1, Math.min(maxFactor, Math.floor(n / b))));
        a = b * result;
        break;
      }
      default:
        throw new Error("Unbekannte Rechenart: " + operationKey);
    }
    const op = OPERATIONS[operationKey];
    return {
      a, b, result,
      operation: operationKey,
      question: `${a} ${op.symbol} ${b} = ?`,
      solution: `${a} ${op.symbol} ${b} = ${result}`,
    };
  }

  /** Neue Position nach einem Zug, nie über das Ziel hinaus. */
  function movePosition(position, steps, fields) {
    return Math.min(position + steps, fields - 1);
  }

  /**
   * Schlangenpfad-Layout des Spielbretts. Feld 0 liegt unten links,
   * die Reihen wechseln die Richtung.
   */
  function boardLayout(fields, width, height) {
    const ideal = Math.round(Math.sqrt((fields * width) / height));
    const cols = Math.min(Math.max(3, Math.min(12, ideal)), fields);
    const rows = Math.ceil(fields / cols);
    const cell = Math.min(width / cols, height / rows);
    const offX = (width - cols * cell) / 2;
    const offY = (height - rows * cell) / 2;
    return { cols, rows, cell, offX, offY, fields };
  }

  function cellCenter(layout, index) {
    const r = Math.floor(index / layout.cols);
    const c0 = index % layout.cols;
    const c = r % 2 === 0 ? c0 : layout.cols - 1 - c0;
    const row = layout.rows - 1 - r;
    return {
      x: layout.offX + (c + 0.5) * layout.cell,
      y: layout.offY + (row + 0.5) * layout.cell,
    };
  }

  /** Punkt auf dem Pfad zwischen zwei Feldern (für die Zug-Animation). */
  function pathPoint(layout, pos) {
    const i = clamp(Math.floor(pos), 0, layout.fields - 1);
    const j = Math.min(i + 1, layout.fields - 1);
    const t = clamp(pos - i, 0, 1);
    const a = cellCenter(layout, i);
    const b = cellCenter(layout, j);
    return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
  }

  /** Erzeugt die Spielerliste aus Namen und Figuren. */
  function createPlayers(names, figures) {
    return names.map((name, i) => ({
      name: (name || "").trim() || `Spieler ${i + 1}`,
      figure: figures[i] || FIGURES[i],
      color: PLAYER_COLORS[i % PLAYER_COLORS.length],
      position: 0,
    }));
  }

  return {
    OPERATIONS, DICE_FACES, FIGURES, PLAYER_COLORS, DEFAULT_SETTINGS, LIMITS,
    sanitizeSettings, stepsFor, generateTask, movePosition,
    boardLayout, cellCenter, pathPoint, createPlayers, randInt,
  };
});
