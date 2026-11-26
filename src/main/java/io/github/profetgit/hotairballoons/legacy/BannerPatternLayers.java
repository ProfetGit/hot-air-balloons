package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;

// 1.20.5's banner pattern layers, saved the same way (dev/renames.json points the imports here).
public record BannerPatternLayers(List<Layer> layers) {
    public static final BannerPatternLayers EMPTY = new BannerPatternLayers(List.of());
    public static final Codec<BannerPatternLayers> CODEC = Layer.CODEC.listOf().xmap(BannerPatternLayers::new, BannerPatternLayers::layers);
    public static final StreamCodec<FriendlyByteBuf, BannerPatternLayers> STREAM_CODEC = StreamCodec.of(
        (buf, v) -> buf.writeCollection(v.layers, (b, layer) -> {
            b.writeId(BuiltInRegistries.BANNER_PATTERN.asHolderIdMap(), layer.pattern);
            b.writeEnum(layer.color);
        }),
        buf -> new BannerPatternLayers(buf.readList(b -> new Layer(b.readById(BuiltInRegistries.BANNER_PATTERN.asHolderIdMap()), b.readEnum(DyeColor.class)))));

    public record Layer(Holder<BannerPattern> pattern, DyeColor color) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.BANNER_PATTERN.holderByNameCodec().fieldOf("pattern").forGetter(Layer::pattern),
            DyeColor.CODEC.fieldOf("color").forGetter(Layer::color)
        ).apply(i, Layer::new));

        // The pattern's texture name: textures/entity/banner/<path>.png.
        public ResourceLocation assetId() {
            return pattern.unwrapKey().map(ResourceKey::location).orElseThrow();
        }
    }

    public static final class Builder {
        private final List<Layer> layers = new ArrayList<>();

        public Builder add(Holder<BannerPattern> pattern, DyeColor color) {
            layers.add(new Layer(pattern, color));
            return this;
        }

        public BannerPatternLayers build() {
            return new BannerPatternLayers(List.copyOf(layers));
        }
    }
}
*///?}
