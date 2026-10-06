import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const core = require("../core.js");

/** Deterministischer Zufallsgenerator (Mulberry32). */
function seeded(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const RANGES = [5, 10, 20, 50, 100, 200, 1000];

test("Plus bleibt im Zahlenraum", () => {
  const rng = seeded(1);
  for (const range of RANGES) for (let i = 0; i < 500; i++) {
    const t = core.generateTask("PLUS", range, rng);
    assert.equal(t.result, t.a + t.b);
    assert.ok(t.a >= 1 && t.b >= 1 && t.result <= range, JSON.stringify(t));
  }
});

test("Minus wird nie negativ", () => {
  const rng = seeded(2);
  for (const range of RANGES) for (let i = 0; i < 500; i++) {
    const t = core.generateTask("MINUS", range, rng);
    assert.equal(t.result, t.a - t.b);
    assert.ok(t.a <= range && t.b >= 1 && t.result >= 1, JSON.stringify(t));
  }
});

test("Mal bleibt im Zahlenraum", () => {
  const rng = seeded(3);
  for (const range of RANGES) for (let i = 0; i < 500; i++) {
    const t = core.generateTask("TIMES", range, rng);
    assert.equal(t.result, t.a * t.b);
    assert.ok(t.a >= 1 && t.b >= 1 && t.result <= range, JSON.stringify(t));
  }
});

test("Geteilt hat keinen Rest", () => {
  const rng = seeded(4);
  for (const range of RANGES) for (let i = 0; i < 500; i++) {
    const t = core.generateTask("DIVIDE", range, rng);
    assert.equal(t.a % t.b, 0);
    assert.equal(t.result, t.a / t.b);
    assert.ok(t.a <= range && t.b >= 1 && t.result >= 1, JSON.stringify(t));
  }
});

test("Fragetext und Lösung", () => {
  const t = core.generateTask("DIVIDE", 20, () => 0.999);
  assert.match(t.question, /^\d+ ÷ \d+ = \?$/);
  assert.equal(t.solution, `${t.a} ÷ ${t.b} = ${t.result}`);
  assert.throws(() => core.generateTask("MODULO", 10));
});

test("Einstellungen werden begrenzt und ergänzt", () => {
  const s = core.sanitizeSettings({ numberRange: "", secondsPerTask: 99999, boardFields: 1, stepsPlus: 0, stepsDivide: "7" });
  assert.equal(s.numberRange, 100);
  assert.equal(s.secondsPerTask, 600);
  assert.equal(s.boardFields, 6);
  assert.equal(s.stepsPlus, 1);
  assert.equal(s.stepsMinus, 2);
  assert.equal(s.stepsDivide, 7);
  assert.equal(core.stepsFor(s, "DIVIDE"), 7);
  assert.deepEqual(core.sanitizeSettings(undefined), core.DEFAULT_SETTINGS);
});

test("Rechenarten lassen sich ausschließen", () => {
  const s = core.sanitizeSettings({ operations: ["DIVIDE", "PLUS", "MODULO"] });
  assert.deepEqual(s.operations, ["PLUS", "DIVIDE"]);
  assert.ok(core.isEnabled(s, "PLUS") && !core.isEnabled(s, "MINUS"));
  assert.deepEqual(core.allowedFaces(s).map((f) => f.key), ["PLUS", "DIVIDE", "JOKER", "SKIP"]);
  // Ohne Rechenart werden alle aktiviert
  assert.deepEqual(core.sanitizeSettings({ operations: [] }).operations, core.OPERATION_KEYS);
  assert.deepEqual(core.sanitizeSettings({}).operations, core.OPERATION_KEYS);
  assert.equal(core.allowedFaces(core.sanitizeSettings({})).length, 6);
});

test("Zug endet spätestens im Ziel", () => {
  assert.equal(core.movePosition(0, 4, 30), 4);
  assert.equal(core.movePosition(27, 4, 30), 29);
  assert.equal(core.movePosition(29, 1, 30), 29);
});

test("Brett: Schlangenpfad beginnt unten links und wechselt die Richtung", () => {
  const layout = core.boardLayout(30, 600, 500);
  assert.ok(layout.cols >= 3 && layout.cols <= 12);
  assert.equal(layout.rows, Math.ceil(30 / layout.cols));
  const first = core.cellCenter(layout, 0);
  const second = core.cellCenter(layout, 1);
  const rowStart = core.cellCenter(layout, layout.cols);
  assert.ok(second.x > first.x, "erste Reihe läuft nach rechts");
  assert.equal(second.y, first.y);
  assert.ok(rowStart.y < first.y, "zweite Reihe liegt darüber");
  assert.equal(Math.round(rowStart.x), Math.round(core.cellCenter(layout, layout.cols - 1).x), "zweite Reihe beginnt rechts");
  // Alle Felder liegen innerhalb der Fläche
  for (let i = 0; i < 30; i++) {
    const c = core.cellCenter(layout, i);
    assert.ok(c.x > 0 && c.x < 600 && c.y > 0 && c.y < 500);
  }
  const mid = core.pathPoint(layout, 0.5);
  assert.equal(mid.x, (first.x + second.x) / 2);
  assert.deepEqual(core.pathPoint(layout, 29.7), core.cellCenter(layout, 29));
});

test("Brett funktioniert für kleine und große Felderzahlen", () => {
  for (const fields of [6, 12, 30, 60, 120]) {
    const layout = core.boardLayout(fields, 400, 300);
    assert.ok(layout.cols * layout.rows >= fields);
    assert.ok(layout.cell > 0);
  }
});

test("Spieler bekommen Standardnamen, Figuren und Farben", () => {
  const p = core.createPlayers(["Anna", "  ", "Ben"], ["🐸", undefined, "🦊"]);
  assert.equal(p[1].name, "Spieler 2");
  assert.equal(p[1].figure, core.FIGURES[1]);
  assert.equal(p[2].figure, "🦊");
  assert.equal(p[0].color, core.PLAYER_COLORS[0]);
  assert.ok(p.every((x) => x.position === 0));
});

test("Würfel hat sechs Seiten mit vier Rechenarten, Joker und Aussetzen", () => {
  assert.equal(core.DICE_FACES.length, 6);
  const keys = core.DICE_FACES.map((f) => f.key);
  assert.deepEqual(keys, ["PLUS", "MINUS", "TIMES", "DIVIDE", "JOKER", "SKIP"]);
  assert.equal(core.DICE_FACES.filter((f) => f.operation).length, 4);
});
