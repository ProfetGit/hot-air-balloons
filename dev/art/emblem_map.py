"""layout.json -> textures/entity/hot_air_balloon_emblem_map.png: where a banner goes on the envelope.

Each envelope texel on the outermost front (+z) or back (-z) face inside the emblem rectangle gets R = banner flag
column (0..19), G = flag row (0..39), A = 255; the back is mirrored so both read correctly from outside. The client
(EmblemTextures) turns this map plus a banner pattern texture into a tinted envelope layer, so no vanilla texture
ships in the jar. The emblem is the banner's own 20x40 pixels at one texel per model unit, centred on the belly.
"""
import json
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
OUT = HERE.parent.parent / "src/main/resources/assets/hotairballoons/textures/entity/hot_air_balloon_emblem_map.png"
W, H = 20, 40
Y_TOP = 141
# Step faces at least this far out (the emblem rows' front faces sit at depth 37..40).
STEP_DEPTH = 37


def main():
    d = json.loads((HERE / "layout.json").read_text())
    aw, ah = d["atlases"]["envelope"]
    best = {}
    for f in d["faces"]:
        if f["atlas"] != "envelope" or f["n"][0] != 0 or f["n"][1] != 0:
            continue
        side = f["n"][2]
        x0, y0, x1, y1 = f["rect"]
        for j in range(y0, y1):
            for i in range(x0, x1):
                a, b = i - x0 + 0.5, j - y0 + 0.5
                p = [f["o"][k] + a * f["du"][k] + b * f["dv"][k] for k in range(3)]
                key = (side, round(p[0] - 0.5), round(p[1] - 0.5))
                depth = p[2] * side
                if key not in best or depth > best[key][0]:
                    best[key] = (depth, i, j)
    # The short horizontal faces where the layers step out cross the emblem as dark lines unless they carry it too:
    # each takes the flag row next to its plane.
    steps = []
    for f in d["faces"]:
        if f["atlas"] != "envelope" or f["n"][1] == 0:
            continue
        x0, y0, x1, y1 = f["rect"]
        for j in range(y0, y1):
            for i in range(x0, x1):
                a, b = i - x0 + 0.5, j - y0 + 0.5
                p = [f["o"][k] + a * f["du"][k] + b * f["dv"][k] for k in range(3)]
                side = 1 if p[2] > 0 else -1
                if p[2] * side >= STEP_DEPTH:
                    steps.append((side, int(p[0] // 1), int((p[1] - 0.5) // 1), i, j))
    img = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    px = img.load()
    hits = {1: 0, -1: 0}
    for (side, x, y), (_, i, j) in best.items():
        u = x + W // 2 if side > 0 else W // 2 - 1 - x
        v = Y_TOP - 1 - y
        if 0 <= u < W and 0 <= v < H:
            px[i, j] = (u, v, 0, 255)
            hits[side] += 1
    for side, x, y, i, j in steps:
        u = x + W // 2 if side > 0 else W // 2 - 1 - x
        v = Y_TOP - 1 - y
        if 0 <= u < W and 0 <= v < H and px[i, j][3] == 0:
            px[i, j] = (u, v, 0, 255)
    img.save(OUT)
    print(f"{OUT.name}: front {hits[1]} / back {hits[-1]} of {W * H} texels each")


if __name__ == "__main__":
    main()
