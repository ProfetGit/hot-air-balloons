package io.github.profetgit.hotairballoons.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.hotairballoons.HotAirBalloons;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * Envelope-sized textures for a banner emblem, made at runtime: textures/entity/hot_air_balloon_emblem_map.png says
 * which banner flag pixel each envelope texel shows (dev/art/emblem_map.py), and each banner pattern's own texture
 * supplies the shape, so modded patterns work and no vanilla texture ships with the mod. Every layer is shaded like the
 * envelope under it (its greyscale textures), so the emblem reads as painted on. Rebuilt after a reload.
 */
final class EmblemTextures {
    private static final Identifier MAP = HotAirBalloons.id("textures/entity/hot_air_balloon_emblem_map.png");
    /** The envelope's own greyscale textures: the emblem takes their shading so it doesn't show as a flat patch. */
    private static final Identifier[] SHADING = {HotAirBalloons.id("textures/entity/hot_air_balloon_envelope.png"),
        HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes.png")};
    /** Flag front in a 64x64 banner pattern texture (vanilla BannerFlagModel: 20x40x1 box at texture offset 0,0). */
    private static final int FLAG_X = 1, FLAG_Y = 1;
    private static final Map<Identifier, Identifier> MADE = new HashMap<>();
    private static int @Nullable [] map;
    private static int[] shade = new int[0];
    private static int width, height;
    private static @Nullable Identifier base;
    private static boolean broken;

    private EmblemTextures() {}

    /** The emblem's background: every emblem texel opaque white, tinted with the banner's base colour. */
    static @Nullable Identifier base() {
        if (base == null && load()) base = make("base", (u, v) -> 0xFFFFFFFF);
        return base;
    }

    /** A pattern layer from the pattern's banner texture (white with alpha; tinted with the layer's dye). */
    static @Nullable Identifier pattern(Identifier assetId) {
        Identifier done = MADE.get(assetId);
        if (done != null || !load()) return done;
        Identifier src = assetId.withPath(p -> "textures/entity/banner/" + p + ".png");
        try (NativeImage pattern = read(src)) {
            done = make("pattern/" + assetId.getNamespace() + "/" + assetId.getPath(),
                (u, v) -> pattern.getPixel(FLAG_X + u, FLAG_Y + v));
        } catch (IOException e) {
            HotAirBalloons.LOG.warn("No banner texture {} for a balloon emblem: {}", src, e.toString());
        }
        MADE.put(assetId, done);
        return done;
    }

    static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : MADE.values()) if (id != null) tm.release(id);
        if (base != null) tm.release(base);
        MADE.clear();
        base = null;
        map = null;
        broken = false;
    }

    private interface Sample {
        int at(int u, int v);
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
            int uv = map[i];
            if (uv >= 0) out.setPixel(i % width, i / width, ARGB.scaleRGB(sample.at(uv >> 8, uv & 0xFF), shade[i] / 255.0F));
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
