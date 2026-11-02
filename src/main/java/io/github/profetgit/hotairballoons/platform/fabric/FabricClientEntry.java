package io.github.profetgit.hotairballoons.platform.fabric;

//? fabric {
import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.client.BalloonHud;
import io.github.profetgit.hotairballoons.client.BalloonModel;
import io.github.profetgit.hotairballoons.client.BalloonRenderer;
import io.github.profetgit.hotairballoons.client.EmblemTextures;
import io.github.profetgit.hotairballoons.client.HotAirBalloonsClient;
import io.github.profetgit.hotairballoons.client.WindLineParticle;
import io.github.profetgit.hotairballoons.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class FabricClientEntry implements ClientModInitializer {
    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        HotAirBalloonsClient.init();
        ModelLayerRegistry.registerModelLayer(BalloonModel.LAYER, BalloonModel::createLayer);
        ModelLayerRegistry.registerModelLayer(BalloonModel.FLAME_LAYER, BalloonModel::createFlameLayer);
        ModelLayerRegistry.registerModelLayer(BalloonModel.ENVELOPE_LAYER, BalloonModel::createEnvelopeLayer);
        EntityRenderers.register(ModContent.BALLOON, BalloonRenderer::new);
        ParticleProviderRegistry.getInstance().register(ModContent.WIND_LINE, WindLineParticle.Provider::new);
        Net.send = ClientPlayNetworking::send;
        Net.canSend = t -> ClientPlayNetworking.canSend((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) t);
        ClientTickEvents.END_CLIENT_TICK.register(HotAirBalloonsClient::sendKeys);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HotAirBalloons.id("gauges"), BalloonHud::draw);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            HotAirBalloons.id("emblems"), (ResourceManagerReloadListener) rm -> EmblemTextures.clear());
    }
}
//?}
