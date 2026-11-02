package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.BalloonPhysics;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.entity.Wind;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Pilot gauges right of the hotbar: heat (the white tick is hover heat), fuel (full = one coal's worth, the number
 * is coal pieces left), which blinks red when it runs out, and the wind here as an arrow turned to where you look
 * (up = straight ahead), green when it carries you the way you look, red when it blows you back, fainter when calm,
 * with the ground speed beside it. A green chevron above or below the arrow means the air 16 or 32 blocks up or down
 * carries you clearly faster the way you look. Shown only while flying a balloon; the fuel bar is left out when the
 * server doesn't use fuel.
 */
public final class BalloonHud {
    private static final int W = 5, H = 20, GAP = 3;
    private static final int CELL = 9;
    /** A layer must beat the wind here by this much (blocks per tick toward where you look) to get a chevron. */
    private static final double BETTER = 0.01;
    /** Up and up-right; turned in 90-degree steps so the pixels stay whole (8 directions, like a compass rose). */
    private static final Identifier ARROW = HotAirBalloons.id("hud/wind_arrow"), DIAGONAL = HotAirBalloons.id("hud/wind_arrow_diagonal");
    private static final int WITH = 0xFF62E062, ACROSS = 0xFFF0F0F0, AGAINST = 0xFFF05050, DEAD = 0xFF707070;
    private static final int FRAME = 0xFFE8D8B0, SHADOW = 0xFF202020;

    private BalloonHud() {}

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !(mc.player.getVehicle() instanceof HotAirBalloon b) || b.getControllingPassenger() != mc.player) return;
        int x = g.guiWidth() / 2 + 91 + 6, y = g.guiHeight() - H - 2;
        float heat = b.heat();
        bar(g, x, y, heat, heat > BalloonPhysics.NEUTRAL ? 0xFFF08A24 : 0xFFE0B040);
        int hover = y + 1 + Math.round((1 - BalloonPhysics.NEUTRAL) * (H - 2));
        g.fill(x - 1, hover, x + W + 1, hover + 1, 0xFFFFFFFF);
        int fuel = b.fuel();
        int wx = x + W + GAP + 1;
        if (fuel != HotAirBalloon.UNLIMITED) {
            float f = Mth.clamp(fuel / 1600.0F, 0, 1);
            boolean empty = fuel <= 0;
            boolean blink = empty && (mc.player.tickCount / 8) % 2 == 0;
            bar(g, x + W + GAP, y, f, 0xFF8A5A2B);
            if (blink) g.fill(x + W + GAP, y, x + 2 * W + GAP, y + H, 0x90D02020);
            if (!empty) g.text(mc.font, Integer.toString(Mth.ceil(fuel / 1600.0F)), x + 2 * W + GAP + 3, y + H - 8, 0xFFE8D8B0, true);
            wx = x + 2 * W + GAP + 17;
        }
        windGauge(g, mc, b, wx, y);
    }

    /** The arrow cell centred on the bars' height, chevrons in the gaps above and below it. */
    private static void windGauge(GuiGraphicsExtractor g, Minecraft mc, HotAirBalloon b, int x, int y) {
        float yaw = mc.gameRenderer.mainCamera().yRot();
        int cy = y + (H - CELL) / 2;
        g.fill(x - 1, cy - 1, x + CELL + 1, cy + CELL + 1, FRAME);
        g.fill(x, cy, x + CELL, cy + CELL, 0xC0303030);
        Vec3 here = b.wind();
        arrow(g, x, cy, here, yaw);
        double now = gain(here, yaw), up = -1, down = -1;
        int ground = b.level().getHeight(Heightmap.Types.MOTION_BLOCKING, b.getBlockX(), b.getBlockZ());
        for (int row = 0; row < HotAirBalloon.LADDER.length; row++) {
            double at = b.getY() + HotAirBalloon.LADDER[row];
            if (at < ground + 1 || at > HotAirBalloon.CEILING) continue;
            double v = gain(b.windAt(row), yaw);
            if (HotAirBalloon.LADDER[row] > 0) up = Math.max(up, v);
            else down = Math.max(down, v);
        }
        if (up > now + BETTER) chevron(g, x + CELL / 2, y, -1);
        if (down > now + BETTER) chevron(g, x + CELL / 2, y + H - 1, 1);
        Vec3 v = b.getDeltaMovement();
        String speed = String.format(Locale.ROOT, "%.1f m/s", v.horizontalDistance() * 20);
        g.text(mc.font, speed, x + CELL + 4, cy + 1, FRAME, true);
    }

    /** Wind speed toward where you look (negative = against). */
    private static double gain(Vec3 w, float yaw) {
        return w.horizontalDistance() * Wind.help(w.x, w.z, yaw);
    }

    /** A 5x3 chevron, tip at (cx, tipY), opening downward for up (dir -1) and upward for down (dir 1). */
    private static void chevron(GuiGraphicsExtractor g, int cx, int tipY, int dir) {
        for (int pass = 0; pass < 2; pass++) {
            int o = pass == 0 ? 1 : 0, c = pass == 0 ? SHADOW : WITH;
            for (int i = 0; i < 3; i++) {
                int yy = tipY - dir * i + o;
                g.fill(cx - i + o, yy, cx - i + o + 1, yy + 1, c);
                g.fill(cx + i + o, yy, cx + i + o + 1, yy + 1, c);
            }
        }
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y, Vec3 w, float yaw) {
        double speed = w.horizontalDistance();
        if (speed < 0.004) {
            g.fill(x + 4, y + 4, x + 5, y + 5, DEAD);
            return;
        }
        double help = Wind.help(w.x, w.z, yaw);
        int color = help >= 0 ? ARGB.srgbLerp((float) help, ACROSS, WITH) : ARGB.srgbLerp((float) -help, ACROSS, AGAINST);
        color = ARGB.color(0.55F + 0.45F * (float) Mth.clamp(speed / Wind.MAX_SPEED, 0, 1), color);
        int dir = Math.floorMod((int) Math.round(Wind.relativeAngle(w.x, w.z, yaw) / (Math.PI / 4)), 8);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + CELL / 2.0F, y + CELL / 2.0F);
        pose.rotate((dir / 2) * Mth.HALF_PI);
        pose.translate(-CELL / 2.0F, -CELL / 2.0F);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, dir % 2 == 0 ? ARROW : DIAGONAL, 0, 0, CELL, CELL, color);
        pose.popMatrix();
    }

    /** A framed vertical bar, filled from the bottom. */
    private static void bar(GuiGraphicsExtractor g, int x, int y, float value, int color) {
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xC0101010);
        g.fill(x, y, x + W, y + H, 0x80303030);
        int h = Math.round(Mth.clamp(value, 0, 1) * H);
        if (h > 0) g.fill(x, y + H - h, x + W, y + H, color);
    }
}
