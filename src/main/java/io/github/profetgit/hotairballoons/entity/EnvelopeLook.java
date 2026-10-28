package io.github.profetgit.hotairballoons.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * How an envelope looks: the base gores' dye, the stripe gores' dye, and an optional banner emblem (the banner's base
 * colour plus its pattern layers) on the front and back. Lives on the balloon entity (synced) and on its item.
 */
public record EnvelopeLook(DyeColor base, DyeColor stripes, Optional<DyeColor> emblemBase, BannerPatternLayers emblem) {
    public static final EnvelopeLook DEFAULT = new EnvelopeLook(DyeColor.WHITE, DyeColor.RED, Optional.empty(), BannerPatternLayers.EMPTY);
    /** Vanilla banners stop at 6 pattern layers; so does the emblem. */
    public static final int MAX_LAYERS = 6;

    public static final Codec<EnvelopeLook> CODEC = RecordCodecBuilder.create(i -> i.group(
        DyeColor.CODEC.optionalFieldOf("base", DyeColor.WHITE).forGetter(EnvelopeLook::base),
        DyeColor.CODEC.optionalFieldOf("stripes", DyeColor.RED).forGetter(EnvelopeLook::stripes),
        DyeColor.CODEC.optionalFieldOf("emblem_base").forGetter(EnvelopeLook::emblemBase),
        BannerPatternLayers.CODEC.optionalFieldOf("emblem", BannerPatternLayers.EMPTY).forGetter(EnvelopeLook::emblem)
    ).apply(i, EnvelopeLook::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnvelopeLook> STREAM_CODEC = StreamCodec.composite(
        DyeColor.STREAM_CODEC, EnvelopeLook::base,
        DyeColor.STREAM_CODEC, EnvelopeLook::stripes,
        ByteBufCodecs.optional(DyeColor.STREAM_CODEC), EnvelopeLook::emblemBase,
        BannerPatternLayers.STREAM_CODEC, EnvelopeLook::emblem,
        EnvelopeLook::new);

    public EnvelopeLook withBase(DyeColor c) {
        return new EnvelopeLook(c, stripes, emblemBase, emblem);
    }

    public EnvelopeLook withStripes(DyeColor c) {
        return new EnvelopeLook(base, c, emblemBase, emblem);
    }

    /**
     * A banner put on the balloon: the envelope takes the banner's colours (base gores = the banner's base colour,
     * stripes = its first pattern colour that differs from it) and its patterns become the emblem, so the logo sits on
     * the envelope instead of on a patch of another colour. Dyes still recolour the envelope afterwards.
     */
    public EnvelopeLook wearing(DyeColor bannerBase, BannerPatternLayers patterns) {
        DyeColor accent = patterns.layers().stream().map(BannerPatternLayers.Layer::color).filter(c -> c != bannerBase).findFirst().orElse(bannerBase);
        return new EnvelopeLook(bannerBase, accent, emblemBase, emblem).withEmblem(bannerBase, patterns);
    }

    public EnvelopeLook withEmblem(DyeColor bannerBase, BannerPatternLayers patterns) {
        var layers = patterns.layers();
        if (layers.size() > MAX_LAYERS) layers = layers.subList(0, MAX_LAYERS);
        return new EnvelopeLook(base, stripes, Optional.of(bannerBase), new BannerPatternLayers(layers));
    }

    public EnvelopeLook withoutEmblem() {
        return new EnvelopeLook(base, stripes, Optional.empty(), BannerPatternLayers.EMPTY);
    }

    public void addToTooltip(Consumer<Component> out) {
        out.accept(Component.translatable("item.hotairballoons.hot_air_balloon.look",
            Component.translatable("color.minecraft." + base.getSerializedName()),
            Component.translatable("color.minecraft." + stripes.getSerializedName())).withStyle(ChatFormatting.GRAY));
        emblemBase.ifPresent(c -> out.accept(Component.translatable("item.hotairballoons.hot_air_balloon.emblem",
            Component.translatable("color.minecraft." + c.getSerializedName()), emblem.layers().size()).withStyle(ChatFormatting.GRAY)));
    }
}
