-- Wind wisp particle: 4 frames of 8x8 (a streak that curls up and frays), drawn into wisp.aseprite and exported
-- as textures/particle/wind_wisp_<n>.png. Run: aseprite --batch --script wisp.lua
local DIR = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/"
local OUT = DIR .. "src/main/resources/assets/hotairballoons/textures/particle/"
-- '#' core, '+' soft edge, '.' clear
local FRAMES = {
  {"........", "........", "........", "........", "++###...", "........", "........", "........"},
  {"........", "........", "........", ".....+..", "+##...#.", "..###...", "........", "........"},
  {"........", "....##..", "...+..#.", "......#.", "+#...#..", "..###...", "........", "........"},
  {"........", "....+...", "...+..+.", "......#.", "+....+..", "..+.#...", "........", "........"},
}
local spr = Sprite(8, 8, ColorMode.RGB)
spr.layers[1].name = "wisp"
for i = 2, #FRAMES do spr:newEmptyFrame() end
for i, rows in ipairs(FRAMES) do
  local img = Image(8, 8, ColorMode.RGB)
  for y, row in ipairs(rows) do
    for x = 1, 8 do
      local ch = row:sub(x, x)
      if ch == "#" then img:drawPixel(x - 1, y - 1, app.pixelColor.rgba(255, 255, 255, 230))
      elseif ch == "+" then img:drawPixel(x - 1, y - 1, app.pixelColor.rgba(235, 245, 255, 120)) end
    end
  end
  spr:newCel(spr.layers[1], i, img, Point(0, 0))
  img:saveAs(OUT .. "wind_wisp_" .. (i - 1) .. ".png")
end
spr:saveAs(DIR .. "dev/art/wisp.aseprite")
print("wisp frames", #FRAMES)
