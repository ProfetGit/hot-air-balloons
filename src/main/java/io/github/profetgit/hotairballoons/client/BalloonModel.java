package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * The balloon in three layers from dev/art/balloon.bbmodel ({@link BalloonMesh}): basket + rigging, the emissive
 * flame, and the envelope (its own texture, tinted). Rigging and envelope share one pose: sway from the frame top and
 * the inflate scale. Model space is the renderer's flipped space: -y is up, 16 units per block.
 */
public class BalloonModel extends EntityModel<BalloonRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "main");
    public static final ModelLayerLocation FLAME_LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "flame");
    public static final ModelLayerLocation ENVELOPE_LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "envelope");
    private final Part kind;
    private final ModelPart a, b;

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
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
