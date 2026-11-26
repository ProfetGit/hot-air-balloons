package io.github.profetgit.hotairballoons.legacy;

//? if <1.21.2 {
/*import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

// The parts of 1.21.2's net.minecraft.util.ARGB the mod uses (dev/renames.json points the imports here).
public final class ARGB {
    private ARGB() {}

    public static int alpha(int c) {
        return FastColor.ARGB32.alpha(c);
    }

    public static int red(int c) {
        return FastColor.ARGB32.red(c);
    }

    public static int green(int c) {
        return FastColor.ARGB32.green(c);
    }

    public static int scaleRGB(int c, float scale) {
        return FastColor.ARGB32.color(FastColor.ARGB32.alpha(c), channel(FastColor.ARGB32.red(c) * scale),
            channel(FastColor.ARGB32.green(c) * scale), channel(FastColor.ARGB32.blue(c) * scale));
    }

    private static int channel(float v) {
        return Mth.clamp((int) v, 0, 255);
    }

    public static int srgbLerp(float t, int a, int b) {
        return FastColor.ARGB32.lerp(t, a, b);
    }

    public static int color(float alpha, int rgb) {
        return Mth.floor(alpha * 255.0F) << 24 | rgb & 0xFFFFFF;
    }
}
*///?}
