package io.github.profetgit.hotairballoons.mixin;

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
