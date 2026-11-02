package io.github.profetgit.hotairballoons.platform.fabric;

//? fabric {
import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.net.BalloonClick;
import io.github.profetgit.hotairballoons.net.BalloonInput;
import io.github.profetgit.hotairballoons.net.Net;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class FabricEntry implements ModInitializer {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitialize() {
        HotAirBalloons.init("fabric", FabricLoader.getInstance().getConfigDir());
        FabricEntityDataRegistry.register(HotAirBalloons.id("envelope_look"), ModContent.LOOK_SERIALIZER);
        ModContent.register(new ModContent.Registrar() {
            @Override
            public <B, T extends B> T add(ResourceKey<? extends Registry<B>> registry, ResourceKey<B> key, T value) {
                Registry<B> target = (Registry<B>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) registry);
                return Registry.register(target, key, value);
            }
        });
        toServer(BalloonInput.TYPE, BalloonInput.CODEC);
        toServer(BalloonClick.TYPE, BalloonClick.CODEC);
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
            .register(out -> out.accept(ModContent.BALLOON_ITEM));
    }

    private static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> ctx.server().execute(() -> Net.onServer(ctx.player(), payload)));
    }
}
//?}
