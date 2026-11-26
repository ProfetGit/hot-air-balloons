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
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
//?}
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
        //? if >=1.20.5 {
        Net.send = ClientPlayNetworking::send;
        Net.canSend = t -> ClientPlayNetworking.canSend((net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type) t);
        //?} else {
        /*Net.send = payload -> {
            net.minecraft.network.FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            if (payload instanceof io.github.profetgit.hotairballoons.net.BalloonInput m) {
                io.github.profetgit.hotairballoons.net.BalloonInput.CODEC.encode(buf, m);
            } else if (payload instanceof io.github.profetgit.hotairballoons.net.BalloonClick m) {
                io.github.profetgit.hotairballoons.net.BalloonClick.CODEC.encode(buf, m);
            }
            ClientPlayNetworking.send(payload.type().id(), buf);
        };
        Net.canSend = t -> ClientPlayNetworking.canSend(t.id());
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(HotAirBalloonsClient::sendKeys);
        //? if >=1.21.6 {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HotAirBalloons.id("gauges"), BalloonHud::draw);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            HotAirBalloons.id("emblems"), (ResourceManagerReloadListener) rm -> EmblemTextures.clear());
        //?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(BalloonHud::draw);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override
                public net.minecraft.resources.Identifier getFabricId() {
                    return HotAirBalloons.id("emblems");
                }

                @Override
                public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                    EmblemTextures.clear();
                }
            });
        *///?}
    }
}
//?}
