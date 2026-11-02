package io.github.profetgit.hotairballoons.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.hotairballoons.HotAirBalloons;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * Envelope-sized textures for a banner emblem, made at runtime. textures/entity/hot_air_balloon_emblem_map.png marks
 * the emblem field on each side (the centre panel between the stripes, dev/art/emblem_map.py) with field coordinates;
 * each banner pattern's own texture supplies the shape, so modded patterns work and no vanilla texture ships with the
 * mod. The banner is cropped to its design (what its patterns cover), scaled by a whole number to fill the field and
 * centred, so a creeper face fills the field instead of sitting in the top of a banner-shaped patch. No base layer: the
 * envelope already wears the banner's base colour. Every layer is shaded like the envelope under it, so it reads as
 * painted on. Rebuilt after a resource reload.
 */
public final class EmblemTextures {
    private static final Identifier MAP = HotAirBalloons.id("textures/entity/hot_air_balloon_emblem_map.png");
    /** The envelope's own greyscale textures: the emblem takes their shading so it doesn't show as a flat patch. */
    private static final Identifier[] SHADING = {HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_framed.png"),
        HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes_framed.png")};
    /** Flag front in a 64x64 banner pattern texture (vanilla BannerFlagModel: 20x40x1 box at texture offset 0,0). */
    private static final int FLAG_X = 1, FLAG_Y = 1, FLAG_W = 20, FLAG_H = 40;
    /** The field in the map, in texels (emblem_map.py FIELD_W/FIELD_H), and the most a banner pixel is blown up. */
    private static final int FIELD_W = 46, FIELD_H = 84, MAX_SCALE = 3;

    /** Where banner pixel (0, 0) lands in the field, and how many texels a banner pixel covers. */
    record Fit(int scale, int x, int y) {}

    private static final Map<String, Identifier> MADE = new HashMap<>();
    private static final Map<Identifier, NativeImage> PATTERNS = new HashMap<>();
    private static final Map<List<Identifier>, Fit> FITS = new HashMap<>();
    private static int @Nullable [] map;
    private static int[] shade = new int[0];
    private static int width, height;
    private static boolean broken;

    private EmblemTextures() {}

    /** How to place a banner made of these patterns: crop to what they cover together, scale up, centre. */
    static @Nullable Fit fit(List<Identifier> assets) {
        if (assets.isEmpty()) return null;
        Fit cached = FITS.get(assets);
        if (cached != null) return cached;
        int x0 = FLAG_W, y0 = FLAG_H, x1 = -1, y1 = -1;
        for (Identifier a : assets) {
            NativeImage img = image(a);
            if (img == null) continue;
            for (int v = 0; v < FLAG_H; v++) {
                for (int u = 0; u < FLAG_W; u++) {
                    if (ARGB.alpha(img.getPixel(FLAG_X + u, FLAG_Y + v)) == 0) continue;
                    x0 = Math.min(x0, u);
                    y0 = Math.min(y0, v);
                    x1 = Math.max(x1, u);
                    y1 = Math.max(y1, v);
                }
            }
        }
        if (x1 < 0) return null;
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        int s = Math.clamp(Math.min(FIELD_W / w, FIELD_H / h), 1, MAX_SCALE);
        Fit fit = new Fit(s, (FIELD_W - w * s) / 2 - x0 * s, (FIELD_H - h * s) / 2 - y0 * s);
        FITS.put(List.copyOf(assets), fit);
        return fit;
    }

    /** A pattern layer placed by {@code fit} (white with alpha; tinted with the layer's dye). */
    static @Nullable Identifier pattern(Identifier assetId, Fit fit) {
        String key = assetId + "@" + fit.scale() + "," + fit.x() + "," + fit.y();
        if (MADE.containsKey(key) || !load()) return MADE.get(key);
        NativeImage img = image(assetId);
        Identifier done = img == null ? null : make(assetId.getNamespace() + "/" + assetId.getPath() + "/" + fit.scale() + "_" + fit.x() + "_" + fit.y(),
            (fx, fy) -> {
                int u = Math.floorDiv(fx - fit.x(), fit.scale()), v = Math.floorDiv(fy - fit.y(), fit.scale());
                return u < 0 || v < 0 || u >= FLAG_W || v >= FLAG_H ? 0 : img.getPixel(FLAG_X + u, FLAG_Y + v);
            });
        MADE.put(key, done);
        return done;
    }

    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : MADE.values()) if (id != null) tm.release(id);
        for (NativeImage img : PATTERNS.values()) if (img != null) img.close();
        MADE.clear();
        PATTERNS.clear();
        FITS.clear();
        map = null;
        broken = false;
    }

    private interface Sample {
        int at(int fx, int fy);
    }

    private static @Nullable NativeImage image(Identifier assetId) {
        if (PATTERNS.containsKey(assetId)) return PATTERNS.get(assetId);
        Identifier src = assetId.withPath(p -> "textures/entity/banner/" + p + ".png");
        NativeImage img = null;
        try {
            img = read(src);
        } catch (IOException e) {
            HotAirBalloons.LOG.warn("No banner texture {} for a balloon emblem: {}", src, e.toString());
        }
        PATTERNS.put(assetId, img);
        return img;
    }

    private static boolean load() {
        if (map != null) return true;
        if (broken) return false;
        try (NativeImage img = read(MAP)) {
            width = img.getWidth();
            height = img.getHeight();
            map = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int c = img.getPixel(x, y);
                    map[x + y * width] = ARGB.alpha(c) == 0 ? -1 : (ARGB.red(c) << 8) | ARGB.green(c);
                }
            }
            shade = new int[width * height];
            java.util.Arrays.fill(shade, 255);
            for (Identifier id : SHADING) {
                try (NativeImage s = read(id)) {
                    if (s.getWidth() != width || s.getHeight() != height) continue;
                    for (int i = 0; i < shade.length; i++) {
                        int c = s.getPixel(i % width, i / width);
                        if (ARGB.alpha(c) > 0) shade[i] = ARGB.red(c);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            HotAirBalloons.LOG.error("Balloon emblem map missing", e);
            broken = true;
            return false;
        }
    }

    private static Identifier make(String name, Sample sample) {
        NativeImage out = new NativeImage(width, height, true);
        for (int i = 0; i < map.length; i++) {
            int f = map[i];
            if (f >= 0) out.setPixel(i % width, i / width, ARGB.scaleRGB(sample.at(f >> 8, f & 0xFF), shade[i] / 255.0F));
        }
        Identifier id = HotAirBalloons.id("dynamic/emblem/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, out));
        return id;
    }

    private static NativeImage read(Identifier id) throws IOException {
        var res = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow(() -> new IOException("not found"));
        try (InputStream in = res.open()) {
            return NativeImage.read(in);
        }
    }
}
