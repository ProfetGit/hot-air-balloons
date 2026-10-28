package io.github.profetgit.hotairballoons;

import io.github.profetgit.hotairballoons.block.MooringPostBlock;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.item.BalloonItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** Every registered thing: the balloon entity type, its item and the mooring post. */
public final class ModContent {
    public static final ResourceKey<EntityType<?>> BALLOON_KEY = ResourceKey.create(Registries.ENTITY_TYPE, HotAirBalloons.id("hot_air_balloon"));
    public static final EntityType<HotAirBalloon> BALLOON = Registry.register(BuiltInRegistries.ENTITY_TYPE, BALLOON_KEY,
        EntityType.Builder.<HotAirBalloon>of(HotAirBalloon::new, MobCategory.MISC)
            .noLootTable()
            .sized(1.75F, 1.0F)
            .eyeHeight(0.9F)
            .clientTrackingRange(12)
            .build(BALLOON_KEY));

    private static final ResourceKey<Item> BALLOON_ITEM_KEY = ResourceKey.create(Registries.ITEM, HotAirBalloons.id("hot_air_balloon"));
    public static final Item BALLOON_ITEM = Registry.register(BuiltInRegistries.ITEM, BALLOON_ITEM_KEY,
        new BalloonItem(new Item.Properties().setId(BALLOON_ITEM_KEY).stacksTo(1)));

    private static final ResourceKey<Block> POST_KEY = ResourceKey.create(Registries.BLOCK, HotAirBalloons.id("mooring_post"));
    public static final Block MOORING_POST = Registry.register(BuiltInRegistries.BLOCK, POST_KEY,
        new MooringPostBlock(BlockBehaviour.Properties.of().setId(POST_KEY).mapColor(MapColor.WOOD).strength(2.0F, 3.0F)
            .sound(SoundType.WOOD).ignitedByLava().instrument(NoteBlockInstrument.BASS).forceSolidOn()));
    private static final ResourceKey<Item> POST_ITEM_KEY = ResourceKey.create(Registries.ITEM, HotAirBalloons.id("mooring_post"));
    public static final Item MOORING_POST_ITEM = Registry.register(BuiltInRegistries.ITEM, POST_ITEM_KEY,
        new BlockItem(MOORING_POST, new Item.Properties().setId(POST_ITEM_KEY).useBlockDescriptionPrefix()));

    public static final SimpleParticleType WIND_WISP = Registry.register(BuiltInRegistries.PARTICLE_TYPE, HotAirBalloons.id("wind_wisp"),
        FabricParticleTypes.simple());

    private ModContent() {}

    static void init() {
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
            .register(out -> {
                out.accept(BALLOON_ITEM);
                out.accept(MOORING_POST_ITEM);
            });
    }
}
