#!/usr/bin/env python3
"""Synthesizes Void Web's interface sounds (stereo, 44.1 kHz) with numpy only. The synth helpers are
VOID Portal's (void-workshop/remote/sounds/make_sounds.py), by way of Void Browse, so the apps sound like one family.

  web_open.ogg      app opening: the fox's flame whips round the globe, then a bright open-fifth bloom
  tab_open.ogg      a new tab: a small rising swirl and one bell
  tab_close.ogg     a tab closed: it spirals down into the void
  done_chime.ogg    a download finished: three bright bells and sparkle dust

Usage: python3 art/make_sounds.py app/src/main/res/raw   (writes .ogg via ffmpeg; the .wav files are removed)
"""
import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100
rng = np.random.default_rng(7)


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack, decay_tau):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay_tau)


def bell(freq, sec, decay=0.6, bright=1.0, detune=0.0):
    """FM bell: inharmonic partials that ring and fade, with a little detune for width."""
    t = t_axis(sec)
    f = freq * (1 + detune)
    mod = bright * 2.2 * np.exp(-t / (decay * 0.35)) * np.sin(2 * np.pi * f * 3.5 * t)
    tone = np.sin(2 * np.pi * f * t + mod)
    tone += 0.35 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / (decay * 0.4))
    tone += 0.18 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t / (decay * 0.2))
    return tone * env(len(t), 0.004, decay)


def sweep(f0, f1, sec, curve=2.0):
    """Exponential glide with a soft tremble."""
    t = t_axis(sec)
    x = (t / sec) ** curve
    f = f0 * (f1 / f0) ** x
    phase = 2 * np.pi * np.cumsum(f) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 6.5 * t)
    return np.sin(phase * vib) + 0.3 * np.sin(2 * phase * vib + 0.5)


def sparkle(sec, density=60, lo=3500, hi=9000, decay=0.05):
    """Twinkling dust: tiny random high pings."""
    n = int(SR * sec)
    out = np.zeros((n, 2))
    for _ in range(int(density * sec)):
        start = int(rng.uniform(0, n - SR * 0.2))
        f = rng.uniform(lo, hi)
        ping = bell(f, 0.2, decay=decay, bright=0.4)
        pan = rng.uniform(0.1, 0.9)
        end = min(n, start + len(ping))
        out[start:end, 0] += ping[: end - start] * (1 - pan)
        out[start:end, 1] += ping[: end - start] * pan
    return out * 0.12


def whoosh(sec, f_start, f_end):
    """Noise through a moving band-pass (an octave below the swept cutoff up to it): air, no rumble."""
    n = int(SR * sec)
    noise = rng.standard_normal((n, 2))
    cut = np.geomspace(f_start, f_end, n)
    hi = 1 - np.exp(-2 * np.pi * cut / SR)
    lo = 1 - np.exp(-2 * np.pi * cut / 4 / SR)
    y = np.zeros_like(noise)
    a = np.zeros(2)
    b = np.zeros(2)
    for i in range(n):
        a += hi[i] * (noise[i] - a)
        b += lo[i] * (noise[i] - b)
        y[i] = a - b
    return y * np.hanning(n)[:, None] * 0.6


def highpass(x, fc=180.0, passes=2):
    """One-pole high-pass, applied twice: phone speakers cannot play lows, so keep them out."""
    alpha = np.exp(-2 * np.pi * fc / SR)
    for _ in range(passes):
        y = np.zeros_like(x)
        prev_x = np.zeros(x.shape[1])
        prev_y = np.zeros(x.shape[1])
        for i in range(len(x)):
            prev_y = alpha * (prev_y + x[i] - prev_x)
            prev_x = x[i]
            y[i] = prev_y
        x = y
    return x


def reverb(x, sec=1.6, mix=0.35, damp=4000):
    """Convolution with decaying, darkened stereo noise: a wide cosmic space."""
    n = int(SR * sec)
    t = np.arange(n) / SR
    ir = rng.standard_normal((n, 2)) * np.exp(-t / (sec / 5))[:, None]
    alpha = 1 - np.exp(-2 * np.pi * damp / SR)
    for ch in range(2):
        acc = 0.0
        col = ir[:, ch]
        for i in range(n):
            acc += alpha * (col[i] - acc)
            col[i] = acc
    ir /= np.sqrt((ir**2).sum(axis=0))
    out_len = len(x) + n - 1
    size = 1 << (out_len - 1).bit_length()
    wet = np.stack([np.fft.irfft(np.fft.rfft(x[:, c], size) * np.fft.rfft(ir[:, c], size), size)[:out_len] for c in range(2)], axis=1)
    dry = np.vstack([x, np.zeros((n - 1, 2))])
    return dry * (1 - mix) + wet * mix * 3.0


def place(buf, sig, at, pan=0.5, gain=1.0):
    """Mix a mono or stereo signal into buf at `at` seconds."""
    i = int(at * SR)
    if sig.ndim == 1:
        sig = np.stack([sig * (1 - pan) * 2 ** 0.5 * 0.7, sig * pan * 2 ** 0.5 * 0.7], axis=1)
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[: end - i] * gain


