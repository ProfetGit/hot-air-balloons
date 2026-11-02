package io.github.profetgit.hotairballoons.net;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pilot → server: burner and vent keys, sent when they change (and once a second), like vanilla's boat paddle packet.
 * The server needs them for the heat everyone else sees; movement itself comes from the pilot's client.
 */
public record BalloonInput(boolean burner, boolean vent) implements CustomPacketPayload {
    public static final Type<BalloonInput> TYPE = new Type<>(HotAirBalloons.id("input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BalloonInput> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, BalloonInput::burner, ByteBufCodecs.BOOL, BalloonInput::vent, BalloonInput::new);

    @Override
    public Type<BalloonInput> type() {
        return TYPE;
    }

    public static void handle(ServerPlayer player, BalloonInput msg) {
        if (player.getVehicle() instanceof HotAirBalloon b && b.getControllingPassenger() == player) {
            b.setPilotKeys(msg.burner(), msg.vent());
        }
    }
}
