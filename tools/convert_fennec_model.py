#!/usr/bin/env python3
"""
Converts the Fennec OBJ (as exported from the Rocket League car model) into the
compact binary mesh the mod loads at runtime:

    src/main/resources/assets/rlfennec/models/entity/fennec.bin

The OBJ has 58 851 vertices / 84 823 triangles split into one object per
part.  Parts are exported as separate groups so that the four wheels can be
spun / steered independently of the body.

Usage:
    python3 tools/convert_fennec_model.py path/to/fennec_-_rocket_league_car.obj

Binary layout (little endian):
    int32   magic      0x434E4E46 ("FNNC")
    int32   version    1
    float32 scale      scale that was baked into the vertices (informational)
    int32   groupCount
    group * groupCount:
        uint16  nameLength, utf8 name
        float32 pivotX, pivotY, pivotZ    (wheel groups are stored relative to this)
        int32   vertexCount
        int32   indexCount
        float32 vertices[vertexCount * 8]  (x y z  nx ny nz  u v)
        uint16  indices[indexCount]

Model space of the result: +X = forward, +Y = up, +Z = right, 1 unit = 1 block,
origin = middle between the axles at ground level.
"""
import struct
import sys
from pathlib import Path

import numpy as np

# 1 model unit (a "Rocket League unit") -> blocks.  140 uu long -> 3.5 blocks.
SCALE = 0.025
# Point of the original model that becomes the entity origin (axle mid-point, ground).
ORIGIN = np.array([2.8, -1.45, 0.0])

# OBJ object name -> (output group name)
GROUPS = {
    "Fennec_Fennec_Chassis_0": "chassis",
    "Fennec_Headlights_0": "headlights",
    "Fennec_Fennec_-_Body_0": "body",
    "Fennec_Window_0": "window",
    "Fennec_Paint_0": "paint",
    "Alpha_-_FR_(Fennec)_Alpha_Rim_0": "wheel_fr_rim",
    "Alpha_-_FR_(Fennec)_Dieci_Tread_0": "wheel_fr_tread",
    "Alpha_-_FL_(Fennec)_Alpha_Rim_0": "wheel_fl_rim",
    "Alpha_-_FL_(Fennec)_Dieci_Tread_0": "wheel_fl_tread",
    "Alpha_-_BR_(Fennec)_Alpha_Rim_0": "wheel_br_rim",
    "Alpha_-_BR_(Fennec)_Dieci_Tread_0": "wheel_br_tread",
    "Alpha_-_BL_(Fennec)_Alpha_Rim_0": "wheel_bl_rim",
    "Alpha_-_BL_(Fennec)_Dieci_Tread_0": "wheel_bl_tread",
}
ORDER = list(GROUPS.values())


def parse_obj(path):
    verts, uvs, norms = [], [], []
    objs = {}
    cur = None
    with open(path) as f:
        for line in f:
            if line.startswith("v "):
                verts.append([float(x) for x in line.split()[1:4]])
            elif line.startswith("vt "):
                uvs.append([float(x) for x in line.split()[1:3]])
            elif line.startswith("vn "):
                norms.append([float(x) for x in line.split()[1:4]])
            elif line.startswith("o "):
                cur = objs.setdefault(line[2:].strip(), [])
            elif line.startswith("f "):
                toks = line.split()[1:]
                assert len(toks) == 3, "only triangles are supported"
                tri = []
                for t in toks:
                    v, vt, vn = (int(i) - 1 for i in t.split("/"))
                    assert v == vt == vn, "expected unified indices"
                    tri.append(v)
                cur.append(tri)
    return np.array(verts), np.array(uvs), np.array(norms), objs


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    V, UV, N, objs = parse_obj(sys.argv[1])
    out_groups = {}
    for obj_name, group in GROUPS.items():
        tris = np.array(objs[obj_name], dtype=np.int64)
        used, inverse = np.unique(tris.ravel(), return_inverse=True)
        p = V[used]
        n = N[used]
        uv = UV[used].copy()
        uv[:, 1] = 1.0 - uv[:, 1]  # OBJ has v up, Minecraft has v down
        pivot = np.zeros(3)
        out_groups[group] = [p, n, uv, inverse.reshape(-1, 3), pivot]

    # wheel pivots: centre of the tread bounding box (rim shares the same pivot)
    for w in ("fr", "fl", "br", "bl"):
        tread = out_groups[f"wheel_{w}_tread"][0]
        centre = (tread.min(0) + tread.max(0)) / 2.0
        for part in ("rim", "tread"):
            out_groups[f"wheel_{w}_{part}"][4] = centre

    out = bytearray()
    out += struct.pack("<iifi", 0x434E4E46, 1, SCALE, len(ORDER))
    for name in ORDER:
        p, n, uv, tris, pivot = out_groups[name]
        if name.startswith("wheel_"):
            pos = (p - pivot) * SCALE
            pivot_out = (pivot - ORIGIN) * SCALE
        else:
            pos = (p - ORIGIN) * SCALE
            pivot_out = np.zeros(3)
        nrm = n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
        vtx = np.concatenate([pos, nrm, uv], axis=1).astype("<f4")
        idx = tris.astype("<u2")
        assert vtx.shape[0] < 65536
        nb = name.encode()
        out += struct.pack("<H", len(nb)) + nb
        out += struct.pack("<fff", *pivot_out)
        out += struct.pack("<ii", vtx.shape[0], idx.size)
        out += vtx.tobytes()
        out += idx.tobytes()
        print(f"{name:16s} verts={vtx.shape[0]:6d} tris={idx.shape[0]:6d} pivot={np.round(pivot_out, 3)}")

    dest = Path(__file__).resolve().parent.parent / "src/main/resources/assets/rlfennec/models/entity/fennec.bin"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(bytes(out))
    print(f"wrote {dest} ({len(out) / 1e6:.2f} MB)")


if __name__ == "__main__":
    main()
