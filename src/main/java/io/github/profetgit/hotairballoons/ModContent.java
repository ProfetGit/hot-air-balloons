package io.github.profetgit.hotairballoons;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import io.github.profetgit.hotairballoons.item.BalloonItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/** Every registered thing: the balloon entity type and its item. */
public final class ModContent {
    public static final ResourceKey<EntityType<?>> BALLOON_KEY = ResourceKey.create(Registries.ENTITY_TYPE, HotAirBalloons.id("hot_air_balloon"));
    public static final EntityType<HotAirBalloon> BALLOON = Registry.register(BuiltInRegistries.ENTITY_TYPE, BALLOON_KEY,
        EntityType.Builder.<HotAirBalloon>of(HotAirBalloon::new, MobCategory.MISC)
            .noLootTable()
            .sized(1.75F, 1.25F)
            .eyeHeight(1.0F)
            .clientTrackingRange(12)
            .build(BALLOON_KEY));

    private static final ResourceKey<Item> BALLOON_ITEM_KEY = ResourceKey.create(Registries.ITEM, HotAirBalloons.id("hot_air_balloon"));
    public static final Item BALLOON_ITEM = Registry.register(BuiltInRegistries.ITEM, BALLOON_ITEM_KEY,
        new BalloonItem(new Item.Properties().setId(BALLOON_ITEM_KEY).stacksTo(1)));

    private ModContent() {}

    static void init() {
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
            .register(out -> out.accept(BALLOON_ITEM));
    }
}
