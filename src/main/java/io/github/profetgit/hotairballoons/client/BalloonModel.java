package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The balloon in three layers from dev/art/balloon.bbmodel ({@link BalloonMesh}): basket + rigging, the emissive
 * flame, and the envelope (its own texture, tinted). Rigging (the skirt) and envelope share one pose: sway from the
 * frame top and the inflate scale. The four ropes stay pinned at the frame corners and are aimed and stretched every
 * frame to where their skirt end has swayed to, so they never come off the frame. Model space is the renderer's flipped
 * space: -y is up, 16 units per block.
 */
public class BalloonModel extends EntityModel<BalloonRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "main");
    public static final ModelLayerLocation FLAME_LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "flame");
    public static final ModelLayerLocation ENVELOPE_LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "envelope");
    private final Part kind;
    private final ModelPart a, b;
    private final ModelPart[] ropes;
    /** Rope length in model units (frame top y 40 to skirt hem y 58). */
    private static final float ROPE = 18.0F;
    private final Quaternionf sway = new Quaternionf();
    private final Vector3f end = new Vector3f();

    enum Part { MAIN, FLAME, ENVELOPE }

    public BalloonModel(ModelPart root, Part kind) {
        super(root);
        this.kind = kind;
        this.a = switch (kind) {
            case MAIN -> root.getChild("rigging");
            case FLAME -> root.getChild("flame");
            case ENVELOPE -> root.getChild("envelope");
        };
        this.b = kind == Part.FLAME ? root.getChild("pilot") : null;
        this.ropes = kind == Part.MAIN ? new ModelPart[] {root.getChild("rope_0"), root.getChild("rope_1"), root.getChild("rope_2"), root.getChild("rope_3")}
            : new ModelPart[0];
    }

    public static LayerDefinition createLayer() {
        return BalloonMesh.main();
    }

    public static LayerDefinition createFlameLayer() {
        return BalloonMesh.flame();
    }

    public static LayerDefinition createEnvelopeLayer() {
        return BalloonMesh.envelope();
    }

    @Override
    public void setupAnim(BalloonRenderState state) {
        super.setupAnim(state);
        if (kind == Part.FLAME) {
            float t = state.ageInTicks;
            float flicker = 1 + 0.08F * Mth.sin(t * 0.9F) * Mth.sin(t * 0.37F + 1) + 0.04F * Mth.sin(t * 1.7F + 2);
            a.visible = state.flame > 0.02F;
            a.yScale = state.flame * flicker;
            a.xScale = a.zScale = (0.75F + 0.25F * state.flame) * (1.0F - 0.3F * (flicker - 1));
            b.visible = state.flame < 0.3F;
            return;
        }
        a.xRot = state.swayX;
        a.zRot = state.swayZ;
        float p = state.inflate;
        float bump = 0.08F * Mth.sin(Mth.PI * Mth.clamp((p - 0.7F) / 0.3F, 0, 1));
        a.yScale = Mth.lerp(smooth(p), 0.12F, 1.0F) + bump;
        a.xScale = a.zScale = Mth.lerp(smooth(Math.min(1, p * 1.25F)), 0.5F, 1.0F) - 0.4F * bump;
        if (ropes.length > 0) aimRopes();
    }

    /**
     * Each rope's skirt end, carried by the rigging pose (scale, then sway about the rigging pivot), relative to its frame
     * corner; the rope (a box along -y) is turned to point there and stretched to reach it.
     */
    private void aimRopes() {
        sway.rotationZYX(a.zRot, a.yRot, a.xRot);
        for (ModelPart r : ropes) {
            end.set((r.x - a.x) * a.xScale, -ROPE * a.yScale, (r.z - a.z) * a.zScale);
            sway.transform(end);
            float dx = a.x + end.x - r.x, dy = a.y + end.y - r.y, dz = a.z + end.z - r.z;
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4F) continue;
            r.xRot = (float) Math.asin(Mth.clamp(-dz / len, -1, 1));
            r.zRot = (float) Mth.atan2(dx, -dy);
            r.yScale = len / ROPE;
        }
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
