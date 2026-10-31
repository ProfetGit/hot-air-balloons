#!/usr/bin/env python3
"""Old vs new item icon next to vanilla items at 1x, 2x and 8x on a vanilla slot grey. Usage: compare_strip.py out.png new.png"""
import sys
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
OLD = HERE.parents[2] / "src/main/resources/assets/hotairballoons/textures/item/hot_air_balloon.png"
VAN = Path.home() / ".cache/mcsrc/26.3/res/assets/minecraft/textures/item"
out, new = Path(sys.argv[1]), Path(sys.argv[2])
items = [("old", OLD), ("new", new)] + [(n, VAN / f"{n}.png") for n in ("oak_boat", "minecart", "saddle")]
SLOT = (139, 139, 139, 255)
PAD = 2
scales = [1, 2, 8]
rowh = [16 * s + 2 * PAD for s in scales]
cellw = [16 * s + 2 * PAD for s in scales]
W = max(cellw) * len(items)
H = sum(rowh)
sheet = Image.new("RGBA", (W, H), (198, 198, 198, 255))
y = 0
for s, rh, cw in zip(scales, rowh, cellw):
    for i, (_, p) in enumerate(items):
        im = Image.open(p).convert("RGBA")
        if im.size != (16, 16):
            im = im.resize((16, 16), Image.NEAREST)
        im = im.resize((16 * s, 16 * s), Image.NEAREST)
        x = i * max(cellw)
        sheet.paste(Image.new("RGBA", (16 * s + 2 * PAD, 16 * s + 2 * PAD), SLOT), (x, y))
        sheet.alpha_composite(im, (x + PAD, y + PAD))
    y += rh
sheet.save(out)
print(out, sheet.size, [n for n, _ in items])
