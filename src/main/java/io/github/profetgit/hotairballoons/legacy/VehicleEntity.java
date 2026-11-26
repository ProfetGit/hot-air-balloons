package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

// The parts of 1.20.3's VehicleEntity the balloon builds on (dev/renames.json points the import here), plus what its
// 1.20.1 base needs: the synced leash holder (see Leashable) and the random.
public abstract class VehicleEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ID_LEASH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

    protected VehicleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved()) return true;
        if (isInvulnerableTo(source)) return false;
        setHurtDir(-getHurtDir());
        setHurtTime(10);
        markHurt();
        setDamage(getDamage() + amount * 10.0F);
        gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
        if (!creative && getDamage() > 40.0F) destroy(source);
        else if (creative) discard();
        return true;
    }

    protected void destroy(DamageSource source) {
        kill();
        spawnAtLocation(getDropItem());
    }

    protected abstract Item getDropItem();

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ID_HURT, 0);
        entityData.define(DATA_ID_HURTDIR, 1);
        entityData.define(DATA_ID_DAMAGE, 0.0F);
        entityData.define(DATA_ID_LEASH, 0);
    }

    public void setHurtTime(int ticks) {
        entityData.set(DATA_ID_HURT, ticks);
    }

    public void setHurtDir(int dir) {
        entityData.set(DATA_ID_HURTDIR, dir);
    }

    public void setDamage(float damage) {
        entityData.set(DATA_ID_DAMAGE, damage);
    }

    public float getDamage() {
        return entityData.get(DATA_ID_DAMAGE);
    }

    public int getHurtTime() {
        return entityData.get(DATA_ID_HURT);
    }

    public int getHurtDir() {
        return entityData.get(DATA_ID_HURTDIR);
    }

    public int syncedLeashHolder() {
        return entityData.get(DATA_ID_LEASH);
    }

    public void syncLeashHolder(int id) {
        entityData.set(DATA_ID_LEASH, id);
    }

    public RandomSource getRandom() {
        return random;
    }
}
*///?}
