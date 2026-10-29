-- Paints the balloon textures in Aseprite from layout.json (export.py): every face texel knows its point on the
-- model, so gores, weave and coil rings line up across cubes. One layer per material; saves main.aseprite and
-- envelope.aseprite next to this script and exports the game PNGs.
-- Run: aseprite --batch --script paint.lua
local DIR = "/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/"
local ART = DIR .. "dev/art/"
local TEX = DIR .. "src/main/resources/assets/hotairballoons/textures/entity/"

local f = io.open(ART .. "layout.json", "r")
local layout = json.decode(f:read("a"))
f:close()

local rgba = app.pixelColor.rgba
local function hex(c, a) return rgba((c >> 16) & 255, (c >> 8) & 255, c & 255, a or 255) end
local function hash(a, b)
  local h = (a * 374761393 + b * 668265263) & 0xffffffff
  h = ((h ~ (h >> 13)) * 1274126177) & 0xffffffff
  return ((h ~ (h >> 16)) & 0xffff) / 65535
end
local function pick(pal, t) return pal[math.max(1, math.min(#pal, math.floor(t * #pal) + 1))] end
local function clamp(v) return math.max(0, math.min(255, math.floor(v + 0.5))) end
local function shade(c, k)
  return rgba(clamp(((c >> 16) & 255) * k), clamp(((c >> 8) & 255) * k), clamp((c & 255) * k), 255)
end

local GORES = 12
local TOP, BOTTOM = 183, 70
-- Box envelope faces, across, panel k a stripe when k is odd. PLAIN: five even 18-unit panels (no banner). FRAMED: base
-- edge, stripe, a wide centre field where a banner emblem goes (so stripes are never cut by it), stripe, base edge.
local PLAIN = {-45, -27, -9, 9, 27, 45}
local FRAMED = {-45, -37, -23, 23, 37, 45}

-- Envelope: greyscale (tinted in game). Returns value and whether the texel is in an odd (stripe) gore.
local function envelope(p, n, fc, h, BOUNDS)
  local x, y, z = p[1], p[2], p[3]
  local r = math.sqrt(x * x + z * z)
  -- Panel coordinate along the face: x on the z faces, z on the x faces; top and bottom take the nearer face's.
  local along
  if n[3] ~= 0 then along = x elseif n[1] ~= 0 then along = z
  elseif math.abs(z) >= math.abs(x) then along = x else along = z end
  local k, lo, hi = 0, BOUNDS[1], BOUNDS[2]
  for i = 1, #BOUNDS - 1 do
    if along < BOUNDS[i + 1] or i == #BOUNDS - 1 then k, lo, hi = i - 1, BOUNDS[i], BOUNDS[i + 1]; break end
  end
  local width = hi - lo
  local frac = math.max(0, math.min(1, (along - lo) / width))
  local t = math.max(0, math.min(1, (y - BOTTOM) / (TOP - BOTTOM)))
  local L = 196 + 40 * (t * t * (3 - 2 * t))
  L = L + 16 * (1 - (2 * frac - 1) ^ 2) - 8
  if n[2] > 0 then L = L + 8 elseif n[2] < 0 then L = L - 34 end
  local arc = math.min(frac, 1 - frac) * width
  if r > 4 and arc < 0.75 then L = L - 30 end
  if n[2] == 0 and y - fc.box[1][2] < 1 then L = L - 10 end
  if n[2] > 0 and y > TOP - 0.5 then
    if r < 7 then L = 168 - (r < 3 and 14 or 0) elseif r < 8.5 then L = 140 end
  end
  L = L + (h - 0.5) * 10
  L = math.floor(L / 5 + 0.5) * 5
  return clamp(L), k % 2 == 1
end

local WICKER = {0x5E4325, 0x8F6A3A, 0xB98E52, 0xD2AA6E}
local STAKE = {0x7E5C32, 0x9C7442, 0xA88048}
local OAK = {0x3A2614, 0x48301B, 0x553A22}
local PLANK = {0x5E432A, 0x6B4E30, 0x7A5A3A}
local LEATHER = {0x47291A, 0x6B4226, 0x7C4E2D, 0x8A5A34}
local SUEDE = {0x6E5034, 0x8E6A44, 0xA27C50}
local STEEL = {0x55555B, 0x737379, 0x8E8E95, 0xB0B0B8}
local COPPER = {0x7A3520, 0x9C4529, 0xC15A36, 0xE77C56, 0xF4A27E}
local ROPE = {0x6E5A40, 0x8B7355, 0xA48C66}
local CANVAS = {0x8C7C64, 0xA6957A, 0xB8A88A, 0xC6B898}

-- Horizontal coordinate along a face and the axis it runs on.
local function along(p, n) if n[3] ~= 0 then return p[1] elseif n[1] ~= 0 then return p[3] end return p[1] end

local main = {}
main.skid = function(p, n, fc, h)
  local row = math.floor(n[2] ~= 0 and p[1] or p[2] * 3)
  return pick(OAK, 0.3 + 0.5 * hash(row, math.floor(p[3] / 3)) + (n[2] > 0 and 0.2 or 0))
end
main.wicker = function(p, n, fc, h)
  local s = math.floor(along(p, n) + 64)
  local yy = math.floor(p[2])
  local c
  if n[2] ~= 0 then
    c = WICKER[3 - (math.floor(p[1]) + math.floor(p[3])) % 2]
  elseif yy == 1 then
    c = WICKER[1]
  else
    local row = (yy - 1) // 2
    local top = (yy - 1) % 2 == 1
    local col = (s + (row % 2) * 2) % 4
    if col == 0 and (row + s // 4) % 2 == 1 then
      c = top and STAKE[2] or STAKE[1]
    elseif top then
      c = (col == 1 or col == 2) and WICKER[4] or WICKER[3]
    else
      c = col == 3 and WICKER[1] or WICKER[2]
    end
    if h > 0.93 then c = STAKE[1] end
  end
  local inside = (n[1] ~= 0 and p[1] * n[1] < 0) or (n[3] ~= 0 and p[3] * n[3] < 0)
  return inside and shade(c, 0.78) or hex(c)
end
main.floor = function(p, n, fc, h)
  local plank = math.floor((p[1] + 12) / 4)
  local c = pick(PLANK, 0.2 + 0.6 * hash(plank, math.floor((p[3] + 40 + plank * 7) / 6)))
  if math.floor(p[1] + 12) % 4 == 0 then c = 0x4E3822 end
  return hex(c)
end
-- Leather rim: one stitch line along the middle of every rim piece, a stitch every 3 texels, the same on all four
-- sides (the pattern runs along each piece's long axis, so the corners line up).
main.rim = function(p, n, fc, h)
  local b = fc.box
  local longX = math.abs(b[2][1] - b[1][1]) >= math.abs(b[2][3] - b[1][3])
  local s = math.floor((longX and p[1] or p[3]) + 64)
  if n[2] ~= 0 then
    local across = longX and p[3] or p[1]
    local mid = longX and (b[1][3] + b[2][3]) / 2 or (b[1][1] + b[2][1]) / 2
    if math.floor(across + 64) == math.floor(mid + 64) and s % 3 == 0 then return hex(LEATHER[1]) end
    return hex(n[2] > 0 and LEATHER[4] or LEATHER[2])
  end
  if n[1] ~= 0 and longX or n[3] ~= 0 and not longX then return hex(LEATHER[2]) end
  if math.floor(p[2]) == 15 and s % 3 == 0 then return hex(LEATHER[1]) end
  return hex(h > 0.75 and LEATHER[3] or LEATHER[2])
end
main.upright = function(p, n, fc, h)
  local yy = math.floor(p[2])
  if yy < 18 or yy >= 36 then return hex(LEATHER[1]) end
  if yy % 6 == 0 then return hex(SUEDE[1]) end
  local s = math.floor(along(p, n) + 64) % 2
  return hex(n[2] ~= 0 and SUEDE[2] or SUEDE[2 + s])
end
local function steel(p, n, fc, h)
  if n[2] > 0 then return hex(STEEL[4]) end
  if n[2] < 0 then return hex(STEEL[1]) end
  local edge = p[2] - fc.box[1][2] < 1
  return hex(edge and STEEL[2] or (h > 0.8 and STEEL[4] or STEEL[3]))
end
main.frame = steel
main.bar = steel
main.valve = function(p, n, fc, h)
  if n[2] ~= 0 then return hex(STEEL[1]) end
  return hex(math.floor(p[2]) == 35 and STEEL[1] or STEEL[2])
end
main.lever = function(p, n, fc, h)
  if p[1] > 4 then return hex(n[2] > 0 and 0xD04545 or 0xA83232) end
  return hex(STEEL[3])
end
main.coil = function(p, n, fc, h)
  if n[2] ~= 0 then
    local r = math.max(math.abs(p[1]), math.abs(p[3]))
    if n[2] < 0 then return hex(COPPER[1]) end
    return hex(r < 2 and COPPER[1] or (r < 3 and COPPER[3] or COPPER[4]))
  end
  local yy = math.floor(p[2])
  local s = math.abs(along(p, n))
  local k = (yy % 2 == 0) and 4 or 2
  if s > 3 then k = k - 1 end
  if h > 0.85 and yy % 2 == 0 then k = 5 end
  return hex(COPPER[k])
end
main.nozzle = function(p, n, fc, h)
  if n[2] > 0 then
    local r = math.max(math.abs(p[1]), math.abs(p[3]))
    return hex(r < 1 and 0x1A1A1E or 0x2E2E34)
  end
  return hex(math.floor(p[2]) == 44 and STEEL[2] or 0x3C3C42)
end
main.rope = function(p, n, fc, h)
  local yy = math.floor(p[2])
  local side = (n[1] ~= 0 and 1 or 0) + (n[1] + n[3] > 0 and 2 or 0)
  return hex(ROPE[((yy + side) % 3) + 1])
end
main.skirt = function(p, n, fc, h)
  local x, y, z = p[1], p[2], p[3]
  if n[2] < 0 then
    local lo, hi = fc.box[1], fc.box[2]
    local edge = math.min(x - lo[1], hi[1] - x, z - lo[3], hi[3] - z)
    if fc.cube:sub(1, 7) == "skirt_1" then return hex(CANVAS[1]) end
    if edge < 1.5 then return hex(0x6B5E4B) end
    if edge < 3 then return hex(0x3A2F26) end
    return hex(h > 0.8 and 0x3A2A20 or 0x2B2420)
  end
  local r = math.sqrt(x * x + z * z)
  local g = math.atan(z, x) / (2 * math.pi) * GORES + 0.5
  local frac = g - math.floor(g)
  if math.min(frac, 1 - frac) * 2 * math.pi * r / GORES < 0.75 then return hex(CANVAS[1]) end
  if n[2] == 0 and y < 59 then return hex(0x6B5E4B) end
  return hex(pick({CANVAS[2], CANVAS[3], CANVAS[4]}, 0.15 + 0.7 * h))
end

-- Additive (emissive) flame: black reads as clear, so tips fade to black.
local function ramp(stops, t)
  t = math.max(0, math.min(1, t))
  for i = 1, #stops - 1 do
    local a, b = stops[i], stops[i + 1]
    if t <= b[1] then
      local u = (t - a[1]) / (b[1] - a[1])
      local ca, cb = a[2], b[2]
      local function ch(s) return ((ca >> s) & 255) * (1 - u) + ((cb >> s) & 255) * u end
      return rgba(clamp(ch(16)), clamp(ch(8)), clamp(ch(0)), 255)
    end
  end
  return hex(stops[#stops][2])
end
main.flame = function(p, n, fc, h)
  if n[2] ~= 0 then return hex(n[2] > 0 and 0x401000 or 0xFFD070) end
  local t = (p[2] - 45) / 16 + (h - 0.5) * 0.18
  local s = math.abs(along(p, n)) / 3
  t = t + s * s * 0.35
  return ramp({{0, 0xFFF4C0}, {0.25, 0xFFC040}, {0.5, 0xFF7A1C}, {0.75, 0xB8300C}, {1, 0x200800}}, t)
end
main.flamecore = function(p, n, fc, h)
  if n[2] ~= 0 then return hex(0x6090FF) end
  local t = (p[2] - 45) / 10 + (h - 0.5) * 0.12
  return ramp({{0, 0x3060FF}, {0.15, 0x80B0FF}, {0.3, 0xFFFFE0}, {0.7, 0xFFE080}, {1, 0x402000}}, t)
end
main.pilot = function(p, n, fc, h)
  if n[2] > 0 then return hex(0x102040) end
  return hex(p[2] < 46 and 0x4070FF or 0x203880)
end

local MAIN_LAYER = {skid = "wood", floor = "wood", wicker = "wicker", rim = "leather", upright = "leather",
  frame = "metal", bar = "metal", valve = "metal", lever = "metal", nozzle = "metal", coil = "copper",
  rope = "rope", skirt = "canvas", flame = "flame", flamecore = "flame", pilot = "flame"}

local function sprite(atlas, names)
  local w, h = layout.atlases[atlas][1], layout.atlases[atlas][2]
  local spr = Sprite(w, h, ColorMode.RGB)
  local imgs, layers = {}, {}
  for i, name in ipairs(names) do
    local layer = i == 1 and spr.layers[1] or spr:newLayer()
    layer.name = name
    imgs[name] = Image(w, h, ColorMode.RGB)
    layers[name] = layer
  end
  return spr, imgs, layers
end

local function each_texel(atlas, fn)
  for _, fc in ipairs(layout.faces) do
    if fc.atlas == atlas then
      local r = fc.rect
      for v = r[2], r[4] - 1 do
        for u = r[1], r[3] - 1 do
          local i, j = u - r[1] + 0.5, v - r[2] + 0.5
          local p = {fc.o[1] + i * fc.du[1] + j * fc.dv[1], fc.o[2] + i * fc.du[2] + j * fc.dv[2], fc.o[3] + i * fc.du[3] + j * fc.dv[3]}
          fn(fc, u, v, p, fc.n, hash(u, v))
        end
      end
    end
  end
end

local function finish(spr, imgs, layers, order, path)
  for _, name in ipairs(order) do spr:newCel(layers[name], 1, imgs[name], Point(0, 0)) end
  spr:saveAs(path)
end

-- Main atlas
local order = {"wood", "wicker", "leather", "metal", "copper", "rope", "canvas", "flame"}
local spr, imgs, layers = sprite("main", order)
local flat = Image(layout.atlases.main[1], layout.atlases.main[2], ColorMode.RGB)
each_texel("main", function(fc, u, v, p, n, h)
  local fn = main[fc.mat]
  if fn then
    local c = fn(p, n, fc, h)
    imgs[MAIN_LAYER[fc.mat]]:drawPixel(u, v, c)
    flat:drawPixel(u, v, c)
  end
end)
finish(spr, imgs, layers, order, ART .. "main.aseprite")
flat:saveAs(TEX .. "hot_air_balloon.png")
flat:saveAs(ART .. "out/main.png")

-- Envelope atlas: base (even gores) and stripes (odd gores), disjoint so the two tinted passes never overlap (the
-- game batches by render type, so draw order between them is not fixed). Plus a white/red preview for Blockbench.
-- Two pairs: the plain layout and the framed one the renderer switches to when the balloon wears a banner.
local eorder = {"base", "stripes", "base_framed", "stripes_framed"}
local espr, eimgs, elayers = sprite("envelope", eorder)
local ew, eh = layout.atlases.envelope[1], layout.atlases.envelope[2]
local preview = Image(ew, eh, ColorMode.RGB)
local A, B = {0xF9, 0xFF, 0xFE}, {0xB0, 0x2E, 0x26}
each_texel("envelope", function(fc, u, v, p, n, h)
  local L, odd = envelope(p, n, fc, h, PLAIN)
  local grey = rgba(L, L, L, 255)
  if odd then eimgs.stripes:drawPixel(u, v, grey) else eimgs.base:drawPixel(u, v, grey) end
  local c = odd and B or A
  preview:drawPixel(u, v, rgba(clamp(L * c[1] / 255), clamp(L * c[2] / 255), clamp(L * c[3] / 255), 255))
  local Lf, oddf = envelope(p, n, fc, h, FRAMED)
  local gf = rgba(Lf, Lf, Lf, 255)
  if oddf then eimgs.stripes_framed:drawPixel(u, v, gf) else eimgs.base_framed:drawPixel(u, v, gf) end
end)
finish(espr, eimgs, elayers, eorder, ART .. "envelope.aseprite")
eimgs.base:saveAs(TEX .. "hot_air_balloon_envelope.png")
eimgs.stripes:saveAs(TEX .. "hot_air_balloon_envelope_stripes.png")
eimgs.base_framed:saveAs(TEX .. "hot_air_balloon_envelope_framed.png")
eimgs.stripes_framed:saveAs(TEX .. "hot_air_balloon_envelope_stripes_framed.png")
preview:saveAs(ART .. "out/envelope_preview.png")
print("painted", #layout.faces, "faces")
