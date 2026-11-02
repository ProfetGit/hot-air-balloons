package io.github.profetgit.hotairballoons.net;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader-neutral packet hooks: each platform registers the payloads and points {@link #send} / {@link #canSend} at its own client API. */
public final class Net {
    /** Client → server; set by the platform's client entry. */
    public static Consumer<CustomPacketPayload> send = p -> {};
    /** Whether the connected server has registered this payload. */
    public static Predicate<CustomPacketPayload.Type<?>> canSend = t -> false;

    private Net() {}

    /** Runs on the server thread. */
    public static void onServer(ServerPlayer player, CustomPacketPayload payload) {
        if (payload instanceof BalloonInput m) BalloonInput.handle(player, m);
        else if (payload instanceof BalloonClick m) BalloonClick.handle(player, m);
    }
}
