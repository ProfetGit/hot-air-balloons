package io.github.profetgit.hotairballoons.mixin;

//? if <1.20.5 {
/*import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Clicking a knot before 1.20.5 only knows mobs: balloons on your lead move to it, and a creative click that takes the
// knot off drops their ropes without an item, as newer vanilla does for every leashable.
@Mixin(LeashFenceKnotEntity.class)
public abstract class LegacyLeashKnotMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void hotairballoons$balloons(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Entity knot = (Entity) (Object) this;
        if (knot.level().isClientSide()) return;
        List<HotAirBalloon> balloons = knot.level().getEntitiesOfClass(HotAirBalloon.class, knot.getBoundingBox().inflate(7.0));
        List<Mob> mobs = knot.level().getEntitiesOfClass(Mob.class, knot.getBoundingBox().inflate(7.0));
        boolean tied = false;
        for (HotAirBalloon b : balloons) {
            if (b.getLeashHolder() == player) {
                b.setLeashedTo(knot, true);
                tied = true;
            }
        }
        if (tied) {
            for (Mob m : mobs) if (m.getLeashHolder() == player) m.setLeashedTo(knot, true);
            knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
            cir.setReturnValue(InteractionResult.CONSUME);
            return;
        }
        if (!player.getAbilities().instabuild || mobs.stream().anyMatch(m -> m.getLeashHolder() == player)) return;
        boolean dropped = false;
        for (HotAirBalloon b : balloons) {
            if (b.getLeashHolder() == knot) {
                b.dropLeash(true, false);
                dropped = true;
            }
        }
        if (dropped && mobs.stream().noneMatch(m -> m.getLeashHolder() == knot)) knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
    }
}
*///?}
