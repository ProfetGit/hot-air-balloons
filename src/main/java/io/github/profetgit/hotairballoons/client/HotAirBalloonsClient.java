package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import org.jspecify.annotations.Nullable;

/** Client entry: renderer, model layer and the pilot's keys. */
public final class HotAirBalloonsClient implements ClientModInitializer {
    /** Test driver override for the pilot's keys; null = the real keyboard. */
    public static @Nullable Input testInput;
    private static @Nullable BalloonInput sent;
    private static int sinceSent;

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(BalloonModel.LAYER, BalloonModel::createLayer);
        EntityRendererRegistry.register(ModContent.BALLOON, BalloonRenderer::new);
        HotAirBalloon.localInput = HotAirBalloonsClient::keys;
        ClientTickEvents.END_CLIENT_TICK.register(HotAirBalloonsClient::sendKeys);
    }

    private static Input keys() {
        if (testInput != null) return testInput;
        var p = Minecraft.getInstance().player;
        return p == null ? Input.EMPTY : p.input.keyPresses;
    }

    private static void sendKeys(Minecraft mc) {
        if (mc.player == null || !(mc.player.getVehicle() instanceof HotAirBalloon b) || b.getControllingPassenger() != mc.player) {
            sent = null;
            return;
        }
        Input k = keys();
        BalloonInput now = new BalloonInput(k.jump(), k.sprint());
        if (now.equals(sent) && ++sinceSent < 20) return;
        if (!ClientPlayNetworking.canSend(BalloonInput.TYPE)) return;
        ClientPlayNetworking.send(now);
        sent = now;
        sinceSent = 0;
    }
}
