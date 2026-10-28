package io.github.profetgit.hotairballoons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings in config/hotairballoons.json (on a server, the server's config folder). The server's copy decides: fuel
 * and wind are simulated there and synced to the balloons.
 */
public final class Config {
    /** The burner needs furnace fuel. Off: it always burns. */
    public boolean needsFuel = true;
    /** Fuel use, percent of normal (25 to 400). */
    public int fuelUse = 100;
    /** Wind strength, percent of normal (0 to 200). */
    public int wind = 100;
    /** Mobs that walk into a resting basket climb in. */
    public boolean mobsBoard = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NAME = "hotairballoons.json";
    private static Config current;
    private static Path file;

    public static synchronized Config get() {
        if (current == null) load();
        return current;
    }

    public static synchronized void load() {
        file = FabricLoader.getInstance().getConfigDir().resolve(NAME);
        Config c = null;
        try {
            if (Files.isRegularFile(file)) c = GSON.fromJson(Files.readString(file), Config.class);
        } catch (Exception e) {
            HotAirBalloons.LOG.warn("Hot Air Balloons: cannot read {}, using defaults ({})", file, e.toString());
        }
        current = c == null ? new Config() : c;
        save();
    }

    private void clamp() {
        fuelUse = Math.clamp(fuelUse, 25, 400);
        wind = Math.clamp(wind, 0, 200);
    }

    public static synchronized void save() {
        if (current == null || file == null) return;
        current.clamp();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current));
        } catch (Exception e) {
            HotAirBalloons.LOG.warn("Hot Air Balloons: cannot write {} ({})", file, e.toString());
        }
    }
}
