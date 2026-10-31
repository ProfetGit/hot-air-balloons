package io.github.profetgit.hotairballoons.mixin;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Model Features (Fresh Animations) poses riders from its own {@code is_riding} variable, which reads
 * {@code isPassenger()} through the method EMF adds to Entity, not the vanilla render state, so the pilot sat in the
 * air; and a non-riding player off the ground gets the jump/fall pose (body dropped below the basket), so the pilot is
 * on the ground too. Applied after EMF's mixin (higher priority) so the methods exist; does nothing without EMF.
 */
@Mixin(value = Entity.class, priority = 1500)
public abstract class EmfEntityMixin {
    @Inject(method = "emf$hasVehicle", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hotairballoons$standInBasket(CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).getVehicle() instanceof HotAirBalloon) cir.setReturnValue(false);
    }

    @Inject(method = "emf$isOnGround", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hotairballoons$onBasketFloor(CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).getVehicle() instanceof HotAirBalloon) cir.setReturnValue(true);
    }
}
