#!/usr/bin/env python3
"""Compose the Hot Air Balloons description banner (1536x512, 3:1) from dev/icon/banner_frames/ (pack-icon-animation skill).

Art: dev/icon/build_scene.js rendered with HABI.camera(0.0443, [0, 88, 0]) -> banner_frames/ (1600 px, cropped 1:1, no
resampling). The shadow key is mapped by make_icon.split and drawn under the outline. Background, title and tagline:
dev/icon/sprites/banner_*.png (draw_sprites.lua), scaled up nearest-neighbour.
Outputs dev/icon/out/banner.png (STILL_FRAME, stepped transparent corners) and banner-animated.webp (60 fps lossless,
alpha corners). Copy the WebP to docs/banner.webp for the README.
Usage: make_banner.py [--bg meadow|sky|plum] [--out DIR]"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_icon  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "dev" / "icon"
SPRITES = ICON / "sprites"
W, H = 1536, 512
FPS = make_icon.FPS
STILL_FRAME = make_icon.STILL_FRAME
ART_OFFSET = (370, -533)      # banner = frame + offset: hovering balloon centred at x 1170, loop's vertical extent centred
OUTLINE = 9
LAYERS = (("banner_title_top", 7, (72, 103)), ("banner_title", 9, (70, 205)), ("banner_tagline", 6, (74, 355)))
WEBP_LIMIT = 5 * 1024 * 1024
GRID = 8
CORNER = (4, 2, 1, 1)


def sprite(name: str, scale: int) -> Image.Image:
    im = Image.open(SPRITES / f"{name}.png").convert("RGBA")
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def corner_mask() -> np.ndarray:
    gw, gh = W // GRID, H // GRID
    m = np.ones((gh, gw), bool)
    for row, cut in enumerate(CORNER):
        m[row, :cut] = m[row, gw - cut:] = False
        m[gh - 1 - row, :cut] = m[gh - 1 - row, gw - cut:] = False
    return np.repeat(np.repeat(m, GRID, 0), GRID, 1)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bg", default="meadow", choices=sorted(make_icon.SHADOW))
    ap.add_argument("--out")
    args = ap.parse_args()
    files = sorted((ICON / "banner_frames").glob("frame_*.png"))
    if not files:
        sys.exit("no frames in dev/icon/banner_frames - render them from Blockbench first")
    dx, dy = ART_OFFSET
    bg = sprite(f"banner_bg_{args.bg}", 8)
    text = [(sprite(n, s), p) for n, s, p in LAYERS]
    frames = []
    for f in files:
        solid, under = make_icon.split(Image.open(f), make_icon.SHADOW[args.bg])
        sol, und = (x.crop((-dx, -dy, W - dx, H - dy)) for x in (solid, under))
        out = Image.alpha_composite(bg, und)
        ink = Image.new("RGBA", (W, H), make_icon.INK + (0,))
        ink.putalpha(sol.getchannel("A").filter(ImageFilter.MaxFilter(OUTLINE)))
        out = Image.alpha_composite(Image.alpha_composite(out, ink), sol)
        for im, pos in text:
            out.alpha_composite(im, pos)
        frames.append(out)

    mask = corner_mask()
    alpha = Image.fromarray(np.where(mask, 255, 0).astype(np.uint8))
    rgba = []
    for f in frames:
        f = f.copy()
        f.putalpha(alpha)
        rgba.append(f)
    out_dir = Path(args.out) if args.out else ICON / "out"
    out_dir.mkdir(parents=True, exist_ok=True)
    rgba[STILL_FRAME].save(out_dir / "banner.png", optimize=True)
    out = out_dir / "banner-animated.webp"
    rgba[0].save(out, save_all=True, append_images=rgba[1:], duration=make_icon.durations(len(rgba)), loop=0,
                 lossless=True, quality=100, method=6, exact=True)

    back = Image.open(out)
    if back.n_frames != len(rgba):
        sys.exit(f"banner WebP: {back.n_frames} frames, {len(rgba)} expected")
    for i in (0, STILL_FRAME, len(rgba) - 1):
        back.seek(i)
        got, want = np.array(back.convert("RGBA")), np.array(rgba[i])
        if not (np.array_equal(got[mask][:, :3], want[mask][:, :3]) and (got[..., 3][~mask] == 0).all()):
            sys.exit(f"banner WebP frame {i} decodes differently")
    size = out.stat().st_size
    print(f"banner.png (frame {STILL_FRAME}); {len(frames)} frames -> {out} {size / 1024:.0f} KiB "
          f"({'OK' if size <= WEBP_LIMIT else 'OVER'} 5 MiB), frames 0/still/last verified")
    if size > WEBP_LIMIT:
        sys.exit(1)


if __name__ == "__main__":
    main()
