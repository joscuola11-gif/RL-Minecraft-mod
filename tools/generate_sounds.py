#!/usr/bin/env python3
"""
Generates the placeholder sounds of the mod (original synthesis, no samples from any game) and
encodes them as mono OGG Vorbis into src/main/resources/assets/rlfennec/sounds/fennec/.

    python3 tools/generate_sounds.py

Needs numpy and ffmpeg (with libvorbis).

To use your own recordings instead, just overwrite the .ogg files with the same names
(mono, 44.1 kHz; engine_loop / boost_loop must loop seamlessly).
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rlfennec/sounds/fennec"
RNG = np.random.default_rng(1234)


def t_axis(seconds):
    return np.arange(int(round(seconds * SR))) / SR


def periodic_noise(seconds, shape):
    """Noise that is exactly periodic over `seconds` (so the loop has no seam), spectrum = shape(freq_hz)."""
    n = int(round(seconds * SR))
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    mag = shape(freqs)
    phase = RNG.uniform(0, 2 * np.pi, len(freqs))
    spec = mag * np.exp(1j * phase)
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / np.max(np.abs(x))


def normalise(x, peak=0.89):
    return x / np.max(np.abs(x)) * peak


def fade(x, fade_in=0.004, fade_out=0.02):
    x = x.copy()
    a = int(fade_in * SR)
    b = int(fade_out * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def lowpass_sweep(noise, f_start, f_end):
    """Cheap one-pole lowpass with a sweeping cutoff (for whooshes)."""
    n = len(noise)
    fc = np.geomspace(f_start, f_end, n)
    alpha = 1 - np.exp(-2 * np.pi * fc / SR)
    out = np.empty(n)
    y = 0.0
    for i in range(n):
        y += alpha[i] * (noise[i] - y)
        out[i] = y
    return out


def engine_loop():
    """2.0 s loop. 70 Hz firing fundamental with a turbine whine on top.  All components are periodic in 2 s."""
    dur = 2.0
    t = t_axis(dur)
    f0 = 70.0  # 140 cycles in 2 s
    sig = np.zeros_like(t)
    for k in range(1, 16):
        amp = 1.0 / k ** 0.95
        # slow, loop-periodic detune of every harmonic gives the "alive" feeling
        wob = 0.18 * np.sin(2 * np.pi * (0.5 * (1 + k % 3)) * t + k)
        sig += amp * np.sin(2 * np.pi * f0 * k * t + wob + 0.6 * k)
    # cylinder pulses: 35 Hz amplitude pulsation
    sig *= 0.72 + 0.28 * np.cos(2 * np.pi * 35.0 * t)
    # turbine whine (6th harmonic, slightly frequency modulated once per loop)
    whine = 0.22 * np.sin(2 * np.pi * 420.0 * t + 1.8 * np.sin(2 * np.pi * 0.5 * t))
    whine += 0.10 * np.sin(2 * np.pi * 840.0 * t + 0.9 * np.sin(2 * np.pi * 1.0 * t))
    # air / intake noise
    air = periodic_noise(dur, lambda f: 1.0 / (1.0 + (f / 900.0) ** 2) * (f > 40)) * 0.18
    x = sig / 3.0 + whine + air
    x = np.tanh(1.6 * x)  # a bit of grit
    return normalise(x, 0.85)


def boost_loop():
    """1.6 s loop of a gas flame / rocket roar."""
    dur = 1.6
    t = t_axis(dur)
    body = periodic_noise(dur, lambda f: np.where(f < 80, f / 80.0, 1.0) / (1.0 + (f / 1400.0) ** 1.4))
    hiss = periodic_noise(dur, lambda f: (f > 2500) * 1.0 / (1.0 + (f / 7000.0) ** 2)) * 0.35
    rumble = np.sin(2 * np.pi * 55.0 * t) * 0.35 + np.sin(2 * np.pi * 110.0 * t + 1.0) * 0.15
    # flame flutter, every LFO has a whole number of cycles in 1.6 s
    flutter = 1.0 + 0.22 * np.sin(2 * np.pi * 12.5 * t) + 0.12 * np.sin(2 * np.pi * 25.0 * t + 2.0) + 0.1 * np.sin(2 * np.pi * 6.25 * t + 4.0)
    x = (body * 0.9 + hiss + rumble) * flutter
    x = np.tanh(1.3 * x)
    return normalise(x, 0.85)


def boost_start():
    """0.55 s ignition: low thump + noise burst that opens up."""
    dur = 0.55
    t = t_axis(dur)
    sweep = 150.0 * np.exp(-t * 7.0) + 38.0
    phase = 2 * np.pi * np.cumsum(sweep) / SR
    thump = np.sin(phase) * np.exp(-t * 9.0)
    burst = lowpass_sweep(RNG.uniform(-1, 1, len(t)), 300, 6500)
    env = np.minimum(t / 0.012, 1.0) * np.exp(-t * 4.5)
    x = thump * 0.9 + burst * env * 1.3
    return fade(normalise(np.tanh(1.2 * x), 0.9), 0.0015, 0.05)


def jump():
    """0.32 s suspension thunk + tiny whoosh."""
    dur = 0.32
    t = t_axis(dur)
    sweep = 110.0 * np.exp(-t * 14.0) + 48.0
    phase = 2 * np.pi * np.cumsum(sweep) / SR
    thunk = np.sin(phase) * np.exp(-t * 16.0)
    click = lowpass_sweep(RNG.uniform(-1, 1, len(t)), 3000, 400) * np.exp(-t * 55.0) * 0.6
    air = lowpass_sweep(RNG.uniform(-1, 1, len(t)), 700, 2500) * np.sin(np.pi * np.clip(t / dur, 0, 1)) * 0.25
    x = thunk + click + air
    return fade(normalise(x, 0.9), 0.001, 0.04)


def flip():
    """0.5 s whoosh with a rising then falling tone, for the dodge flips."""
    dur = 0.5
    t = t_axis(dur)
    env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
    air = lowpass_sweep(RNG.uniform(-1, 1, len(t)), 500, 5500) * env
    f = 250.0 + 650.0 * np.sin(np.pi * np.clip(t / dur, 0, 1))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env * 0.25
    thump = np.sin(2 * np.pi * 70.0 * t) * np.exp(-t * 18.0) * 0.5
    x = air + tone + thump
    return fade(normalise(x, 0.85), 0.004, 0.06)


def write_ogg(name, samples, loop=False):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = np.clip(samples * 32767, -32768, 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        dest = OUT / f"{name}.ogg"
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1", "-ar", str(SR), "-c:a", "libvorbis", "-q:a", "4", str(dest)],
            check=True)
    # for a seamless loop the step from the last to the first sample must look like any other step
    seam = abs(float(samples[0]) - float(samples[-1])) / float(np.mean(np.abs(np.diff(samples))))
    print(f"{name:12s} {len(samples) / SR:5.2f}s  {dest.stat().st_size / 1024:6.1f} KiB" + (f"  loop seam step = {seam:.2f} x the average step" if loop else ""))


if __name__ == "__main__":
    # the engine sounds come from a real recording, see prepare_engine_sound.py
    write_ogg("boost_loop", boost_loop(), loop=True)
    write_ogg("boost_start", boost_start())
    write_ogg("jump", jump())
    write_ogg("flip", flip())
