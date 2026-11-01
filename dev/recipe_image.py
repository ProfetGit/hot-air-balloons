"""Crafting-table picture of the balloon recipe for the description: vanilla GUI texture + 16 px item icons (isometric
cubes for wool and slabs), our own item icon in the result slot. Writes docs/desc/recipe.webp (and a PNG)."""
from pathlib import Path
from PIL import Image

RES = Path.home() / ".cache/mcsrc/26.3/res/assets/minecraft/textures"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SCALE = 3


def tex(path):
    return Image.open(RES / path).convert("RGBA")


def shade(c, k):
    return (int(c[0] * k), int(c[1] * k), int(c[2] * k), c[3])


def iso(top, side, slab=False):
    """16x16 GUI cube icon: top face, left and right sides (slab = half height)."""
    out = Image.new("RGBA", (16, 16))
    px = out.load()
    t, s = top.load(), side.load()
    h = 8 if slab else 16
    dy = 4 if slab else 0
    for y in range(16):
        for x in range(16):
            fx, fy = x + 0.5, y + 0.5
            # top rhombus: P = (8, dy) + a*(8,4) + b*(-8,4)
            px_, py_ = fx - 8, fy - dy
            a = (px_ / 8 + py_ / 4) / 2
            b = (py_ / 4 - px_ / 8) / 2
            if 0 <= a < 1 and 0 <= b < 1:
                px[x, y] = t[int(a * 16), int(b * 16)]
                continue
            y0 = dy + 4
            if fx < 8:
                a = fx / 8
                by = fy - (y0 + a * 4)
                v = by / (h - 4 + 4 - 4 + (16 - dy - 8) if False else (16 - y0 - 0))
                sy = int(by / (16 - y0 - 4 + 4) * 16) if False else None
            if fx < 8:
                a = fx / 8
                top_y = y0 + a * 4
                bot_y = top_y + (8 if not slab else 4)
                if top_y <= fy < bot_y:
                    v = (fy - top_y) / (bot_y - top_y)
                    row = int((0.5 + 0.5 * v) * 16) if slab else int(v * 16)
                    px[x, y] = shade(s[int(a * 16), min(row, 15)], 0.78)
            else:
                a = (fx - 8) / 8
                top_y = y0 + 4 - a * 4
                bot_y = top_y + (8 if not slab else 4)
                if top_y <= fy < bot_y:
                    v = (fy - top_y) / (bot_y - top_y)
                    row = int((0.5 + 0.5 * v) * 16) if slab else int(v * 16)
                    px[x, y] = shade(s[int(a * 16), min(row, 15)], 0.6)
    return out


def main():
    gui = tex("gui/container/crafting_table.png").crop((0, 0, 176, 166))
    wool = tex("block/white_wool.png")
    planks = tex("block/oak_planks.png")
    icons = {
        "W": iso(wool, wool),
        "P": iso(planks, planks, slab=True),
        "S": tex("item/string.png"),
        "C": tex("item/campfire.png"),
    }
    for r, row in enumerate(("WWW", "SCS", "PPP")):
        for c, k in enumerate(row):
            gui.alpha_composite(icons[k], (30 + 18 * c, 17 + 18 * r))
    result = Image.open(ROOT / "src/main/resources/assets/hotairballoons/textures/item/hot_air_balloon.png").convert("RGBA")
    gui.alpha_composite(result, (124, 35))
    top = gui.crop((0, 0, 176, 76))
    bottom = gui.crop((0, 162, 176, 166))
    gui = Image.new("RGBA", (176, 80))
    gui.paste(top, (0, 0))
    gui.paste(bottom, (0, 76))
    big = gui.resize((gui.width * SCALE, gui.height * SCALE), Image.NEAREST)
    out = ROOT / "docs/desc"
    out.mkdir(parents=True, exist_ok=True)
    big.save(out / "recipe.webp", lossless=True)
    print("wrote", out / "recipe.webp", big.size)


main()
