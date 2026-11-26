package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;

// The few of 1.20.5's ByteBufCodecs the mod uses (dev/renames.json points the imports here).
public final class ByteBufCodecs {
    private ByteBufCodecs() {}

    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT = StreamCodec.of((b, v) -> b.writeVarInt(v), FriendlyByteBuf::readVarInt);
    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.of((b, v) -> b.writeBoolean(v), FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, DyeColor> DYE = StreamCodec.of((b, v) -> b.writeEnum(v), b -> b.readEnum(DyeColor.class));

    public static <V> StreamCodec<FriendlyByteBuf, Optional<V>> optional(StreamCodec<FriendlyByteBuf, V> codec) {
        return StreamCodec.of((b, v) -> {
            b.writeBoolean(v.isPresent());
            v.ifPresent(x -> codec.encode(b, x));
        }, b -> b.readBoolean() ? Optional.of(codec.decode(b)) : Optional.empty());
    }
}
*///?}
