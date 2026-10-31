-- Candidate item icon, box envelope (hot_air_balloon.png, 16x16, front view).
-- Envelope: 12 wide x 11 tall, five even 2 px panels W R W R W (outline pixels count as the outer white panels),
-- flat top with bevelled corners, three-step taper to the tan skirt, one rope row, wicker basket (rim + 2 rows).
-- Light from the top left (1 px highlight along the top row and left column, shade on the right column and tapered rows), 1 px outline in the
-- panel's own darkest tone like vanilla items. Colours are the entity's (paint.lua ramps, vanilla red/white dye tones).
-- Run: aseprite --batch --script icon_box.lua   (writes icon_box.aseprite, icon_box.png, pack/.../hot_air_balloon.png)
local DIR = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/dev/art/candidates/item_icon_box/"
local FLAME = os.getenv("ICON_FLAME") == "1"
local rgba = app.pixelColor.rgba
local function hex(c) return rgba((c >> 16) & 255, (c >> 8) & 255, c & 255, 255) end
local WHITE = {0xFFFFFF, 0xE6E9E9, 0xCBD2D2, 0x7F8C8C}
local RED = {0xD9493B, 0xB02E26, 0x8A221C, 0x5C1612}
local SKIRT = {0xC6B898, 0xB8A88A, 0xA6957A, 0x7A6B55}
local ROPE = 0x6E5A40
local img = Image(16, 16, ColorMode.RGB)

local X0, X1 = 3, 12
local BODY, TAPER = 7, 3
local span = {}
local y = 0
span[y] = {X0, X1}; y = y + 1
for _ = 1, BODY do span[y] = {X0 - 1, X1 + 1}; y = y + 1 end
for i = 1, TAPER do span[y] = {X0 - 1 + i, X1 + 1 - i}; y = y + 1 end
local ENV = y - 1
local function inside(x, yy) local s = span[yy]; return s ~= nil and x >= s[1] and x <= s[2] end
for yy = 0, ENV do
  for x = span[yy][1], span[yy][2] do
    local p = (x - X0) // 2
    if x < X0 then p = 0 elseif x > X1 then p = 4 end
    local pal = (p % 2 == 1) and RED or WHITE
    local k = 2
    if yy == 1 or (x == 3 and yy <= 6) then k = 1 end
    if x >= 12 or yy >= 9 then k = 3 end
    if not (inside(x - 1, yy) and inside(x + 1, yy) and inside(x, yy - 1) and inside(x, yy + 1)) then k = 4 end
    img:drawPixel(x, yy, hex(pal[k]))
  end
end
local sy = ENV + 1
for x = 6, 9 do img:drawPixel(x, sy, hex(SKIRT[x == 9 and 4 or (x == 6 and 2 or 3)])) end
img:drawPixel(6, sy + 1, hex(ROPE))
img:drawPixel(9, sy + 1, hex(ROPE))
if FLAME then
  img:drawPixel(7, sy + 1, hex(0xF2A02B))
  img:drawPixel(8, sy + 1, hex(0xE0701C))
end
local by = sy + 2
for x = 5, 10 do img:drawPixel(x, by, hex(x == 10 and 0x47291A or 0x6B4226)) end
for yy = by + 1, 15 do
  for x = 5, 10 do
    local c = ((x + yy) % 2 == 0) and 0xD2AA6E or 0xB98E52
    if x == 10 or yy == 15 then c = 0x8F6A3A end
    img:drawPixel(x, yy, hex(c))
  end
end
local spr = Sprite(16, 16, ColorMode.RGB)
spr.layers[1].name = "icon"
spr:newCel(spr.layers[1], 1, img, Point(0, 0))
local base = FLAME and "alt_flame/icon_box_flame" or "icon_box"
os.execute('mkdir -p "' .. DIR .. 'alt_flame"')
spr:saveAs(DIR .. base .. ".aseprite")
img:saveAs(DIR .. base .. ".png")
if not FLAME then
  os.execute('mkdir -p "' .. DIR .. 'pack/assets/hotairballoons/textures/item"')
  img:saveAs(DIR .. "pack/assets/hotairballoons/textures/item/hot_air_balloon.png")
  -- playtest.py zips the folder and adds pack.mcmeta itself
end
print(base)
