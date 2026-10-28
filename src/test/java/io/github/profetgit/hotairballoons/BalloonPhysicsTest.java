package io.github.profetgit.hotairballoons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.profetgit.hotairballoons.entity.BalloonPhysics;
import io.github.profetgit.hotairballoons.entity.Wind;
import org.junit.jupiter.api.Test;

class BalloonPhysicsTest {
    @Test
    void burnerFillsInAboutFourSeconds() {
        float h = BalloonPhysics.NEUTRAL;
        int ticks = 0;
        while (h < 1.0F) {
            h = BalloonPhysics.heat(h, true, false, true);
            ticks++;
        }
        assertTrue(ticks >= 30 && ticks <= 60, "ticks " + ticks);
    }

    @Test
    void settlesToHoverWhenFuelledAndCoolsWithout() {
        float h = 1.0F, cold = 1.0F;
        for (int i = 0; i < 400; i++) {
            h = BalloonPhysics.heat(h, false, false, true);
            cold = BalloonPhysics.heat(cold, false, false, false);
        }
        assertEquals(BalloonPhysics.NEUTRAL, h, 1e-6);
        assertEquals(0.0F, cold, 1e-6);
    }

    @Test
    void climbConvergesAndFadesAtCeiling() {
        double vy = 0;
        for (int i = 0; i < 200; i++) vy = BalloonPhysics.climb(vy, 1.0F, 100, 320);
        assertEquals(BalloonPhysics.MAX_CLIMB, vy, 1e-3);
        assertEquals(0.0, BalloonPhysics.targetClimb(1.0F, 320, 320), 1e-9);
        assertEquals(0.0, BalloonPhysics.targetClimb(BalloonPhysics.NEUTRAL, 100, 320), 1e-9);
        assertTrue(BalloonPhysics.targetClimb(0.0F, 100, 320) < 0);
    }

    @Test
    void thrustPointsAlongYaw() {
        double[] south = BalloonPhysics.thrust(0, true, false);
        assertTrue(south[1] > 0 && Math.abs(south[0]) < 1e-9);
        double[] west = BalloonPhysics.thrust(90, true, false);
        assertTrue(west[0] < 0 && Math.abs(west[1]) < 1e-9);
    }

    @Test
    void windIsDeterministicSmoothAndStrongerUp() {
        double[] a = Wind.at(42, 100, 1000), b = Wind.at(42, 100, 1000);
        assertEquals(a[0], b[0]);
        assertEquals(a[1], b[1]);
        double low = Math.hypot(Wind.at(42, 70, 0)[0], Wind.at(42, 70, 0)[1]);
        double high = Math.hypot(Wind.at(42, 220, 0)[0], Wind.at(42, 220, 0)[1]);
        assertTrue(high > low, low + " vs " + high);
        for (double y = 64; y < 300; y += 0.5) {
            double[] p = Wind.at(42, y, 0), q = Wind.at(42, y + 0.5, 0);
            double turn = Math.abs(Math.atan2(Math.sin(Math.atan2(q[1], q[0]) - Math.atan2(p[1], p[0])),
                Math.cos(Math.atan2(q[1], q[0]) - Math.atan2(p[1], p[0]))));
            assertTrue(turn < 0.6, "wind jumps " + turn + " rad at y=" + y);
        }
        assertTrue(Math.hypot(a[0], a[1]) <= Wind.MAX_SPEED + 1e-9);
    }

    @Test
    void driftSettlesToTheWindAndThrustAddsOnTop() {
        double v = 0;
        for (int i = 0; i < 400; i++) v = BalloonPhysics.horizontal(v, 0.03, 0, BalloonPhysics.AIR_DRAG);
        assertEquals(0.03, v, 1e-4);
        double fast = 0;
        for (int i = 0; i < 400; i++) fast = BalloonPhysics.horizontal(fast + BalloonPhysics.THRUST, 0.0, 0, BalloonPhysics.AIR_DRAG);
        assertTrue(fast > 0.08 && fast < 0.12, "top speed " + fast);
    }

    @Test
    void tetherHoldsABurningBalloonNearTheRope() {
        double x = 0, y = 0, vx = 0, vy = 0;
        for (int i = 0; i < 2000; i++) {
            vy = BalloonPhysics.climb(vy, 1.0F, 100 + y, 320);
            vx = BalloonPhysics.horizontal(vx, 0.035, 0, BalloonPhysics.AIR_DRAG);
            double[] t = BalloonPhysics.tether(x, y, 0, vx, vy, 0, 8);
            vx = t[0];
            vy = t[1];
            x += vx;
            y += vy;
        }
        double d = Math.hypot(x, y);
        assertTrue(d > 8 && d < 12, "rope at " + d + " (snaps at 12)");
        double[] slack = BalloonPhysics.tether(1, 2, 3, 0.1, 0.1, 0.1, 8);
        assertEquals(0.1, slack[0]);
    }
}
