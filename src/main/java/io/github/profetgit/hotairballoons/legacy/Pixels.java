package io.github.profetgit.hotairballoons.legacy;

//? if <1.21.2 {
/*import com.mojang.blaze3d.platform.NativeImage;

// ARGB pixel access: before 1.21.2 NativeImage reads and writes ABGR only.
public final class Pixels {
    private Pixels() {}

    public static int get(NativeImage image, int x, int y) {
        return swap(image.getPixelRGBA(x, y));
    }

    public static void set(NativeImage image, int x, int y, int argb) {
        image.setPixelRGBA(x, y, swap(argb));
    }

    private static int swap(int c) {
        return c & 0xFF00FF00 | (c & 0x00FF0000) >> 16 | (c & 0x000000FF) << 16;
    }
}
*///?}
