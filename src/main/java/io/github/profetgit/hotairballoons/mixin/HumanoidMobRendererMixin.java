package io.github.profetgit.hotairballoons.mixin;

//? if >=1.21.2 {
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Riders stand in the basket instead of taking the sitting pose (players go through this method too). */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {
    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
    private static void hotairballoons$standInBasket(LivingEntity entity, HumanoidRenderState state, float partialTicks,
                                                     ItemModelResolver itemModelResolver, CallbackInfo ci) {
        if (entity.getVehicle() instanceof HotAirBalloon) state.isPassenger = false;
    }
}
//?} else {
/*import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Riders stand in the basket instead of taking the sitting pose (humanoid models, players included, as on newer versions).
@Mixin(LivingEntityRenderer.class)
public abstract class HumanoidMobRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/EntityModel;riding:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void hotairballoons$standInBasket(T entity, float yaw, float partialTicks, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                              net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (model instanceof HumanoidModel<?> && entity.getVehicle() instanceof HotAirBalloon) model.riding = false;
    }
}
*///?}
