"""layout.json -> textures/entity/hot_air_balloon_emblem_map.png: the emblem field on each side of the envelope.

Each envelope texel on the outermost face of a side inside the field (the centre panel between the stripes, see
paint.lua BOUNDS, over the flat main face) gets R = column from the viewer's left (0..FIELD_W-1), G = row from the
top (0..FIELD_H-1), A = 255, oriented to read correctly from outside that side, on all four sides. The client
(EmblemTextures) crops a banner to its design, scales it by a whole number to fill the field and centres it, so the
logo fits the field whatever the pattern; no vanilla texture ships in the jar.
"""
import json
import math
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
OUT = HERE.parent.parent / "src/main/resources/assets/hotairballoons/textures/entity/hot_air_balloon_emblem_map.png"
# The field: x -23..23 (paint.lua BOUNDS centre panel), y 92..176 (the flat main face). Must match EmblemTextures.
FIELD_X0, FIELD_W = -23, 46
Y_TOP, FIELD_H = 176, 84
# (axis, sign): the faces looking along +z, -z, +x, -x. "right" is the viewer's right on that side, as (axis, sign).
SIDES = [((2, 1), (0, 1)), ((2, -1), (0, -1)), ((0, 1), (2, -1)), ((0, -1), (2, 1))]
# Horizontal step faces at least this far out carry the emblem too (none on the box's flat main faces).
STEP_DEPTH = 40


def main():
    d = json.loads((HERE / "layout.json").read_text())
    aw, ah = d["atlases"]["envelope"]
    best = {}
    steps = []
    for f in d["faces"]:
        if f["atlas"] != "envelope":
            continue
        n = f["n"]
        x0, y0, x1, y1 = f["rect"]
        for j in range(y0, y1):
            for i in range(x0, x1):
                a, b = i - x0 + 0.5, j - y0 + 0.5
                p = [f["o"][k] + a * f["du"][k] + b * f["dv"][k] for k in range(3)]
                if n[1] == 0:
                    for side, right in SIDES:
                        ax, sg = side
                        if n[ax] * sg <= 0 or n[2 - ax] != 0:
                            continue
                        key = (side, math.floor(p[right[0]] * right[1]), math.floor(p[1]))
                        depth = p[ax] * sg
                        if key not in best or depth > best[key][0]:
                            best[key] = (depth, i, j)
                else:
                    ax = 2 if abs(p[2]) >= abs(p[0]) else 0
                    sg = 1 if p[ax] > 0 else -1
                    if p[ax] * sg >= STEP_DEPTH:
                        side, right = next(s for s in SIDES if s[0] == (ax, sg))
                        steps.append((side, math.floor(p[right[0]] * right[1]), math.floor(p[1] - 0.5), i, j))
    img = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    px = img.load()
    hits = {}
    for (side, c, y), (_, i, j) in best.items():
        u, v = c - FIELD_X0, Y_TOP - 1 - y
        if 0 <= u < FIELD_W and 0 <= v < FIELD_H:
            px[i, j] = (u, v, 0, 255)
            hits[side] = hits.get(side, 0) + 1
    for side, c, y, i, j in steps:
        u, v = c - FIELD_X0, Y_TOP - 1 - y
        if 0 <= u < FIELD_W and 0 <= v < FIELD_H and px[i, j][3] == 0:
            px[i, j] = (u, v, 0, 255)
    img.save(OUT)
    print(f"{OUT.name}: " + ", ".join(f"{s}: {hits.get(s, 0)}" for s, _ in SIDES) + f" of {FIELD_W * FIELD_H} texels each")


if __name__ == "__main__":
    main()
