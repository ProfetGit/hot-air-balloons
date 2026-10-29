#!/usr/bin/env python3
"""Icon copy of the envelope texture: the mod's grey base + stripes masks tinted like the default look (white base, red
stripes; DyeColor texture diffuse colours, multiplied like the renderer's tint) -> dev/icon/sprites/tex_envelope.png.
--smooth also removes the per-texel noise (median brightness among same-layer neighbours, then 6 tones per layer): it
can't be seen at icon size but makes every moving frame dearer in the lossless WebP."""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/hotairballoons/textures/entity"
WHITE, RED = (0xF9, 0xFF, 0xFE), (0xB0, 0x2E, 0x26)


def layer(name):
    a = np.array(Image.open(TEX / name).convert("RGBA")).astype(float)
    return a[..., 0], a[..., 3] > 0


base_l, base_m = layer("hot_air_balloon_envelope.png")
stripe_l, stripe_m = layer("hot_air_balloon_envelope_stripes.png")
out = np.zeros((*base_l.shape, 4), np.uint8)
for lum, mask, tint in ((base_l, base_m, WHITE), (stripe_l, stripe_m, RED)):
    if "--smooth" in sys.argv:
        stack = [np.roll(np.roll(np.where(mask, lum, np.nan), dy, 0), dx, 1) for dy in range(-2, 3) for dx in range(-2, 3)]
        lum = np.where(mask, np.nanmedian(np.stack(stack), axis=0), 0)
        vals = lum[mask]
        c = np.quantile(vals, np.linspace(0.05, 0.95, 6))
        for _ in range(30):
            k = np.abs(vals[:, None] - c[None]).argmin(1)
            c = np.array([vals[k == j].mean() if (k == j).any() else c[j] for j in range(6)])
        lum = np.where(mask, c[np.abs(lum[..., None] - c[None, None]).argmin(-1)], 0)
    for i in range(3):
        out[..., i] = np.where(mask, np.clip(lum * tint[i] / 255 + 0.5, 0, 255), out[..., i])
    out[..., 3] = np.where(mask, 255, out[..., 3])
Image.fromarray(out).save(ROOT / "dev/icon/sprites/tex_envelope.png")
print("colours", len(Image.fromarray(out).getcolors(1 << 16)))
