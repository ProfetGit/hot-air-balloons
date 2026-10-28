package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.entity.BalloonPhysics;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Pilot gauges right of the hotbar: heat (the white tick is hover heat) and fuel (full = one coal's worth, the number
 * is coal pieces left), which blinks red when it runs out.
 * Shown only while flying a balloon; the fuel bar is left out when the server doesn't use fuel.
 */
final class BalloonHud {
    private static final int W = 5, H = 20, GAP = 3;

    private BalloonHud() {}

    static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !(mc.player.getVehicle() instanceof HotAirBalloon b) || b.getControllingPassenger() != mc.player) return;
        int x = g.guiWidth() / 2 + 91 + 6, y = g.guiHeight() - H - 2;
        float heat = b.heat();
        bar(g, x, y, heat, heat > BalloonPhysics.NEUTRAL ? 0xFFF08A24 : 0xFFE0B040);
        int hover = y + 1 + Math.round((1 - BalloonPhysics.NEUTRAL) * (H - 2));
        g.fill(x - 1, hover, x + W + 1, hover + 1, 0xFFFFFFFF);
        int fuel = b.fuel();
        if (fuel == HotAirBalloon.UNLIMITED) return;
        float f = Mth.clamp(fuel / 1600.0F, 0, 1);
        boolean empty = fuel <= 0;
        boolean blink = empty && (mc.player.tickCount / 8) % 2 == 0;
        bar(g, x + W + GAP, y, f, 0xFF8A5A2B);
        if (blink) g.fill(x + W + GAP, y, x + 2 * W + GAP, y + H, 0x90D02020);
        if (!empty) g.text(mc.font, Integer.toString(Mth.ceil(fuel / 1600.0F)), x + 2 * W + GAP + 3, y + H - 8, 0xFFE8D8B0, true);
    }

    /** A framed vertical bar, filled from the bottom. */
    private static void bar(GuiGraphicsExtractor g, int x, int y, float value, int color) {
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xC0101010);
        g.fill(x, y, x + W, y + H, 0x80303030);
        int h = Math.round(Mth.clamp(value, 0, 1) * H);
        if (h > 0) g.fill(x, y + H - h, x + W, y + H, color);
    }
}
