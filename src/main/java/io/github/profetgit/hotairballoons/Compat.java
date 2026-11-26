package io.github.profetgit.hotairballoons;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/** Lookups that differ between the supported Minecraft versions. */
public final class Compat {
    private Compat() {}

    public static Item dye(DyeColor color) {
        //? if >=26.2 {
        return net.minecraft.world.item.Items.DYE.pick(color);
        //?} else {
        /*return item(color.getSerializedName() + "_dye");
        *///?}
    }

    public static Item banner(DyeColor color) {
        //? if >=26.2 {
        return net.minecraft.world.item.Items.BANNER.pick(color);
        //?} else {
        /*return item(color.getSerializedName() + "_banner");
        *///?}
    }

    //? if >=1.21.2 <26.2 {
    /*private static Item item(String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(path));
    }
    *///?}
    //? if <1.21.2 {
    /*private static Item item(String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.withDefaultNamespace(path));
    }
    *///?}

    // Furnace burn time before 1.21.2 (no FuelValues): Forge's entry class replaces this with its own hook.
    //? if <1.21.2 && neoforge {
    /*public static java.util.function.ToIntFunction<net.minecraft.world.item.ItemStack> fuelTime =
        stack -> stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
    *///?}
    //? if <1.21.2 && !neoforge {
    /*public static java.util.function.ToIntFunction<net.minecraft.world.item.ItemStack> fuelTime =
        stack -> net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
    *///?}
}
