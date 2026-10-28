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
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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
    /** Rider feet sit this high above the entity origin (the basket floor). */
    private static final double RIDE_HEIGHT = 0.6 + 2.0 / 16.0;
    private static final double SEAT_OFFSET = 0.42;
    /** Envelope box relative to the basket floor: skirt hem to crown of dev/art/balloon.bbmodel (16 units per block). */
    public static final double ENVELOPE_HALF = 46 / 16.0, ENVELOPE_BOTTOM = 58 / 16.0, ENVELOPE_TOP = 170 / 16.0;
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
    private float fuelUsed;
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(CHEST_SIZE, ItemStack.EMPTY);
    private @Nullable ResourceKey<LootTable> lootTable;
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
            boolean lit = piloted && hasFuel();
            heat = BalloonPhysics.heat(heat, burner && lit, vent, lit || isLeashed());
            burning = burner && lit;
        }
        if (level() instanceof ServerLevel sl) {
            entityData.set(DATA_HEAT, heat);
            entityData.set(DATA_BURNER, burning);
            useFuel(sl, piloted);
            double[] w = Wind.at(sl.getSeed(), getY(), sl.getGameTime());
            double k = Config.get().wind / 100.0;
            entityData.set(DATA_WIND_X, (float) (w[0] * k));
            entityData.set(DATA_WIND_Z, (float) (w[1] * k));
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

    /** Server, on the ground: mobs that walk into the basket climb in, like boats take them (players board by click). */
    private void boardMobs() {
        if (getPassengers().size() >= seats()) return;
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.2, -0.01, 0.2), EntitySelector.pushableBy(this))) {
            if (getPassengers().size() >= seats()) return;
            if (e instanceof LivingEntity && !(e instanceof Player) && !e.isPassenger() && !e.hasPassenger(this)
                && !e.is(EntityTypeTags.CANNOT_BE_PUSHED_ONTO_BOATS) && e.getBbWidth() < MAX_MOB_WIDTH && !(e instanceof Leashable l && l.isLeashed())) {
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

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats();
    }

    /** Four, or three with a chest in the basket. */
    public int seats() {
        return hasChest() ? SEATS_WITH_CHEST.length : MAX_PASSENGERS;
    }

    /** The pilot: the first player aboard, so a sheep that walked in first doesn't take the controls. */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        for (Entity e : getPassengers()) if (e instanceof Player p) return p;
        return null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int i = getPassengers().indexOf(passenger);
        int[] order = hasChest() ? SEATS_WITH_CHEST : SEATS;
        double x = 0, z = 0;
        if ((getPassengers().size() > 1 || hasChest()) && i >= 0 && i < order.length) {
            int c = order[i];
            x = (c == 1 || c == 2) ? SEAT_OFFSET : -SEAT_OFFSET;
            z = (c >= 2) ? -SEAT_OFFSET : SEAT_OFFSET;
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

    /**
     * Vanilla first (leads, shears, sneak-use while leading mobs ties them to the balloon). Then: a chest in hand adds the
     * chest; sneak-use or a full basket opens it; anything else boards.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult r = super.interact(player, hand, location);
        if (r != InteractionResult.PASS) return r;
        ItemStack held = player.getItemInHand(hand);
        InteractionResult looks = restyle(player, held);
        if (looks != InteractionResult.PASS) return looks;
        if (!player.isSecondaryUseActive() && level() instanceof ServerLevel sl && addFuel(sl, held, player)) {
            playSound(SoundEvents.FIRECHARGE_USE, 0.4F, 1.6F);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!hasChest() && held.is(Items.CHEST) && !player.isSecondaryUseActive()) {
            if (getPassengers().size() > SEATS_WITH_CHEST.length) return InteractionResult.PASS;
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
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide()) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    /**
     * Dye: base gores, or the stripes while sneaking. Banner: the envelope takes its colours and patterns (any earlier
     * banner comes back). Shears: take the emblem off. All consume like vanilla (not in creative).
     */
    private InteractionResult restyle(Player player, ItemStack held) {
        EnvelopeLook look = look();
        net.minecraft.world.item.DyeColor dye = held.get(net.minecraft.core.component.DataComponents.DYE);
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
                if (!old.isEmpty() && !player.hasInfiniteMaterials()) player.getInventory().placeItemBackInInventory(old, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.SHEARS) && look.emblemBase().isPresent() && !player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                ItemStack old = emblemBanner(look);
                setLook(look.withoutEmblem());
                if (!player.hasInfiniteMaterials()) player.getInventory().placeItemBackInInventory(old, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** The banner an emblem was made from, to give back; empty without one. */
    private static ItemStack emblemBanner(EnvelopeLook look) {
        if (look.emblemBase().isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Items.BANNER.pick(look.emblemBase().get()));
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

    /** Server: burn fuel while a pilot is aloft; top up from the pilot or the chest when low. */
    private void useFuel(ServerLevel sl, boolean piloted) {
        Config c = Config.get();
        if (!c.needsFuel) {
            entityData.set(DATA_FUEL, UNLIMITED);
            return;
        }
        int fuel = fuel();
        if (fuel == UNLIMITED) fuel = 0;
        if (piloted && !resting()) {
            fuelUsed += (burning ? BURN_USE : IDLE_USE) * c.fuelUse / 100.0F;
            int whole = (int) fuelUsed;
            fuelUsed -= whole;
            fuel = Math.max(0, fuel - whole);
        }
        if (piloted && fuel < REFUEL_AT && getControllingPassenger() instanceof Player pilot) {
            entityData.set(DATA_FUEL, fuel);
            if (!refuelFrom(sl, pilot.getInventory().getNonEquipmentItems(), pilot) && hasChest()) refuelFrom(sl, getItemStacks(), pilot);
            return;
        }
        entityData.set(DATA_FUEL, fuel);
    }

    private boolean refuelFrom(ServerLevel sl, java.util.List<ItemStack> stacks, Player pilot) {
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
        var remainder = stack.getItem().getCraftingRemainder();
        ItemStack left = remainder == null ? ItemStack.EMPTY : remainder.create();
        if (player != null) stack.consume(1, player);
        else stack.shrink(1);
        if (!left.isEmpty() && getControllingPassenger() instanceof Player pilot) pilot.getInventory().placeItemBackInInventory(left, net.minecraft.util.Prediction.SERVER_ONLY);
        return true;
    }

    private int burnTime(ServerLevel sl, ItemStack stack) {
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(sl)
            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, position())
            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.COMMAND);
        var ctx = new net.minecraft.world.level.storage.loot.LootContext.Builder(params).create(java.util.Optional.empty());
        return net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.getFromItem(stack,
            net.minecraft.core.component.DataComponents.COOKING_FUEL, net.minecraft.world.item.component.CookingFuel::burnTime, ctx, 0);
    }

    /** A balloon never falls freely (it sinks at most {@link BalloonPhysics#MAX_SINK}), so landing hurts nobody aboard. */
    @Override
    protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        resetFallDistance();
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    public boolean hasChest() {
        return entityData.get(DATA_CHEST);
    }

    public void setChest(boolean chest) {
        entityData.set(DATA_CHEST, chest);
    }

    @Override
    public void destroy(ServerLevel level, DamageSource source) {
        if (!resting()) {
            for (Entity e : getPassengers()) {
                if (e instanceof LivingEntity rider) rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 400));
            }
        }
        kill(level);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack stack = asItem();
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
            spawnAtLocation(level, stack);
        }
        if (hasChest()) {
            if (level.getGameRules().get(GameRules.ENTITY_DROPS)) spawnAtLocation(level, new ItemStack(Items.CHEST));
            chestVehicleDestroyed(source, level, this);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) {
            Containers.dropContents(level(), this, this);
            if (isLeashed()) dropLeash();
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
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
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
    public @Nullable ResourceKey<LootTable> getContainerLootTable() {
        return lootTable;
    }

    @Override
    public void setContainerLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getContainerLootTableSeed() {
        return lootTableSeed;
    }

    @Override
    public void setContainerLootTableSeed(long seed) {
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
    public void stopOpen(ContainerUser user) {
        level().gameEvent(GameEvent.CONTAINER_CLOSE, position(), GameEvent.Context.of(user.getLivingEntity()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        writeLeashData(output, leashData);
        output.putFloat("heat", heat);
        output.putBoolean("chest", hasChest());
        output.putInt("fuel", Math.max(0, fuel()));
        if (!look().equals(EnvelopeLook.DEFAULT)) output.store("look", EnvelopeLook.CODEC, look());
        if (hasChest()) addChestVehicleSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        readLeashData(input);
        heat = input.getFloatOr("heat", BalloonPhysics.NEUTRAL);
        setChest(input.getBooleanOr("chest", false));
        entityData.set(DATA_FUEL, input.getIntOr("fuel", 0));
        setLook(input.read("look", EnvelopeLook.CODEC).orElse(EnvelopeLook.DEFAULT));
        readChestVehicleSaveData(input);
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
