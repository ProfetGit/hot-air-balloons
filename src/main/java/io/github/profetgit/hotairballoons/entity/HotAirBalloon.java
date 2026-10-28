package io.github.profetgit.hotairballoons.entity;

import io.github.profetgit.hotairballoons.ModContent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The balloon: a vehicle moved by whoever is authoritative, like a boat (the controlling player's client, else the
 * server). Heat is simulated on both sides from the pilot's keys; the server's copy is synced for everyone else's
 * flame and sound. Wind is computed on the server and synced, since clients don't know the seed.
 */
public class HotAirBalloon extends VehicleEntity implements Leashable {
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BURNER = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_WIND_X = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIND_Z = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.FLOAT);
    /** Server ticks left of the inflate animation after placing; never saved, so loaded balloons start inflated. */
    private static final EntityDataAccessor<Integer> DATA_INFLATE = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.INT);
    public static final int INFLATE_TICKS = 40;
    public static final int MAX_PASSENGERS = 4;
    public static final double CEILING = 320.0;
    /** Rider feet sit this high above the entity origin (the basket floor). */
    private static final double RIDE_HEIGHT = 0.6 + 2.0 / 16.0;
    private static final double SEAT_OFFSET = 0.42;
    /** Envelope box relative to the basket floor: skirt hem to crown of dev/art/balloon.bbmodel (16 units per block). */
    public static final double ENVELOPE_HALF = 40 / 16.0, ENVELOPE_BOTTOM = 58 / 16.0, ENVELOPE_TOP = 157 / 16.0;
    /** Slack rope length before the tether pulls; vanilla's lead snaps at 12. */
    public static final double ROPE = 8.0;

    /** Set by the client entry: the local player's keys. Null on a dedicated server. */
    public static @Nullable Supplier<Input> localInput;
    /** Set by the client entry: per-tick client effects (sounds, wind wisps). */
    public static @Nullable Consumer<HotAirBalloon> clientTick;

    private float heat = BalloonPhysics.NEUTRAL;
    private float yawSpeed;
    private boolean burning;
    private boolean pilotBurner, pilotVent;
    private Leashable.@Nullable LeashData leashData;
    private int inflateLeft;

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
        entityData.define(DATA_INFLATE, 0);
    }

    @Override
    protected Item getDropItem() {
        return ModContent.BALLOON_ITEM;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModContent.BALLOON_ITEM);
    }

    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return net.minecraft.world.entity.LinearInterpolationHandler.create(this, 3);
    }

    /** Heat for rendering: the local simulation when this client flies it, else the server's. */
    public float heat() {
        return level().isClientSide() && !isLocalInstanceAuthoritative() ? entityData.get(DATA_HEAT) : heat;
    }

    public boolean burning() {
        return level().isClientSide() && !isLocalInstanceAuthoritative() ? entityData.get(DATA_BURNER) : burning;
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

    /** Server: the pilot's burner/vent keys from {@link io.github.profetgit.hotairballoons.net.BalloonInput}. */
    public void setPilotKeys(boolean burner, boolean vent) {
        pilotBurner = burner;
        pilotVent = vent;
    }

    /** The pilot's keys on this side: the local keyboard on the pilot's client, the last BalloonInput on the server. */
    private @Nullable Input pilotInput() {
        if (!(getControllingPassenger() instanceof Player)) {
            pilotBurner = pilotVent = false;
            return null;
        }
        if (level().isClientSide()) return isLocalInstanceAuthoritative() && localInput != null ? localInput.get() : null;
        return new Input(false, false, false, false, pilotBurner, false, pilotVent);
    }

    @Override
    public void tick() {
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0.0F) setDamage(getDamage() - 1.0F);
        super.tick();

        Input in = pilotInput();
        boolean piloted = getControllingPassenger() instanceof Player;
        boolean burner = in != null && in.jump();
        boolean vent = in != null && in.sprint();
        if (!level().isClientSide() || isLocalInstanceAuthoritative()) {
            heat = BalloonPhysics.heat(heat, burner, vent, piloted || isLeashed());
            burning = burner && piloted;
        }
        if (level() instanceof ServerLevel sl) {
            entityData.set(DATA_HEAT, heat);
            entityData.set(DATA_BURNER, burning);
            double[] w = Wind.at(sl.getSeed(), getY(), sl.getGameTime());
            entityData.set(DATA_WIND_X, (float) w[0]);
            entityData.set(DATA_WIND_Z, (float) w[1]);
            if (inflateLeft > 0) entityData.set(DATA_INFLATE, --inflateLeft);
        }

        if (isLocalInstanceAuthoritative()) {
            fly(in);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
            yawSpeed = 0;
        }
        applyEffectsFromBlocks();
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

    private void fly(@Nullable Input in) {
        Vec3 v = getDeltaMovement();
        boolean grounded = onGround();
        double vy = BalloonPhysics.climb(v.y, heat, getY(), CEILING);
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
        vx = BalloonPhysics.horizontal(vx, w.x, 0, drag);
        vz = BalloonPhysics.horizontal(vz, w.z, 0, drag);
        Entity anchor = getLeashHolder();
        if (anchor != null) {
            Vec3 d = position().subtract(anchor.position());
            double[] t = BalloonPhysics.tether(d.x, d.y, d.z, vx, vy, vz, ROPE);
            vx = t[0];
            vy = t[1];
            vz = t[2];
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
    public boolean checkElasticInteractions(Entity leashHolder, Leashable.LeashData leashData) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < MAX_PASSENGERS;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : super.getControllingPassenger();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int i = getPassengers().indexOf(passenger);
        double x = 0, z = 0;
        if (getPassengers().size() > 1 && i >= 0) {
            x = (i == 1 || i == 2) ? SEAT_OFFSET : -SEAT_OFFSET;
            z = (i >= 2) ? -SEAT_OFFSET : SEAT_OFFSET;
        }
        return new Vec3(x, RIDE_HEIGHT, z).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger.isLocalInstanceAuthoritative()) {
            passenger.setYRot(passenger.getYRot() + yawSpeed);
            passenger.setYHeadRot(passenger.getYHeadRot() + yawSpeed);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 out = getCollisionHorizontalEscapeVector(getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), passenger.getYRot());
        Vec3 spot = new Vec3(getX() + out.x, getY(), getZ() + out.z);
        return onGround() ? spot : super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult r = super.interact(player, hand, location);
        if (r != InteractionResult.PASS) return r;
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide()) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        writeLeashData(output, leashData);
        output.putFloat("heat", heat);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        readLeashData(input);
        heat = input.getFloatOr("heat", BalloonPhysics.NEUTRAL);
    }

    @Override
    public Leashable.@Nullable LeashData getLeashData() {
        return leashData;
    }

    @Override
    public void setLeashData(Leashable.@Nullable LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.9 * getBbHeight(), 0.0);
    }
}
