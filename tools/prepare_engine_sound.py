#!/usr/bin/env python3
"""
Cuts a single recording of a running engine into the three engine sounds of the mod:

    engine_start.ogg  the engine firing up      (played once when somebody gets in)
    engine_loop.ogg   steady running, seamless   (looped while driving, pitch follows the speed)
    engine_stop.ogg   the engine winding down    (played once when the driver gets out)

    python3 tools/prepare_engine_sound.py path/to/engine.mp3

The cut points were chosen from the spectrogram of the supplied 22.8 s recording (start-up in the first 1.6 s,
steady between 15.9 s and 20.3 s, wind-down from 20.6 s). Change START_END / LOOP / STOP_START for another file.
Needs numpy and ffmpeg (libvorbis).
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rlfennec/sounds/fennec"

START_END = 1.55          # seconds: the start-up is 0 .. START_END
LOOP = (15.90, 20.30)     # seconds: steady running, looped
LOOP_CROSSFADE = 0.60     # seconds blended between the end and the beginning of the loop
STOP_START = 20.55        # seconds: the wind-down is STOP_START .. end


def load(path):
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "in.wav"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-c:a", "pcm_s16le", str(wav)], check=True)
        with wave.open(str(wav)) as w:
            return np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(np.float64) / 32768.0


def seg(x, a, b):
    return x[int(a * SR):int(b * SR)].copy()


def fade(x, fade_in, fade_out):
    x = x.copy()
    a, b = int(fade_in * SR), int(fade_out * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def seamless(x, crossfade):
    """Blend the last `crossfade` seconds into the first ones, so that the end flows into the start."""
    n = int(crossfade * SR)
    t = np.linspace(0, np.pi / 2, n)
    head, tail = x[:n], x[-n:]
    blended = tail * np.cos(t) + head * np.sin(t)       # equal-power crossfade
    return np.concatenate([x[n:-n], blended])


def write_ogg(name, x, gain):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = np.clip(x * gain * 32767, -32768, 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        dest = OUT / f"{name}.ogg"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1", "-ar", str(SR), "-c:a", "libvorbis", "-q:a", "5", str(dest)], check=True)
    print(f"{name:13s} {len(x) / SR:5.2f}s  {dest.stat().st_size / 1024:6.1f} KiB")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    x = load(sys.argv[1])
    start = fade(seg(x, 0.0, START_END), 0.004, 0.05)
    loop = seamless(seg(x, *LOOP), LOOP_CROSSFADE)
    stop = fade(seg(x, STOP_START, len(x) / SR), 0.01, 0.25)
    # the same gain for all three so that they keep their relative loudness
    gain = 0.89 / max(np.max(np.abs(a)) for a in (start, loop, stop))
    write_ogg("engine_start", start, gain)
    write_ogg("engine_loop", loop, gain)
    write_ogg("engine_stop", stop, gain)
    # how well does the loop close? (a step at the seam should look like any other step)
    seam = abs(loop[0] - loop[-1]) / np.mean(np.abs(np.diff(loop)))
    print(f"loop seam step = {seam:.2f} x the average step")


if __name__ == "__main__":
    main()
