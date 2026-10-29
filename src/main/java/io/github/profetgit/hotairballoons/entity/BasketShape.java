package io.github.profetgit.hotairballoons.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The basket as something to stand in: a floor and four walls in the world, turned with the balloon. Vanilla collides
 * an entity with another entity's whole bounding box, which would make the basket a solid block, so the balloon is not
 * collidable itself and {@code mixin/EntityCollideMixin} adds these shapes to every entity's movement instead. Shapes
 * are axis-aligned, so a turned floor or wall is laid out as a row of small boxes along it. Sizes match the model:
 * floor top at 2/16, inner walls at 12/16, outer at 15/16, rim top at 16/16 (a jump clears it).
 */
public final class BasketShape {
    public static final double FLOOR_TOP = 2 / 16.0, INNER = 12 / 16.0, OUTER = 15 / 16.0, RIM = 1.0;
    /** Boxes per wall and floor strips: enough that a turned basket has no gaps a player could slip through. */
    private static final int PIECES = 8;

    private BasketShape() {}

    /** The shapes of every balloon basket near {@code area}, except one {@code source} is riding or is. */
    public static List<VoxelShape> near(Level level, Entity source, AABB area) {
        List<HotAirBalloon> balloons = level.getEntitiesOfClass(HotAirBalloon.class, area.inflate(1.5),
            b -> b != source && source.getVehicle() != b && !b.isRemoved());
        if (balloons.isEmpty()) return List.of();
        List<VoxelShape> out = new ArrayList<>();
        for (HotAirBalloon b : balloons) out.addAll(b.basketShapes());
        return out;
    }

    /** Floor strips and wall pieces for a basket at {@code pos} turned by {@code yaw} (degrees). */
    static List<VoxelShape> build(Vec3 pos, float yaw) {
        List<VoxelShape> out = new ArrayList<>(PIECES * 5);
        double step = 2 * INNER / PIECES;
        for (int i = 0; i < PIECES; i++) {
            double t0 = -INNER + i * step, t1 = t0 + step;
            out.add(piece(pos, yaw, t0, t1, -INNER, INNER, 0, FLOOR_TOP));
        }
        double wstep = 2 * OUTER / PIECES;
        for (int i = 0; i < PIECES; i++) {
            double t0 = -OUTER + i * wstep, t1 = t0 + wstep;
            out.add(piece(pos, yaw, t0, t1, -OUTER, -INNER, 0, RIM));
            out.add(piece(pos, yaw, t0, t1, INNER, OUTER, 0, RIM));
            out.add(piece(pos, yaw, -OUTER, -INNER, t0, t1, 0, RIM));
            out.add(piece(pos, yaw, INNER, OUTER, t0, t1, 0, RIM));
        }
        return out;
    }

    /** A basket-local rectangle (x0..x1, z0..z1, y0..y1) turned with the balloon, as its axis-aligned box. */
    private static VoxelShape piece(Vec3 pos, float yaw, double x0, double x1, double z0, double z1, double y0, double y1) {
        double r = -yaw * Mth.DEG_TO_RAD, c = Math.cos(r), s = Math.sin(r);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double x : new double[] {x0, x1}) {
            for (double z : new double[] {z0, z1}) {
                double wx = x * c + z * s, wz = z * c - x * s;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return Shapes.create(new AABB(pos.x + minX, pos.y + y0, pos.z + minZ, pos.x + maxX, pos.y + y1, pos.z + maxZ));
    }

    /** {@code p} in the basket's frame (x, y above the balloon origin, z), using the balloon at {@code pos}/{@code yaw}. */
    public static Vec3 toLocal(Vec3 p, Vec3 pos, float yaw) {
        return p.subtract(pos).yRot(yaw * Mth.DEG_TO_RAD);
    }

    public static Vec3 toWorld(Vec3 local, Vec3 pos, float yaw) {
        return local.yRot(-yaw * Mth.DEG_TO_RAD).add(pos);
    }

    /** Feet at {@code feet} stand in the basket (inside the walls, between the floor and a little above the rim). */
    public static boolean inside(Vec3 feet, Vec3 pos, float yaw) {
        Vec3 l = toLocal(feet, pos, yaw);
        return Math.abs(l.x) < OUTER && Math.abs(l.z) < OUTER && l.y > FLOOR_TOP - 0.3 && l.y < RIM + 0.25;
    }
}
