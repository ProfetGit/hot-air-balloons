package io.github.profetgit.hotairballoons.selftest;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.client.HotAirBalloonsClient;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.AABB;

final class Scenarios {
    private static final Input NONE = new Input(false, false, false, false, false, false, false);
    private static final Input BURN = new Input(false, false, false, false, true, false, false);
    private static final Input BURN_FORWARD = new Input(true, false, false, false, true, false, false);
    private static final Input VENT = new Input(false, false, false, false, false, false, true);
    private static double groundY, startX, startZ, highY;

    private Scenarios() {}

    private static HotAirBalloon balloon(Minecraft mc) {
        return mc.player.getVehicle() instanceof HotAirBalloon b ? b : null;
    }

    static Script build() {
        Script s = new Script();
        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("habtest-" + System.currentTimeMillis(),
                new LevelSettings("habtest", GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen()))
            .until("world to load", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null)
            .waitTicks(40)
            .check("world loaded", mc -> mc.player.isAlive() ? null : "player not alive");
        ride(s);
        return s;
    }

    /**
     * Dedicated server (dev/server/mp.py, allow-flight off). "pilot": the server seats it in a balloon; it burns for
     * 200 ticks (well past the 80-tick float limit), hovers, vents down. "watch": logs what it sees of the pilot's
     * balloon. Both log HAB-MP lines; the harness checks the server side.
     */
    static Script multiplayer(String role) {
        Script s = new Script();
        s.until("joined a server", 6000, mc -> mc.player != null && mc.level != null && mc.getSingleplayerServer() == null && mc.gui.screen() == null)
            .waitTicks(20);
        if (role.equals("pilot")) {
            s.run(mc -> HotAirBalloons.LOG.info("HAB-MP pilot ready"))
                .until("seated by the server", 2400, mc -> balloon(mc) != null)
                .run(mc -> {
                    groundY = balloon(mc).getY();
                    HotAirBalloonsClient.testInput = BURN;
                })
                .waitTicks(200)
                .check("climbed on a server", mc -> balloon(mc) != null && balloon(mc).getY() > groundY + 8 ? null
                    : balloon(mc) == null ? "lost the balloon" : String.format("rose %.2f", balloon(mc).getY() - groundY))
                .run(mc -> {
                    highY = balloon(mc).getY();
                    HotAirBalloons.LOG.info("HAB-MP pilot aloft y={}", String.format("%.2f", highY));
                    HotAirBalloonsClient.testInput = NONE;
                })
                .waitTicks(100)
                .check("still connected after floating", mc -> mc.getConnection() != null && balloon(mc) != null ? null : "disconnected or dismounted")
                .run(mc -> HotAirBalloonsClient.testInput = VENT)
                .until("landing", 1600, mc -> balloon(mc) != null && balloon(mc).onGround())
                .check("landed on a server", mc -> balloon(mc).getY() < highY - 5 ? null : "y " + balloon(mc).getY())
                .run(mc -> {
                    HotAirBalloonsClient.testInput = null;
                    HotAirBalloons.LOG.info("HAB-MP pilot done");
                })
                .waitTicks(40);
        } else {
            double[] seen = {Double.MAX_VALUE, -Double.MAX_VALUE};
            boolean[] flame = {false};
            s.until("pilot's balloon in view", 2400, mc -> !mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(64)).isEmpty())
                .until("pilot done", 3600, mc -> {
                    var list = mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(96));
                    if (!list.isEmpty()) {
                        HotAirBalloon b = list.getFirst();
                        seen[0] = Math.min(seen[0], b.getY());
                        seen[1] = Math.max(seen[1], b.getY());
                        flame[0] |= b.burning();
                    }
                    return mc.level.players().stream().noneMatch(p -> p != mc.player)
                        || (seen[1] - seen[0] > 8 && !list.isEmpty() && list.getFirst().onGround() && list.getFirst().getY() < seen[1] - 5);
                })
                .check("watcher saw the climb", mc -> seen[1] - seen[0] > 8 ? null : String.format("saw %.2f..%.2f", seen[0], seen[1]))
                .check("watcher saw the flame", mc -> flame[0] ? null : "never burning");
        }
        return s;
    }

    /** M1: place from the item, board, climb on the burner, thrust, vent down to a landing, break for the item. */
    private static void ride(Script s) {
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.getInventory().clearContent();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.BALLOON_ITEM));
                p.snapTo(p.getX(), p.getY(), p.getZ(), 0, 60);
                ModContent.BALLOON_ITEM.use(p.level(), p, InteractionHand.MAIN_HAND);
            })
            .waitTicks(5)
            .check("item places a balloon", mc -> {
                var list = mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(8));
                return list.size() == 1 ? null : "found " + list.size();
            })
            .check("item consumed", mc -> mc.player.getMainHandItem().isEmpty() ? null : "still holding " + mc.player.getMainHandItem())
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                var b = p.level().getEntitiesOfClass(HotAirBalloon.class, p.getBoundingBox().inflate(8)).getFirst();
                p.startRiding(b);
            })
            .until("boarded", 100, mc -> balloon(mc) != null)
            .run(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                groundY = balloon(mc).getY();
                HotAirBalloonsClient.testInput = BURN;
            })
            .waitTicks(100)
            .check("burner climbs", mc -> {
                double dy = balloon(mc).getY() - groundY;
                return dy > 3 ? null : "rose only " + String.format("%.2f", dy);
            })
            .check("flame shows", mc -> balloon(mc).burning() ? null : "not burning")
            .run(mc -> {
                startX = balloon(mc).getX();
                startZ = balloon(mc).getZ();
                HotAirBalloonsClient.testInput = BURN_FORWARD;
            })
            .waitTicks(100)
            .check("thrust moves it", mc -> {
                double d = Math.hypot(balloon(mc).getX() - startX, balloon(mc).getZ() - startZ);
                return d > 3 ? null : "moved only " + String.format("%.2f", d);
            })
            .run(mc -> {
                highY = balloon(mc).getY();
                HotAirBalloonsClient.testInput = NONE;
            })
            .capture("aloft", 3)
            .waitTicks(5)
            .server(server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                SelfTest.record("server sees it aloft", p.getVehicle() instanceof HotAirBalloon b && b.getY() > groundY + 3,
                    p.getVehicle() == null ? "no vehicle" : String.format("server y %.2f", p.getVehicle().getY()));
                SelfTest.record("pilot not kicked", p.connection != null && !p.hasDisconnected(), "");
            })
            .run(mc -> HotAirBalloonsClient.testInput = VENT)
            .until("landing", 1200, mc -> balloon(mc) != null && balloon(mc).onGround())
            .check("vent lands it", mc -> balloon(mc).getY() < highY - 3 ? null : "y " + balloon(mc).getY())
            .run(mc -> HotAirBalloonsClient.testInput = null)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = (HotAirBalloon) p.getVehicle();
                p.stopRiding();
                ServerLevel sl = (ServerLevel) b.level();
                b.hurtServer(sl, sl.damageSources().generic(), 100);
            })
            .waitTicks(5)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                List<ItemEntity> drops = p.level().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(10),
                    e -> e.getItem().is(ModContent.BALLOON_ITEM));
                SelfTest.record("break drops the item", drops.size() == 1, "drops " + drops.size());
            });
    }
}
