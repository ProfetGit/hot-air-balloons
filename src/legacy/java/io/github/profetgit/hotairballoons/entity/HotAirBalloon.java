package io.github.profetgit.hotairballoons.entity;

import io.github.profetgit.hotairballoons.Config;
import io.github.profetgit.hotairballoons.ModContent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/**
 * The balloon: a vehicle moved by whoever is authoritative, like a boat (the controlling player's client, else the
 * server). Heat is simulated on both sides from the pilot's keys; the server's copy is synced for everyone else's
 * flame and sound. Wind is computed on the server and synced, since clients don't know the seed. Four riders stand in
 * the basket corners (players or mobs that walk in on the ground); a chest can be put in one corner, taking that seat,
 * opened like a chest boat's (inventory key while riding, sneak-use from outside).
 */
public class HotAirBalloon extends VehicleEntity implements Leashable, HasCustomInventoryScreen, ContainerEntity {
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BURNER = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_WIND_X = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIND_Z = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    /** Height offsets of the wind ladder (the pilot's HUD and the wind lines above and below). */
    public static final int[] LADDER = {-32, -16, 16, 32};
    /** Wind (x, z) at each {@link #LADDER} offset, refreshed every tick on the server (gusts change it quickly). */
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Float>[] DATA_WIND_LADDER = new EntityDataAccessor[LADDER.length * 2];
    static {
        for (int i = 0; i < DATA_WIND_LADDER.length; i++) DATA_WIND_LADDER[i] = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    }
    /** Server ticks left of the inflate animation after placing; never saved, so loaded balloons start inflated. */
    private static final EntityDataAccessor<Integer> DATA_INFLATE = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CHEST = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<EnvelopeLook> DATA_LOOK = SynchedEntityData.defineId(HotAirBalloon.class, ModContent.LOOK_SERIALIZER);
    /** Burner fuel in furnace ticks; {@link #UNLIMITED} when the server's config says fuel isn't needed. */
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.INT);
    public static final int UNLIMITED = -1;
    /** Two coal blocks' worth. */
    public static final int MAX_FUEL = 32000;
    /** Below this the pilot's (or the chest's) next fuel item goes in by itself. */
    public static final int REFUEL_AT = 200;
    /** Fuel per tick aloft: the pilot light, and the burner on top. */
    public static final float IDLE_USE = 1.0F, BURN_USE = 3.0F;
    public static final int CHEST_SIZE = 27;
    /** Corner seats in boarding order, without a chest and with one (the chest takes corner 3; the pilot faces it). */
    private static final int[] SEATS = {0, 1, 2, 3}, SEATS_WITH_CHEST = {1, 0, 2};
    /** Mobs wider than this don't fit in the basket. */
    public static final float MAX_MOB_WIDTH = 1.4F;
    public static final int INFLATE_TICKS = 40;
    public static final int MAX_PASSENGERS = 4;
    public static final double CEILING = 320.0;
    /** Rider feet sit this high above the entity origin: on the basket floor. */
    private static final double RIDE_HEIGHT = BasketShape.FLOOR_TOP;
    private static final double SEAT_OFFSET = 0.42;
    /** Envelope box relative to the basket floor: skirt hem to crown of dev/art/balloon.bbmodel (16 units per block). */
    public static final double ENVELOPE_HALF = 45 / 16.0, ENVELOPE_BOTTOM = 58 / 16.0, ENVELOPE_TOP = 183 / 16.0;
    /** Slack rope length before the tether pulls; vanilla's lead snaps at 12. */
    public static final double ROPE = 8.0;

    /** Set by the client entry: the local player's keys. Null on a dedicated server. */
    @Nullable public static Supplier<PilotKeys> localInput;
    /** Set by the client entry: per-tick client effects (sounds, wind lines). */
    @Nullable public static Consumer<HotAirBalloon> clientTick;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;
    private float heat = BalloonPhysics.NEUTRAL;
    /** Server: how far the wind leans toward the pilot's heading (eases to {@link Wind#FAVOUR} while piloted) and that heading, eased. */
    private double favourShare;
    private float favourYaw;
    /** Server: the side of its anchor a moored balloon hangs on (radians, world x/z), turned slowly toward the wind; NaN = unset. */
    private double moorDir = Double.NaN;
    private float yawSpeed;
    private boolean burning;
    private boolean pilotBurner, pilotVent;
    @Nullable private Leashable.LeashData leashData;
    private int inflateLeft;
    private float fuelUsed;
    private int lastBlockedHint = -1000, lastFuelHint = -1000, savedFuel;
    private static final java.util.Set<java.util.UUID> HINTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(CHEST_SIZE, ItemStack.EMPTY);
    @Nullable private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    /** Client animation state, advanced once per tick in {@link #animTick}: envelope sway spring, flame, inflation. */
    public float swayX, swayZ, swayXo, swayZo, flame, flameO;
    public int inflateTicks = -1;
    private float swayVX, swayVZ;
    private double lastX, lastZ, lastVX, lastVZ, accX, accZ;

    public HotAirBalloon(EntityType<? extends HotAirBalloon> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public void setInitialPos(double x, double y, double z) {
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_HEAT, BalloonPhysics.NEUTRAL);
        entityData.define(DATA_BURNER, false);
        entityData.define(DATA_WIND_X, 0.0F);
        entityData.define(DATA_WIND_Z, 0.0F);
        for (EntityDataAccessor<Float> a : DATA_WIND_LADDER) entityData.define(a, 0.0F);
        entityData.define(DATA_INFLATE, 0);
        entityData.define(DATA_CHEST, false);
        entityData.define(DATA_LOOK, EnvelopeLook.DEFAULT);
        entityData.define(DATA_FUEL, 0);
    }

    @Override
    protected Item getDropItem() {
        return ModContent.BALLOON_ITEM;
    }

    @Override
    public ItemStack getPickResult() {
        return asItem();
    }

    /** The balloon item with this envelope's look, so a dyed balloon stays dyed when picked up again. */
    public ItemStack asItem() {
        ItemStack stack = new ItemStack(ModContent.BALLOON_ITEM);
        if (!look().equals(EnvelopeLook.DEFAULT)) stack.set(ModContent.ENVELOPE, look());
        return stack;
    }

    public EnvelopeLook look() {
        return entityData.get(DATA_LOOK);
    }

    public void setLook(EnvelopeLook look) {
        entityData.set(DATA_LOOK, look);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /** Not as one solid box: the basket is a floor and walls ({@link BasketShape}) that people stand in. */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** Riders stand in the basket instead of taking the sitting pose (NeoForge asks the vehicle). */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Set by the client entry: the local player, carried along when standing in a basket. Null on a dedicated server. */
    @Nullable public static Supplier<Player> localPlayer;
    @Nullable private List<net.minecraft.world.phys.shapes.VoxelShape> shapes;
    @Nullable private Vec3 shapesAt;
    private float shapesYaw;
    /** Client: the local player stood in this basket last tick (so a floor overlap is lag, not someone underneath). */
    private boolean localAboard;
    /** How far into or under the floor someone who was aboard is lifted back onto it (lag between pilot and server). */
    private static final double RESCUE = 0.8;

    /** This basket's floor and walls where the balloon is now (rebuilt when it has moved or turned). */
    public List<net.minecraft.world.phys.shapes.VoxelShape> basketShapes() {
        if (shapes == null || !position().equals(shapesAt) || getYRot() != shapesYaw) {
            shapesAt = position();
            shapesYaw = getYRot();
            shapes = BasketShape.build(shapesAt, shapesYaw);
        }
        return shapes;
    }

    /**
     * Server: this player is at a balloon basket, by a loose measure: a walker's position comes from their own client,
     * which sees the balloon smoothed and a few ticks behind, so while it climbs they can report up to half a block
     * under the server's floor. Used only to spare them vanilla's floating kick.
     */
    public static boolean atBasket(Player player) {
        for (HotAirBalloon b : player.level().getEntitiesOfClass(HotAirBalloon.class, player.getBoundingBox().inflate(4))) {
            Vec3 l = BasketShape.toLocal(player.position(), b.position(), b.getYRot());
            if (Math.abs(l.x) < BasketShape.OUTER + 0.5 && Math.abs(l.z) < BasketShape.OUTER + 0.5 && l.y > -3 && l.y < BasketShape.RIM + 1) return true;
        }
        return false;
    }

    /** The balloon whose basket this player stands in, or null. */
    @Nullable public static HotAirBalloon standingIn(Player player) {
        for (HotAirBalloon b : player.level().getEntitiesOfClass(HotAirBalloon.class, player.getBoundingBox().inflate(2))) {
            if (BasketShape.inside(player.position(), b.position(), b.getYRot())) return b;
        }
        return null;
    }

    /** Server: someone stands in the basket without flying it, so it keeps its heat (the pilot light stays on). */
    private boolean crewed() {
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(1, 1.5, 1))) {
            if (!p.isPassenger() && BasketShape.inside(p.position(), position(), getYRot())) return true;
        }
        return false;
    }

    /**
     * What stands in the basket moves and turns with it: after the balloon's own move, everything that stood inside the
     * basket where it was is put at the same place in the basket where it is now. The local player on the client (its
     * own movement is the client's), everything but players on the server. Whichever ticks first, the floor and the
     * people on it end each tick together.
     */
    private void carryAboard(Vec3 from, float fromYaw) {
        float dyaw = getYRot() - fromYaw;
        if (from.equals(position()) && dyaw == 0) return;
        List<Entity> aboard = new java.util.ArrayList<>();
        if (level().isClientSide()) {
            Player me = localPlayer == null ? null : localPlayer.get();
            if (me != null && !me.isPassenger()) aboard.add(me);
        } else {
            aboard.addAll(level().getEntities(this, new AABB(from, from).inflate(1.2, 1.5, 1.2),
                e -> !(e instanceof Player) && !e.isPassenger() && !(e instanceof HotAirBalloon)));
        }
        for (Entity e : aboard) {
            if (!BasketShape.inside(e.position(), from, fromYaw)) continue;
            Vec3 to = BasketShape.toWorld(BasketShape.toLocal(e.position(), from, fromYaw), position(), getYRot());
            e.setPos(to.x, to.y, to.z);
            if (dyaw != 0) {
                e.setYRot(e.getYRot() + dyaw);
                e.setYHeadRot(e.getYHeadRot() + dyaw);
            }
        }
    }

    /**
     * Client: if the local player stood in this basket last tick but is now just inside or under its floor (the server
     * put them by its own, lagging, view of the balloon, or the basket jumped up), stand them back on it: vanilla lets
     * anything that already overlaps a shape fall through it.
     */
    private void keepLocalAboard() {
        Player me = localPlayer == null ? null : localPlayer.get();
        if (me == null || me.isPassenger() || me.level() != level()) {
            localAboard = me != null && me.getVehicle() == this;
            return;
        }
        Vec3 l = BasketShape.toLocal(me.position(), position(), getYRot());
        boolean over = Math.abs(l.x) < BasketShape.OUTER && Math.abs(l.z) < BasketShape.OUTER;
        if (localAboard && over && l.y < BasketShape.FLOOR_TOP && l.y > BasketShape.FLOOR_TOP - RESCUE) {
            Vec3 up = BasketShape.toWorld(new Vec3(l.x, BasketShape.FLOOR_TOP, l.z), position(), getYRot());
            me.setPos(up.x, up.y, up.z);
            me.setDeltaMovement(me.getDeltaMovement().x, 0, me.getDeltaMovement().z);
            me.resetFallDistance();
        }
        localAboard = BasketShape.inside(me.position(), position(), getYRot());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Client: where the server says the balloon is, reached in a few steps (a boat's smoothing) unless this client flies it. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = 3;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
        }
    }

    /** Heat for rendering: the local simulation when this client flies it, else the server's. */
    public float heat() {
        return level().isClientSide() && !isControlledByLocalInstance() ? entityData.get(DATA_HEAT) : heat;
    }

    public boolean burning() {
        return level().isClientSide() && !isControlledByLocalInstance() ? entityData.get(DATA_BURNER) : burning;
    }

    /** Server: play the inflate animation, from {@link io.github.profetgit.hotairballoons.item.BalloonItem}. */
    public void startInflating() {
        inflateLeft = INFLATE_TICKS;
        entityData.set(DATA_INFLATE, inflateLeft);
    }

    /** Client: inflate progress 0..1 at this frame; before the first client tick it comes from the synced counter. */
    public float inflateProgress(float partialTicks) {
        if (inflateTicks < 0) return 1 - entityData.get(DATA_INFLATE) / (float) INFLATE_TICKS;
        return Math.min(1, (inflateTicks + (inflateTicks < INFLATE_TICKS ? partialTicks : 0)) / INFLATE_TICKS);
    }

    public boolean inflating() {
        return level().isClientSide() ? inflateTicks >= 0 && inflateTicks < INFLATE_TICKS : inflateLeft > 0;
    }

    /** Burner flame for looks and sound: the pilot's burner, or the burst that inflates a fresh balloon. */
    public boolean burnerLit() {
        return burning() || inflating();
    }

    public Vec3 wind() {
        return new Vec3(entityData.get(DATA_WIND_X), 0, entityData.get(DATA_WIND_Z));
    }

    /**
     * Server: this balloon's wind at height y: the world's wind with its gusts (none on a lead, or a gust would pull
     * past the rope), leaned toward the pilot's heading, scaled by the config.
     */
    public double[] windFor(ServerLevel sl, double y) {
        double[] w = Wind.favour(Wind.at(sl.getSeed(), y, sl.getGameTime(), !isLeashed()), favourYaw, favourShare);
        double k = Config.get().wind / 100.0;
        return new double[] {w[0] * k, w[1] * k};
    }

    /** Wind at {@link #LADDER}[row] blocks above (or below) the balloon, as last synced. */
    public Vec3 windAt(int row) {
        return new Vec3(entityData.get(DATA_WIND_LADDER[row * 2]), 0, entityData.get(DATA_WIND_LADDER[row * 2 + 1]));
    }

    /** Server: the pilot's burner/vent keys from {@link io.github.profetgit.hotairballoons.net.BalloonInput}. */
    public void setPilotKeys(boolean burner, boolean vent) {
        pilotBurner = burner;
        pilotVent = vent;
    }

    /** The pilot's keys on this side: the local keyboard on the pilot's client, the last BalloonInput on the server. */
    @Nullable private PilotKeys pilotInput() {
        if (!(getControllingPassenger() instanceof Player)) {
            pilotBurner = pilotVent = false;
            return null;
        }
        if (level().isClientSide()) return isControlledByLocalInstance() && localInput != null ? localInput.get() : null;
        return new PilotKeys(false, false, false, false, pilotBurner, pilotVent);
    }

    @Override
    public void tick() {
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0.0F) setDamage(getDamage() - 1.0F);
        // Where the balloon was at the end of last tick: the level saves it in commonTick, before the client's
        // interpolation step moves the balloon, so the carry sees interpolated motion as well as our own.
        Vec3 from = new Vec3(xo, yo, zo);
        float fromYaw = yRotO;
        super.tick();
        tickLerp();

        PilotKeys in = pilotInput();
        boolean piloted = getControllingPassenger() instanceof Player;
        boolean burner = in != null && in.jump();
        boolean vent = in != null && in.sprint();
        boolean crew = !piloted && !level().isClientSide() && crewed();
        if (!level().isClientSide() || isControlledByLocalInstance()) {
            boolean lit = (piloted || crew) && hasFuel();
            heat = BalloonPhysics.heat(heat, burner && piloted && lit, vent, lit || isLeashed());
            burning = burner && piloted && lit;
        }
        if (level() instanceof ServerLevel sl) {
            entityData.set(DATA_HEAT, heat);
            entityData.set(DATA_BURNER, burning);
            useFuel(sl, piloted || crew);
            favourShare = Mth.approach((float) favourShare, piloted ? (float) Wind.FAVOUR : 0.0F, 0.01F);
            if (favourShare == 0) favourYaw = getYRot();
            favourYaw += Mth.clamp(Mth.wrapDegrees(getYRot() - favourYaw), -1.5F, 1.5F);
            double[] w = windFor(sl, getY());
            entityData.set(DATA_WIND_X, (float) w[0]);
            entityData.set(DATA_WIND_Z, (float) w[1]);
            for (int i = 0; i < LADDER.length; i++) {
                double[] l = windFor(sl, getY() + LADDER[i]);
                entityData.set(DATA_WIND_LADDER[i * 2], (float) l[0]);
                entityData.set(DATA_WIND_LADDER[i * 2 + 1], (float) l[1]);
            }
            if (inflateLeft > 0) entityData.set(DATA_INFLATE, --inflateLeft);
        }

        if (isControlledByLocalInstance()) {
            fly(in);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
            yawSpeed = 0;
        }
        carryAboard(from, fromYaw);
        if (level().isClientSide()) keepLocalAboard();
        if (!level().isClientSide() && Config.get().mobsBoard && resting()) boardMobs();
        if (level().isClientSide()) {
            animTick();
            if (clientTick != null) clientTick.accept(this);
        }
    }

    /**
     * Envelope sway: a damped spring (period ~1.6 s) leaning the canopy toward the basket's horizontal acceleration,
     * measured from rendered positions so watchers see it too. Flame level eases toward the burner state.
     */
    private void animTick() {
        if (inflateTicks < 0) inflateTicks = INFLATE_TICKS - entityData.get(DATA_INFLATE);
        else if (inflateTicks < INFLATE_TICKS) inflateTicks++;
        double vx = getX() - lastX, vz = getZ() - lastZ;
        if (tickCount <= 1) vx = vz = 0;
        accX += (vx - lastVX - accX) * 0.5;
        accZ += (vz - lastVZ - accZ) * 0.5;
        lastX = getX();
        lastZ = getZ();
        lastVX = vx;
        lastVZ = vz;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fwd = -Mth.sin(yaw) * accX + Mth.cos(yaw) * accZ;
        double left = Mth.cos(yaw) * accX + Mth.sin(yaw) * accZ;
        float idle = onGround() ? 0 : 0.012F * Mth.sin(tickCount * 0.05F);
        float tx = (float) Mth.clamp(fwd * 25, -0.15, 0.15) + idle;
        float tz = (float) Mth.clamp(left * 25, -0.15, 0.15) + idle * Mth.cos(tickCount * 0.031F);
        swayXo = swayX;
        swayZo = swayZ;
        swayVX += 0.0386F * (tx - swayX) - 0.098F * swayVX;
        swayVZ += 0.0386F * (tz - swayZ) - 0.098F * swayVZ;
        swayX += swayVX;
        swayZ += swayVZ;
        flameO = flame;
        float want = burnerLit() ? 0.55F + 0.45F * Mth.clamp((heat() - BalloonPhysics.NEUTRAL) * 2, 0, 1) : 0;
        flame += (want - flame) * (want > flame ? 0.5F : 0.35F);
        if (flame < 0.01F && want == 0) flame = 0;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 160.0 * getViewScale();
        return distance < range * range;
    }

    private void fly(@Nullable PilotKeys in) {
        Vec3 v = getDeltaMovement();
        boolean grounded = onGround();
        Entity anchor = getLeashHolder();
        boolean moored = anchor != null && !(getControllingPassenger() instanceof Player);
        double vy = moored ? BalloonPhysics.moorClimb(v.y, getY(), anchor.getY() + BalloonPhysics.MOOR_HEIGHT)
            : BalloonPhysics.climb(v.y, heat, getY(), CEILING);
        if (grounded && vy < 0) vy = 0;
        double vx = v.x, vz = v.z;
        if (in != null) {
            yawSpeed = BalloonPhysics.yawSpeed(yawSpeed, in.left(), in.right());
            double[] t = BalloonPhysics.thrust(getYRot(), in.forward(), in.backward());
            vx += t[0];
            vz += t[1];
        } else {
            yawSpeed = BalloonPhysics.yawSpeed(yawSpeed, false, false);
        }
        setYRot(getYRot() + yawSpeed);
        double drag = grounded ? BalloonPhysics.GROUND_DRAG : BalloonPhysics.AIR_DRAG;
        Vec3 w = grounded ? Vec3.ZERO : wind();
        if (moored && !grounded) {
            double wa = Math.atan2(w.z, w.x);
            if (Double.isNaN(moorDir)) {
                double ox = getX() - anchor.getX(), oz = getZ() - anchor.getZ();
                moorDir = ox * ox + oz * oz > 0.25 ? Math.atan2(oz, ox) : w.horizontalDistance() > 0.004 ? wa : Math.toRadians(getYRot() + 90);
            } else if (w.horizontalDistance() > 0.01) {
                moorDir += Mth.wrapDegrees((float) Math.toDegrees(wa - moorDir)) * 0.01 * Mth.DEG_TO_RAD;
            }
            double sx = anchor.getX() + Math.cos(moorDir) * BalloonPhysics.MOOR_STANDOFF, sz = anchor.getZ() + Math.sin(moorDir) * BalloonPhysics.MOOR_STANDOFF;
            w = new Vec3(BalloonPhysics.moorWind(w.x, getX() - sx), 0, BalloonPhysics.moorWind(w.z, getZ() - sz));
        } else if (!moored) {
            moorDir = Double.NaN;
        }
        vx = BalloonPhysics.horizontal(vx, w.x, 0, drag);
        vz = BalloonPhysics.horizontal(vz, w.z, 0, drag);
        if (anchor != null) {
            Vec3 d = position().subtract(anchor.position());
            double[] t = BalloonPhysics.tether(d.x, d.y, d.z, vx, vy, vz, ROPE);
            vx = t[0];
            vy = t[1];
            vz = t[2];
        }
        if (vy > 0 && getControllingPassenger() instanceof Player pilot && !level().noCollision(null, pilot.getBoundingBox().deflate(0.01, 0, 0.01).move(0, vy + 0.05, 0))) {
            vy = 0;
            heat = Math.min(heat, BalloonPhysics.NEUTRAL);
            if (level().isClientSide() && tickCount - lastBlockedHint > 100) {
                lastBlockedHint = tickCount;
                pilot.displayClientMessage(net.minecraft.network.chat.Component.translatable("hotairballoons.hint.blocked"), true);
            }
        }
        Vec3 want = new Vec3(vx, vy, vz);
        Vec3 ok = collideBoundingBox(this, want, envelopeBox(), level(), List.of());
        setDeltaMovement(ok.x != want.x ? 0 : vx, ok.y != want.y ? Math.min(0, vy) : vy, ok.z != want.z ? 0 : vz);
    }

    /** The envelope's box in the world: the basket box only covers the basket, so the canopy needs its own check. */
    public AABB envelopeBox() {
        return new AABB(getX() - ENVELOPE_HALF, getY() + ENVELOPE_BOTTOM, getZ() - ENVELOPE_HALF,
            getX() + ENVELOPE_HALF, getY() + ENVELOPE_TOP, getZ() + ENVELOPE_HALF);
    }

    /** The rope pull is ours (see {@link #fly}): vanilla's runs on the server only, but the pilot's client moves us. */
    @Override
    public void elasticRangeLeashBehaviour(Entity leashHolder, float distance) {
    }

    /** Server, on the ground: mobs that walk into the basket climb in, like boats take them (players board by click). */
    private void boardMobs() {
        if (mobsAboard() >= seats()) return;
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.2, -0.01, 0.2), EntitySelector.pushableBy(this))) {
            if (mobsAboard() >= seats()) return;
            if (e instanceof LivingEntity && !(e instanceof Player) && !e.isPassenger() && !e.hasPassenger(this)
                && e.getBbWidth() < MAX_MOB_WIDTH && !(e instanceof Leashable l && l.isLeashed())) {
                e.startRiding(this);
            }
        }
    }

    /**
     * Server: standing on something. Not {@code onGround()}: while a player pilots, the server doesn't move the balloon
     * and its ground flag is whatever the last vehicle packet said.
     */
    private boolean resting() {
        return !level().noCollision(this, getBoundingBox().move(0, -0.1, 0));
    }

    /** One player at the controls (at the burner); mobs in the corners. Other players just stand in the basket. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player ? !(getControllingPassenger() instanceof Player) : mobsAboard() < seats();
    }

    private int mobsAboard() {
        int n = 0;
        for (Entity e : getPassengers()) if (!(e instanceof Player)) n++;
        return n;
    }

    /** Corner seats for mobs: four, or three with a chest in the basket. */
    public int seats() {
        return hasChest() ? SEATS_WITH_CHEST.length : MAX_PASSENGERS;
    }

    /** The pilot: the first player aboard, so a sheep that walked in first doesn't take the controls. */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        for (Entity e : getPassengers()) if (e instanceof Player p) return p;
        return null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int i = 0;
        for (Entity e : getPassengers()) {
            if (e == passenger) break;
            if (!(e instanceof Player)) i++;
        }
        int[] order = hasChest() ? SEATS_WITH_CHEST : SEATS;
        double x = 0, z = 0;
        if (i < order.length) {
            int c = order[i];
            double off = Math.min(SEAT_OFFSET, Math.max(0.15, BasketShape.INNER - dimensions.width() / 2 - 0.05));
            x = (c == 1 || c == 2) ? off : -off;
            z = (c >= 2) ? -off : off;
        }
        return new Vec3(x, RIDE_HEIGHT, z).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /**
     * The pilot stands at the burner, feet on the basket floor; mobs stand in the corners, feet on the floor too (vanilla
     * would add the mob's own seated "riding offset", which lifted them half a block into the air).
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (passenger instanceof Player) {
            Vec3 at = BasketShape.toWorld(new Vec3(0, BasketShape.FLOOR_TOP, 0), position(), getYRot());
            moveFunction.accept(passenger, at.x, at.y, at.z);
        } else {
            Vec3 at = position().add(getPassengerAttachmentPoint(passenger, passenger.getDimensions(passenger.getPose()), 1.0F));
            moveFunction.accept(passenger, at.x, at.y, at.z);
            if (!level().isClientSide()) faceCentre(passenger, at);
        }
        if (passenger.isControlledByLocalInstance()) {
            passenger.setYRot(passenger.getYRot() + yawSpeed);
            passenger.setYHeadRot(passenger.getYHeadRot() + yawSpeed);
        }
    }

    /** A mob in a corner stands side-on to the wall, looking across the basket, so its rump stays inside the rim. */
    private void faceCentre(Entity mob, Vec3 at) {
        Vec3 local = BasketShape.toLocal(at, position(), getYRot());
        float yaw = getYRot() + (local.x > 0 ? 90F : -90F);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        if (mob instanceof LivingEntity l) l.yBodyRot = yaw;
    }

    /** Letting go of the controls leaves the pilot standing where they were, in the basket; mobs step out beside it. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (passenger instanceof Player) return BasketShape.toWorld(new Vec3(0, BasketShape.FLOOR_TOP + 0.3, 0), position(), getYRot());
        Vec3 out = getCollisionHorizontalEscapeVector(getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), passenger.getYRot());
        Vec3 spot = new Vec3(getX() + out.x, getY(), getZ() + out.z);
        return onGround() ? spot : super.getDismountLocationForPassenger(passenger);
    }

    /**
     * Vanilla first (leads, shears, sneak-use while leading mobs ties them to the balloon). Then: a chest in hand adds the
     * chest; sneak-use opens it; otherwise take the controls if nobody has them, or step into the basket if someone does.
     */
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return interact(player, hand);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult r = super.interact(player, hand);
        if (r != InteractionResult.PASS) return r;
        ItemStack held = player.getItemInHand(hand);
        InteractionResult looks = restyle(player, held);
        if (looks != InteractionResult.PASS) return looks;
        boolean inBasket = player.getVehicle() != this && !(getControllingPassenger() instanceof Player) && BasketShape.inside(player.position(), position(), getYRot()) && fuel() >= REFUEL_AT;
        if (!inBasket && !player.isSecondaryUseActive() && level() instanceof ServerLevel sl && addFuel(sl, held, player)) {
            playSound(SoundEvents.FIRECHARGE_USE, 0.4F, 1.6F);
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!hasChest() && held.is(Items.CHEST) && !player.isSecondaryUseActive()) {
            if (mobsAboard() > SEATS_WITH_CHEST.length) return InteractionResult.PASS;
            if (!level().isClientSide()) {
                setChest(true);
                held.consume(1, player);
                playSound(SoundEvents.CHEST_LOCKED, 0.6F, 1.2F);
                gameEvent(GameEvent.ENTITY_INTERACT, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (hasChest() && (player.isSecondaryUseActive() || player.getVehicle() != this && !canAddPassenger(player))) {
            InteractionResult open = interactWithContainerVehicle(player);
            if (open.consumesAction()) gameEvent(GameEvent.CONTAINER_OPEN, player);
            return open;
        }
        if (player.isSecondaryUseActive() || player.getVehicle() == this) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(getControllingPassenger() instanceof Player)) {
            boolean ok = player.startRiding(this);
            if (ok && HINTED.add(player.getUUID())) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hotairballoons.hint.controls"), true);
            return ok ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (BasketShape.inside(player.position(), position(), getYRot())) return InteractionResult.PASS;
        Vec3 spot = BasketShape.toWorld(new Vec3(SEAT_OFFSET, BasketShape.FLOOR_TOP + 0.01, SEAT_OFFSET), position(), getYRot());
        player.teleportTo(spot.x, spot.y, spot.z);
        return InteractionResult.CONSUME;
    }

    /**
     * Dye: base gores, or the stripes while sneaking. Banner: the envelope takes its colours and patterns (any earlier
     * banner comes back). Shears: take the emblem off. All consume like vanilla (not in creative).
     */
    private InteractionResult restyle(Player player, ItemStack held) {
        EnvelopeLook look = look();
        net.minecraft.world.item.DyeColor dye = held.getItem() instanceof net.minecraft.world.item.DyeItem d ? d.getDyeColor() : null;
        if (dye != null && !(held.getItem() instanceof net.minecraft.world.item.BannerItem)) {
            boolean stripes = player.isSecondaryUseActive();
            if ((stripes ? look.stripes() : look.base()) == dye) return InteractionResult.PASS;
            if (!level().isClientSide()) {
                setLook(stripes ? look.withStripes(dye) : look.withBase(dye));
                held.consume(1, player);
            }
            playSound(SoundEvents.DYE_USE, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (held.getItem() instanceof net.minecraft.world.item.BannerItem banner && !player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                ItemStack old = emblemBanner(look);
                setLook(look.wearing(banner.getColor(), held.getOrDefault(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                    net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)));
                held.consume(1, player);
                if (!old.isEmpty() && !player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(old);
            }
            playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.SHEARS) && look.emblemBase().isPresent() && !player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                ItemStack old = emblemBanner(look);
                setLook(look.withoutEmblem());
                if (!player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(old);
            }
            playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** The banner an emblem was made from, to give back; empty without one. */
    private static ItemStack emblemBanner(EnvelopeLook look) {
        if (look.emblemBase().isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(net.minecraft.world.level.block.BannerBlock.byColor(look.emblemBase().get()));
        if (!look.emblem().layers().isEmpty()) stack.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, look.emblem());
        return stack;
    }

    /** Fuel left in furnace ticks, or {@link #UNLIMITED}. Synced, so the pilot's client knows when the burner dies. */
    public int fuel() {
        return entityData.get(DATA_FUEL);
    }

    /** Tests and commands: set the burner's fuel directly. */
    public void setFuel(int fuel) {
        entityData.set(DATA_FUEL, Math.clamp(fuel, 0, MAX_FUEL));
    }

    public boolean hasFuel() {
        int f = fuel();
        return f == UNLIMITED || f > 0;
    }

    /** Server: burn fuel while someone is aboard and aloft; top up from the pilot or the chest when low. */
    private void useFuel(ServerLevel sl, boolean piloted) {
        Config c = Config.get();
        boolean creative = getControllingPassenger() instanceof Player cp && cp.getAbilities().instabuild;
        if (!c.needsFuel || creative) {
            if (fuel() != UNLIMITED) savedFuel = Math.max(0, fuel());
            entityData.set(DATA_FUEL, UNLIMITED);
            return;
        }
        int fuel = fuel();
        if (fuel == UNLIMITED) fuel = savedFuel;
        if (piloted && fuel == 0 && pilotBurner && tickCount - lastFuelHint > 100 && getControllingPassenger() instanceof Player out) {
            lastFuelHint = tickCount;
            out.displayClientMessage(net.minecraft.network.chat.Component.translatable("hotairballoons.hint.no_fuel"), true);
        }
        if (piloted && !resting()) {
            fuelUsed += (burning ? BURN_USE : IDLE_USE) * c.fuelUse / 100.0F;
            int whole = (int) fuelUsed;
            fuelUsed -= whole;
            fuel = Math.max(0, fuel - whole);
        }
        if (piloted && fuel < REFUEL_AT) {
            entityData.set(DATA_FUEL, fuel);
            Player pilot = getControllingPassenger() instanceof Player p ? p : null;
            if ((pilot == null || !refuelFrom(sl, pilot.getInventory().items, pilot)) && hasChest()) refuelFrom(sl, getItemStacks(), pilot);
            return;
        }
        entityData.set(DATA_FUEL, fuel);
    }

    private boolean refuelFrom(ServerLevel sl, java.util.List<ItemStack> stacks, @Nullable Player pilot) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && burnTime(sl, stack) > 0 && !stack.has(ModContent.ENVELOPE)) {
                return addFuel(sl, stack, null);
            }
        }
        return false;
    }

    /** One fuel item into the burner if it fits; the item is used up (not for players with infinite materials). */
    private boolean addFuel(ServerLevel sl, ItemStack stack, @Nullable Player player) {
        if (!Config.get().needsFuel || stack.isEmpty()) return false;
        int burn = burnTime(sl, stack);
        int fuel = Math.max(0, fuel());
        if (burn <= 0 || fuel + burn > MAX_FUEL) return false;
        entityData.set(DATA_FUEL, fuel + burn);
        ItemStack left = stack.getCraftingRemainingItem();
        if (player != null) stack.consume(1, player);
        else stack.shrink(1);
        if (!left.isEmpty() && getControllingPassenger() instanceof Player pilot) pilot.getInventory().placeItemBackInInventory(left);
        return true;
    }

    private int burnTime(ServerLevel sl, ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING);
    }

    /** A balloon never falls freely (it sinks at most {@link BalloonPhysics#MAX_SINK}), so landing hurts nobody aboard. */
    @Override
    protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        resetFallDistance();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    public boolean hasChest() {
        return entityData.get(DATA_CHEST);
    }

    public void setChest(boolean chest) {
        entityData.set(DATA_CHEST, chest);
    }

    @Override
    protected void destroy(DamageSource source) {
        if (!resting()) {
            for (Entity e : getPassengers()) {
                if (e instanceof LivingEntity rider) rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 400));
            }
        }
        kill();
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack stack = asItem();
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
            spawnAtLocation(stack);
        }
        if (hasChest()) {
            if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) spawnAtLocation(new ItemStack(Items.CHEST));
            chestVehicleDestroyed(source, level(), this);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) {
            Containers.dropContents(level(), this, this);
            if (isLeashed()) dropLeash(true, true);
        }
        super.remove(reason);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!hasChest()) return;
        player.openMenu(this);
        gameEvent(GameEvent.CONTAINER_OPEN, player);
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (!hasChest() || lootTable != null && player.isSpectator()) return null;
        unpackChestVehicleLootTable(inventory.player);
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public void clearContent() {
        clearChestVehicleContent();
    }

    @Override
    public int getContainerSize() {
        return CHEST_SIZE;
    }

    @Override
    public ItemStack getItem(int slot) {
        return getChestVehicleItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return removeChestVehicleItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeChestVehicleItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setChestVehicleItem(slot, stack);
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return getChestVehicleSlot(slot);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return hasChest() && isChestVehicleStillValid(player);
    }

    @Override
    @Nullable
    public ResourceKey<LootTable> getLootTable() {
        return lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getLootTableSeed() {
        return lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long seed) {
        this.lootTableSeed = seed;
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return itemStacks;
    }

    @Override
    public void clearItemStacks() {
        itemStacks = NonNullList.withSize(CHEST_SIZE, ItemStack.EMPTY);
    }

    @Override
    public void stopOpen(Player user) {
        level().gameEvent(GameEvent.CONTAINER_CLOSE, position(), GameEvent.Context.of(user));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        writeLeashData(tag, leashData);
        tag.putFloat("heat", heat);
        tag.putBoolean("chest", hasChest());
        tag.putInt("fuel", fuel() == UNLIMITED ? savedFuel : Math.max(0, fuel()));
        if (!look().equals(EnvelopeLook.DEFAULT)) {
            EnvelopeLook.CODEC.encodeStart(registryAccess().createSerializationContext(NbtOps.INSTANCE), look()).result().ifPresent(t -> tag.put("look", t));
        }
        if (hasChest()) addChestVehicleSaveData(tag, registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        leashData = readLeashData(tag);
        heat = tag.contains("heat") ? tag.getFloat("heat") : BalloonPhysics.NEUTRAL;
        setChest(tag.getBoolean("chest"));
        entityData.set(DATA_FUEL, tag.getInt("fuel"));
        setLook(tag.contains("look") ? EnvelopeLook.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("look")).result().orElse(EnvelopeLook.DEFAULT) : EnvelopeLook.DEFAULT);
        readChestVehicleSaveData(tag, registryAccess());
    }

    @Override
    @Nullable public Leashable.LeashData getLeashData() {
        return leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public Vec3 getLeashOffset() {
        // The rope is tied to the outside of the rim, on the side facing whoever holds it (vanilla turns this offset
        // with the balloon, so it is worked out in the basket's own frame).
        double lx = 0, lz = 1;
        Entity holder = getLeashHolder();
        if (holder != null) {
            Vec3 d = holder.position().subtract(position());
            Vec3 l = new Vec3(d.x, 0, d.z).yRot(getYRot() * Mth.DEG_TO_RAD);
            if (l.x * l.x + l.z * l.z > 1e-4) {
                lx = l.x;
                lz = l.z;
            }
        }
        double k = (BasketShape.OUTER + 0.02) / Math.max(Math.abs(lx), Math.abs(lz));
        return new Vec3(lx * k, BasketShape.RIM - 0.1, lz * k);
    }
}
