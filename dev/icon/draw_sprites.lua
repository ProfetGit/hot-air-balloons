-- Hot Air Balloons icon sprites (pack-icon-animation skill). Run through the aseprite MCP:
--   dofile("/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/dev/icon/draw_sprites.lua")
-- The balloon itself uses the mod's own textures (dev/art/out). Drawn here: the ground-shadow key, the dust puff,
-- backgrounds and the banner lettering. #FF00FF = shadow key, mapped to its real colour by make_icon.py.
dofile("/home/emppu/Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets/pixel_art.lua")
local OUT = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/dev/icon/sprites/"

PA.sprite_from_grid(OUT .. "key_shadow", {
  ".....SSSSSS.....",
  "...SSSSSSSSSS...",
  "..SSSSSSSSSSSS..",
  ".SSSSSSSSSSSSSS.",
  ".SSSSSSSSSSSSSS.",
  "SSSSSSSSSSSSSSSS",
  "SSSSSSSSSSSSSSSS",
  "SSSSSSSSSSSSSSSS",
  "SSSSSSSSSSSSSSSS",
  "SSSSSSSSSSSSSSSS",
  "SSSSSSSSSSSSSSSS",
  ".SSSSSSSSSSSSSS.",
  ".SSSSSSSSSSSSSS.",
  "..SSSSSSSSSSSS..",
  "...SSSSSSSSSS...",
  ".....SSSSSS.....",
}, { S = "#FF00FF" })

-- dust poof: overlapping round lumps, white with a warm-grey lower-right shade and rim (binary alpha)
PA.sprite_from_grid(OUT .. "fx_puff", {
  "................",
  "......wwww......",
  ".....wwwwww.....",
  "..ww.wwwwwwww...",
  ".wwwwwwwwwwwwr..",
  ".wwwwwwwwwwwsr..",
  "wwwwwwwwwwwwwsr.",
  "wwwwwwwwwwwwwwsr",
  "wwwwwwwwwwwwwwsr",
  ".wwwwwwwwwwwwssr",
  ".wwwwwwwwwwwssr.",
  "..swwwwwwwwsssr.",
  "..rssswwssssrr..",
  "...rrrsssrrr....",
  "......rrr.......",
  "................",
}, { w = "#FFFFFF", s = "#D9D2C5", r = "#A89F92" })

-- backgrounds (64x64 icon, 192x64 banner; x8 on output). Painted per pixel by a colour function.
local function paint(name, w, h, colour_at)
  local img = Image(w, h, ColorMode.RGB)
  for y = 0, h - 1 do for x = 0, w - 1 do img:drawPixel(x, y, Color(PA.hex(colour_at(x, y)))) end end
  local spr = Sprite(w, h, ColorMode.RGB)
  spr.cels[1].image = img
  spr:saveAs(OUT .. name .. ".aseprite")
  spr:saveCopyAs(OUT .. name .. ".png")
  spr:close()
  print("saved " .. name)
end
local SKY, CLOUD, CLOUD_SH = "#74BDF0", "#FFFFFF", "#D3EAFB"
local GRASS_HI, GRASS, GRASS_LO = "#8AD45E", "#5DB547", "#4A9A3A"
-- pixel cloud: rows of [x0, x1] spans relative to the cloud's top-left, bottom row shaded
local CLOUD_ROWS = { {3, 6}, {1, 9}, {0, 11}, {0, 11} }
local function cloud_at(clouds, x, y)
  for _, c in ipairs(clouds) do
    local r = y - c[2] + 1
    local span = CLOUD_ROWS[r]
    if span and x >= c[1] + span[1] and x <= c[1] + span[2] then
      return r == #CLOUD_ROWS and CLOUD_SH or CLOUD
    end
  end
