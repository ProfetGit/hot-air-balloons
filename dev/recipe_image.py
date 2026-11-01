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


def iso(top, side, slab=False, k=1):
    """16k x 16k GUI cube icon (top face, left and right sides; slab = half height), sampled per output pixel."""
    n = 16 * k
    out = Image.new("RGBA", (n, n))
    px = out.load()
    t, s = top.load(), side.load()
    dy = 4 if slab else 0
    sh = 4 if slab else 8
    for y in range(n):
        for x in range(n):
            fx, fy = (x + 0.5) / k, (y + 0.5) / k
            ux, uy = fx - 8, fy - dy
            a = (ux / 8 + uy / 4) / 2
            b = (uy / 4 - ux / 8) / 2
            if 0 <= a < 1 and 0 <= b < 1:
                px[x, y] = t[int(a * 16), int(b * 16)]
                continue
            y0 = dy + 4
            if fx < 8:
                a = fx / 8
                top_y = y0 + a * 4
                shade_k = 0.78
            else:
                a = (fx - 8) / 8
                top_y = y0 + 4 - a * 4
                shade_k = 0.6
            if top_y <= fy < top_y + sh:
                v = (fy - top_y) / sh
                row = int((0.5 + 0.5 * v) * 16) if slab else int(v * 16)
                px[x, y] = shade(s[int(a * 16), min(row, 15)], shade_k)
    return out


def main():
    gui = tex("gui/container/crafting_table.png").crop((0, 0, 176, 166))
    top = gui.crop((0, 0, 176, 76))
    bottom = gui.crop((0, 162, 176, 166))
    base = Image.new("RGBA", (176, 80))
    base.paste(top, (0, 0))
    base.paste(bottom, (0, 76))
    big = base.resize((176 * SCALE, 80 * SCALE), Image.NEAREST)
    wool = tex("block/white_wool.png")
    planks = tex("block/oak_planks.png")
    icons = {
        "W": iso(wool, wool, k=SCALE),
        "P": iso(planks, planks, slab=True, k=SCALE),
        "S": tex("item/string.png").resize((16 * SCALE, 16 * SCALE), Image.NEAREST),
        "C": tex("item/campfire.png").resize((16 * SCALE, 16 * SCALE), Image.NEAREST),
    }
    for r, row in enumerate(("WWW", "SCS", "PPP")):
        for c, k in enumerate(row):
            big.alpha_composite(icons[k], ((30 + 18 * c) * SCALE, (17 + 18 * r) * SCALE))
    result = Image.open(ROOT / "src/main/resources/assets/hotairballoons/textures/item/hot_air_balloon.png").convert("RGBA")
    big.alpha_composite(result.resize((16 * SCALE, 16 * SCALE), Image.NEAREST), (124 * SCALE, 35 * SCALE))
    out = ROOT / "docs/desc"
    out.mkdir(parents=True, exist_ok=True)
    big.save(out / "recipe.webp", lossless=True)
    print("wrote", out / "recipe.webp", big.size)


main()
