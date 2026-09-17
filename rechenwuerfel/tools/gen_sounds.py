#!/usr/bin/env python3
"""
Erzeugt alle Soundeffekte und die Hintergrundmusik als WAV-Dateien in app/src/main/res/raw.
Rein synthetisch (Chiptune-Stil), keine externen Abhängigkeiten.

Aufruf:  python3 tools/gen_sounds.py
"""
import math
import os
import random
import struct
import wave
from array import array

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
SR_FX = 22050
SR_MUSIC = 16000


# ----------------------------------------------------------------- Helfer

def note_freq(midi):
    return 440.0 * 2 ** ((midi - 69) / 12.0)


NOTE_NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name):
    """'C4' -> 60, 'F#5' -> 78, 'Bb3' -> 58"""
    n = NOTE_NAMES[name[0]]
    rest = name[1:]
    if rest.startswith("#"):
        n += 1
        rest = rest[1:]
    elif rest.startswith("b"):
        n -= 1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + n


def osc(kind, phase):
    """phase in [0,1)"""
    if kind == "sine":
        return math.sin(2 * math.pi * phase)
    if kind == "square":
        return 1.0 if phase < 0.5 else -1.0
    if kind == "pulse":
        return 1.0 if phase < 0.25 else -1.0
    if kind == "tri":
        return 4 * abs(phase - 0.5) - 1
    if kind == "saw":
        return 2 * phase - 1
    raise ValueError(kind)


class Buffer:
    def __init__(self, seconds, sr):
        self.sr = sr
        self.n = int(seconds * sr)
        self.data = [0.0] * self.n

    def add_tone(self, start, dur, freq, kind="sine", vol=0.5, attack=0.005, release=0.05,
                 decay=0.0, sustain=1.0, freq_end=None, vibrato=0.0):
        s0 = int(start * self.sr)
        n = int(dur * self.sr)
        a = max(1, int(attack * self.sr))
        r = max(1, int(release * self.sr))
        d = int(decay * self.sr)
        phase = 0.0
        for i in range(n):
            idx = s0 + i
            if idx >= self.n:
                break
            t = i / self.sr
            f = freq if freq_end is None else freq + (freq_end - freq) * (i / max(1, n))
            if vibrato:
                f *= 1 + vibrato * math.sin(2 * math.pi * 6 * t)
            phase = (phase + f / self.sr) % 1.0
            env = 1.0
            if i < a:
                env = i / a
            elif d and i < a + d:
                env = 1 - (1 - sustain) * ((i - a) / d)
            elif d:
                env = sustain
            if n - i < r:
                env *= (n - i) / r
            self.data[idx] += osc(kind, phase) * vol * env

    def add_noise(self, start, dur, vol=0.5, attack=0.001, decay_to=0.0, lowpass=0.0):
        s0 = int(start * self.sr)
        n = int(dur * self.sr)
        a = max(1, int(attack * self.sr))
        last = 0.0
        for i in range(n):
            idx = s0 + i
            if idx >= self.n:
                break
            x = random.uniform(-1, 1)
            if lowpass:
                last = last + lowpass * (x - last)
                x = last
            env = (i / a) if i < a else (1 - (1 - decay_to) * ((i - a) / max(1, n - a)))
            self.data[idx] += x * vol * env

    def write(self, name):
        peak = max(1e-6, max(abs(v) for v in self.data))
        scale = 0.92 / peak if peak > 0.92 else 1.0
        samples = array("h", (int(max(-1, min(1, v * scale)) * 32767) for v in self.data))
        path = os.path.join(OUT, name)
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(self.sr)
            w.writeframes(samples.tobytes())
        print(f"{name:20s} {os.path.getsize(path) / 1024:7.1f} KB")


# ----------------------------------------------------------------- Effekte

def sfx_dice():
    b = Buffer(0.9, SR_FX)
    t = 0.0
    random.seed(1)
    for i in range(9):
        b.add_noise(t, 0.05, vol=0.7 - i * 0.05, attack=0.002, lowpass=0.35)
        b.add_tone(t, 0.04, random.choice([900, 1200, 1500, 1800]), "tri", vol=0.25, release=0.02)
        t += 0.06 + i * 0.02
    b.write("sfx_dice.wav")


def sfx_correct():
    b = Buffer(0.8, SR_FX)
    for i, n in enumerate(["C5", "E5", "G5", "C6"]):
        b.add_tone(i * 0.09, 0.35 if i == 3 else 0.14, note_freq(midi(n)), "pulse", vol=0.35, release=0.06)
        b.add_tone(i * 0.09, 0.35 if i == 3 else 0.14, note_freq(midi(n)), "sine", vol=0.35, release=0.06)
    b.write("sfx_correct.wav")


def sfx_wrong():
    b = Buffer(0.7, SR_FX)
    b.add_tone(0.0, 0.25, 220, "saw", vol=0.4, freq_end=180, release=0.03)
    b.add_tone(0.28, 0.4, 170, "saw", vol=0.4, freq_end=120, release=0.1)
    b.write("sfx_wrong.wav")


def sfx_step():
    b = Buffer(0.12, SR_FX)
    b.add_tone(0.0, 0.08, 880, "sine", vol=0.5, freq_end=1100, release=0.04)
    b.write("sfx_step.wav")


def sfx_timeout():
    b = Buffer(1.0, SR_FX)
    b.add_tone(0.0, 0.5, 330, "square", vol=0.25, freq_end=200, release=0.05)
    b.add_tone(0.0, 0.5, 331, "tri", vol=0.3, freq_end=201, release=0.05)
    b.add_tone(0.55, 0.4, 160, "saw", vol=0.35, freq_end=90, release=0.15)
    b.write("sfx_timeout.wav")


