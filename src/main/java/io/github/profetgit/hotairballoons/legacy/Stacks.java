package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.EnvelopeLook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

// What data components do from 1.20.5 on, as item NBT (dev/renames.json points the calls here).
public final class Stacks {
    private static final String LOOK = HotAirBalloons.ID + ":envelope";

    private Stacks() {}

    public static boolean hasLook(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(LOOK);
    }

    public static @Nullable EnvelopeLook lookOrNull(ItemStack stack) {
        if (!hasLook(stack)) return null;
        return EnvelopeLook.CODEC.parse(NbtOps.INSTANCE, stack.getTag().get(LOOK)).result().orElse(null);
    }

    public static EnvelopeLook look(ItemStack stack) {
        EnvelopeLook look = lookOrNull(stack);
        return look == null ? EnvelopeLook.DEFAULT : look;
    }

    public static void setLook(ItemStack stack, EnvelopeLook look) {
        EnvelopeLook.CODEC.encodeStart(NbtOps.INSTANCE, look).result().ifPresent(tag -> stack.addTagElement(LOOK, tag));
    }

    // A banner item's layers, in the loom's NBT (patterns by their short hash name).
    public static BannerPatternLayers patterns(ItemStack stack) {
        ListTag list = BannerBlockEntity.getItemPatterns(stack);
        if (list == null) return BannerPatternLayers.EMPTY;
        BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            var pattern = BannerPattern.byHash(t.getString("Pattern"));
            if (pattern != null) layers.add(pattern, DyeColor.byId(t.getInt("Color")));
        }
        return layers.build();
    }

    public static void setPatterns(ItemStack stack, BannerPatternLayers layers) {
        ListTag list = new ListTag();
        for (BannerPatternLayers.Layer layer : layers.layers()) {
            CompoundTag t = new CompoundTag();
            t.putString("Pattern", layer.pattern().value().getHashname());
            t.putInt("Color", layer.color().getId());
            list.add(t);
        }
        CompoundTag data = new CompoundTag();
        data.put("Patterns", (Tag) list);
        BlockItem.setBlockEntityData(stack, BlockEntityType.BANNER, data);
    }

    public static void consume(ItemStack stack, int count, Player player) {
        if (!player.getAbilities().instabuild) stack.shrink(count);
    }

    // 1.21's DyeColor.getTextureDiffuseColor: opaque ARGB.
    public static int diffuse(DyeColor color) {
        float[] c = color.getTextureDiffuseColors();
        return 0xFF000000 | Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
    }
}
*///?}
