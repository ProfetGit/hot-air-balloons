package io.github.profetgit.hotairballoons.platform.forge;

import io.github.profetgit.hotairballoons.Compat;
import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.net.BalloonClick;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import io.github.profetgit.hotairballoons.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

// Forge 47 (and NeoForge 47.1, which runs this jar)
@Mod(HotAirBalloons.ID)
public final class ForgeEntry {
    private static final String PROTOCOL = "1";
    static SimpleChannel channel;

    public ForgeEntry() {
        HotAirBalloons.init("forge", FMLPaths.CONFIGDIR.get());
        Compat.fuelTime = stack -> ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterEvent e) -> register(e));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> tabs(e));
        channel = NetworkRegistry.newSimpleChannel(HotAirBalloons.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.messageBuilder(BalloonInput.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder((msg, buf) -> BalloonInput.CODEC.encode(buf, msg))
            .decoder(BalloonInput.CODEC::decode).consumerMainThread((msg, ctx) -> Net.onServer(ctx.get().getSender(), msg)).add();
        channel.messageBuilder(BalloonClick.class, 1, NetworkDirection.PLAY_TO_SERVER).encoder((msg, buf) -> BalloonClick.CODEC.encode(buf, msg))
            .decoder(BalloonClick.CODEC::decode).consumerMainThread((msg, ctx) -> Net.onServer(ctx.get().getSender(), msg)).add();
        if (FMLEnvironment.dist.isClient()) ForgeClient.register(modBus);
    }

    private record Entry(ResourceKey<? extends Registry<?>> registry, ResourceKey<?> key, Object value) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean built;

    // RegisterEvent fires once per registry: build the content once, then hand each registry its own entries.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(RegisterEvent event) {
        if (!built) {
            built = true;
            ModContent.register(new ModContent.Registrar() {
                @Override
                public <B, T extends B> T add(ResourceKey<? extends Registry<B>> registry, ResourceKey<B> key, T value) {
                    ENTRIES.add(new Entry(registry, key, value));
                    return value;
                }
            });
        }
        for (Entry e : ENTRIES) {
            if (e.registry().equals(event.getRegistryKey())) {
                event.register((ResourceKey) e.registry(), e.key().location(), () -> e.value());
            }
        }
        event.register(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, HotAirBalloons.id("envelope_look"), () -> ModContent.LOOK_SERIALIZER);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ModContent.BALLOON_ITEM);
    }
}
