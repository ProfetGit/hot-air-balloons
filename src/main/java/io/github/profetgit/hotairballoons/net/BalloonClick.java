package io.github.profetgit.hotairballoons.net;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * Player → server: a right click that hit a balloon's envelope. Vanilla only picks the basket's box, so the client
 * ray-casts the envelope itself and sends this; the server checks the reach and then handles it like a basket click.
 */
public record BalloonClick(int entityId, boolean offHand) implements CustomPacketPayload {
    public static final Type<BalloonClick> TYPE = new Type<>(HotAirBalloons.id("click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BalloonClick> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, BalloonClick::entityId, ByteBufCodecs.BOOL, BalloonClick::offHand, BalloonClick::new);
    public static final double REACH = 5.0;

    @Override
    public Type<BalloonClick> type() {
        return TYPE;
    }

    public static void handle(ServerPlayer player, BalloonClick msg) {
        Entity e = player.level().getEntity(msg.entityId());
        if (!(e instanceof HotAirBalloon b)) return;
        if (b.envelopeBox().distanceToSqr(player.getEyePosition()) > (REACH + 1) * (REACH + 1)) return;
        b.interact(player, msg.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, b.position());
    }
}
