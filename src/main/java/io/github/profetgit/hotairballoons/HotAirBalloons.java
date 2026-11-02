package io.github.profetgit.hotairballoons;

import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common constants; each loader's entry class calls {@link #init} and does its own registration (see {@code platform/}). */
public final class HotAirBalloons {
    public static final String ID = "hotairballoons";
    public static final Logger LOG = LoggerFactory.getLogger("Hot Air Balloons");
    private static String loader = "?";
    private static Path configDir;

    private HotAirBalloons() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    public static void init(String loaderName, Path config) {
        loader = loaderName;
        configDir = config;
        LOG.info("Hot Air Balloons loaded on {}", loader);
    }

    public static String loader() {
        return loader;
    }

    public static Path configDir() {
        return configDir;
    }
}
