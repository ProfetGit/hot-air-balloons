package io.github.profetgit.hotairballoons.selftest;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.client.HotAirBalloonsClient;
import io.github.profetgit.hotairballoons.entity.EnvelopeLook;
import io.github.profetgit.hotairballoons.entity.BalloonPhysics;
import io.github.profetgit.hotairballoons.entity.BasketShape;
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
    private static float health;
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
        String only = System.getProperty("hotairballoons.selftest.scene", "all");
        if (only.equals("all") || only.contains("ride")) ride(s);
        if (only.equals("all") || only.contains("weather")) weather(s);
        if (only.equals("all") || only.contains("showcase")) showcase(s);
        if (only.equals("all") || only.contains("cargo")) cargo(s);
        if (only.equals("all") || only.contains("looks")) looks(s);
        if (only.equals("all") || only.contains("fuel")) fuel(s);
        if (only.equals("all") || only.contains("walk")) walk(s);
        if (only.equals("all") || only.contains("roof")) roof(s);
        if (only.equals("all") || only.contains("wind")) wind(s);
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
                b.setFuel(HotAirBalloon.MAX_FUEL);
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

    private static Entity spawnMob(ServerLevel sl, net.minecraft.world.entity.EntityType<?> type, double x, double y, double z) {
        Entity e = type.create(sl, EntitySpawnReason.COMMAND);
        e.snapTo(x, y, z, 0, 0);
        if (e instanceof net.minecraft.world.entity.Mob m) m.setNoAi(true);
        sl.addFreshEntity(e);
        return e;
    }

    /**
     * M4: a sheep walks in first but the player still flies it; a chest from inside the basket takes a corner seat; the
     * inventory key opens the chest only once there is one; seats fill to 3; contents survive a save/load; mobs ride a
     * climb; a cow tied to the balloon is lifted; breaking it drops balloon, chest and contents.
     */
    private static void cargo(Script s) {
        double[] ground = new double[3];
        Entity[] mobs = new Entity[4];
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.stopRiding();
                ServerLevel sl = (ServerLevel) p.level();
                ground[0] = Math.floor(p.getX()) - 60.5;
                ground[2] = Math.floor(p.getZ()) + 0.5;
                ground[1] = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(ground[0]), (int) Math.floor(ground[2]));
                p.teleportTo(ground[0] - 3, ground[1], ground[2]);
                HotAirBalloon b = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                b.setInitialPos(ground[0], ground[1], ground[2]);
                sl.addFreshEntity(b);
                b.setFuel(HotAirBalloon.MAX_FUEL);
                balloonId = b.getUUID();
                mobs[0] = spawnMob(sl, net.minecraft.world.entity.EntityTypes.SHEEP, ground[0] + 0.3, ground[1], ground[2]);
            })
            .waitTicks(10)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                SelfTest.record("sheep walks in", mobs[0].getVehicle() == b, "vehicle " + mobs[0].getVehicle());
                p.startRiding(b);
                SelfTest.record("player flies though the sheep came first", b.getControllingPassenger() == p && mobs[0].getVehicle() == b,
                    "controller " + b.getControllingPassenger() + ", sheep aboard " + (mobs[0].getVehicle() == b));
            })
            .until("aboard the cargo balloon", 100, mc -> balloon(mc) != null)
            .check("inventory key opens own inventory without a chest", mc -> !mc.gameMode.isServerControlledInventory() ? null : "server-controlled")
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
                InteractionResult r = b.interact(p, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("chest goes in from the basket", b.hasChest() && p.getMainHandItem().isEmpty(), r + ", hand " + p.getMainHandItem());
                ServerLevel sl = (ServerLevel) b.level();
                mobs[1] = spawnMob(sl, net.minecraft.world.entity.EntityTypes.PIG, ground[0] - 0.3, ground[1], ground[2] + 0.3);
            })
            .waitTicks(10)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                ServerLevel sl = (ServerLevel) b.level();
                mobs[2] = spawnMob(sl, net.minecraft.world.entity.EntityTypes.CHICKEN, ground[0], ground[1], ground[2] - 0.3);
            })
            .waitTicks(10)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                mobs[3] = spawnMob((ServerLevel) b.level(), net.minecraft.world.entity.EntityTypes.RABBIT, ground[0] + 0.2, ground[1], ground[2] - 0.2);
            })
            .waitTicks(10)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                SelfTest.record("chest takes a corner: pilot + 3 mobs", b.getPassengers().size() == 4 && mobs[1].getVehicle() == b && mobs[2].getVehicle() == b
                    && mobs[3].getVehicle() == null, "passengers " + b.getPassengers().size() + ", pig " + (mobs[1].getVehicle() == b)
                    + ", chicken " + (mobs[2].getVehicle() == b) + ", rabbit " + (mobs[3].getVehicle() == b));
                mobs[2].discard();
                mobs[3].discard();
                b.setItem(0, new ItemStack(Items.DIAMOND, 5));
                b.setItem(26, new ItemStack(Items.BREAD, 12));
                var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, b.registryAccess());
                b.saveWithoutId(out);
                HotAirBalloon copy = ModContent.BALLOON.create(b.level(), EntitySpawnReason.LOAD);
                copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, b.registryAccess(), out.buildResult()));
                SelfTest.record("chest survives save and load", copy.hasChest() && copy.getItem(0).is(Items.DIAMOND) && copy.getItem(0).getCount() == 5
                    && copy.getItem(26).is(Items.BREAD), "chest " + copy.hasChest() + ", slot0 " + copy.getItem(0) + ", slot26 " + copy.getItem(26));
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                SelfTest.record("chest menu opens", b.createMenu(1, p.getInventory(), p) != null, "");
            })
            .waitTicks(5)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                double sheep = mobs[0].getY() - b.getY(), pig = mobs[1].getY() - b.getY();
                SelfTest.record("mobs stand on the basket floor", Math.abs(sheep - BasketShape.FLOOR_TOP) < 0.01 && Math.abs(pig - BasketShape.FLOOR_TOP) < 0.01,
                    String.format("sheep %.3f, pig %.3f above the origin, floor %.3f", sheep, pig, BasketShape.FLOOR_TOP));
            })
            .check("inventory key opens the chest once it is there", mc -> balloon(mc).hasChest() && mc.gameMode.isServerControlledInventory() ? null
                : "client chest " + balloon(mc).hasChest())
            .run(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setXRot(35);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            })
            .waitTicks(10)
            .capture("still_cargo", 1)
            .waitTicks(5)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(100)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                SelfTest.record("mobs ride the climb", b.getY() > ground[1] + 3 && mobs[0].getVehicle() == b && mobs[1].getVehicle() == b,
                    String.format("y +%.2f, sheep %s, pig %s", b.getY() - ground[1], mobs[0].getVehicle() == b, mobs[1].getVehicle() == b));
            })
            .run(mc -> HotAirBalloonsClient.testInput = VENT)
            .until("landing", 1600, mc -> balloon(mc) != null && balloon(mc).onGround())
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                ServerLevel sl = (ServerLevel) b.level();
                p.stopRiding();
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                mobs[3] = spawnMob(sl, net.minecraft.world.entity.EntityTypes.COW, b.getX() + 4, b.getY(), b.getZ());
                ((net.minecraft.world.entity.Mob) mobs[3]).setNoAi(false);
                ((net.minecraft.world.entity.Leashable) mobs[3]).setLeashedTo(p, true);
                p.setShiftKeyDown(true);
                InteractionResult r = b.interact(p, InteractionHand.MAIN_HAND, b.position());
                p.setShiftKeyDown(false);
                SelfTest.record("leashed cow ties to the balloon", ((net.minecraft.world.entity.Leashable) mobs[3]).getLeashHolder() == b,
                    r + ", holder " + ((net.minecraft.world.entity.Leashable) mobs[3]).getLeashHolder());
                ground[1] = mobs[3].getY();
                p.startRiding(b);
            })
            .until("aboard to lift the cow", 100, mc -> balloon(mc) != null)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(200)
            .server(server -> {
                var cow = (net.minecraft.world.entity.Leashable) mobs[3];
                SelfTest.record("balloon lifts the tied cow", cow.getLeashHolder() == serverBalloon(server) && mobs[3].getY() > ground[1] + 2,
                    String.format("leashed %s, cow +%.2f, balloon +%.2f", cow.isLeashed(), mobs[3].getY() - ground[1], serverBalloon(server).getY() - ground[1]));
            })
            .capture("still_cow", 1)
            .waitTicks(5)
            .run(mc -> HotAirBalloonsClient.testInput = VENT)
            .until("landing", 1600, mc -> balloon(mc) != null && balloon(mc).onGround())
            .run(mc -> {
                HotAirBalloonsClient.testInput = null;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            })
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                p.stopRiding();
                for (Entity m : List.copyOf(b.getPassengers())) m.discard();
                mobs[3].discard();
                p.setNoGravity(true);
                p.setDeltaMovement(Vec3.ZERO);
                double a = Math.toRadians(b.getYRot());
                look(p, b.getX() - 2.2 * Math.sin(a) + 1.2 * Math.cos(a), b.getY() + 2.4, b.getZ() + 2.2 * Math.cos(a) + 1.2 * Math.sin(a),
                    b.getX(), b.getY() + 0.4, b.getZ());
            })
            .waitTicks(20)
            .check("chest model resolves", mc -> {
                HotAirBalloon b = mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(6)).getFirst();
                var r = (io.github.profetgit.hotairballoons.client.BalloonRenderer) mc.getEntityRenderDispatcher().getRenderer(b);
                var st = r.createRenderState();
                r.extractRenderState(b, st, 0);
                return !st.chest.isEmpty() ? null : "empty chest model, client hasChest " + b.hasChest();
            })
            .capture("still_chest", 1)
            .waitTicks(5)
            .run(mc -> mc.gui.hud.toggle())
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                ServerLevel sl = (ServerLevel) b.level();
                p.setNoGravity(false);
                p.teleportTo(b.getX() + 20, b.getY() + 1, b.getZ());
                ground[0] = b.getX();
                ground[2] = b.getZ();
                b.hurtServer(sl, sl.damageSources().generic(), 100);
            })
            .waitTicks(5)
            .server(server -> {
                ServerLevel sl = server.overworld();
                List<ItemEntity> drops = sl.getEntitiesOfClass(ItemEntity.class, new AABB(ground[0] - 6, -64, ground[2] - 6, ground[0] + 6, 320, ground[2] + 6));
                boolean balloon = drops.stream().anyMatch(e -> e.getItem().is(ModContent.BALLOON_ITEM));
                boolean chest = drops.stream().anyMatch(e -> e.getItem().is(Items.CHEST));
                boolean diamonds = drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND));
                SelfTest.record("break drops balloon, chest and contents", balloon && chest && diamonds,
                    "balloon " + balloon + ", chest " + chest + ", diamonds " + diamonds);
            });
    }

    private static ItemStack banner(MinecraftServer server, net.minecraft.world.item.Item item, Object... layers) {
        var reg = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
        var b = new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder();
        for (int i = 0; i < layers.length; i += 2) {
            @SuppressWarnings("unchecked")
            var key = (net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.entity.BannerPattern>) layers[i];
            b.add(reg.getOrThrow(key), (net.minecraft.world.item.DyeColor) layers[i + 1]);
        }
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, b.build());
        return stack;
    }

    /**
     * M5: dye the base and (sneaking) the stripes, put a banner on as the emblem, see it on both sides, swap banners
     * (the old one comes back), shear it off, keep the look through break and re-place, then all 16 dyes in a row.
     */
    private static void looks(Script s) {
        double[] at = new double[3];
        s.run(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            })
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.stopRiding();
                p.setNoGravity(false);
                ServerLevel sl = (ServerLevel) p.level();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 5000");
                at[0] = Math.floor(p.getX()) + 0.5;
                at[2] = Math.floor(p.getZ()) - 80.5;
                at[1] = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at[0]), (int) Math.floor(at[2]));
                p.teleportTo(at[0] + 3, at[1], at[2]);
                HotAirBalloon b = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                b.setInitialPos(at[0], at[1], at[2]);
                b.setYRot(0);
                sl.addFreshEntity(b);
                b.setFuel(HotAirBalloon.MAX_FUEL);
                balloonId = b.getUUID();
            })
            .waitTicks(5)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.BLUE), 2));
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW), 2));
                p.setShiftKeyDown(true);
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                p.setShiftKeyDown(false);
                SelfTest.record("dye colours base, sneak-dye the stripes", b.look().base() == net.minecraft.world.item.DyeColor.BLUE
                    && b.look().stripes() == net.minecraft.world.item.DyeColor.YELLOW && p.getMainHandItem().getCount() == 1,
                    b.look() + ", hand " + p.getMainHandItem());
                p.setItemInHand(InteractionHand.MAIN_HAND, banner(server, Items.BANNER.pick(net.minecraft.world.item.DyeColor.BLACK),
                    net.minecraft.world.level.block.entity.BannerPatterns.CREEPER, net.minecraft.world.item.DyeColor.LIME));
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("banner repaints the envelope in its colours", b.look().emblemBase().orElse(null) == net.minecraft.world.item.DyeColor.BLACK
                    && b.look().base() == net.minecraft.world.item.DyeColor.BLACK && b.look().stripes() == net.minecraft.world.item.DyeColor.LIME
                    && b.look().emblem().layers().size() == 1 && p.getMainHandItem().isEmpty(), b.look() + ", hand " + p.getMainHandItem());
            })
            .waitTicks(5)
            .check("client draws base, stripes and the creeper layer", mc -> {
                HotAirBalloon b = mc.level.getEntitiesOfClass(HotAirBalloon.class, new AABB(at[0] - 2, at[1] - 2, at[2] - 2, at[0] + 2, at[1] + 2, at[2] + 2)).getFirst();
                var r = (io.github.profetgit.hotairballoons.client.BalloonRenderer) mc.getEntityRenderDispatcher().getRenderer(b);
                var st = r.createRenderState();
                r.extractRenderState(b, st, 0);
                return st.emblemCount == 1 && st.primary == net.minecraft.world.item.DyeColor.BLACK.getTextureDiffuseColor() ? null
                    : "emblem layers " + st.emblemCount + ", primary " + Integer.toHexString(st.primary);
            })
            .server(server -> look(server.getPlayerList().getPlayers().getFirst(), at[0], at[1] + 1, at[2] + 14, at[0], at[1] + 7.4, at[2]))
            .waitTicks(20)
            .capture("still_look_south", 1)
            .waitTicks(5)
            .server(server -> look(server.getPlayerList().getPlayers().getFirst(), at[0], at[1] + 1, at[2] - 14, at[0], at[1] + 7.4, at[2]))
            .waitTicks(20)
            .capture("still_look_north", 1)
            .waitTicks(5)
            .server(server -> look(server.getPlayerList().getPlayers().getFirst(), at[0] + 14, at[1] + 1, at[2], at[0], at[1] + 7.4, at[2]))
            .waitTicks(20)
            .capture("still_look_side", 1)
            .waitTicks(5)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                p.teleportTo(at[0] + 3, at[1], at[2]);
                p.getInventory().clearContent();
                p.setItemInHand(InteractionHand.MAIN_HAND, banner(server, Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE),
                    net.minecraft.world.level.block.entity.BannerPatterns.STRIPE_CENTER, net.minecraft.world.item.DyeColor.RED));
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                boolean back = p.getInventory().contains(st -> st.is(Items.BANNER.pick(net.minecraft.world.item.DyeColor.BLACK))
                    && st.getOrDefault(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY).layers().size() == 1);
                SelfTest.record("new banner swaps in, old one comes back", back && b.look().emblemBase().orElse(null) == net.minecraft.world.item.DyeColor.WHITE,
                    "old back " + back + ", " + b.look());
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                boolean white = p.getInventory().contains(st -> st.is(Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)));
                SelfTest.record("shears take the emblem off", b.look().emblemBase().isEmpty() && white, b.look() + ", white banner back " + white);
                b.setLook(b.look().withEmblem(net.minecraft.world.item.DyeColor.BLACK, banner(server, Items.BANNER.pick(net.minecraft.world.item.DyeColor.BLACK),
                    net.minecraft.world.level.block.entity.BannerPatterns.CREEPER, net.minecraft.world.item.DyeColor.LIME)
                    .get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS)));
                var want = b.look();
                p.teleportTo(at[0] + 30, at[1] + 1, at[2]);
                ServerLevel sl = (ServerLevel) b.level();
                b.hurtServer(sl, sl.damageSources().generic(), 100);
                List<ItemEntity> drops = sl.getEntitiesOfClass(ItemEntity.class, new AABB(at[0] - 4, -64, at[2] - 4, at[0] + 4, 320, at[2] + 4),
                    e -> e.getItem().is(ModContent.BALLOON_ITEM));
                ItemStack item = drops.isEmpty() ? ItemStack.EMPTY : drops.getFirst().getItem().copy();
                SelfTest.record("dropped balloon keeps its look", want.equals(item.get(ModContent.ENVELOPE)), "item " + item.get(ModContent.ENVELOPE));
                drops.forEach(Entity::discard);
                p.teleportTo(at[0], at[1] + 0.5, at[2] + 4);
                p.snapTo(at[0], at[1] + 0.5, at[2] + 4, 180, 70);
                p.setItemInHand(InteractionHand.MAIN_HAND, item);
                ModContent.BALLOON_ITEM.use(p.level(), p, InteractionHand.MAIN_HAND);
                var placed = sl.getEntitiesOfClass(HotAirBalloon.class, p.getBoundingBox().inflate(8));
                SelfTest.record("placed balloon wears the item's look", placed.size() == 1 && placed.getFirst().look().equals(want),
                    placed.isEmpty() ? "none placed" : placed.getFirst().look().toString());
                placed.forEach(Entity::discard);
                var dyes = net.minecraft.world.item.DyeColor.values();
                for (int i = 0; i < dyes.length; i++) {
                    HotAirBalloon g = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                    double x = at[0] - 24 + (i % 8) * 7, z = at[2] - 10 - (i / 8) * 10;
                    g.setInitialPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)) + (i / 8) * 4, z);
                    g.setLook(EnvelopeLook.DEFAULT.withBase(dyes[i]).withStripes(dyes[(i + 8) % 16]));
                    sl.addFreshEntity(g);
                }
                look(p, at[0], at[1] + 6, at[2] + 26, at[0], at[1] + 8, at[2] - 15);
            })
            .waitTicks(40)
            .capture("still_dyes", 1)
            .waitTicks(5)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                ServerLevel sl = (ServerLevel) p.level();
                Object[][] banners = {
                    {net.minecraft.world.item.DyeColor.BLACK, net.minecraft.world.level.block.entity.BannerPatterns.CREEPER, net.minecraft.world.item.DyeColor.LIME},
                    {net.minecraft.world.item.DyeColor.RED, net.minecraft.world.level.block.entity.BannerPatterns.FLOWER, net.minecraft.world.item.DyeColor.WHITE},
                    {net.minecraft.world.item.DyeColor.WHITE, net.minecraft.world.level.block.entity.BannerPatterns.GLOBE, net.minecraft.world.item.DyeColor.LIGHT_BLUE},
                    {net.minecraft.world.item.DyeColor.PURPLE, net.minecraft.world.level.block.entity.BannerPatterns.RHOMBUS_MIDDLE, net.minecraft.world.item.DyeColor.YELLOW,
                        net.minecraft.world.level.block.entity.BannerPatterns.CIRCLE_MIDDLE, net.minecraft.world.item.DyeColor.ORANGE}};
                double bx = at[0] + 80;
                for (int i = 0; i < banners.length; i++) {
                    Object[] row = banners[i];
                    Object[] layers = java.util.Arrays.copyOfRange(row, 1, row.length);
                    var base = (net.minecraft.world.item.DyeColor) row[0];
                    var patterns = banner(server, Items.BANNER.pick(base), layers).get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
                    HotAirBalloon g = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                    double x = bx - 21 + i * 14, z = at[2];
                    g.setInitialPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)), z);
                    g.setYRot(0);
                    g.setLook(EnvelopeLook.DEFAULT.wearing(base, patterns));
                    sl.addFreshEntity(g);
                }
                look(p, bx, at[1] + 4, at[2] + 30, bx, at[1] + 7, at[2]);
            })
            .waitTicks(40)
            .capture("still_banners", 1)
            .waitTicks(5)
            .run(mc -> mc.gui.hud.toggle());
    }

    /**
     * M6: a balloon with a little fuel and a pilot without any runs dry, the flame dies, it sinks and lands softly;
     * clicking with coal refuels; the recipe exists; flying above 200 gives the advancement; with fuel turned off in the
     * config the burner works on an empty tank.
     */
    /** A low roof stops the climb before the pilot's head enters it; a creative pilot needs no fuel. */
    private static void roof(Script s) {
        double[] at = new double[2];
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.stopRiding();
                p.setNoGravity(false);
                p.getInventory().clearContent();
                p.setHealth(p.getMaxHealth());
                ServerLevel sl = (ServerLevel) p.level();
                double x = Math.floor(p.getX()) + 60.5, z = Math.floor(p.getZ()) + 60.5;
                int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                BlockPos c = BlockPos.containing(x, y + 2, z);
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, 0, -6), c.offset(6, 0, 6))) sl.setBlock(q, Blocks.STONE.defaultBlockState(), 3);
                HotAirBalloon b = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                b.setInitialPos(x, y, z);
                sl.addFreshEntity(b);
                b.setFuel(HotAirBalloon.MAX_FUEL);
                balloonId = b.getUUID();
                p.teleportTo(x, y, z);
                p.startRiding(b);
                at[0] = y;
            })
            .until("aboard the roofed balloon", 100, mc -> balloon(mc) != null)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(120)
            .check("climb stops under a low roof", mc -> {
                double dy = balloon(mc).getY() - at[0];
                return dy < 0.3 ? null : String.format("balloon rose %.2f", dy);
            })
            .check("pilot's head stays out of the roof", mc -> mc.player.getHealth() >= mc.player.getMaxHealth() - 0.01F && !mc.player.isInWall() ? null
                : "health " + mc.player.getHealth() + ", inWall " + mc.player.isInWall())
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                ServerLevel sl = (ServerLevel) b.level();
                BlockPos c = BlockPos.containing(b.getX(), at[0] + 2, b.getZ());
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, 0, -6), c.offset(6, 0, 6))) sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                p.getInventory().clearContent();
                p.setGameMode(GameType.CREATIVE);
                b.setFuel(0);
            })
            .waitTicks(10)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(120)
            .check("creative pilot climbs with an empty tank", mc -> balloon(mc).getY() > at[0] + 3 ? null : "y " + balloon(mc).getY())
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.setGameMode(GameType.SURVIVAL);
            })
            .waitTicks(10)
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .server(server -> SelfTest.record("tank kept for survival", serverBalloon(server).fuel() == 0, "fuel " + serverBalloon(server).fuel()));
    }

    private static void fuel(Script s) {
        double[] at = new double[2];
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.stopRiding();
                p.setNoGravity(false);
                p.getInventory().clearContent();
                p.setHealth(p.getMaxHealth());
                ServerLevel sl = (ServerLevel) p.level();
                double x = Math.floor(p.getX()) + 40.5, z = Math.floor(p.getZ()) + 40.5;
                double y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                HotAirBalloon b = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                b.setInitialPos(x, y, z);
                sl.addFreshEntity(b);
                b.setFuel(400);
                balloonId = b.getUUID();
                p.teleportTo(x, y, z);
                p.startRiding(b);
                at[0] = y;
            })
            .until("aboard the fuel balloon", 100, mc -> balloon(mc) != null)
            .until("client has the fuel", 100, mc -> balloon(mc) != null && balloon(mc).fuel() == 400)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .until("aloft", 300, mc -> balloon(mc) != null && balloon(mc).getY() > at[0] + 2)
            .run(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON))
            .capture("still_hud", 1)
            .until("fuel runs out", 600, mc -> balloon(mc) != null && !balloon(mc).hasFuel())
            .waitTicks(5)
            .check("no fuel, no flame", mc -> !balloon(mc).burning() ? null : "still burning")
            .run(mc -> at[1] = balloon(mc).getY())
            .until("sinks to the ground", 2400, mc -> balloon(mc) != null && balloon(mc).onGround())
            .check("empty tank sinks it", mc -> at[1] - balloon(mc).getY() > 2 ? null : String.format("from %.2f to %.2f", at[1], balloon(mc).getY()))
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .waitTicks(5)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                SelfTest.record("soft landing", p.getHealth() >= p.getMaxHealth(), String.format("health %.1f after %.1f blocks", p.getHealth(), at[1] - b.getY()));
                p.stopRiding();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 2));
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("clicking with coal refuels", b.fuel() == 1600 && p.getMainHandItem().getCount() == 1, "fuel " + b.fuel() + ", hand " + p.getMainHandItem());
                var recipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    io.github.profetgit.hotairballoons.HotAirBalloons.id("hot_air_balloon")));
                SelfTest.record("balloon recipe loads", recipe.isPresent(), "");
                b.setFuel(HotAirBalloon.MAX_FUEL);
                b.teleportTo(b.getX(), 205, b.getZ());
                p.teleportTo(b.getX() + 2, 206, b.getZ());
                p.setNoGravity(true);
            })
            .waitTicks(20)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.setNoGravity(false);
                p.startRiding(serverBalloon(server));
            })
            .until("aboard high up", 100, mc -> balloon(mc) != null && balloon(mc).getY() > 200)
            .waitTicks(45)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                var adv = server.getAdvancements().get(io.github.profetgit.hotairballoons.HotAirBalloons.id("adventure/up_up_and_away"));
                SelfTest.record("advancement above 200", adv != null && p.getAdvancements().getOrStartProgress(adv).isDone(),
                    adv == null ? "advancement not loaded" : String.format("y %.1f", p.getY()));
                io.github.profetgit.hotairballoons.Config.get().needsFuel = false;
                serverBalloon(server).setFuel(0);
            })
            .waitTicks(5)
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(20)
            .check("fuel off in config: burns on an empty tank", mc -> balloon(mc).fuel() == HotAirBalloon.UNLIMITED && balloon(mc).burning() ? null
                : "fuel " + balloon(mc).fuel() + ", burning " + balloon(mc).burning())
            .run(mc -> {
                HotAirBalloonsClient.testInput = null;
                io.github.profetgit.hotairballoons.Config.get().needsFuel = true;
            });
    }

    private static HotAirBalloon clientBalloon(Minecraft mc) {
        return (HotAirBalloon) mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(12)).stream()
            .filter(b -> b.getUUID().equals(balloonId)).findFirst().orElse(null);
    }

    private static boolean standing(Minecraft mc) {
        HotAirBalloon b = clientBalloon(mc);
        return b != null && !mc.player.isPassenger() && io.github.profetgit.hotairballoons.entity.BasketShape.inside(mc.player.position(), b.position(), b.getYRot());
    }

    /**
     * Walkable basket: a click takes the controls and the pilot stands on the floor; letting go leaves the player standing
     * in the basket, carried along while the balloon hovers on its pilot light; the walls hold a walker in; a click from
     * inside takes the controls again; on the ground a jump clears the rim.
     */
    private static void walk(Script s) {
        double[] y = new double[2];
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.stopRiding();
                p.setNoGravity(false);
                p.getInventory().clearContent();
                p.setHealth(p.getMaxHealth());
                ServerLevel sl = (ServerLevel) p.level();
                double x = Math.floor(p.getX()) - 30.5, z = Math.floor(p.getZ()) - 30.5;
                double gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                HotAirBalloon b = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                b.setInitialPos(x, gy, z);
                sl.addFreshEntity(b);
                b.setFuel(HotAirBalloon.MAX_FUEL);
                balloonId = b.getUUID();
                p.teleportTo(x + 2.5, gy, z);
            })
            .waitTicks(10)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("click takes the controls", p.getVehicle() == b, "vehicle " + p.getVehicle());
            })
            .until("at the controls", 100, mc -> balloon(mc) != null)
            .waitTicks(5)
            .check("pilot stands on the basket floor", mc -> {
                double d = mc.player.getY() - (balloon(mc).getY() + io.github.profetgit.hotairballoons.entity.BasketShape.FLOOR_TOP);
                return Math.abs(d) < 0.02 ? null : String.format("feet %.3f off the floor", d);
            })
            .run(mc -> HotAirBalloonsClient.testInput = BURN)
            .waitTicks(90)
            .run(mc -> HotAirBalloonsClient.testInput = NONE)
            .waitTicks(30)
            .server(server -> server.getPlayerList().getPlayers().getFirst().stopRiding())
            .waitTicks(20)
            .check("letting go leaves you standing in the basket", mc -> standing(mc) && mc.player.onGround() ? null
                : "passenger " + mc.player.isPassenger() + ", inside " + standing(mc) + ", onGround " + mc.player.onGround())
            .run(mc -> {
                y[0] = clientBalloon(mc).getY();
                y[1] = mc.player.getY() - y[0];
            })
            .waitTicks(100)
            .check("crewed balloon keeps its heat and carries you", mc -> {
                HotAirBalloon b = clientBalloon(mc);
                double off = mc.player.getY() - b.getY();
                return standing(mc) && b.getY() > y[0] - 0.5 && Math.abs(off - y[1]) < 0.1 && mc.player.fallDistance < 0.5 ? null
                    : String.format("inside %s, balloon %.2f -> %.2f, feet offset %.3f -> %.3f, fall %.2f", standing(mc), y[0], b.getY(), y[1], off, mc.player.fallDistance);
            })
            .run(mc -> mc.options.keyUp.setDown(true))
            .waitTicks(30)
            .run(mc -> mc.options.keyUp.setDown(false))
            .waitTicks(5)
            .check("walls hold a walker in", mc -> {
                HotAirBalloon b = clientBalloon(mc);
                var l = io.github.profetgit.hotairballoons.entity.BasketShape.toLocal(mc.player.position(), b.position(), b.getYRot());
                return standing(mc) && Math.abs(l.x) < 0.8 && Math.abs(l.z) < 0.8 ? null : String.format("local %.2f, %.2f, %.2f", l.x, l.y, l.z);
            })
            .capture("still_walk", 1)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                b.interact(p, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("click inside takes the controls again", p.getVehicle() == b, "vehicle " + p.getVehicle());
            })
            .until("back at the controls", 100, mc -> balloon(mc) != null)
            .run(mc -> HotAirBalloonsClient.testInput = VENT)
            .until("landing", 1600, mc -> balloon(mc) != null && balloon(mc).onGround())
            .run(mc -> HotAirBalloonsClient.testInput = null)
            .server(server -> server.getPlayerList().getPlayers().getFirst().stopRiding())
            .waitTicks(10)
            .run(mc -> {
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
            })
            .waitTicks(25)
            .run(mc -> {
                mc.options.keyUp.setDown(false);
                mc.options.keyJump.setDown(false);
            })
            .waitTicks(20)
            .check("a jump clears the rim", mc -> {
                HotAirBalloon b = clientBalloon(mc);
                double d = Math.hypot(mc.player.getX() - b.getX(), mc.player.getZ() - b.getZ());
                return !standing(mc) && d > 1.2 ? null : String.format("still inside %s, %.2f from the middle", standing(mc), d);
            })
            .server(server -> serverBalloon(server).discard());
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
                b.setFuel(HotAirBalloon.MAX_FUEL);
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
                sl.setBlock(post[0], Blocks.OAK_FENCE.defaultBlockState(), 3);
                p.stopRiding();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
                p.interactOn(b, InteractionHand.MAIN_HAND, b.position());
                SelfTest.record("lead ties to the balloon", b.getLeashHolder() == p, "holder " + b.getLeashHolder());
                InteractionResult r = sl.getBlockState(post[0]).useWithoutItem(sl, p, new BlockHitResult(Vec3.atCenterOf(post[0]), Direction.UP, post[0], false));
                SelfTest.record("a plain fence takes the lead", b.getLeashHolder() instanceof LeashFenceKnotEntity, r + ", holder " + b.getLeashHolder());
                look(p, b.getX() - 9, b.getY() + 1.5, b.getZ() + 9, b.getX(), b.getY() + 1.4, b.getZ());
            })
            .waitTicks(260)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                var knot = b.getLeashHolder();
                double dh = knot == null ? -1 : Math.hypot(b.getX() - knot.getX(), b.getZ() - knot.getZ());
                double dy = knot == null ? -99 : b.getY() - knot.getY();
                SelfTest.record("tied balloon floats up by itself", knot != null && Math.abs(dy - BalloonPhysics.MOOR_HEIGHT) < 0.6 && dh > 1.2 && dh < 3.6,
                    String.format("%.2f above the knot (want %.1f), %.2f to the side", dy, BalloonPhysics.MOOR_HEIGHT, dh));
                Vec3 off = b.getLeashOffset();
                double r = Math.max(Math.abs(off.x), Math.abs(off.z));
                SelfTest.record("rope is tied to the rim", r > 0.9 && r < 1.0 && off.y > 0.7 && off.y < 1.0, String.valueOf(off));
            })
            .capture("still_moored", 1)
            .until("still taken", 40, mc -> !SelfTest.capturing())
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                var knot = b.getLeashHolder();
                p.setNoGravity(true);
                look(p, b.getX() + 5, b.getY() + 0.9, b.getZ() + 5, (b.getX() + knot.getX()) / 2, b.getY() - 0.8, (b.getZ() + knot.getZ()) / 2);
            })
            .waitTicks(10)
            .capture("still_moored_side", 1)
            .until("still taken", 40, mc -> !SelfTest.capturing())
            .server(server -> server.getPlayerList().getPlayers().getFirst().setNoGravity(false))
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                HotAirBalloon b = serverBalloon(server);
                p.teleportTo(b.getX(), b.getY(), b.getZ());
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
            .until("settles on its rope", 1200, mc -> {
                var sb = serverBalloon(mc.getSingleplayerServer());
                var knot = sb.getLeashHolder();
                return knot != null && Math.abs(sb.getY() - knot.getY() - BalloonPhysics.MOOR_HEIGHT) < 0.4 && sb.getDeltaMovement().lengthSqr() < 1e-4;
            })
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                var knot = b.getLeashHolder();
                double dh = knot == null ? -1 : Math.hypot(b.getX() - knot.getX(), b.getZ() - knot.getZ());
                SelfTest.record("moored balloon hovers unmanned", b.isLeashed() && dh > 1.2 && dh < 3.6,
                    String.format("leashed %s, %.2f to the side of the knot", b.isLeashed(), dh));
            });
    }

    /**
     * Dedicated server (dev/server/mp.py, allow-flight off). "pilot": the server seats it in a balloon; it burns for
     * 200 ticks (well past the 80-tick float limit), hovers, vents down. "watch": logs what it sees of the pilot's
     * balloon. Both log HAB-MP lines; the harness checks the server side.
     */
    /** Wind cues: the ladder reaches the client, lines stream downwind past the pilot; HUD stills and a flight capture high up. */
    private static void wind(Script s) {
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.stopRiding();
                ServerLevel sl = (ServerLevel) p.level();
                HotAirBalloon b = ModContent.BALLOON.create(sl, EntitySpawnReason.COMMAND);
                b.setInitialPos(p.getX() + 6, 216, p.getZ());
                sl.addFreshEntity(b);
                b.setFuel(HotAirBalloon.MAX_FUEL);
                balloonId = b.getUUID();
                p.teleportTo(b.getX(), b.getY(), b.getZ());
                p.startRiding(b);
            })
            .until("aboard high up", 200, mc -> balloon(mc) != null && balloon(mc).getY() > 200)
            .run(mc -> {
                HotAirBalloonsClient.testInput = NONE;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            })
            .waitTicks(80)
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                ServerLevel sl = (ServerLevel) b.level();
                String bad = null;
                for (int i = 0; i < HotAirBalloon.LADDER.length && bad == null; i++) {
                    double[] w = b.windFor(sl, b.getY() + HotAirBalloon.LADDER[i]);
                    Vec3 got = b.windAt(i);
                    if (Math.hypot(got.x - w[0], got.z - w[1]) > 1e-4) bad = String.format("row %d: %.4f,%.4f vs %.4f,%.4f", i, got.x, got.z, w[0], w[1]);
                }
                SelfTest.record("server ladder is the balloon's wind", bad == null, bad == null ? "ok" : bad);
            })
            .check("client gets the wind ladder", mc -> {
                HotAirBalloon b = balloon(mc), sb = serverBalloon(mc.getSingleplayerServer());
                for (int i = 0; i < HotAirBalloon.LADDER.length; i++) {
                    Vec3 w = b.windAt(i), want = sb.windAt(i);
                    double err = w.subtract(want).horizontalDistance();
                    if (err > 0.02) return String.format("row %d: %.4f,%.4f vs server %.4f,%.4f", i, w.x, w.z, want.x, want.z);
                }
                return null;
            })
            .check("wind lines stream downwind", mc -> {
                HotAirBalloon b = balloon(mc);
                Vec3 w = b.wind();
                int n = 0, layers = 0;
                double cos = 0;
                for (var l : io.github.profetgit.hotairballoons.client.WindLineParticle.live()) {
                    if (l.balloon() != b) continue;
                    if (l.layer() != 0) {
                        layers++;
                        continue;
                    }
                    Vec3 d = l.delta();
                    double h = d.horizontalDistance();
                    if (h < 1e-6) continue;
                    cos += (d.x * w.x + d.z * w.z) / (h * w.horizontalDistance());
                    n++;
                }
                String what = String.format("%d lines, %d layer lines, mean cos %.2f, wind %.4f/t", n, layers, n == 0 ? 0 : cos / n, w.horizontalDistance());
                return n >= 4 && layers > 0 && cos / n > 0.9 ? null : what;
            })
            .run(mc -> faceWind(mc, Math.PI / 2))
            .waitTicks(5)
            .capture("still_wind_across", 1)
            .until("still taken", 40, mc -> !SelfTest.capturing())
            .capture("wind_lines", 300)
            .until("wind lines clip", 400, mc -> !SelfTest.capturing())
            .capture("wind_turn", 120)
            .until("turn clip", 200, mc -> {
                float yaw = mc.player.getYRot();
                mc.player.yRotO = yaw;
                mc.player.setYRot(yaw + 0.5F);
                return !SelfTest.capturing();
            })
            .run(mc -> faceWind(mc, 0))
            .waitTicks(5)
            .capture("still_wind_ahead", 1)
            .until("still taken", 40, mc -> !SelfTest.capturing())
            .run(mc -> {
                faceWind(mc, Math.PI);
                mc.player.setXRot(-35);
                mc.player.xRotO = -35;
            })
            .waitTicks(5)
            .capture("still_wind_up", 1)
            .until("still taken", 40, mc -> !SelfTest.capturing())
            .run(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(20);
                mc.player.xRotO = 20;
            })
            .waitTicks(5)
            .capture("still_pilot_stands", 1)
            .until("still taken", 40, mc -> !SelfTest.capturing())
            .run(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON))
            .server(server -> {
                HotAirBalloon b = serverBalloon(server);
                server.getPlayerList().getPlayers().getFirst().stopRiding();
                b.discard();
            })
            .run(mc -> HotAirBalloonsClient.testInput = null)
            .waitTicks(10);
    }

    /** Turns the pilot so the wind blows at {@code rel} radians from straight ahead (positive = to the right). */
    private static void faceWind(Minecraft mc, double rel) {
        Vec3 w = balloon(mc).wind();
        double a = Math.atan2(w.z, w.x) - rel;
        float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(5);
        mc.player.xRotO = 5;
    }

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
                .check("pilot gets the wind ladder", mc -> {
                    HotAirBalloon b = balloon(mc);
                    for (int i = 0; i < HotAirBalloon.LADDER.length; i++) if (b.windAt(i).horizontalDistance() < 1e-4) return "row " + i + " is calm";
                    return null;
                })
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
            int[] stood = {0, 0};
            s.until("pilot's balloon in view", 2400, mc -> !mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(64)).isEmpty())
                .until("pilot done", 3600, mc -> {
                    var list = mc.level.getEntitiesOfClass(HotAirBalloon.class, mc.player.getBoundingBox().inflate(96));
                    if (!list.isEmpty()) {
                        HotAirBalloon b = list.getFirst();
                        seen[0] = Math.min(seen[0], b.getY());
                        seen[1] = Math.max(seen[1], b.getY());
                        flame[0] |= b.burning();
                        boolean in = io.github.profetgit.hotairballoons.entity.BasketShape.inside(mc.player.position(), b.position(), b.getYRot());
                        if (in || stood[1] > 0) stood[1]++;
                        if (in) stood[0]++;
                    }
                    return mc.level.players().stream().noneMatch(p -> p != mc.player)
                        || (seen[1] - seen[0] > 8 && !list.isEmpty() && list.getFirst().onGround() && list.getFirst().getY() < seen[1] - 5);
                })
                .check("watcher saw the climb", mc -> seen[1] - seen[0] > 8 ? null : String.format("saw %.2f..%.2f", seen[0], seen[1]))
                .check("watcher saw the flame", mc -> flame[0] ? null : "never burning")
                .check("watcher stood in the basket the whole flight", mc -> stood[1] > 200 && stood[0] >= stood[1] - 3 ? null
                    : "in the basket " + stood[0] + " of " + stood[1] + " ticks");
        }
        return s;
    }

    /** M1: place from the item, board, climb on the burner, thrust, vent down to a landing, break for the item. */
    private static void ride(Script s) {
        s.server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.getInventory().clearContent();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.BALLOON_ITEM));
                p.getInventory().setItem(5, new ItemStack(Items.COAL, 4));
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
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                int coal = p.getInventory().countItem(Items.COAL);
                HotAirBalloon b = (HotAirBalloon) p.getVehicle();
                SelfTest.record("pilot's coal feeds the burner", coal == 3 && b.fuel() > 0, "coal left " + coal + ", fuel " + b.fuel());
                health = p.getHealth();
            })
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
            .waitTicks(5)
            .server(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                SelfTest.record("landing hurts nobody aboard", p.getHealth() >= health, String.format("health %.1f -> %.1f after %.1f blocks down", health, p.getHealth(), highY - groundY));
            })
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
