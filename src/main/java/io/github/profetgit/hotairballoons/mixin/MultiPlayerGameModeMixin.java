package io.github.profetgit.hotairballoons.mixin;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.client.HotAirBalloonsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

    /** A click at a balloon's envelope acts like a click on its basket (reached only when vanilla found nothing to use). */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void hotairballoons$envelopeClick(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult r = HotAirBalloonsClient.envelopeClick(player, player.level(), hand);
        if (r != InteractionResult.PASS) cir.setReturnValue(r);
    }

    @Inject(method = "isServerControlledInventory", at = @At("RETURN"), cancellable = true)
    private void hotairballoons$ownInventory(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && minecraft.player.getVehicle() instanceof HotAirBalloon b && !b.hasChest()) cir.setReturnValue(false);
    }
}
