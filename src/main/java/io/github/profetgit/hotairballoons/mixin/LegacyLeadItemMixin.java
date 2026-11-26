package io.github.profetgit.hotairballoons.mixin;

//? if <1.20.5 {
/*import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Before 1.20.5 a fence takes only the mobs on your leads: balloons go with them, as newer vanilla ties every leashable.
@Mixin(LeadItem.class)
public abstract class LegacyLeadItemMixin {
    @Inject(method = "bindPlayerMobs", at = @At("RETURN"), cancellable = true)
    private static void hotairballoons$bindBalloons(Player player, Level level, BlockPos pos, CallbackInfoReturnable<InteractionResult> cir) {
        List<HotAirBalloon> balloons = level.getEntitiesOfClass(HotAirBalloon.class, new AABB(pos.getX() - 7.0, pos.getY() - 7.0,
            pos.getZ() - 7.0, pos.getX() + 7.0, pos.getY() + 7.0, pos.getZ() + 7.0), b -> b.getLeashHolder() == player);
        if (balloons.isEmpty()) return;
        boolean mobs = cir.getReturnValue() == InteractionResult.SUCCESS;
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, pos);
        if (!mobs) knot.playPlacementSound();
        for (HotAirBalloon b : balloons) b.setLeashedTo(knot, true);
        if (!mobs) level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
*///?}
