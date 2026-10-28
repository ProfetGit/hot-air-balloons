package io.github.profetgit.hotairballoons.mixin;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A balloon has a vehicle inventory only with a chest aboard; without one the inventory key opens the player's own. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "isServerControlledInventory", at = @At("RETURN"), cancellable = true)
    private void hotairballoons$ownInventory(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && minecraft.player.getVehicle() instanceof HotAirBalloon b && !b.hasChest()) cir.setReturnValue(false);
    }
}
