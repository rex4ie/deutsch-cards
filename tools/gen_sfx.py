"""Synthesise the app's sound effects (soft wooden/bell tones) into res/raw as .ogg.

Usage: python tools/gen_sfx.py   (needs numpy and ffmpeg)
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def bell(freq, dur, amp=1.0, decay=7.0, attack=0.004):
    t = np.arange(int(SR * dur)) / SR
    # a few inharmonic partials give a warm glockenspiel/marimba-like colour
    parts = [(1.0, 1.0), (2.0, 0.35), (3.01, 0.15), (4.2, 0.06)]
    y = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-decay * m * 0.55 * t) for m, a in parts)
    y *= np.minimum(1, t / attack)
    return amp * y / max(1e-9, np.abs(y).max())

def place(parts):
    n = max(int(SR * s) + len(y) for s, y in parts)
    out = np.zeros(n)
    for s, y in parts:
        i = int(SR * s); out[i:i + len(y)] += y
    return out

def thud(f0, f1, dur):
    t = np.arange(int(SR * dur)) / SR
    f = f0 + (f1 - f0) * (t / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.sin(ph) + 0.25 * np.sin(2 * ph)
    return y * np.exp(-9 * t) * np.minimum(1, t / 0.006)

sounds = {
    "sfx_correct": place([(0, bell(659.25, .45, .8)), (.075, bell(987.77, .55, .7))]),          # E5 -> B5
    "sfx_wrong": thud(196, 140, .28) * .9,                                                      # soft low drop
    "sfx_done": place([(i * .11, bell(f, .9, .75, decay=5)) for i, f in
                       enumerate([523.25, 659.25, 783.99, 1046.5])]),                           # C major arpeggio
}

os.makedirs(OUT, exist_ok=True)
for name, y in sounds.items():
    y = y / max(1e-9, np.abs(y).max()) * 0.6
    y[-int(SR * .02):] *= np.linspace(1, 0, int(SR * .02))
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes((y * 32767).astype(np.int16).tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3",
                        os.path.join(OUT, name + ".ogg")], check=True)
print("done")
