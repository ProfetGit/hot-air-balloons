-- 16x16 item icon: the striped envelope, skirt, ropes and wicker basket. Saves icon.aseprite and the item texture.
-- Run: aseprite --batch --script icon.lua
local DIR = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/"
local rgba = app.pixelColor.rgba
local function hex(c) return rgba((c >> 16) & 255, (c >> 8) & 255, c & 255, 255) end
local HALF = {2, 4, 5, 6, 6, 6, 5, 5, 4, 3}
local WHITE = {0xFAFAFA, 0xE2E2E2, 0xBDBDBD, 0x8E8E8E}
local RED = {0xD9493B, 0xB02E26, 0x8A221C, 0x5C1612}
local img = Image(16, 16, ColorMode.RGB)
for y = 0, 9 do
  local hw = HALF[y + 1]
  for x = 8 - hw, 7 + hw do
    local u = (x + 0.5 - 8) / hw
    local g = math.floor((math.asin(math.max(-1, math.min(1, u))) / (math.pi / 2)) * 3.5 + 3.5)
    local pal = g % 2 == 1 and WHITE or RED
    local k = 2
    if u < -0.3 and y < 6 then k = 1 end
    if u > 0.45 or y >= 8 then k = 3 end
    if x == 8 - hw or x == 7 + hw or (y == 0) or (y == 9) then k = 4 end
    img:drawPixel(x, y, hex(pal[k]))
  end
end
for x = 6, 9 do img:drawPixel(x, 10, hex(x == 9 and 0x7A6B55 or 0xA6957A)) end
for y = 11, 12 do
  img:drawPixel(6, y, hex(0x6E5A40))
  img:drawPixel(9, y, hex(0x6E5A40))
end
for x = 5, 10 do img:drawPixel(x, 13, hex(x == 10 and 0x47291A or 0x6B4226)) end
for y = 14, 15 do
  for x = 5, 10 do
    local c = ((x + y) % 2 == 0) and 0xD2AA6E or 0xB98E52
    if x == 10 or y == 15 then c = 0x8F6A3A end
    img:drawPixel(x, y, hex(c))
  end
end
local spr = Sprite(16, 16, ColorMode.RGB)
spr.layers[1].name = "icon"
spr:newCel(spr.layers[1], 1, img, Point(0, 0))
spr:saveAs(DIR .. "dev/art/icon.aseprite")
img:saveAs(DIR .. "src/main/resources/assets/hotairballoons/textures/item/hot_air_balloon.png")
print("icon")