def finish(x, fade=0.25, floor_db=-40):
    x = highpass(x)
    x = np.tanh(x * 1.2) / np.tanh(1.2)  # soft saturation glues it together
    x = x / np.max(np.abs(x)) * 0.89  # about -1 dBFS
    # Notifications should be short: cut the tail once it falls below floor_db, then fade.
    blk = int(SR * 0.02)
    level = [np.sqrt(np.mean(x[i : i + blk] ** 2)) for i in range(0, len(x), blk)]
    last = max(i for i, v in enumerate(level) if 20 * np.log10(v + 1e-9) > floor_db)
    x = x[: min(len(x), (last + 1) * blk + int(SR * fade))].copy()
    n = min(len(x), int(SR * fade))
    x[-n:] *= np.linspace(1, 0, n)[:, None] ** 2
    return x


def web_open():
    """The flame circles the globe (a whoosh that pans all the way round), then the world lights up."""
    buf = np.zeros((int(SR * 1.1), 2))
    t = t_axis(0.5)
    air = whoosh(0.5, 300, 7000)
    pan = 0.5 + 0.45 * np.sin(2 * np.pi * 1.0 * t / 0.5 - np.pi / 2)  # one full lap, left to right and back
    place(buf, np.stack([air[:, 0] * (1 - pan) * 1.4, air[:, 1] * pan * 1.4], axis=1), 0.0, gain=0.7)
    place(buf, sweep(180, 1100, 0.45) * env(int(SR * 0.45), 0.05, 0.5), 0.0, pan=0.35, gain=0.28)
    place(buf, sweep(182, 1112, 0.45) * env(int(SR * 0.45), 0.05, 0.5), 0.0, pan=0.65, gain=0.28)
    for k, f in enumerate([523.25, 783.99, 1046.5, 1567.98]):  # C, G, C, G: an open, bright bloom
        place(buf, bell(f, 0.7, decay=0.32, bright=0.7), 0.42 + k * 0.035, pan=0.2 + 0.6 * k / 3, gain=0.3)
    place(buf, sparkle(0.5, density=45), 0.45)
    return finish(reverb(buf, 1.1, mix=0.33), fade=0.2)


def tab_open():
    buf = np.zeros((int(SR * 0.6), 2))
    place(buf, sweep(400, 1600, 0.16) * env(int(SR * 0.16), 0.01, 0.12), 0.0, pan=0.45, gain=0.25)
    place(buf, whoosh(0.18, 1500, 8000), 0.0, gain=0.3)
    place(buf, bell(1318.5, 0.4, decay=0.14, bright=0.6), 0.12, pan=0.55, gain=0.3)  # E6
    return finish(reverb(buf, 0.7, mix=0.25), fade=0.12)


def void_vanish():
    """A falling swirl: the sweep wobbles like something circling a drain, the air falls with it."""
    buf = np.zeros((int(SR * 0.9), 2))
    t = t_axis(0.55)
    wob = 1 + 0.06 * np.sin(2 * np.pi * (14 - 8 * t / 0.55) * t)
    f = 1500 * (130 / 1500) ** ((t / 0.55) ** 0.7) * wob
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = (np.sin(ph) + 0.25 * np.sin(2 * ph)) * env(len(t), 0.005, 0.32)
    pan = 0.5 + 0.35 * np.sin(2 * np.pi * 3.0 * t)  # it circles as it falls
    place(buf, np.stack([s * (1 - pan), s * pan], axis=1) * 0.9, 0.0)
    place(buf, whoosh(0.5, 5000, 200), 0.0, gain=0.55)
    place(buf, bell(1567.98, 0.3, decay=0.08, bright=0.5), 0.0, pan=0.5, gain=0.18)  # a glint as it goes
    return finish(reverb(buf, 1.2, mix=0.42), fade=0.25)


def done_chime():
    buf = np.zeros((int(SR * 0.9), 2))
    place(buf, whoosh(0.3, 800, 7000), 0.0, gain=0.4)
    for k, f in enumerate([880.0, 1318.5, 1760.0]):  # A5 E6 A6
        place(buf, bell(f, 0.6, decay=0.26, bright=0.9), 0.03 + k * 0.06, pan=0.3 + 0.2 * k, gain=0.4)
    place(buf, sparkle(0.45, density=45, lo=4000, hi=10000), 0.1)
    return finish(reverb(buf, 0.9, mix=0.3), fade=0.15)


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for name, fn in [("web_open", web_open), ("tab_open", tab_open), ("tab_close", void_vanish), ("done_chime", done_chime)]:
        wav = os.path.join(out, f"{name}.wav")
        write_wav(wav, fn())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "6", os.path.join(out, f"{name}.ogg")], check=True)
        os.remove(wav)
        print(name, "ok")


if __name__ == "__main__":
    main()
