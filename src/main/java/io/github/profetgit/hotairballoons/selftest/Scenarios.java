package io.github.profetgit.hotairballoons.selftest;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.client.HotAirBalloonsClient;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
import net.minecraft.world.level.levelgen.Heightmap;

final class Scenarios {
    private static final Input NONE = new Input(false, false, false, false, false, false, false);
    private static final Input BURN = new Input(false, false, false, false, true, false, false);
    private static final Input BURN_FORWARD = new Input(true, false, false, false, true, false, false);
    private static final Input VENT = new Input(false, false, false, false, false, false, true);
    private static double groundY, startX, startZ, highY;
    private static UUID balloonId;

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
        weather(s);
        showcase(s);
        return s;
    }

    private static @org.jspecify.annotations.Nullable Entity cam;
    private static int flightTicks;

    /** Client-only side camera 16 blocks off the balloon, moved every tick (old pose kept for smooth interpolation). */
    private static void chase(Minecraft mc, HotAirBalloon b) {
        if (cam == null) {
            cam = net.minecraft.world.entity.EntityTypes.MARKER.create(mc.level, EntitySpawnReason.COMMAND);
            cam.setPos(b.getX(), b.getY() + 4, b.getZ() + 16);
            cam.setId(-4242);
            mc.level.addEntity(cam);
            mc.setCameraEntity(cam);
        }
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();
        cam.yRotO = cam.getYRot();
        cam.xRotO = cam.getXRot();
        double x = b.getX(), y = b.getY() + 4, z = b.getZ() + 16;
        double dx = b.getX() - x, dy = b.getY() + 5 - y, dz = b.getZ() - z;
        cam.setPos(x, y, z);
        cam.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        cam.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - (y + p.getEyeHeight()), dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    /** M3 looks: inflate after placing, stills from outside and below, a burning climb with thrust (sway), pilot view. */
    private static void showcase(Script s) {
        double[] at = new double[3];
        s.run(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            })
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                ServerLevel sl = (ServerLevel) p.level();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 5000");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                at[0] = Math.floor(p.getX()) + 40.5;
                at[2] = Math.floor(p.getZ()) + 0.5;
                at[1] = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at[0]), (int) Math.floor(at[2]));
                double y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at[0] - 15), (int) Math.floor(at[2]));
                look(p, at[0] - 15, y, at[2], at[0], at[1] + 5.5, at[2]);
            })
            .waitTicks(60)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = ModContent.BALLOON.create(p.level(), EntitySpawnReason.SPAWN_ITEM_USE);
                b.setInitialPos(at[0], at[1], at[2]);
                b.setYRot(90);
                b.startInflating();
                p.level().addFreshEntity(b);
                balloonId = b.getUUID();
            })
            .capture("inflate", 150)
            .waitTicks(60)
            .check("inflates after placing", mc -> {
                var list = mc.level.getEntitiesOfClass(HotAirBalloon.class, new AABB(at[0] - 2, at[1] - 2, at[2] - 2, at[0] + 2, at[1] + 2, at[2] + 2));
                return list.size() == 1 && list.getFirst().inflateProgress(0) >= 1 ? null
                    : list.isEmpty() ? "no balloon" : "progress " + list.getFirst().inflateProgress(0);
            })
            .server(server -> look(server.getPlayerList().getPlayers().getFirst(), at[0] - 10, at[1], at[2] + 10, at[0], at[1] + 5, at[2]))
            .waitTicks(30)
            .capture("still_quarter", 1)
            .waitTicks(5)
            .server(server -> look(server.getPlayerList().getPlayers().getFirst(), at[0] - 3, at[1], at[2] - 3, at[0], at[1] + 6, at[2]))
            .waitTicks(20)
            .capture("still_below", 1)
            .waitTicks(5)
            .server(server -> look(server.getPlayerList().getPlayers().getFirst(), at[0] - 6, at[1], at[2] + 3, at[0], at[1] + 0.8, at[2]))
            .waitTicks(20)
            .capture("still_basket", 1)
            .waitTicks(5)
            .server(server -> server.getPlayerList().getPlayers().getFirst().startRiding(serverBalloon(server)))
            .until("boarded the showcase balloon", 100, mc -> balloon(mc) != null)
            .run(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setXRot(-12);
                HotAirBalloonsClient.testInput = BURN;
            })
            .waitTicks(60)
            .capture("still_rider", 1)
            .waitTicks(40)
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .waitTicks(60)
            .run(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                chase(mc, balloon(mc));
                HotAirBalloonsClient.testInput = BURN_FORWARD;
            })
            .capture("flight", 300)
            .until("flight clip", 200, mc -> {
                chase(mc, balloon(mc));
                if (flightTicks++ == 45) HotAirBalloonsClient.testInput = NONE;
                return !SelfTest.capturing();
            })
            .run(mc -> {
                mc.setCameraEntity(mc.player);
                cam.discard();
                cam = null;
            })
            .run(mc -> {
                HotAirBalloonsClient.testInput = NONE;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setXRot(-60);
            })
            .waitTicks(10)
            .capture("still_pilot", 1)
            .waitTicks(5)
            .run(mc -> {
                HotAirBalloonsClient.testInput = null;
                mc.gui.hud.toggle();
            });
    }

    private static HotAirBalloon serverBalloon(MinecraftServer server) {
        return (HotAirBalloon) server.overworld().getEntity(balloonId);
    }

    /** M2: drift with the wind, stop under a ceiling, leash to a mooring post, climb against the rope, hover tethered. */
    private static void weather(Script s) {
        double[] drift = new double[5];
        BlockPos[] post = new BlockPos[1];
        int[] ceiling = new int[1];
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = ModContent.BALLOON.create(p.level(), EntitySpawnReason.COMMAND);
                b.setInitialPos(p.getX() + 6, p.getY(), p.getZ());
                p.level().addFreshEntity(b);
                balloonId = b.getUUID();
                p.startRiding(b);
            })
            .until("boarded the second balloon", 100, mc -> balloon(mc) != null)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(60)
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .waitTicks(40)
            .run(mc -> {
                drift[0] = balloon(mc).getX();
                drift[1] = balloon(mc).getZ();
            })
            .waitTicks(150)
            .check("drifts with the wind", mc -> {
                HotAirBalloon b = balloon(mc);
                double dx = b.getX() - drift[0], dz = b.getZ() - drift[1];
                Vec3 w = b.wind();
                double want = w.horizontalDistance() * 150;
                double cos = (dx * w.x + dz * w.z) / (Math.hypot(dx, dz) * w.horizontalDistance() + 1e-9);
                String what = String.format("moved %.2f (wind %.4f/t, expect ~%.2f), cos %.2f", Math.hypot(dx, dz), w.horizontalDistance(), want, cos);
                return Math.hypot(dx, dz) > 0.6 * want && cos > 0.85 ? null : what;
            })
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                ceiling[0] = (int) Math.floor(b.getY() + HotAirBalloon.ENVELOPE_TOP) + 3;
                BlockPos c = BlockPos.containing(b.getX(), ceiling[0], b.getZ());
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, 0, -6), c.offset(6, 0, 6))) {
                    b.level().setBlock(q, Blocks.STONE.defaultBlockState(), 3);
                }
            })
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(120)
            .check("envelope stops under a ceiling", mc -> {
                double top = balloon(mc).envelopeBox().maxY;
                return top <= ceiling[0] + 1e-3 && top > ceiling[0] - 1.5 ? null : String.format("envelope top %.2f, ceiling %d", top, ceiling[0]);
            })
            .run(mc -> HotAirBalloonsClient.testInput = VENT)
            .until("landing", 1600, mc -> balloon(mc) != null && balloon(mc).onGround())
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                ServerLevel sl = (ServerLevel) b.level();
                BlockPos c = BlockPos.containing(b.getX(), ceiling[0], b.getZ());
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, 0, -6), c.offset(6, 0, 6))) sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                post[0] = BlockPos.containing(b.getX() + 3, b.getY(), b.getZ());
                sl.setBlock(post[0], ModContent.MOORING_POST.defaultBlockState(), 3);
                p.stopRiding();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
                p.interactOn(b, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("lead ties to the balloon", b.getLeashHolder() == p, "holder " + b.getLeashHolder());
                InteractionResult r = sl.getBlockState(post[0]).useWithoutItem(sl, p, new BlockHitResult(Vec3.atCenterOf(post[0]), Direction.UP, post[0], false));
                SelfTest.record("post takes the lead", b.getLeashHolder() instanceof LeashFenceKnotEntity, r + ", holder " + b.getLeashHolder());
                p.startRiding(b);
            })
            .until("back aboard", 100, mc -> balloon(mc) != null)
            .waitTicks(40)
            .check("client sees the rope", mc -> balloon(mc).isLeashed() ? null : "not leashed on the client")
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(300)
            .check("rope holds a burning balloon", mc -> {
                HotAirBalloon b = balloon(mc);
                double d = b.position().distanceTo(Vec3.atBottomCenterOf(post[0]));
                return b.isLeashed() && d < 12 && b.getY() > post[0].getY() + 5 ? null
                    : String.format("leashed %s, %.2f from the post, %.2f above it", b.isLeashed(), d, b.getY() - post[0].getY());
            })
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                p.stopRiding();
                p.teleportTo(post[0].getX() + 2.5, post[0].getY(), post[0].getZ() + 0.5);
                drift[2] = b.getY();
            })
            .waitTicks(200)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                double d = b.position().distanceTo(Vec3.atBottomCenterOf(post[0]));
                SelfTest.record("moored balloon hovers unmanned", b.isLeashed() && Math.abs(b.getY() - drift[2]) < 2 && d < 12,
                    String.format("leashed %s, y %.2f -> %.2f, %.2f from the post", b.isLeashed(), drift[2], b.getY(), d));
            });
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