def sfx_win():
    b = Buffer(2.4, SR_FX)
    seq = [("C5", 0.15), ("C5", 0.15), ("C5", 0.15), ("E5", 0.45), ("D5", 0.15), ("D5", 0.15), ("D5", 0.15), ("G5", 0.45),
           ("E5", 0.15), ("G5", 0.15), ("C6", 0.6)]
    t = 0.0
    for n, d in seq:
        f = note_freq(midi(n))
        b.add_tone(t, d, f, "pulse", vol=0.3, release=0.04)
        b.add_tone(t, d, f / 2, "tri", vol=0.3, release=0.04)
        t += d + 0.02
    b.write("sfx_win.wav")


def sfx_joker():
    b = Buffer(0.9, SR_FX)
    for i, n in enumerate(["C6", "E6", "G6", "C7", "E7"]):
        b.add_tone(i * 0.07, 0.25, note_freq(midi(n)), "sine", vol=0.3, release=0.15, vibrato=0.01)
    b.add_tone(0.35, 0.5, note_freq(midi("G6")), "tri", vol=0.2, release=0.3, vibrato=0.02)
    b.write("sfx_joker.wav")


def sfx_skip():
    b = Buffer(0.9, SR_FX)
    b.add_tone(0.0, 0.8, 500, "tri", vol=0.4, freq_end=180, release=0.2, vibrato=0.03)
    b.write("sfx_skip.wav")


def sfx_tick():
    b = Buffer(0.08, SR_FX)
    b.add_tone(0.0, 0.05, 1500, "square", vol=0.25, release=0.03)
    b.write("sfx_tick.wav")


def sfx_click():
    b = Buffer(0.06, SR_FX)
    b.add_tone(0.0, 0.04, 700, "sine", vol=0.4, release=0.02)
    b.write("sfx_click.wav")


# ----------------------------------------------------------------- Musik

def music_loop():
    bpm = 132
    beat = 60.0 / bpm
    eighth = beat / 2
    bars = 16
    total = bars * 4 * beat
    b = Buffer(total, SR_MUSIC)

    # Melodie: pro Takt 8 Achtel, "N:len" = Note mit Länge in Achteln, "R" = Pause
    melody = [
        "E5 G5 E5 C5 D5 E5 D5:2",
        "D5 F5 D5 B4 G4:2 B4:2",
        "C5 E5 C5 A4 B4 C5 B4:2",
        "A4 C5 A4 F4 G4:2 A4:2",
        "E5 G5 E5 C5 G5:2 E5:2",
        "D5 F5 D5 B4 D5:2 G5:2",
        "A5 G5 F5 E5 D5 C5 D5:2",
        "B4 C5 D5 E5 D5:4",
        "C5 E5 G5 C6 B5:2 G5:2",
        "B4 D5 G5 B5 A5:2 G5:2",
        "A4 C5 E5 A5 G5:2 E5:2",
        "F5 E5 D5 C5 A4:2 C5:2",
        "E5 G5 C6 G5 E5:2 C5:2",
        "D5 G5 B5 G5 D5:2 B4:2",
        "A4 C5 F5 A5 G5 F5 E5 D5",
        "D5:2 B4:2 C5:4",
    ]
    chords = ["C", "G", "Am", "F", "C", "G", "F", "G", "C", "G", "Am", "F", "C", "G", "F", "G"]
    roots = {"C": "C3", "G": "G2", "Am": "A2", "F": "F2"}
    thirds = {"C": "E3", "G": "B2", "Am": "C3", "F": "A2"}

    for bar, line in enumerate(melody):
        t = bar * 4 * beat
        for tok in line.split():
            if ":" in tok:
                n, l = tok.split(":")
                l = int(l)
            else:
                n, l = tok, 1
            dur = l * eighth
            if n != "R":
                f = note_freq(midi(n))
                b.add_tone(t, dur * 0.92, f, "pulse", vol=0.16, attack=0.01, release=0.03, vibrato=0.004)
                b.add_tone(t, dur * 0.92, f, "tri", vol=0.12, attack=0.01, release=0.03)
            t += dur

    # Bass: Grundton / Oktave / Quinte / Terz
    for bar, ch in enumerate(chords):
        t0 = bar * 4 * beat
        root = midi(roots[ch])
        pattern = [root, root + 12, root + 7, midi(thirds[ch])]
        for i, m in enumerate(pattern):
            b.add_tone(t0 + i * beat, beat * 0.8, note_freq(m), "tri", vol=0.28, attack=0.005, release=0.05)
            b.add_tone(t0 + i * beat, beat * 0.8, note_freq(m), "square", vol=0.06, attack=0.005, release=0.05)

    # Schlagzeug: Kick auf 1 und 3, Snare auf 2 und 4, Hi-Hat auf jedes Achtel
    random.seed(7)
    for bar in range(bars):
        t0 = bar * 4 * beat
        for i in range(4):
            t = t0 + i * beat
            if i % 2 == 0:
                b.add_tone(t, 0.12, 150, "sine", vol=0.5, freq_end=45, release=0.05)
            else:
                b.add_noise(t, 0.09, vol=0.22, decay_to=0.0, lowpass=0.6)
        for i in range(8):
            b.add_noise(t0 + i * eighth, 0.025, vol=0.09 if i % 2 == 0 else 0.06, decay_to=0.0, lowpass=0.9)

    b.write("music_loop.wav")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    sfx_dice()
    sfx_correct()
    sfx_wrong()
    sfx_step()
    sfx_timeout()
    sfx_win()
    sfx_joker()
    sfx_skip()
    sfx_tick()
    sfx_click()
    music_loop()
    print("fertig")
