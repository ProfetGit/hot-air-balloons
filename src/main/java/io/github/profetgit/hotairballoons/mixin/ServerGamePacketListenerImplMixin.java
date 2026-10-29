package io.github.profetgit.hotairballoons.mixin;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player standing in a balloon basket isn't floating: vanilla kicks players who hang in the air for 80 ticks unless
 * blocks are around, and the walker's own view of the moving floor lags the server's ({@link HotAirBalloon#atBasket}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "noBlocksAround", at = @At("RETURN"), cancellable = true)
    private void hotairballoons$basketFloor(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof Player p && !p.isPassenger() && HotAirBalloon.atBasket(p)) cir.setReturnValue(false);
    }
}
