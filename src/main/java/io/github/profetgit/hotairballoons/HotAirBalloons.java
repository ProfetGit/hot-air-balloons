package io.github.profetgit.hotairballoons;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry: registers the balloon entity, its items and the mooring post on both sides. */
public final class HotAirBalloons implements ModInitializer {
    public static final String ID = "hotairballoons";
    public static final Logger LOG = LoggerFactory.getLogger("Hot Air Balloons");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        ModContent.init();
        io.github.profetgit.hotairballoons.net.BalloonInput.register();
        LOG.info("Hot Air Balloons loaded");
    }
}
