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
    void lowAirStaysGentleAndTheTopBlowsAsFastAsThrust() {
        assertTrue(Wind.speed(100) < 0.02, "y 100: " + Wind.speed(100));
        assertTrue(Wind.speed(230) >= Wind.MAX_SPEED * 0.95, "y 230: " + Wind.speed(230));
        for (double y = 0; y < 320; y += 1) assertTrue(Wind.speed(y + 1) >= Wind.speed(y), "speed drops at y=" + y);
    }

    @Test
    void windRelativeToTheLookDirection() {
        // Wind blowing east (+x). Yaw -90 looks east, 0 looks south, 180 looks north, 90 looks west.
        assertEquals(0.0, Wind.relativeAngle(0.05, 0, -90), 1e-9);
        assertEquals(1.0, Wind.help(0.05, 0, -90), 1e-9);
        assertEquals(-Math.PI / 2, Wind.relativeAngle(0.05, 0, 0), 1e-9);
        assertEquals(Math.PI / 2, Wind.relativeAngle(0.05, 0, 180), 1e-9);
        assertEquals(0.0, Wind.help(0.05, 0, 0), 1e-9);
        assertEquals(-1.0, Wind.help(0.05, 0, 90), 1e-9);
        assertEquals(0.0, Wind.help(0, 0, 0));
    }

    @Test
    void windLeansTowardThePilotsHeading() {
        // Heading east = yaw -90. Tailwind unchanged, headwind turned 72 degrees, crosswind 36 degrees, speed kept.
        double[] tail = Wind.favour(new double[] {0.1, 0}, -90, Wind.FAVOUR);
        assertEquals(1.0, Wind.help(tail[0], tail[1], -90), 1e-9);
        double[] head = Wind.favour(new double[] {-0.1, 0}, -90, Wind.FAVOUR);
        assertEquals(Math.cos(Math.toRadians(108)), Wind.help(head[0], head[1], -90), 1e-6);
        double[] cross = Wind.favour(new double[] {0, 0.1}, -90, Wind.FAVOUR);
        assertEquals(Math.cos(Math.toRadians(54)), Wind.help(cross[0], cross[1], -90), 1e-6);
        assertEquals(0.1, Math.hypot(cross[0], cross[1]), 1e-9);
        double[] none = Wind.favour(new double[] {0, 0.1}, -90, 0);
        assertEquals(0.1, none[1]);
        // Over every wind direction the leaned wind helps on average, but not everywhere.
        double sum = 0, worst = 1;
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            double[] w = Wind.favour(new double[] {Math.cos(a), Math.sin(a)}, -90, Wind.FAVOUR);
            double h = Wind.help(w[0], w[1], -90);
            sum += h;
            worst = Math.min(worst, h);
        }
        assertTrue(sum / 360 > 0.45 && worst < 0, "mean help " + sum / 360 + ", worst " + worst);
    }

    @Test
    void gustsComeAndGoSmoothlyAndBandsGustOnTheirOwn() {
        int gusting = 0, both = 0, ticks = 72000;
        double top = 1;
        for (int band = 5; band < 15; band++) {
            double prev = Wind.gust(42, band, 0);
            for (long t = 1; t < ticks; t++) {
                double g = Wind.gust(42, band, t);
                assertTrue(g >= 1.0 && g <= 1.0 + Wind.GUST_PEAK_MAX + 1e-9, "gust " + g);
                assertTrue(Math.abs(g - prev) < 0.1, "gust jumps " + prev + " -> " + g + " at t=" + t);
                if (g > 1.5) gusting++;
                if (g > 1.5 && Wind.gust(42, band + 1, t) > 1.5) both++;
                top = Math.max(top, g);
                prev = g;
            }
        }
        double share = gusting / (10.0 * ticks);
        assertTrue(share > 0.05 && share < 0.3, "gusting " + share + " of the time");
        assertTrue(both < gusting * 0.5, "neighbouring bands gust together " + both + " of " + gusting);
        assertTrue(top > 2.5, "strongest gust " + top);
        assertEquals(Wind.gust(42, 7, 1234), Wind.gust(42, 7, 1234));
        double[] calm = Wind.at(42, 230, 0, false), any = Wind.at(42, 230, 0);
        assertTrue(Math.hypot(any[0], any[1]) >= Math.hypot(calm[0], calm[1]) - 1e-12);
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
    void aMooredBalloonRisesToItsHeightAndSettlesThere() {
        double y = 0, vy = 0, top = 0;
        for (int i = 0; i < 1500; i++) {
            vy = BalloonPhysics.moorClimb(vy, y, BalloonPhysics.MOOR_HEIGHT);
            y += vy;
            top = Math.max(top, y);
            assertTrue(vy <= BalloonPhysics.MOOR_CLIMB + 1e-9, "climbs too fast " + vy);
        }
        assertEquals(BalloonPhysics.MOOR_HEIGHT, y, 0.02);
        assertTrue(top < BalloonPhysics.MOOR_HEIGHT * 1.25, "overshoots to " + top);
        // Too high sinks back, and the wind only leans it: even the strongest wind stays well inside the rope.
        assertTrue(BalloonPhysics.moorClimb(0, 6, BalloonPhysics.MOOR_HEIGHT) < 0);
        double x = 0, vx = 0;
        for (int i = 0; i < 1500; i++) {
            vx = BalloonPhysics.horizontal(vx, BalloonPhysics.moorWind(Wind.MAX_SPEED, x), 0, BalloonPhysics.AIR_DRAG);
            x += vx;
        }
        assertTrue(x > 0 && x + BalloonPhysics.MOOR_STANDOFF < 7, "leans " + x + " blocks in the strongest wind");
    }

    @Test
    void tetherHoldsABurningBalloonNearTheRope() {
        double x = 0, y = 0, vx = 0, vy = 0;
        for (int i = 0; i < 2000; i++) {
            vy = BalloonPhysics.climb(vy, 1.0F, 100 + y, 320);
            vx = BalloonPhysics.horizontal(vx, Wind.MAX_SPEED, 0, BalloonPhysics.AIR_DRAG);
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
