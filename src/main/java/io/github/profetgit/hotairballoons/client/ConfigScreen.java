package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Settings on one page. They apply at once in singleplayer; a dedicated server reads its own config/hotairballoons.json. */
public final class ConfigScreen extends OptionsSubScreen {
    public ConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal("Hot Air Balloons"));
    }

    @Override
    protected void addOptions() {
        Config c = Config.get();
        list.addHeader(Component.literal("Flight (servers: config/hotairballoons.json)"));
        list.addSmall(
            bool("Burner needs fuel", "Coal, charcoal, wood, blaze rods: anything a furnace burns. Click the balloon with fuel, or carry it: the pilot's fuel is used when the burner runs dry.",
                c.needsFuel, v -> c.needsFuel = v),
            percent("Fuel use", "How fast the burner uses fuel. 100%: a piece of coal lasts about a minute of flight.", 25, 16, c.fuelUse, v -> c.fuelUse = Math.max(25, v)),
            percent("Wind", "How hard the wind pushes a balloon. 0%: no wind.", 25, 8, c.wind, v -> c.wind = v),
            bool("Mobs climb in", "Animals and other mobs that walk into a basket on the ground climb in, like boats.", c.mobsBoard, v -> c.mobsBoard = v));
    }

    private static OptionInstance<Boolean> bool(String name, String tip, boolean value, OptionInstance.ValueUpdateListener<Boolean> set) {
        return OptionInstance.createBoolean(name, OptionInstance.cachedConstantTooltip(Component.literal(tip)), value, set);
    }

    /** A slider from 0 to steps x step percent. */
    private static OptionInstance<Integer> percent(String name, String tip, int step, int steps, int value, java.util.function.IntConsumer set) {
        return new OptionInstance<>(name, OptionInstance.cachedConstantTooltip(Component.literal(tip)),
            (caption, v) -> Component.literal(name + ": " + v * step + "%"), new OptionInstance.IntRange(0, steps),
            Math.clamp(Math.round(value / (float) step), 0, steps), v -> set.accept(v * step));
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }
}
