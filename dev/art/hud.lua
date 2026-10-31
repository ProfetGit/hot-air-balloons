-- HUD wind arrows, 9x9, white body (tinted in code) with a dark outline added around it: straight up and up-right
-- (the HUD turns them in 90-degree steps, which keeps the pixels whole). Drawn into hud.aseprite (one frame each)
-- and exported as textures/gui/sprites/hud/wind_arrow{,_diagonal}.png. Run: aseprite --batch --script hud.lua
local DIR = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/"
local OUT = DIR .. "src/main/resources/assets/hotairballoons/textures/gui/sprites/hud/"
-- '#' body, '.' clear; the outline is every clear pixel touching the body
local ARROWS = {
  wind_arrow = {
    ".........",
    "....#....",
    "...###...",
    "..#####..",
    "....#....",
    "....#....",
    "....#....",
    "....#....",
    ".........",
  },
  wind_arrow_diagonal = {
    ".........",
    "....####.",
    ".....###.",
    ".....###.",
    "....#..#.",
    "...#.....",
    "..#......",
    ".#.......",
    ".........",
  },
}
local NAMES = {"wind_arrow", "wind_arrow_diagonal"}
local spr = Sprite(9, 9, ColorMode.RGB)
spr.layers[1].name = "arrow"
local function body(rows, x, y)
  if x < 1 or x > 9 or y < 1 or y > 9 then return false end
  return rows[y]:sub(x, x) == "#"
end
for i, name in ipairs(NAMES) do
  if i > 1 then spr:newEmptyFrame() end
  local rows = ARROWS[name]
  local img = Image(9, 9, ColorMode.RGB)
  for y = 1, 9 do
    for x = 1, 9 do
      if body(rows, x, y) then
        img:drawPixel(x - 1, y - 1, app.pixelColor.rgba(255, 255, 255, 255))
      else
        local edge = false
        for dy = -1, 1 do for dx = -1, 1 do if body(rows, x + dx, y + dy) then edge = true end end end
        if edge then img:drawPixel(x - 1, y - 1, app.pixelColor.rgba(16, 16, 16, 200)) end
      end
    end
  end
  spr:newCel(spr.layers[1], i, img, Point(0, 0))
  img:saveAs(OUT .. name .. ".png")
end
spr:saveAs(DIR .. "dev/art/hud.aseprite")
print("wind arrows", #NAMES)
