#!/usr/bin/env python3
"""
Draws the pitch of the arena: mown grass stripes, the team colours at the two ends, the centre line and circle, the
goal zones and the edge of the flat floor. 6 pixels per block, mapped on the flat part of the floor of the arena
(x in [-CORE_X, CORE_X], z in [-CORE_Z, CORE_Z], the same numbers as com.rlfennec.physics.Arena).

    python3 tools/generate_arena_textures.py

Needs numpy + Pillow.
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rlfennec/textures/arena/floor.png"

# keep these in sync with Arena.java
HALF_WIDTH, HALF_LENGTH, CORNER_CUT, RADIUS = 82.0, 102.0, 23.0, 8.0
CORE_X = HALF_WIDTH - RADIUS
CORE_Z = HALF_LENGTH - RADIUS
CORE_DIAGONAL = (HALF_WIDTH + HALF_LENGTH - CORNER_CUT) - RADIUS * math.sqrt(2.0)

PPB = 6  # pixels per block
W = int(2 * CORE_X * PPB)
H = int(2 * CORE_Z * PPB)


def px(x, z):
    return ((x + CORE_X) * PPB, (z + CORE_Z) * PPB)


def main():
    zs = (np.arange(H) + 0.5) / PPB - CORE_Z
    xs = (np.arange(W) + 0.5) / PPB - CORE_X
    zz, xx = np.meshgrid(zs, xs, indexing="ij")

    # mown stripes across the pitch, every 12 blocks, with a little noise so that it is not flat
    stripe = ((np.floor((zz + CORE_Z) / 12.0).astype(int)) % 2).astype(float)
    base_a = np.array([20, 74, 34], float)
    base_b = np.array([27, 90, 43], float)
    img = base_a[None, None, :] * (1 - stripe[..., None]) + base_b[None, None, :] * stripe[..., None]
    rng = np.random.default_rng(7)
    noise = rng.normal(0.0, 3.0, (H, W, 1))
    img = img + noise

    # the team colours at the ends (blue at -z, orange at +z), strongest at the goal lines
    blue = np.array([30, 80, 190], float)
    orange = np.array([210, 100, 25], float)
    t_blue = np.clip((-zz - 34.0) / (CORE_Z - 34.0), 0.0, 1.0) ** 1.3 * 0.55
    t_orange = np.clip((zz - 34.0) / (CORE_Z - 34.0), 0.0, 1.0) ** 1.3 * 0.55
    img = img * (1 - t_blue[..., None]) + blue * t_blue[..., None]
    img = img * (1 - t_orange[..., None]) + orange * t_orange[..., None]

    pic = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(pic)
    line = (232, 240, 244)
    w = max(2, int(0.55 * PPB))

    # centre line, circle and spot
    d.line([px(-CORE_X, 0), px(CORE_X, 0)], fill=line, width=w)
    r = 20.0
    d.ellipse([px(-r, -r), px(r, r)], outline=line, width=w)
    d.ellipse([px(-0.9, -0.9), px(0.9, 0.9)], fill=line)

    # goal zones: a rectangle in front of each back wall
    for end in (-1, 1):
        z0, z1 = end * 76.0, end * CORE_Z
        d.rectangle([px(-36.0, min(z0, z1)), px(36.0, max(z0, z1))], outline=line, width=w)
        d.ellipse([px(-0.7, end * 60.0 - 0.7), px(0.7, end * 60.0 + 0.7)], fill=line)

    # the edge of the flat floor, where the ramps start (the octagon of the core, a little inside)
    dz0 = CORE_DIAGONAL - CORE_X
    dx0 = CORE_DIAGONAL - CORE_Z
    pts = [(CORE_X, -dz0), (CORE_X, dz0), (dx0, CORE_Z), (-dx0, CORE_Z), (-CORE_X, dz0), (-CORE_X, -dz0), (-dx0, -CORE_Z), (dx0, -CORE_Z)]
    inset = 0.7
    poly = [px(math.copysign(abs(x) - inset if abs(x) > inset else x, x), math.copysign(abs(z) - inset if abs(z) > inset else z, z)) for x, z in pts]
    d.line(poly + [poly[0]], fill=(120, 200, 255), width=max(2, int(0.45 * PPB)))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    pic.save(OUT, optimize=True)
    print(f"wrote {OUT} ({W}x{H}, {OUT.stat().st_size / 1024:.0f} KiB)")


if __name__ == "__main__":
    main()
