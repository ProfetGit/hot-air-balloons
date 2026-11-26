package io.github.profetgit.hotairballoons;

import io.github.profetgit.hotairballoons.entity.EnvelopeLook;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.item.BalloonItem;
import net.minecraft.core.Registry;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
//?}
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/** Every registered thing. Each loader calls {@link #register} with its own way of adding to a registry. */
public final class ModContent {
    /** Created here, registered by the platform (id {@code hotairballoons:envelope_look}) before the entity is used. */
    //? if >=1.20.5 {
    public static final EntityDataSerializer<EnvelopeLook> LOOK_SERIALIZER = EntityDataSerializer.forValueType(EnvelopeLook.STREAM_CODEC);

    public static DataComponentType<EnvelopeLook> ENVELOPE;
    //?} else {
    /*public static final EntityDataSerializer<EnvelopeLook> LOOK_SERIALIZER = EntityDataSerializer.simple(
        (buf, look) -> EnvelopeLook.STREAM_CODEC.encode(buf, look), EnvelopeLook.STREAM_CODEC::decode);
    *///?}
    public static EntityType<HotAirBalloon> BALLOON;
    public static Item BALLOON_ITEM;
    public static SimpleParticleType WIND_LINE;

    private ModContent() {}

    public interface Registrar {
        <B, T extends B> T add(ResourceKey<? extends Registry<B>> registry, ResourceKey<B> key, T value);
    }

    public static void register(Registrar r) {
        //? if >=1.20.5 {
        ENVELOPE = r.add(Registries.DATA_COMPONENT_TYPE, ResourceKey.create(Registries.DATA_COMPONENT_TYPE, HotAirBalloons.id("envelope")),
            DataComponentType.<EnvelopeLook>builder().persistent(EnvelopeLook.CODEC).networkSynchronized(EnvelopeLook.STREAM_CODEC).build());
        //?}

        ResourceKey<EntityType<?>> balloonKey = ResourceKey.create(Registries.ENTITY_TYPE, HotAirBalloons.id("hot_air_balloon"));
        BALLOON = r.add(Registries.ENTITY_TYPE, balloonKey,
            EntityType.Builder.<HotAirBalloon>of(HotAirBalloon::new, MobCategory.MISC)
                .noLootTable()
                .sized(1.75F, 1.0F)
                .eyeHeight(0.9F)
                .clientTrackingRange(12)
                //? if >=1.21.2 {
                .build(balloonKey));
                //?} else if >=1.20.5 {
                /*// A null id skips the data fixer lookup in Fabric API, which logs an ERROR for a modded id (NeoForge and
                // Forge don't).
                .build(HotAirBalloons.loader().equals("fabric") ? null : balloonKey.location().toString()));
                *///?} else {
                /*// The id only checks the data fixer schema. On Fabric 1.20.1 null crashes and a modded id logs an ERROR, so it
                // gets a vanilla one there.
                .build(HotAirBalloons.loader().equals("fabric") ? "minecraft:boat" : balloonKey.location().toString()));
                *///?}

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, HotAirBalloons.id("hot_air_balloon"));
        BALLOON_ITEM = r.add(Registries.ITEM, itemKey, new BalloonItem(new Item.Properties().setId(itemKey).stacksTo(1)));

        WIND_LINE = r.add(Registries.PARTICLE_TYPE, ResourceKey.create(Registries.PARTICLE_TYPE, HotAirBalloons.id("wind_line")),
            new SimpleParticleType(false) {});
    }
}
