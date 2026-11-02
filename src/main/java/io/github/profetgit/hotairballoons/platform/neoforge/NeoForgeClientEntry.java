package io.github.profetgit.hotairballoons.platform.neoforge;

//? neoforge {
/*import io.github.profetgit.hotairballoons.HotAirBalloons;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@Mod(value = HotAirBalloons.ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntry {
    public NeoForgeClientEntry(IEventBus modBus, ModContainer container) {
        HotAirBalloonsClient.init();
        Net.send = ClientPacketDistributor::sendToServer;
        Net.canSend = t -> {
            var conn = Minecraft.getInstance().getConnection();
            return conn != null && NetworkRegistry.hasChannel(conn, t.id());
        };
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, event -> {
            event.registerLayerDefinition(BalloonModel.LAYER, BalloonModel::createLayer);
            event.registerLayerDefinition(BalloonModel.FLAME_LAYER, BalloonModel::createFlameLayer);
            event.registerLayerDefinition(BalloonModel.ENVELOPE_LAYER, BalloonModel::createEnvelopeLayer);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
            event -> event.registerEntityRenderer(ModContent.BALLOON, BalloonRenderer::new));
        modBus.addListener(RegisterParticleProvidersEvent.class,
            event -> event.registerSpriteSet(ModContent.WIND_LINE, WindLineParticle.Provider::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
            event -> event.registerAbove(VanillaGuiLayers.HOTBAR, HotAirBalloons.id("gauges"), BalloonHud::draw));
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(HotAirBalloons.id("emblems"),
            (ResourceManagerReloadListener) rm -> EmblemTextures.clear()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> HotAirBalloonsClient.sendKeys(Minecraft.getInstance()));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigScreen(parent));
    }
}
*///?}
