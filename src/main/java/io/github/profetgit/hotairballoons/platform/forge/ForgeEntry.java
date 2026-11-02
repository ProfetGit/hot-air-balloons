package io.github.profetgit.hotairballoons.platform.forge;

//? forge {
/*import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.net.BalloonClick;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import io.github.profetgit.hotairballoons.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod(HotAirBalloons.ID)
public final class ForgeEntry {
    static SimpleChannel channel;

    public ForgeEntry(FMLJavaModLoadingContext context) {
        HotAirBalloons.init("forge", FMLPaths.CONFIGDIR.get());
        RegisterEvent.getBus(context.getModBusGroup()).addListener(ForgeEntry::register);
        BuildCreativeModeTabContentsEvent.BUS.addListener(ForgeEntry::tabs);
        channel = ChannelBuilder.named(HotAirBalloons.id("main")).networkProtocolVersion(1).simpleChannel();
        channel.play().serverbound()
            .addMain(BalloonInput.class, BalloonInput.CODEC, (msg, ctx) -> Net.onServer(ctx.getSender(), msg))
            .addMain(BalloonClick.class, BalloonClick.CODEC, (msg, ctx) -> Net.onServer(ctx.getSender(), msg));
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) ForgeClient.register(context);
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
                event.register((ResourceKey) e.registry(), e.key().identifier(), () -> e.value());
            }
        }
        event.register(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, HotAirBalloons.id("envelope_look"), () -> ModContent.LOOK_SERIALIZER);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ModContent.BALLOON_ITEM);
    }
}
*///?}
