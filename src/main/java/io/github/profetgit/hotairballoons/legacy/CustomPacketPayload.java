package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import net.minecraft.resources.ResourceLocation;

// Stands in for 1.20.5's CustomPacketPayload (dev/renames.json points the imports here): a payload is its channel id.
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
*///?}
