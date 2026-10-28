package io.github.profetgit.hotairballoons.entity;

import io.github.profetgit.hotairballoons.ModContent;
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
    public static final int MAX_PASSENGERS = 4;
    public static final double CEILING = 320.0;
    /** Rider feet sit this high above the entity origin (the basket floor). */
    private static final double RIDE_HEIGHT = 0.6;
    private static final double SEAT_OFFSET = 0.42;

    /** Set by the client entry: the local player's keys. Null on a dedicated server. */
    public static @Nullable Supplier<Input> localInput;

    private float heat = BalloonPhysics.NEUTRAL;
    private float yawSpeed;
    private boolean burning;
    private boolean pilotBurner, pilotVent;
    private Leashable.@Nullable LeashData leashData;

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
            heat = BalloonPhysics.heat(heat, burner, vent, piloted);
            burning = burner && piloted;
        }
        if (level() instanceof ServerLevel sl) {
            entityData.set(DATA_HEAT, heat);
            entityData.set(DATA_BURNER, burning);
            double[] w = Wind.at(sl.getSeed(), getY(), sl.getGameTime());
            entityData.set(DATA_WIND_X, (float) w[0]);
            entityData.set(DATA_WIND_Z, (float) w[1]);
        }

        if (isLocalInstanceAuthoritative()) {
            fly(in);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
            yawSpeed = 0;
        }
        applyEffectsFromBlocks();
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
        vx *= drag;
        vz *= drag;
        if (!grounded && !isLeashed()) {
            Vec3 w = wind();
            vx += (w.x - vx) * (1 - BalloonPhysics.AIR_DRAG);
            vz += (w.z - vz) * (1 - BalloonPhysics.AIR_DRAG);
        }
        setDeltaMovement(vx, vy, vz);
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
