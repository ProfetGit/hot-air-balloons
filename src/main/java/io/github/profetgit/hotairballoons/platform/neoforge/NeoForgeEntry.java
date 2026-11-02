package io.github.profetgit.hotairballoons.platform.neoforge;

//? neoforge {
/*import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.net.BalloonClick;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import io.github.profetgit.hotairballoons.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(HotAirBalloons.ID)
public final class NeoForgeEntry {
    public NeoForgeEntry(IEventBus modBus) {
        HotAirBalloons.init("neoforge", FMLPaths.CONFIGDIR.get());
        modBus.addListener(NeoForgeEntry::register);
        modBus.addListener(NeoForgeEntry::tabs);
        modBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgeEntry::payloads);
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
        event.register(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, HotAirBalloons.id("envelope_look"), () -> ModContent.LOOK_SERIALIZER);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(BalloonInput.TYPE, BalloonInput.CODEC, (p, ctx) -> Net.onServer((ServerPlayer) ctx.player(), p));
        r.playToServer(BalloonClick.TYPE, BalloonClick.CODEC, (p, ctx) -> Net.onServer((ServerPlayer) ctx.player(), p));
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ModContent.BALLOON_ITEM);
    }
}
*///?}
