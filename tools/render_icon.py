#!/usr/bin/env python3
"""
Tiny software rasteriser that renders the baked Fennec mesh (fennec.bin) with its
textures and writes the inventory icon of the spawn item.

    python3 tools/render_icon.py [azimuth_deg] [out.png]

Only needs numpy + Pillow.
"""
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/rlfennec"

BODY = np.array([0.0, 0.40, 0.91])
DARK = np.array([0.07, 0.07, 0.07])
GLASS = np.array([0.03, 0.04, 0.06])
WHITE = np.array([1.0, 1.0, 1.0])


def load_mesh(path):
    data = path.read_bytes()
    magic, version, scale, count = struct.unpack_from("<iifi", data, 0)
    assert magic == 0x434E4E46 and version == 1
    off = 16
    groups = {}
    for _ in range(count):
        (nl,) = struct.unpack_from("<H", data, off); off += 2
        name = data[off:off + nl].decode(); off += nl
        pivot = np.array(struct.unpack_from("<fff", data, off)); off += 12
        vc, ic = struct.unpack_from("<ii", data, off); off += 8
        verts = np.frombuffer(data, "<f4", vc * 8, off).reshape(vc, 8); off += vc * 32
        idx = np.frombuffer(data, "<u2", ic, off).reshape(-1, 3); off += ic * 2
        groups[name] = (pivot, verts, idx)
    return groups


def material(name):
    """-> (texture name or None, tint, emissive)"""
    if name == "chassis":
        return "fennec_chassis", WHITE, False
    if name == "headlights":
        return None, np.array([1.0, 0.92, 0.94]), True
    if name == "body":
        return None, BODY, False
    if name == "window":
        return None, GLASS, False
    if name == "paint":
        return None, DARK, False
    if name.endswith("_rim"):
        return "alpha_rim", WHITE, False
    return None, DARK, False


def main():
    az = float(sys.argv[1]) if len(sys.argv) > 1 else 35.0
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else ASSETS / "textures/item/fennec.png"
    groups = load_mesh(ASSETS / "models/entity/fennec.bin")
    tex = {n: np.asarray(Image.open(ASSETS / f"textures/entity/{n}.png").convert("RGB"), dtype=np.float32) / 255.0
           for n in ("fennec_chassis", "alpha_rim")}

    S = 1024  # supersampled canvas
    # camera: looking at the car from front-right, slightly above
    a = np.radians(az)
    elev = np.radians(20.0)
    fwd = np.array([-np.cos(a) * np.cos(elev), -np.sin(elev), -np.sin(a) * np.cos(elev)])  # view direction
    right = np.cross(fwd, np.array([0, 1.0, 0])); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    light = np.array([0.35, 0.9, -0.3]); light /= np.linalg.norm(light)
    light2 = np.array([-0.4, 0.5, 0.8]); light2 /= np.linalg.norm(light2)

    zbuf = np.full((S, S), -1e9)
    img = np.zeros((S, S, 4))
    scale = S / 4.6
    cx, cy = S / 2, S / 2 + 20
    centre = np.array([0.0, 0.55, 0.0])

    for name, (pivot, verts, idx) in groups.items():
        tname, tint, emissive = material(name)
        pos = verts[:, :3].astype(np.float64) + pivot
        nrm = verts[:, 3:6].astype(np.float64)
        uv = verts[:, 6:8].astype(np.float64)
        rel = pos - centre
        sx = cx + (rel @ right) * scale
        sy = cy - (rel @ up) * scale
        sz = rel @ fwd * -1.0  # larger = closer to camera
        shade = np.clip(nrm @ light, 0, 1) * 0.65 + np.clip(nrm @ light2, 0, 1) * 0.25 + 0.35
        if emissive:
            shade[:] = 1.0
        T = tex.get(tname)
        for tri in idx:
            xs, ys, zs = sx[tri], sy[tri], sz[tri]
            x0, x1 = int(max(xs.min(), 0)), int(min(xs.max() + 1, S - 1))
            y0, y1 = int(max(ys.min(), 0)), int(min(ys.max() + 1, S - 1))
            if x1 <= x0 or y1 <= y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            px, py = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((ys[1] - ys[2]) * (px - xs[2]) + (xs[2] - xs[1]) * (py - ys[2])) / den
            l1 = ((ys[2] - ys[0]) * (px - xs[2]) + (xs[0] - xs[2]) * (py - ys[2])) / den
            l2 = 1 - l0 - l1
            m = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
            if not m.any():
                continue
            z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            m &= z > sub
            if not m.any():
                continue
            sub[m] = z[m]
            if T is not None:
                u = l0 * uv[tri[0], 0] + l1 * uv[tri[1], 0] + l2 * uv[tri[2], 0]
                v = l0 * uv[tri[0], 1] + l1 * uv[tri[1], 1] + l2 * uv[tri[2], 1]
                ti = np.clip((u[m] * T.shape[1]).astype(int), 0, T.shape[1] - 1)
                tj = np.clip((v[m] * T.shape[0]).astype(int), 0, T.shape[0] - 1)
                col = T[tj, ti] * tint
            else:
                col = np.broadcast_to(tint, (int(m.sum()), 3))
            sh = l0 * shade[tri[0]] + l1 * shade[tri[1]] + l2 * shade[tri[2]]
            rgb = np.clip(col * sh[m][:, None], 0, 1)
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[m, :3] = rgb
            region[m, 3] = 1.0

    im = Image.fromarray((img * 255).astype(np.uint8), "RGBA")
    # crop to content, pad to a square, downsample
    bbox = im.getchannel("A").getbbox()
    im = im.crop(bbox)
    side = max(im.size) + 16
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    canvas = canvas.resize((128, 128), Image.LANCZOS)
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
