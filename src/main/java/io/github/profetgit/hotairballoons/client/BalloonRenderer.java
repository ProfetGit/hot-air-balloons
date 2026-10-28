package io.github.profetgit.hotairballoons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Basket, emissive flame, and the envelope drawn twice: base gores in the primary dye, stripe gores in the secondary. */
public class BalloonRenderer extends EntityRenderer<HotAirBalloon, BalloonRenderState> {
    private static final Identifier TEXTURE = HotAirBalloons.id("textures/entity/hot_air_balloon.png");
    private static final Identifier ENVELOPE = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope.png");
    private static final Identifier STRIPES = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes.png");
    private final BalloonModel model, flame, envelope;

    public BalloonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BalloonModel(context.bakeLayer(BalloonModel.LAYER), BalloonModel.Part.MAIN);
        this.flame = new BalloonModel(context.bakeLayer(BalloonModel.FLAME_LAYER), BalloonModel.Part.FLAME);
        this.envelope = new BalloonModel(context.bakeLayer(BalloonModel.ENVELOPE_LAYER), BalloonModel.Part.ENVELOPE);
        this.shadowRadius = 1.0F;
    }

    @Override
    public void submit(BalloonRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        float hurt = state.hurtTime;
        if (hurt > 0.0F) poseStack.rotateDegrees(Axis.XP, Mth.sin(hurt) * hurt * state.damageTime / 40.0F * state.hurtDir);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        int light = state.lightCoords, overlay = OverlayTexture.NO_OVERLAY;
        collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), light, overlay, -1, null, state.outlineColor);
        collector.submitModel(envelope, state, poseStack, RenderTypes.entityCutout(ENVELOPE), light, overlay, state.primary, null, state.outlineColor);
        collector.submitModel(envelope, state, poseStack, RenderTypes.entityCutout(STRIPES), light, overlay, state.secondary, null, state.outlineColor);
        collector.submitModel(flame, state, poseStack, RenderTypes.eyes(TEXTURE), light, overlay, -1, null, 0);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** The envelope towers ~10 blocks over the basket box; cull against all of it. */
    @Override
    protected AABB getBoundingBoxForCulling(HotAirBalloon entity, float partialTicks) {
        return entity.getBoundingBox().minmax(entity.envelopeBox()).inflate(0.75, 0, 0.75);
    }

    @Override
    public BalloonRenderState createRenderState() {
        return new BalloonRenderState();
    }

    @Override
    public void extractRenderState(HotAirBalloon entity, BalloonRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
        state.damageTime = Math.max(entity.getDamage() - partialTicks, 0.0F);
        state.heat = entity.heat();
        state.burning = entity.burnerLit();
        state.swayX = Mth.lerp(partialTicks, entity.swayXo, entity.swayX);
        state.swayZ = Mth.lerp(partialTicks, entity.swayZo, entity.swayZ);
        state.flame = Mth.lerp(partialTicks, entity.flameO, entity.flame);
        state.inflate = entity.inflateProgress(partialTicks);
    }
}
