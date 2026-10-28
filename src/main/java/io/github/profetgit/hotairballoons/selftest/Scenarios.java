package io.github.profetgit.hotairballoons.selftest;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class Scenarios {
    private Scenarios() {}

    static Script build() {
        Script s = new Script();
        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("habtest-" + System.currentTimeMillis(),
                new LevelSettings("habtest", GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen()))
            .until("world to load", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null)
            .waitTicks(40)
            .check("world loaded", mc -> mc.player.isAlive() ? null : "player not alive");
        return s;
    }
}
