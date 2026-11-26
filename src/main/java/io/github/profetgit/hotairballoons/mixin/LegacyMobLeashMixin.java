package io.github.profetgit.hotairballoons.mixin;

//? if <1.20.5 {
/*import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Before 1.20.5 a mob saves its rope only for a living or hanging holder: save one tied to a balloon by its UUID too.
@Mixin(Mob.class)
public abstract class LegacyMobLeashMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void hotairballoons$saveBalloonRope(CompoundTag tag, CallbackInfo ci) {
        if (((Mob) (Object) this).getLeashHolder() instanceof HotAirBalloon b) {
            CompoundTag leash = new CompoundTag();
            leash.putUUID("UUID", b.getUUID());
            tag.put("Leash", leash);
        }
    }
}
*///?}
