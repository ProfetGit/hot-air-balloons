package io.github.profetgit.hotairballoons.entity;

/**
 * Balloon flight rules as pure functions of one tick, so they can be unit-tested and run the same on the controlling
 * client and the server. Units: blocks and ticks. Heat 0..1, where {@link #NEUTRAL} hovers.
 */
public final class BalloonPhysics {
    public static final float NEUTRAL = 0.5F;
    public static final float BURN_RATE = 0.012F;
    public static final float VENT_RATE = 0.02F;
    /** Without input the envelope settles back to hovering this fast; with no fuel it cools to 0 instead. */
    public static final float SETTLE_RATE = 0.004F;
    public static final double MAX_CLIMB = 0.12;
    public static final double MAX_SINK = 0.10;
    public static final double VERTICAL_LAG = 0.05;
    public static final double THRUST = 0.004;
    public static final double REVERSE_THRUST = 0.002;
    public static final double AIR_DRAG = 0.96;
    public static final double GROUND_DRAG = 0.6;
    public static final float YAW_ACCEL = 0.25F;
    public static final float YAW_DRAG = 0.85F;
    /** Lift fades over this many blocks below the ceiling. */
    public static final double CEILING_FADE = 32.0;

    private BalloonPhysics() {}

    public static float heat(float heat, boolean burner, boolean vent, boolean fuelled) {
        if (burner && fuelled) return Math.min(1.0F, heat + BURN_RATE);
        if (vent) return Math.max(0.0F, heat - VENT_RATE);
        float target = fuelled ? NEUTRAL : 0.0F;
        if (heat > target) return Math.max(target, heat - SETTLE_RATE);
        return Math.min(target, heat + SETTLE_RATE);
    }

    /** The vertical speed the balloon drifts towards at this heat and height. */
    public static double targetClimb(float heat, double y, double ceiling) {
        double lift = (heat - NEUTRAL) / NEUTRAL;
        double v = lift >= 0 ? lift * MAX_CLIMB : lift * MAX_SINK;
        if (v > 0) v *= clamp((ceiling - y) / CEILING_FADE, 0.0, 1.0);
        return v;
    }

    public static double climb(double vy, float heat, double y, double ceiling) {
        return vy + (targetClimb(heat, y, ceiling) - vy) * VERTICAL_LAG;
    }

    /** Forward/back thrust along yaw (degrees, Minecraft convention) as an x/z acceleration. */
    public static double[] thrust(float yawDeg, boolean forward, boolean back) {
        double a = (forward ? THRUST : 0) - (back ? REVERSE_THRUST : 0);
        double r = Math.toRadians(yawDeg);
        return new double[] {-Math.sin(r) * a, Math.cos(r) * a};
    }

    /**
     * One horizontal axis: drag acts on the speed relative to the air, so with no thrust the balloon settles to the
     * wind's speed (pass wind 0 on the ground or tethered).
     */
    public static double horizontal(double v, double wind, double thrust, double drag) {
        return wind + (v - wind) * drag + thrust;
    }

    public static final double TETHER_STIFFNESS = 0.04;
    public static final double TETHER_MAX_PULL = 0.3;

    /**
     * Rope to an anchor: (dx, dy, dz) = balloon minus anchor. Inside the slack it does nothing; past it, the outward
     * part of the velocity is mostly cancelled and a spring pulls back, capped so it never yanks.
     */
    public static double[] tether(double dx, double dy, double dz, double vx, double vy, double vz, double rope) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= rope || d < 1e-6) return new double[] {vx, vy, vz};
        double nx = dx / d, ny = dy / d, nz = dz / d;
        double out = vx * nx + vy * ny + vz * nz;
        if (out > 0) {
            vx -= nx * out * 0.8;
            vy -= ny * out * 0.8;
            vz -= nz * out * 0.8;
        }
        double pull = Math.min(TETHER_MAX_PULL, (d - rope) * TETHER_STIFFNESS);
        return new double[] {vx - nx * pull, vy - ny * pull, vz - nz * pull};
    }

    public static float yawSpeed(float speed, boolean left, boolean right) {
        if (left) speed -= YAW_ACCEL;
        if (right) speed += YAW_ACCEL;
        return speed * YAW_DRAG;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
