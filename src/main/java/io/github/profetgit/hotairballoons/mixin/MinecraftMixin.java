package io.github.profetgit.hotairballoons.mixin;

import io.github.profetgit.hotairballoons.selftest.SelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void hotairballoons$frame(boolean advance, CallbackInfo ci) {
        if (SelfTest.active()) SelfTest.onFrame((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hotairballoons$tick(CallbackInfo ci) {
        if (SelfTest.active()) SelfTest.onClientTick((Minecraft) (Object) this);
    }
}
