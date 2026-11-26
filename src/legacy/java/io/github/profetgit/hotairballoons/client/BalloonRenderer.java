package io.github.profetgit.hotairballoons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.EnvelopeLook;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
public class BalloonRenderer extends EntityRenderer<HotAirBalloon> {
    private static final ResourceLocation TEXTURE = HotAirBalloons.id("textures/entity/hot_air_balloon.png");
    private static final ResourceLocation ENVELOPE = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope.png");
    private static final ResourceLocation STRIPES = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes.png");
    /** With a banner: the stripes move out to frame the centre field the emblem fills. */
    private static final ResourceLocation ENVELOPE_FRAMED = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_framed.png");
    private static final ResourceLocation STRIPES_FRAMED = HotAirBalloons.id("textures/entity/hot_air_balloon_envelope_stripes_framed.png");
    /**
     * In the basket's back corner (seat corner 3; renderer space is the entity's turned 180 degrees), facing the pilot,
     * filling the corner so the lid sits level with the wall top and shows over the rim.
     */
    private static final BlockState CHEST = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
    private static final float CHEST_SCALE = 0.75F;
    private final BalloonModel model, flame, envelope;
    private final BalloonRenderState state = new BalloonRenderState();

    public BalloonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BalloonModel(context.bakeLayer(BalloonModel.LAYER), BalloonModel.Part.MAIN);
        this.flame = new BalloonModel(context.bakeLayer(BalloonModel.FLAME_LAYER), BalloonModel.Part.FLAME);
        this.envelope = new BalloonModel(context.bakeLayer(BalloonModel.ENVELOPE_LAYER), BalloonModel.Part.ENVELOPE);
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(HotAirBalloon entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        BalloonRenderState state = extract(entity, partialTicks);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        float hurt = state.hurtTime;
        if (hurt > 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * state.damageTime / 40.0F * state.hurtDir));
        if (state.hasChest) {
            poseStack.pushPose();
            poseStack.translate(0, 2 / 16.0F, 0);
            poseStack.scale(CHEST_SCALE, CHEST_SCALE, CHEST_SCALE);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CHEST, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        int overlay = OverlayTexture.NO_OVERLAY;
        model.setup(state);
        envelope.setup(state);
        flame.setup(state);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay, -1);
        boolean framed = state.emblemCount > 0;
        envelope.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutout(framed ? ENVELOPE_FRAMED : ENVELOPE)), light, overlay, state.primary);
        envelope.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutout(framed ? STRIPES_FRAMED : STRIPES)), light, overlay, state.secondary);
        for (int i = 0; i < state.emblemCount; i++) {
            envelope.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(state.emblemTextures[i])), light, overlay, state.emblemColors[i]);
        }
        flame.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(TEXTURE)), light, overlay, -1);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HotAirBalloon entity) {
        return TEXTURE;
    }

    /** The envelope towers ~10 blocks over the basket box; cull against all of it. */
    @Override
    public boolean shouldRender(HotAirBalloon entity, Frustum frustum, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) return false;
        AABB box = entity.getBoundingBox().minmax(entity.envelopeBox()).inflate(0.75, 0, 0.75);
        return frustum.isVisible(box);
    }

    private BalloonRenderState extract(HotAirBalloon entity, float partialTicks) {
        state.ageInTicks = entity.tickCount + partialTicks;
        state.yRot = entity.getViewYRot(partialTicks);
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
        state.damageTime = Math.max(entity.getDamage() - partialTicks, 0.0F);
        state.heat = entity.heat();
        state.burning = entity.burnerLit();
        state.swayX = Mth.lerp(partialTicks, entity.swayXo, entity.swayX);
        state.swayZ = Mth.lerp(partialTicks, entity.swayZo, entity.swayZ);
        state.flame = Mth.lerp(partialTicks, entity.flameO, entity.flame);
        state.inflate = entity.inflateProgress(partialTicks);
        state.hasChest = entity.hasChest();
        EnvelopeLook look = entity.look();
        state.primary = 0xFF000000 | look.base().getTextureDiffuseColor();
        state.secondary = 0xFF000000 | look.stripes().getTextureDiffuseColor();
        state.emblemCount = 0;
        if (look.emblemBase().isPresent() && !look.emblem().layers().isEmpty()) {
            var layers = look.emblem().layers();
            EmblemTextures.Fit fit = EmblemTextures.fit(layers.stream().map(l -> l.pattern().value().assetId()).toList());
            if (fit != null) {
                for (BannerPatternLayers.Layer layer : layers) {
                    ResourceLocation tex = EmblemTextures.pattern(layer.pattern().value().assetId(), fit);
                    if (tex != null) add(state, tex, 0xFF000000 | layer.color().getTextureDiffuseColor());
                }
            }
        }
        return state;
    }

    private static void add(BalloonRenderState state, ResourceLocation texture, int color) {
        if (state.emblemCount >= state.emblemTextures.length) return;
        state.emblemTextures[state.emblemCount] = texture;
        state.emblemColors[state.emblemCount++] = color;
    }
}
