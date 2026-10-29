package io.github.profetgit.hotairballoons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.EnvelopeLook;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Basket, emissive flame, the envelope drawn twice (base panels in the base dye, stripe panels in the stripe dye; even
 * stripes, or stripes framing a centre field when the balloon wears a banner), then
 * one envelope pass per emblem layer ({@link EmblemTextures}), and the chest in the basket's back corner.
 */
public class BalloonRenderer extends EntityRenderer<HotAirBalloon, BalloonRenderState> {
    private static final Identifier TEXTURE = HotAirBalloons.id("textures/entity/hot_air_balloon.png");
    private static final Identifier ENVELOPE = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope.png");
    private static final Identifier STRIPES = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes.png");
    /** With a banner: the stripes move out to frame the centre field the emblem fills. */
    private static final Identifier ENVELOPE_FRAMED = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_framed.png");
    private static final Identifier STRIPES_FRAMED = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes_framed.png");
    private static final BlockDisplayContext CHEST_CONTEXT = BlockDisplayContext.create();
    /**
     * In the basket's back corner (seat corner 3; renderer space is the entity's turned 180 degrees), facing the pilot,
     * filling the corner so the lid sits level with the wall top and shows over the rim.
     */
    private static final BlockState CHEST = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
    private static final float CHEST_SCALE = 0.75F;
    private final BalloonModel model, flame, envelope;
    private final BlockModelResolver blocks;

    public BalloonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BalloonModel(context.bakeLayer(BalloonModel.LAYER), BalloonModel.Part.MAIN);
        this.flame = new BalloonModel(context.bakeLayer(BalloonModel.FLAME_LAYER), BalloonModel.Part.FLAME);
        this.envelope = new BalloonModel(context.bakeLayer(BalloonModel.ENVELOPE_LAYER), BalloonModel.Part.ENVELOPE);
        this.blocks = context.getBlockModelResolver();
        this.shadowRadius = 1.0F;
    }

    @Override
    public void submit(BalloonRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        float hurt = state.hurtTime;
        if (hurt > 0.0F) poseStack.rotateDegrees(Axis.XP, Mth.sin(hurt) * hurt * state.damageTime / 40.0F * state.hurtDir);
        if (!state.chest.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 2 / 16.0F, 0);
            poseStack.scale(CHEST_SCALE, CHEST_SCALE, CHEST_SCALE);
            state.chest.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        int light = state.lightCoords, overlay = OverlayTexture.NO_OVERLAY;
        collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), light, overlay, -1, null, state.outlineColor);
        boolean framed = state.emblemCount > 0;
        collector.submitModel(envelope, state, poseStack, RenderTypes.entityCutout(framed ? ENVELOPE_FRAMED : ENVELOPE), light, overlay, state.primary, null, state.outlineColor);
        collector.submitModel(envelope, state, poseStack, RenderTypes.entityCutout(framed ? STRIPES_FRAMED : STRIPES), light, overlay, state.secondary, null, state.outlineColor);
        for (int i = 0; i < state.emblemCount; i++) {
            collector.submitModel(envelope, state, poseStack, RenderTypes.entityTranslucent(state.emblemTextures[i]), light, overlay,
                state.emblemColors[i], null, state.outlineColor);
        }
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
        blocks.update(state.chest, entity.hasChest() ? CHEST : Blocks.AIR.defaultBlockState(), CHEST_CONTEXT);
        EnvelopeLook look = entity.look();
        state.primary = look.base().getTextureDiffuseColor();
        state.secondary = look.stripes().getTextureDiffuseColor();
        state.emblemCount = 0;
        if (look.emblemBase().isPresent() && !look.emblem().layers().isEmpty()) {
            var layers = look.emblem().layers();
            EmblemTextures.Fit fit = EmblemTextures.fit(layers.stream().map(l -> l.pattern().value().assetId()).toList());
            if (fit != null) {
                for (BannerPatternLayers.Layer layer : layers) {
                    Identifier tex = EmblemTextures.pattern(layer.pattern().value().assetId(), fit);
                    if (tex != null) add(state, tex, layer.color().getTextureDiffuseColor());
                }
            }
        }
    }

    private static void add(BalloonRenderState state, Identifier texture, int color) {
        if (state.emblemCount >= state.emblemTextures.length) return;
        state.emblemTextures[state.emblemCount] = texture;
        state.emblemColors[state.emblemCount++] = color;
    }
}
