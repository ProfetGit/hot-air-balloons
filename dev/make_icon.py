#!/usr/bin/env python3
"""Compose the Hot Air Balloons icon from the Blockbench renders in dev/icon/frames/ (pack-icon-animation skill).

Scene: dev/icon/build_scene.js (HABI.render, 60 fps, 1600 px). The ground shadow is rendered in the key colour #FF00FF and
mapped to the background's shadow colour here, under the dark outline that hugs only the solid art.

Outputs (dev/icon/out/, git-ignored):
  icon-animated.webp   Modrinth icon, 256 px, 60 fps lossless
  icon-512.png         still (STILL_FRAME)
  src/main/resources/assets/hotairballoons/icon.png   the mod's own icon (128 px still)
  dev/icon/contact.png                                 frame sheet for review
Usage: make_icon.py [--bg meadow|sky|plum] [--out DIR] (--out skips the mod icon, for option sheets)."""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "dev" / "icon"
SPRITES = ICON / "sprites"
MOD_ICON = ROOT / "src/main/resources/assets/hotairballoons/icon.png"
S = 512
FPS = 60
STILL_FRAME = 130          # hovering at the top of the climb
WEBP_SIZE = 256
WEBP_LIMIT = 256 * 1024
OUTLINE = 15
INK = (16, 22, 44)
KEY = (255, 0, 255)
SHADOW = {"meadow": (0x3F, 0x86, 0x33), "sky": (0x5C, 0xA4, 0xDA), "plum": (0x2C, 0x23, 0x60)}
MARGIN = 1.06
TOP_PAD = 0.05             # free space above the loop's highest pixel, as a share of the side


def durations(n: int) -> list:
    return [round((i + 1) * 1000 / FPS) - round(i * 1000 / FPS) for i in range(n)]


def split(im: Image.Image, shadow: tuple) -> tuple:
    """(solid art RGBA, shadow RGBA) with the key colour mapped."""
    a = np.array(im.convert("RGBA"))
    under = np.zeros_like(a)
    m = (a[..., 3] > 0) & (a[..., 0] == KEY[0]) & (a[..., 1] == KEY[1]) & (a[..., 2] == KEY[2])
    under[m] = shadow + (255,)
    a[m] = 0
    return Image.fromarray(a), Image.fromarray(under)


def crop_box(solids: list) -> tuple:
    """Square centred on the upright balloon (STILL_FRAME), wide enough for the envelope lying on the ground."""
    boxes = [s.getchannel("A").getbbox() for s in solids]
    still = boxes[STILL_FRAME]
    cx = (still[0] + still[2]) // 2
    x0, x1 = min(b[0] for b in boxes), max(b[2] for b in boxes)
    y0, y1 = min(b[1] for b in boxes), max(b[3] for b in boxes)
    side = int(max(2 * max(cx - x0, x1 - cx), y1 - y0) * MARGIN)
    top = y0 - int(side * TOP_PAD)
    return (cx - side // 2, top, cx - side // 2 + side, top + side)


def compose(solid: Image.Image, under: Image.Image, box: tuple, bg: Image.Image, size: int = S) -> Image.Image:
    solid, under = (x.crop(box).resize((size, size), Image.NEAREST) for x in (solid, under))
    out = Image.alpha_composite(bg, under)
    ink = Image.new("RGBA", (size, size), INK + (0,))
    ink.putalpha(solid.getchannel("A").filter(ImageFilter.MaxFilter(OUTLINE)))
    return Image.alpha_composite(Image.alpha_composite(out, ink), solid)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bg", default="meadow", choices=sorted(SHADOW))
    ap.add_argument("--out")
    args = ap.parse_args()
    files = sorted((ICON / "frames").glob("frame_*.png"))
    if not files:
        sys.exit("no frames in dev/icon/frames - render them from Blockbench first")
    parts = [split(Image.open(f), SHADOW[args.bg]) for f in files]
    box = crop_box([s for s, _ in parts])
    print("crop", box, "side", box[2] - box[0])
    bg = Image.open(SPRITES / f"bg_{args.bg}.png").convert("RGBA").resize((S, S), Image.NEAREST)
    frames = [compose(s, u, box, bg).convert("RGB") for s, u in parts]

    out_dir = Path(args.out) if args.out else ICON / "out"
    out_dir.mkdir(parents=True, exist_ok=True)
    frames[STILL_FRAME].save(out_dir / "icon-512.png", optimize=True)
    if not args.out:
        MOD_ICON.parent.mkdir(parents=True, exist_ok=True)
        frames[STILL_FRAME].resize((128, 128), Image.NEAREST).save(MOD_ICON, optimize=True)

    small = [f.resize((WEBP_SIZE, WEBP_SIZE), Image.NEAREST) for f in frames]
    out = out_dir / "icon-animated.webp"
    small[0].save(out, save_all=True, append_images=small[1:], duration=durations(len(small)), loop=0,
                  lossless=True, quality=100, method=6)
    back = Image.open(out)
    if back.n_frames != len(small):
        sys.exit(f"{out.name}: {back.n_frames} frames decoded, {len(small)} expected")

    cols = 12
    rows = (len(frames) + cols - 1) // cols
    contact = Image.new("RGB", (cols * 128, rows * 128))
    for i, f in enumerate(frames):
        contact.paste(f.resize((128, 128), Image.LANCZOS), ((i % cols) * 128, (i // cols) * 128))
    contact.save(out_dir / "contact.png" if args.out else ICON / "contact.png")

    size = out.stat().st_size
    print(f"{len(frames)} frames @ {FPS} fps ({len(frames) / FPS:.2f} s) -> {out} {size / 1024:.0f} KiB "
          f"({'OK' if size <= WEBP_LIMIT else 'OVER'} 256 KiB); still = frame {STILL_FRAME}; bg pixel {small[0].getpixel((2, 2))}")


if __name__ == "__main__":
    main()