end
-- meadow: sky, two clouds, a grass hill whose crest is at row `top` in the middle and dips at the sides
local function meadow(w, top, clouds)
  return function(x, y)
    local c = cloud_at(clouds, x, y)
    if c then return c end
    local d = math.abs(x - (w - 1) / 2) / (w / 2)
    local crest = top + math.floor(d * d * 3 + 0.5)
    if y < crest then return SKY end
    if y == crest then return GRASS_HI end
    return y >= 62 and GRASS_LO or GRASS
  end
end
paint("bg_meadow", 64, 64, meadow(64, 51, { {3, 9}, {50, 21} }))
paint("bg_sky", 64, 64, function(x, y) return cloud_at({ {3, 9}, {50, 21}, {6, 44} }, x, y) or SKY end)
paint("bg_plum", 64, 64, function() return "#3D3179" end)

-- banner backgrounds (192x64): the balloon stands at column ~146, its shadow at row ~53, so the grass is a plateau there
local function banner_meadow(x, y)
  local c = cloud_at({ {8, 3}, {98, 6}, {172, 24} }, x, y)
  if c then return c end
  local d = x < 112 and (112 - x) / 40 or (x > 180 and (x - 180) / 14 or 0)
  local crest = 51 + math.min(6, math.floor(d * d * 5 + 0.5))
  if y < crest then return SKY end
  if y == crest then return GRASS_HI end
  return y >= 62 and GRASS_LO or GRASS
end
paint("banner_bg_meadow", 192, 64, banner_meadow)
paint("banner_bg_sky", 192, 64, function(x, y) return cloud_at({ {8, 3}, {98, 6}, {172, 24}, {118, 50} }, x, y) or SKY end)
local PLUM_STARS = { [9 * 256 + 8] = 1, [27 * 256 + 17] = 1, [44 * 256 + 6] = 1, [96 * 256 + 9] = 1, [118 * 256 + 4] = 1,
  [170 * 256 + 12] = 1, [186 * 256 + 30] = 1, [104 * 256 + 56] = 1, [14 * 256 + 56] = 1, [180 * 256 + 50] = 1 }
paint("banner_bg_plum", 192, 64, function(x, y) return PLUM_STARS[x * 256 + y] and "#6454B4" or "#3D3179" end)

-- lettering: HOT AIR in white with a red extrude over BALLOONS in envelope red, tagline with a flame-yellow AWAY.
local RED = { "#FFB3A6", "#F2604F", "#F2604F", "#F2604F", "#DB3D2F", "#DB3D2F", "#DB3D2F", "#B32D24", "#B32D24", "#B32D24" }
local WHITE = { "#FFFFFF", "#FFFFFF", "#FFFFFF", "#F4F6F8", "#F4F6F8", "#F4F6F8", "#E2E6EA", "#E2E6EA", "#E2E6EA", "#E2E6EA" }
local INK = "#10162C"
PA.title_sprite(OUT .. "banner_title_top", "HOT AIR", { bands = WHITE, extrude = { "#B32D24", "#7A1C16" }, outline = INK })
PA.title_sprite(OUT .. "banner_title", "BALLOONS", { bands = RED, extrude = { "#7A1C16", "#4E120E" }, outline = INK })
-- a bolder comma than the shared font's, so "UP," does not read as "UP."
PA.SMALL[","] = { "..", "..", "..", "..", "##", ".#", "#." }
PA.label_sprite(OUT .. "banner_tagline", "UP, UP AND AWAY.", function(i) return i > 11 and "#FFD45C" or "#FFFFFF" end, INK)

-- description-kit strip (tools/Description-Kit PACKS hot_air_balloons): a twisted basket rope, tiles every 4 px
PA.sprite_from_grid(OUT .. "kit_rope_strip", {
  "LLMDLLMDLLMDLLMD",
  "LMDLLMDLLMDLLMDL",
  "MDLLMDLLMDLLMDLL",
  "DLLMDLLMDLLMDLLM",
  "kkkkkkkkkkkkkkkk",
}, { L = "#E3C08A", M = "#C29A5E", D = "#94703E", k = "#6B4A26" })
