package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Client entry: renderer, model layer, particles, sounds and the pilot's keys. */
public final class HotAirBalloonsClient implements ClientModInitializer {
    /** Test driver override for the pilot's keys; null = the real keyboard. */
    public static @Nullable Input testInput;
    private static @Nullable BalloonInput sent;
    private static int sinceSent;

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(BalloonModel.LAYER, BalloonModel::createLayer);
        ModelLayerRegistry.registerModelLayer(BalloonModel.FLAME_LAYER, BalloonModel::createFlameLayer);
        ModelLayerRegistry.registerModelLayer(BalloonModel.ENVELOPE_LAYER, BalloonModel::createEnvelopeLayer);
        EntityRenderers.register(ModContent.BALLOON, BalloonRenderer::new);
        ParticleProviderRegistry.getInstance().register(ModContent.WIND_WISP, WindWispParticle.Provider::new);
        HotAirBalloon.clientTick = b -> {
            BalloonSounds.tick(b);
            spawnWisps(b);
        };
        HotAirBalloon.localInput = HotAirBalloonsClient::keys;
        ClientTickEvents.END_CLIENT_TICK.register(HotAirBalloonsClient::sendKeys);
    }

    private static void spawnWisps(HotAirBalloon b) {
        Minecraft mc = Minecraft.getInstance();
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL || b.onGround()) return;
        Vec3 wind = b.wind();
        double speed = wind.horizontalDistance();
        if (speed < 0.004) return;
        RandomSource rnd = b.getRandom();
        int gap = status == ParticleStatus.DECREASED ? 5 : 3;
        if (rnd.nextInt(gap) != 0) return;
        Level level = b.level();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, b.getBlockX(), b.getBlockZ());
        if (b.getY() - ground < 2.0) return;
        double theta = rnd.nextDouble() * Math.PI * 2;
        double cosPhi = rnd.nextDouble() * 2 - 1;
        double sinPhi = Math.sqrt(1 - cosPhi * cosPhi);
        double r = 3.0 + rnd.nextDouble() * 2.0;
        double x = b.getX() + r * sinPhi * Math.cos(theta);
        double y = b.getY() + 6.4 + Math.min(r * cosPhi, 3.0) * 1.0;
        double z = b.getZ() + r * sinPhi * Math.sin(theta);
        double k = 1.5 + rnd.nextDouble();
        level.addParticle(ModContent.WIND_WISP, x, Mth.clamp(y, b.getY() + 3.4, b.getY() + 9.4), z, wind.x * k, 0.0, wind.z * k);
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
