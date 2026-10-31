-- Wind line particle: 8 frames of a 16-wide wisp in a 16x16 square, played once over the particle's life: it blows in,
-- a ripple runs down the tail, the head curls up (the old 8x8 wisp's curl, tail stretched) and it frays away. The code
-- lays the quad along the wind. Drawn into wind_line.aseprite and exported as textures/particle/wind_line_<n>.png.
-- Run: aseprite --batch --script wind_line.lua
local DIR = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/"
local OUT = DIR .. "src/main/resources/assets/hotairballoons/textures/particle/"
-- '#' core, '+' soft edge, '.' clear; rows 5..10 of the square; the wisp moves toward +x (right)
local E = "................"
local FRAMES = {
  {E, E, E, "......++#####...", E, E},
  {E, E, E, "++++##########..", E, E},
  {E, E, "....++........", "++#+..########..", E, E},
  {E, E, "........++......", "+######+..####..", E, E},
  {E, E, ".............+..", "+###########..#.", "............##..", E},
  {E, "............##..", "...........+..#.", "+#########....#.", "..........###...", E},
  {E, "............+...", "...........+..+.", "+.##.+##......#.", "..........+.#...", E},
  {E, "............+...", "..............+.", "..+...+......+..", "...........+....", E},
}
local TOP = 4
local spr = Sprite(16, 16, ColorMode.RGB)
spr.layers[1].name = "wisp"
for i = 2, #FRAMES do spr:newEmptyFrame() end
for i, rows in ipairs(FRAMES) do
  local img = Image(16, 16, ColorMode.RGB)
  for y, row in ipairs(rows) do
    for x = 1, 16 do
      local ch = row:sub(x, x)
      if ch == "#" then img:drawPixel(x - 1, TOP + y - 1, app.pixelColor.rgba(255, 255, 255, 230))
      elseif ch == "+" then img:drawPixel(x - 1, TOP + y - 1, app.pixelColor.rgba(235, 245, 255, 120)) end
    end
  end
  spr:newCel(spr.layers[1], i, img, Point(0, 0))
  img:saveAs(OUT .. "wind_line_" .. (i - 1) .. ".png")
end
spr:saveAs(DIR .. "dev/art/wind_line.aseprite")
print("wind line frames", #FRAMES)
