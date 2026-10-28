"""M1 placeholder textures: striped envelope, wicker basket, rope, burner, flame. Replaced by Aseprite art in M3."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

out = Path(sys.argv[1])
img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
for x in range(0, 288, 12):
    d.rectangle([x, 0, x + 11, 151], fill=(200, 50, 45, 255) if (x // 12) % 2 == 0 else (240, 225, 190, 255))
d.rectangle([300, 0, 399, 119], fill=(150, 105, 60, 255))
for y in range(0, 120, 3):
    d.line([300, y, 399, y], fill=(120, 82, 45, 255))
d.rectangle([0, 160, 95, 184], fill=(130, 90, 50, 255))
d.rectangle([100, 160, 139, 202], fill=(190, 165, 120, 255))
d.rectangle([140, 160, 171, 173], fill=(90, 90, 95, 255))
d.rectangle([180, 160, 195, 171], fill=(255, 170, 40, 255))
img.save(out / "textures/entity/hot_air_balloon.png")

icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
i = ImageDraw.Draw(icon)
i.ellipse([3, 0, 12, 10], fill=(200, 50, 45, 255))
i.line([6, 2, 6, 9], fill=(240, 225, 190, 255))
i.line([9, 2, 9, 9], fill=(240, 225, 190, 255))
i.line([5, 10, 6, 12], fill=(190, 165, 120, 255))
i.line([10, 10, 9, 12], fill=(190, 165, 120, 255))
i.rectangle([6, 12, 9, 15], fill=(150, 105, 60, 255))
icon.save(out / "textures/item/hot_air_balloon.png")
