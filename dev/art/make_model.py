"""Starting geometry for the balloon (M3), as a spec that dev/art/bb_build.js turns into balloon.bbmodel in Blockbench.

Units: 16 per block, y up, origin at the basket floor centre (the entity position). Each cube's material is the name
prefix before '_' (paint.lua paints by material). Box UV offsets are packed here with the same integer dims the game
uses (a fractional size becomes the next integer plus a negative CubeDeformation, see export.py).
Re-running overwrites hand edits in balloon.bbmodel; after editing in Blockbench, run export.py only.
"""
import json
import math
from pathlib import Path

HERE = Path(__file__).parent

# Envelope body: (bottom, top, half-width). Teardrop: cone up to the widest band, flat dome on top.
BODY = [(68, 74, 20), (74, 82, 25), (82, 90, 30), (90, 100, 34), (100, 110, 37), (110, 120, 39), (120, 134, 40),
        (134, 142, 39), (142, 148, 36), (148, 152, 31), (152, 155, 25), (155, 157, 16)]
SKIRT = [(58, 63, 14), (63, 68, 18)]
# Envelope and skirt scaled up about the skirt hem (user, 2026-10-28: "a little bit bigger"), whole units only.
SCALE = 1.2
HEM = 58


# Box envelope prototype (user, 2026-10-28): a bevelled cube instead of the teardrop. Rows are (bottom, top,
# half-width, bevel), already at full size; the skirt stays scaled.
BOX = [(70, 74, 34, 6), (74, 78, 42, 4), (78, 162, 46, 4), (162, 166, 42, 4), (166, 170, 34, 6)]


def boxy(prefix, i, y0, y1, h, b):
    """One box layer with chamfered vertical edges: a core plus four side slabs, no overlaps."""
    c = h - b
    return [
        (f"{prefix}_{i}c", (-c, y0, -c), (c, y1, c)),
        (f"{prefix}_{i}e", (c, y0, -c), (h, y1, c)),
        (f"{prefix}_{i}w", (-h, y0, -c), (-c, y1, c)),
        (f"{prefix}_{i}s", (-c, y0, c), (c, y1, h)),
        (f"{prefix}_{i}n", (-c, y0, -h), (c, y1, -c)),
    ]


def scaled(rows):
    return [(round(HEM + (y0 - HEM) * SCALE), round(HEM + (y1 - HEM) * SCALE), round(h * SCALE)) for y0, y1, h in rows]


def rounded(prefix, i, y0, y1, h):
    """One rounded layer: a square core plus four side slabs. Nothing overlaps, so equal-height tops can't z-fight."""
    r1, r2 = round(0.58 * h), round(0.78 * h)
    return [
        (f"{prefix}_{i}c", (-r2, y0, -r2), (r2, y1, r2)),
        (f"{prefix}_{i}e", (r2, y0, -r1), (h, y1, r1)),
        (f"{prefix}_{i}w", (-h, y0, -r1), (-r2, y1, r1)),
        (f"{prefix}_{i}s", (-r1, y0, r2), (r1, y1, h)),
        (f"{prefix}_{i}n", (-r1, y0, -h), (r1, y1, -r2)),
    ]


