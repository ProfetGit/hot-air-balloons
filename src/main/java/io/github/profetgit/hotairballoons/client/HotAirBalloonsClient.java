package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.entity.Wind;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Input;
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
        ParticleProviderRegistry.getInstance().register(ModContent.WIND_LINE, WindLineParticle.Provider::new);
        HotAirBalloon.clientTick = b -> {
            BalloonSounds.tick(b);
            spawnWindLines(b);
        };
        HotAirBalloon.localInput = HotAirBalloonsClient::keys;
        HotAirBalloon.localPlayer = () -> Minecraft.getInstance().player;
        ClientTickEvents.END_CLIENT_TICK.register(HotAirBalloonsClient::sendKeys);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR, io.github.profetgit.hotairballoons.HotAirBalloons.id("gauges"), BalloonHud::draw);
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
            io.github.profetgit.hotairballoons.HotAirBalloons.id("emblems"),
            (net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> EmblemTextures.clear());
    }

    private static final double MIN_WIND = 0.004;

    private static void spawnWindLines(HotAirBalloon b) {
        Minecraft mc = Minecraft.getInstance();
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL || b.onGround() || !(b.level() instanceof ClientLevel level)) return;
        if (level.getGameTime() % 20 == 0) WindLineParticle.purge(level);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, b.getBlockX(), b.getBlockZ());
        if (b.getY() - ground < 2.0) return;
        RandomSource rnd = b.getRandom();
        double share = status == ParticleStatus.DECREASED ? 0.4 : 1.0;
        boolean piloted = mc.player != null && mc.player.getVehicle() == b;

        Vec3 wind = b.wind();
        double speed = wind.horizontalDistance();
        if (speed >= MIN_WIND) {
            // Up to 2: a gust blows harder than the plain top wind, and the wisps grow and multiply with it.
            float s = (float) Mth.clamp(speed / Wind.MAX_SPEED, 0.0, 2.0);
            for (int n = count(Mth.lerp(s, 0.08F, 0.3F) * share, rnd); n > 0; n--) {
                boolean basket = rnd.nextDouble() < (piloted ? 0.75 : 0.15);
                Vec3 at = basket ? basketStart(wind, speed, rnd) : envelopeStart(wind, speed, rnd);
                WindLineParticle.spawn(level, b, 0, at, Mth.lerp(s, 0.45F, 0.9F));
            }
        }
        for (int row = 1; row <= 2; row++) {
            Vec3 w = b.windAt(row);
            double sp = w.horizontalDistance();
            if (sp < MIN_WIND) continue;
            float s = windFraction(sp);
            for (int n = count(Mth.lerp(s, 0.04F, 0.15F) * share, rnd); n > 0; n--) {
                double theta = rnd.nextDouble() * Math.PI * 2;
                double r = 4.0 + rnd.nextDouble() * 10.0;
                double dy = HotAirBalloon.LADDER[row] + (rnd.nextDouble() * 4.0 - 2.0);
                double ox = r * Math.cos(theta);
                double oz = r * Math.sin(theta);
                if (row == 1 && b.getY() + dy - level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    Mth.floor(b.getX() + ox), Mth.floor(b.getZ() + oz)) < 1.0) continue;
                WindLineParticle.spawn(level, b, HotAirBalloon.LADDER[row], new Vec3(ox, dy, oz), Mth.lerp(s, 0.9F, 1.4F));
            }
        }
    }

    private static float windFraction(double speed) {
        return (float) Mth.clamp(speed / Wind.MAX_SPEED, 0.0, 1.0);
    }

    private static int count(double rate, RandomSource rnd) {
        int n = (int) rate;
        return rnd.nextDouble() < rate - n ? n + 1 : n;
    }

    /** Eye-level start upwind of the basket, off to one side so the line passes the camera without cutting the basket. */
    private static Vec3 basketStart(Vec3 wind, double speed, RandomSource rnd) {
        double dx = wind.x / speed, dz = wind.z / speed;
        double a = -2.0 - rnd.nextDouble() * 5.0;
        double side = (1.8 + rnd.nextDouble() * 5.2) * (rnd.nextBoolean() ? 1 : -1);
        return new Vec3(dx * a - dz * side, -1.0 + rnd.nextDouble() * 5.0, dz * a + dx * side);
    }

    /** Start around the envelope, on its upwind half. */
    private static Vec3 envelopeStart(Vec3 wind, double speed, RandomSource rnd) {
        double dx = wind.x / speed, dz = wind.z / speed;
        double theta = rnd.nextDouble() * Math.PI * 2;
        double r = 3.6 + rnd.nextDouble() * 3.4;
        double ox = r * Math.cos(theta), oz = r * Math.sin(theta);
        if (ox * dx + oz * dz > 0) {
            ox = -ox;
            oz = -oz;
        }
        return new Vec3(ox, 4.0 + rnd.nextDouble() * 8.0, oz);
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
