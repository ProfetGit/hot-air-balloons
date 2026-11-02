package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A long wisp (a streak that curls up and frays as it ages) that streams through the air the way the wind carries the
 * balloon. It lives in the balloon's frame (an offset from the balloon, advanced along that air's wind each tick), so
 * it shows the wind whatever the pilot's thrust. The quad is fixed in the world with its long axis along the wind and
 * only turns about that axis to face the camera (a camera-facing quad rolled to the wind's on-screen angle spun as you
 * turned your head); the curl side is the one nearer world up, the texture is mirrored when the quad's front would face
 * away, and it fades when seen end-on.
 */
public final class WindLineParticle extends SingleQuadParticle {
    private static @Nullable SpriteSet sprites;
    private static final List<WindLineParticle> LIVE = new ArrayList<>();

    private static final int FADE_IN = 6;
    private static final int FADE_OUT = 12;
    private static final double WOBBLE = 0.3;
    private static final float ALPHA = 0.45F;

    private final HotAirBalloon balloon;
    private final int layer;
    private final int row;
    private final float wobbleSide;
    private final float wobbleUp;
    private final float periodSide;
    private final float periodUp;
    private Vec3 base;
    private Vec3 offset;
    private Vec3 prevOffset;
    private Vec3 delta = Vec3.ZERO;
    private double dirX = 1, dirZ;
    private boolean mirrored;

    private WindLineParticle(ClientLevel level, HotAirBalloon balloon, int layer, Vec3 start, float size) {
        super(level, balloon.getX() + start.x, balloon.getY() + start.y, balloon.getZ() + start.z, sprites.first());
        this.balloon = balloon;
        this.layer = layer;
        this.row = layer < 0 ? 1 : layer > 0 ? 2 : -1;
        this.base = start;
        this.offset = start;
        this.prevOffset = start;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.lifetime = 40 + random.nextInt(31);
        this.wobbleSide = random.nextFloat() * Mth.TWO_PI;
        this.wobbleUp = random.nextFloat() * Mth.TWO_PI;
        this.periodSide = 40 + random.nextInt(21);
        this.periodUp = 40 + random.nextInt(21);
        this.quadSize = size;
        setSpriteFromAge(sprites);
        LIVE.add(this);
    }

    /** Adds a wisp of half-size {@code size} to the particle engine; no-op until the sprite set is registered. */
    public static @Nullable WindLineParticle spawn(ClientLevel level, HotAirBalloon b, int layer, Vec3 offset, float size) {
        if (sprites == null) return null;
        WindLineParticle p = new WindLineParticle(level, b, layer, offset, size);
        net.minecraft.client.Minecraft.getInstance().particleEngine.add(p);
        return p;
    }

    /** Lines alive on the client thread (for the test driver). */
    public static List<WindLineParticle> live() {
        return LIVE;
    }

    /** Drops lines whose level or balloon is gone without the engine telling us (level change, cleared particles). */
    public static void purge(@Nullable ClientLevel level) {
        LIVE.removeIf(l -> l.level != level || l.balloon.isRemoved());
    }

    public HotAirBalloon balloon() {
        return balloon;
    }

    /** 0 = the balloon's own band, -16 / +16 = the neighbouring layers. */
    public int layer() {
        return layer;
    }

    /** Motion relative to the balloon during the last tick. */
    public Vec3 delta() {
        return delta;
    }

    @Override
    public void tick() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        if (age++ >= lifetime || balloon.isRemoved() || balloon.level() != level) {
            remove();
            return;
        }
        Vec3 wind = row < 0 ? balloon.wind() : balloon.windAt(row);
        double speed = wind.horizontalDistance();
        if (speed > 1.0E-5) {
            dirX = wind.x / speed;
            dirZ = wind.z / speed;
        }
        double rel = 0.05 + 1.5 * speed;
        base = base.add(dirX * rel, 0.0, dirZ * rel);
        double side = WOBBLE * (Math.sin(Mth.TWO_PI * age / periodSide + wobbleSide) - Math.sin(wobbleSide));
        double up = WOBBLE * (Math.sin(Mth.TWO_PI * age / periodUp + wobbleUp) - Math.sin(wobbleUp));
        prevOffset = offset;
        offset = base.add(-dirZ * side, up, dirX * side);
        delta = offset.subtract(prevOffset);
        x = balloon.getX() + offset.x;
        y = balloon.getY() + offset.y;
        z = balloon.getZ() + offset.z;
        setBoundingBox(new AABB(x - quadSize, y - quadSize, z - quadSize, x + quadSize, y + quadSize, z + quadSize));
        setSpriteFromAge(sprites);
    }

    @Override
    public void remove() {
        super.remove();
        LIVE.remove(this);
    }

    /** Full bright: air is white, not lit by the sky (sky light tinted it lavender). */
    @Override
    protected int getLightCoords(float a) {
        return 0xF000F0;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float fade = Math.min(1.0F, Math.min((age + partialTick) / FADE_IN, (lifetime - age - partialTick) / FADE_OUT));
        if (fade <= 0.0F) return;
        Vec3 cam = camera.position();
        Vec3 bpos = balloon.getPosition(partialTick);
        Vec3 p = prevOffset.lerp(offset, partialTick);
        double px = bpos.x + p.x, py = bpos.y + p.y, pz = bpos.z + p.z;
        Vector3f d = new Vector3f((float) dirX, 0, (float) dirZ);
        Vector3f t = new Vector3f((float) (cam.x - px), (float) (cam.y - py), (float) (cam.z - pz));
        if (t.lengthSquared() < 1e-6F) return;
        t.normalize();
        Vector3f n = new Vector3f(t).sub(new Vector3f(d).mul(t.dot(d)));
        float seen = n.length();
        if (seen < 0.05F) return;
        n.div(seen);
        Vector3f y = new Vector3f(n).cross(d);
        if (y.y < 0) y.negate();
        Vector3f x = new Vector3f(d);
        mirrored = new Vector3f(x).cross(y).dot(t) < 0;
        if (mirrored) x.negate();
        Vector3f z = new Vector3f(x).cross(y);
        Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
        alpha = ALPHA * fade * Mth.clamp((seen - 0.05F) / 0.45F, 0.0F, 1.0F);
        extractRotatedQuad(state, rotation, (float) (px - cam.x), (float) (py - cam.y), (float) (pz - cam.z), partialTick);
    }
    @Override
    protected float getU0() {
        return mirrored ? sprite.getU1() : sprite.getU0();
    }

    @Override
    protected float getU1() {
        return mirrored ? sprite.getU0() : sprite.getU1();
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /** Registers the sprite set; lines are spawned directly with {@link #spawn}. */
    public record Provider(SpriteSet set) implements ParticleProvider<SimpleParticleType> {
        public Provider {
            sprites = set;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double xa, double ya, double za, RandomSource random) {
            return null;
        }
    }
}