def groups():
    basket = [
        ("skid_l", (-10, 0, -14), (-8, 1, 14)), ("skid_r", (8, 0, -14), (10, 1, 14)),
        ("wicker_n", (-14, 1, -14), (14, 14, -12)), ("wicker_s", (-14, 1, 12), (14, 14, 14)),
        ("wicker_w", (-14, 1, -12), (-12, 14, 12)), ("wicker_e", (12, 1, -12), (14, 14, 12)),
        ("floor_0", (-12, 1, -12), (12, 2, 12)),
        ("rim_n", (-15, 14, -15), (15, 16, -12)), ("rim_s", (-15, 14, 12), (15, 16, 15)),
        ("rim_w", (-15, 14, -12), (-12, 16, 12)), ("rim_e", (12, 14, -12), (15, 16, 12)),
    ]
    for n, (sx, sz) in zip("nesw", [(1, -1), (1, 1), (-1, 1), (-1, -1)]):
        x0, z0 = (12, -14)[sx < 0], (12, -14)[sz < 0]
        basket.append((f"upright_{n}", (x0, 16, z0), (x0 + 2, 38, z0 + 2)))
    basket += [
        ("frame_n", (-14, 38, -14), (14, 40, -12)), ("frame_s", (-14, 38, 12), (14, 40, 14)),
        ("frame_w", (-14, 38, -12), (-12, 40, 12)), ("frame_e", (12, 38, -12), (14, 40, 12)),
        ("bar_x", (-12, 38.5, -0.5), (12, 39.5, 0.5)), ("bar_z", (-0.5, 38.5, -12), (0.5, 39.5, 12)),
        ("valve_0", (-2, 35, -2), (2, 37, 2)), ("lever_0", (2, 35.5, -0.5), (6, 36.5, 0.5)),
        ("coil_0", (-4, 37, -4), (4, 43, 4)), ("nozzle_0", (-2, 43, -2), (2, 45, 2)),
    ]
    rigging = [(f"rope_{i}", (sx * 11.5 - 0.5, 40, sz * 11.5 - 0.5), (sx * 11.5 + 0.5, 58, sz * 11.5 + 0.5))
               for i, (sx, sz) in enumerate([(1, -1), (1, 1), (-1, 1), (-1, -1)])]
    for i, (y0, y1, h) in enumerate(scaled(SKIRT)):
        rigging += rounded("skirt", i, y0, y1, h)
    envelope = []
    for i, (y0, y1, h, b) in enumerate(BOX):
        envelope += boxy("env", i, y0, y1, h, b)
    flame = [("flame_outer", (-3, 45, -3), (3, 61, 3)), ("flamecore_0", (-1.5, 45, -1.5), (1.5, 55, 1.5))]
    pilot = [("pilot_0", (-1, 45, -1), (1, 47, 1))]
    return [
        ("basket", (0, 0, 0), basket), ("rigging", (0, 40, 0), rigging), ("envelope", (0, 40, 0), envelope),
        ("flame", (0, 45, 0), flame), ("pilot", (0, 45, 0), pilot),
    ]


def uv_dims(a, b):
    return [max(1, math.ceil(round(b[k] - a[k], 4) - 1e-6)) for k in range(3)]


def pack(cubes, width):
    """Skyline packer over box-UV footprints (2D+2W wide, D+H tall), largest first, lowest fit wins."""
    sized = []
    for c in cubes:
        w, h, d = uv_dims(c["from"], c["to"])
        sized.append((d + h, 2 * d + 2 * w, c))
    sized.sort(key=lambda t: (-t[0] * t[1], -t[0]))
    sky = [0] * width
    for fh, fw, c in sized:
        best = None
        for x in range(0, width - fw + 1):
            y = max(sky[x:x + fw])
            if best is None or y < best[1]:
                best = (x, y)
        x, y = best
        c["uv"] = [x, y]
        for k in range(x, x + fw):
            sky[k] = y + fh
    return max(sky)


def main():
    """Two atlases: 'envelope' (tinted, M5 dyes) and 'main' (everything else), each with its own model layer."""
    spec = {"groups": [], "atlases": {}}
    per_atlas = {"main": [], "envelope": []}
    for name, pivot, cubes in groups():
        atlas = "envelope" if name == "envelope" else "main"
        g = {"name": name, "pivot": list(pivot), "atlas": atlas, "cubes": []}
        for cname, a, b in cubes:
            c = {"name": cname, "from": list(a), "to": list(b)}
            g["cubes"].append(c)
            per_atlas[atlas].append(c)
        spec["groups"].append(g)
    for atlas, cubes in per_atlas.items():
        widest = max(2 * d + 2 * w for w, h, d in (uv_dims(c["from"], c["to"]) for c in cubes))
        best = None
        for width in (64, 128, 256, 512, 1024):
            if width < widest:
                continue
            tall = -(-pack(cubes, width) // 16) * 16
            if tall <= 2 * width and (best is None or width * tall < best[0] * best[1]):
                best = (width, tall)
        height = pack(cubes, best[0])
        spec["atlases"][atlas] = list(best)
        print(atlas, spec["atlases"][atlas], "used height", height, "cubes", len(cubes))
    (HERE / "model_spec.json").write_text(json.dumps(spec, indent=1))


if __name__ == "__main__":
    main()
