package io.github.profetgit.hotairballoons.platform.forge;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.client.BalloonHud;
import io.github.profetgit.hotairballoons.client.BalloonModel;
import io.github.profetgit.hotairballoons.client.BalloonRenderer;
import io.github.profetgit.hotairballoons.client.ConfigScreen;
import io.github.profetgit.hotairballoons.client.EmblemTextures;
import io.github.profetgit.hotairballoons.client.HotAirBalloonsClient;
import io.github.profetgit.hotairballoons.client.WindLineParticle;
import io.github.profetgit.hotairballoons.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.network.PacketDistributor;

// Client only (ForgeEntry calls it on the client)
final class ForgeClient {
    private ForgeClient() {
    }

    static void register(IEventBus modBus) {
        HotAirBalloonsClient.init();
        Net.send = p -> ForgeEntry.channel.send(p, PacketDistributor.SERVER.noArg());
        Net.canSend = t -> {
            var conn = Minecraft.getInstance().getConnection();
            return conn != null && ForgeEntry.channel.isRemotePresent(conn.getConnection());
        };
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            event.registerLayerDefinition(BalloonModel.LAYER, BalloonModel::createLayer);
            event.registerLayerDefinition(BalloonModel.FLAME_LAYER, BalloonModel::createFlameLayer);
            event.registerLayerDefinition(BalloonModel.ENVELOPE_LAYER, BalloonModel::createEnvelopeLayer);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(ModContent.BALLOON, BalloonRenderer::new));
        modBus.addListener((RegisterParticleProvidersEvent event) -> event.registerSpriteSet(ModContent.WIND_LINE, WindLineParticle.Provider::new));
        modBus.addListener((AddGuiOverlayLayersEvent event) -> event.getLayeredDraw().add(HotAirBalloons.id("gauges"), BalloonHud::draw));
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener((ResourceManagerReloadListener) rm -> EmblemTextures.clear()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> HotAirBalloonsClient.sendKeys(Minecraft.getInstance()));
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
    }
}
