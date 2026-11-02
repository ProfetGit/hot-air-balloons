package io.github.profetgit.hotairballoons.platform.forge;

//? forge {
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
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;

/^* Client only (ForgeEntry calls it on the client). ^/
final class ForgeClient {
    private ForgeClient() {
    }

    static void register(FMLJavaModLoadingContext context) {
        HotAirBalloonsClient.init();
        Net.send = p -> ForgeEntry.channel.send(p, PacketDistributor.SERVER.noArg());
        Net.canSend = t -> {
            var conn = Minecraft.getInstance().getConnection();
            return conn != null && ForgeEntry.channel.isRemotePresent(conn.getConnection());
        };
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> {
            event.registerLayerDefinition(BalloonModel.LAYER, BalloonModel::createLayer);
            event.registerLayerDefinition(BalloonModel.FLAME_LAYER, BalloonModel::createFlameLayer);
            event.registerLayerDefinition(BalloonModel.ENVELOPE_LAYER, BalloonModel::createEnvelopeLayer);
        });
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(
            event -> event.registerEntityRenderer(ModContent.BALLOON, BalloonRenderer::new));
        RegisterParticleProvidersEvent.BUS.addListener(
            event -> event.registerSpriteSet(ModContent.WIND_LINE, WindLineParticle.Provider::new));
        AddGuiOverlayLayersEvent.BUS.addListener(
            event -> event.getLayeredDraw().add(HotAirBalloons.id("gauges"), BalloonHud::draw));
        RegisterClientReloadListenersEvent.BUS.addListener(
            event -> event.registerReloadListener((ResourceManagerReloadListener) rm -> EmblemTextures.clear()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> HotAirBalloonsClient.sendKeys(Minecraft.getInstance()));
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
    }
}
*///?}
