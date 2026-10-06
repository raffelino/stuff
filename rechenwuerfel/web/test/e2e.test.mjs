/*
 * Browser-Durchlauf mit Playwright: Menü, Anleitung, Optionen, Konfiguration,
 * komplettes Spiel (alle Würfelseiten, richtig/falsch/Zeit/Joker/Aussetzen, Sieg,
 * Neustart) und zurück ins Menü. Läuft in Chromium ohne sichtbares Fenster.
 */
import { test, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { chromium } from "playwright";
import { createServer } from "./serve.mjs";

let server, browser, baseUrl;

before(async () => {
  server = createServer();
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  baseUrl = `http://127.0.0.1:${server.address().port}/`;
  const options = { headless: true };
  const local = process.env.CHROMIUM_PATH || "/opt/pw-browsers/chromium";
  if (existsSync(local)) options.executablePath = local; // vorinstalliertes Chromium nutzen
  browser = await chromium.launch(options);
});

after(async () => {
  await browser?.close();
  server?.close();
});

let page;
beforeEach(async () => {
  page = await browser.newPage({ viewport: { width: 420, height: 820 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.errors = errors;
  await page.goto(baseUrl + "?fast=1");
  await page.evaluate(() => localStorage.clear());
  await page.reload();
});

const state = () => page.evaluate(() => window.Rechenwuerfel.state());
const setDice = (face) => page.evaluate((f) => { window.Rechenwuerfel.diceOverride = f; }, face);
const visible = (sel) => page.isVisible(sel);

async function waitPhase(phase) {
  await page.waitForFunction((p) => window.Rechenwuerfel.state().phase === p, phase, { timeout: 8000 });
}

async function roll(face) {
  await setDice(face);
  await page.click("#btn-roll");
  await page.waitForFunction(() => window.Rechenwuerfel.state().phase !== "roll", null, { timeout: 8000 });
}

async function solve(offset = 0) {
  const q = await page.textContent("#question");
  const m = q.match(/(\d+)\s*([+−×÷])\s*(\d+)\s*=\s*\?/);
  assert.ok(m, "Aufgabe lesbar: " + q);
  const a = +m[1], b = +m[3];
  const result = { "+": a + b, "−": a - b, "×": a * b, "÷": a / b }[m[2]] + offset;
  for (const ch of String(result)) await page.click(`#keypad [data-key="${ch}"]`);
  assert.equal(await page.textContent("#answer"), String(result));
  await page.click('#keypad [data-key="ok"]');
  return result - offset;
}

async function next() {
  await page.waitForSelector("#btn-next:visible", { timeout: 8000 });
  await page.click("#btn-next");
  await waitPhase("roll");
}

async function startGame({ names = ["Anna", "Ben"], fields = 6, seconds = 5, range = 20, steps = [1, 2, 3, 4] } = {}) {
  await page.click("#btn-play");
  await page.click(`#player-count [data-count="${names.length}"]`);
  for (let i = 0; i < names.length; i++) await page.fill(`#name-${i}`, names[i]);
  await page.fill("#set-range", String(range));
  await page.fill("#set-seconds", String(seconds));
  await page.fill("#set-fields", String(fields));
  await page.fill("#set-steps-plus", String(steps[0]));
  await page.fill("#set-steps-minus", String(steps[1]));
  await page.fill("#set-steps-times", String(steps[2]));
  await page.fill("#set-steps-divide", String(steps[3]));
  await page.click("#btn-start");
  await waitPhase("roll");
}

test("Menü: Anleitung und Optionen", async () => {
  assert.ok(await visible("#btn-play"));
  await page.click("#btn-rules");
  assert.ok(await visible("#dlg-rules"));
  assert.match(await page.textContent("#dlg-rules"), /Joker/);
  await page.click("#btn-rules-ok");
  assert.ok(!(await visible("#dlg-rules")));

  await page.click("#btn-options");
  assert.ok(await page.isChecked("#opt-music"));
  await page.uncheck("#opt-music");
  await page.uncheck("#opt-sfx");
  await page.click("#btn-options-ok");
  let s = await state();
  assert.equal(s.music, false);
  assert.equal(s.sfx, false);
  await page.reload();
  s = await state();
  assert.equal(s.music, false, "Einstellung überlebt Neuladen");
  assert.deepEqual(page.errors, []);
});

test("Setup: Spieleranzahl, Figurentausch, Einstellungen werden gespeichert", async () => {
  await page.click("#btn-play");
  assert.ok(await visible("#screen-setup"));
  assert.ok(await visible('[data-player="0"]'));
  assert.ok(await visible('[data-player="1"]'));
  assert.ok(!(await visible('[data-player="2"]')));
  await page.click('#player-count [data-count="4"]');
  assert.ok(await visible('[data-player="3"]'));

  // Spieler 1 nimmt die Figur von Spieler 2 -> Tausch
  const fig = (p, i) => `[data-player="${p}"] .figure >> nth=${i}`;
  await page.click(fig(0, 1));
  assert.equal(await page.getAttribute(fig(0, 1), "aria-pressed"), "true");
  assert.equal(await page.getAttribute(fig(1, 0), "aria-pressed"), "true");
  await page.click(fig(1, 7));
  assert.equal(await page.getAttribute(fig(1, 7), "aria-pressed"), "true");
  assert.ok((await page.getAttribute(fig(0, 7), "class")).includes("taken"));

  await page.fill("#name-0", "Mia");
  await page.click('.chip[data-set="range"][data-value="50"]');
  await page.click('.chip[data-set="seconds"][data-value="10"]');
  await page.fill("#set-fields", "2");   // zu klein -> Minimum 6
  await page.fill("#set-steps-plus", "");
  await page.click("#btn-start");
  const s = await state();
  assert.equal(s.screen, "game");
  assert.equal(s.settings.numberRange, 50);
  assert.equal(s.settings.secondsPerTask, 10);
  assert.equal(s.settings.boardFields, 6);
  assert.equal(s.settings.stepsPlus, 1);
  assert.deepEqual(s.names, ["Mia", "Spieler 2", "Spieler 3", "Spieler 4"]);
  assert.match(await page.textContent("#turn"), /Mia ist dran/);

  // Nach Neuladen sind die Einstellungen wieder da
  await page.reload();
  await page.click("#btn-play");
  assert.equal(await page.inputValue("#name-0"), "Mia");
  assert.equal(await page.inputValue("#set-range"), "50");
  assert.equal(await page.getAttribute('#player-count [data-count="4"]', "aria-pressed"), "true");
  assert.equal(await page.getAttribute(fig(1, 7), "aria-pressed"), "true");
  assert.deepEqual(page.errors, []);
});

test("Spiel: richtig, falsch, Zeit abgelaufen, Joker, Aussetzen, Tastatur", async () => {
  await startGame({ seconds: 3 });
  assert.match(await page.textContent("#steps-hint"), /\+ 1 · − 2 · × 3 · ÷ 4 Felder/);

  // Plus richtig -> 1 Feld
  await roll("PLUS");
  assert.match(await page.textContent("#task-info"), /Plus: richtig = 1 Feld vor/);
  await solve();
  await next();
  assert.deepEqual((await state()).positions, [1, 0]);
  assert.match(await page.textContent("#turn"), /Ben/);

  // Minus falsch -> bleibt, Lösung wird gezeigt
  await roll("MINUS");
  const solution = await solve(1);
  assert.match(await page.textContent("#message"), new RegExp(`❌[\\s\\S]*= ${solution}`));
  await next();
  assert.deepEqual((await state()).positions, [1, 0]);

  // Mal + Zeit abgelaufen -> bleibt
  await roll("TIMES");
  await page.waitForFunction(() => document.getElementById("time-fill").style.width !== "100%");
  await waitPhase("message");
  assert.match(await page.textContent("#message"), /⏰/);
  await next();
  assert.deepEqual((await state()).positions, [1, 0]);

  // Joker -> Geteilt richtig -> 4 Felder
  await roll("JOKER");
  assert.ok(await visible("#panel-joker"));
  await page.click('#panel-joker [data-op="DIVIDE"]');
  assert.match(await page.textContent("#question"), /÷/);
  await solve();
  await next();
  assert.deepEqual((await state()).positions, [1, 4]);

  // Aussetzen
  await roll("SKIP");
  assert.match(await page.textContent("#message"), /Anna muss leider aussetzen/);
  await next();
  assert.equal((await state()).current, 1);

  // Tastatur: Ziffern, Backspace, Enter, keine führende Null
  await roll("PLUS");
  await page.keyboard.type("0");
  await page.keyboard.type("12");
  assert.equal(await page.textContent("#answer"), "12");
  await page.keyboard.press("Backspace");
  await page.keyboard.press("Backspace");
  assert.equal(await page.textContent("#answer"), "_");
  const q = await page.textContent("#question");
  const m = q.match(/(\d+) \+ (\d+)/);
  await page.keyboard.type(String(+m[1] + +m[2]));
  await page.keyboard.press("Enter");
  // Ben stand auf 4, mit Plus erreicht er Feld 5 = Ziel
  await waitPhase("win");
  assert.match(await page.textContent("#winner"), /Ben hat gewonnen/);
  assert.deepEqual((await state()).positions, [1, 5]);
  assert.deepEqual(page.errors, []);
});

test("Komplettes Spiel mit drei Spielern bis zum Sieg, Neustart und zurück ins Menü", async () => {
  await startGame({ names: ["Mia", "Leo", "Zoe"], fields: 8, steps: [2, 2, 3, 4] });
  await roll("DIVIDE"); await solve(); await next();          // Mia -> 4
  await roll("JOKER"); await page.click('#panel-joker [data-op="TIMES"]'); await solve(); await next(); // Leo -> 3
  await roll("SKIP"); await next();                            // Zoe setzt aus
  assert.deepEqual((await state()).positions, [4, 3, 0]);
  await roll("PLUS"); await solve(); await next();            // Mia -> 6
  await roll("MINUS"); await solve(); await next();           // Leo -> 5
  await roll("TIMES"); await solve(); await next();           // Zoe -> 3
  assert.deepEqual((await state()).positions, [6, 5, 3]);
  await roll("PLUS"); await solve();                          // Mia -> 7 = Ziel
  await waitPhase("win");
  assert.match(await page.textContent("#winner"), /🏆.*Mia hat gewonnen/);
  assert.deepEqual((await state()).positions, [7, 5, 3]);

  await page.click("#btn-again");
  await waitPhase("roll");
  assert.deepEqual((await state()).positions, [0, 0, 0]);

  // Ziel wird nie übersprungen
  await roll("DIVIDE"); await solve(); await next();
  await roll("DIVIDE"); await solve(); await next();
  await roll("DIVIDE"); await solve(); await next();
  const positions = (await state()).positions;
  assert.ok(positions.every((p) => p <= 7));

  await page.click("#btn-quit");
  assert.ok(await visible("#dlg-quit"));
  await page.click("#btn-quit-no");
  assert.equal((await state()).screen, "game");
  await page.click("#btn-quit");
  await page.click("#btn-quit-yes");
  assert.equal((await state()).screen, "menu");
  assert.ok(await visible("#btn-play"));
  assert.deepEqual(page.errors, []);
});

test("Ausgeschlossene Rechenarten kommen nicht vor", async () => {
  await page.click("#btn-play");
  await page.uncheck("#op-MINUS");
  await page.uncheck("#op-TIMES");
  assert.ok(await page.isDisabled("#set-steps-minus"));
  assert.ok(!(await page.isDisabled("#set-steps-plus")));
  await page.fill("#set-fields", "120");
  await page.click("#btn-start");
  await waitPhase("roll");
  assert.deepEqual((await state()).settings.operations, ["PLUS", "DIVIDE"]);
  assert.equal(await page.textContent("#steps-hint"), "+ 1 · ÷ 4 Felder");

  // 25 echte Zufallswürfe: nur erlaubte Seiten; Joker bietet nur erlaubte Rechenarten an
  const seen = new Set();
  for (let i = 0; i < 25; i++) {
    await page.click("#btn-roll");
    await page.waitForFunction(() => window.Rechenwuerfel.state().phase !== "roll", null, { timeout: 8000 });
    const phase = (await state()).phase;
    if (phase === "joker") {
      seen.add("JOKER");
      assert.ok(await visible('#panel-joker [data-op="PLUS"]'));
      assert.ok(await visible('#panel-joker [data-op="DIVIDE"]'));
      assert.ok(!(await visible('#panel-joker [data-op="MINUS"]')));
      assert.ok(!(await visible('#panel-joker [data-op="TIMES"]')));
      await page.click('#panel-joker [data-op="DIVIDE"]');
    }
    if ((await state()).phase === "task") {
      const q = await page.textContent("#question");
      assert.doesNotMatch(q, /[−×]/, "unerlaubte Rechenart: " + q);
      seen.add(q.includes("+") ? "PLUS" : "DIVIDE");
      await solve(1); // absichtlich falsch, damit niemand gewinnt
    } else if (phase === "message") {
      seen.add("SKIP");
    }
    await next();
  }
  assert.ok(seen.size >= 2, "mehrere erlaubte Seiten gesehen: " + [...seen]);
  assert.deepEqual(page.errors, []);
});

test("Ohne Rechenart startet kein Spiel", async () => {
  await page.click("#btn-play");
  for (const op of ["PLUS", "MINUS", "TIMES", "DIVIDE"]) await page.uncheck("#op-" + op);
  assert.ok(await visible("#ops-error"));
  await page.click("#btn-start");
  assert.ok(await visible("#screen-setup"));
  await page.check("#op-TIMES");
  assert.ok(!(await visible("#ops-error")));
  await page.click("#btn-start");
  await waitPhase("roll");
  assert.deepEqual((await state()).settings.operations, ["TIMES"]);
  // Einstellung bleibt erhalten
  await page.reload();
  await page.click("#btn-play");
  assert.ok(!(await page.isChecked("#op-PLUS")));
  assert.ok(await page.isChecked("#op-TIMES"));
  assert.deepEqual(page.errors, []);
});

test("Audio-Schalter im Spiel", async () => {
  await startGame();
  assert.equal(await page.textContent("#btn-music"), "🎵");
  await page.click("#btn-music");
  assert.equal(await page.textContent("#btn-music"), "🔇");
  await page.click("#btn-sfx");
  assert.equal(await page.textContent("#btn-sfx"), "🔈");
  const s = await state();
  assert.equal(s.music, false);
  assert.equal(s.sfx, false);
  assert.deepEqual(page.errors, []);
});
