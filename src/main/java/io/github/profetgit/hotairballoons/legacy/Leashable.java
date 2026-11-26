package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

// 1.20.5's Leashable as 1.21.1 behaves (dev/renames.json points the import here): before it only mobs can be leashed. The
// balloon implements it; its holder lives on the server, is synced as an entity id and saved in the newer "leash" form.
// Of.mob is the same view of a vanilla mob.
public interface Leashable {
    @Nullable LeashData getLeashData();

    void setLeashData(@Nullable LeashData data);

    int syncedLeashHolder();

    void syncLeashHolder(int id);

    static @Nullable Leashable of(Entity e) {
        return e instanceof Leashable l ? l : e instanceof Mob m ? new OfMob(m) : null;
    }

    default Entity entity() {
        return (Entity) this;
    }

    default boolean isLeashed() {
        return getLeashHolder() != null;
    }

    default boolean canBeLeashed() {
        return true;
    }

    default @Nullable Entity getLeashHolder() {
        if (entity().level().isClientSide()) {
            int id = syncedLeashHolder();
            return id == 0 ? null : entity().level().getEntity(id);
        }
        LeashData data = getLeashData();
        return data == null ? null : data.holder;
    }

    default void setLeashedTo(Entity holder, boolean broadcast) {
        LeashData data = getLeashData();
        if (data == null) {
            data = new LeashData();
            setLeashData(data);
        }
        data.holder = holder;
        data.uuid = null;
        data.knot = null;
        if (!entity().level().isClientSide()) syncLeashHolder(holder.getId());
        if (entity().isPassenger()) entity().stopRiding();
    }

    default void dropLeash(boolean broadcast, boolean dropItem) {
        LeashData data = getLeashData();
        if (data == null || data.holder == null) return;
        setLeashData(null);
        if (entity().level().isClientSide()) return;
        syncLeashHolder(0);
        if (dropItem) entity().spawnAtLocation(Items.LEAD);
    }

    // The lead part of newer Entity.interact: clicking your own rope takes it off, a lead in hand ties.
    default InteractionResult interactLeash(Player player, InteractionHand hand) {
        if (!entity().isAlive()) return InteractionResult.PASS;
        boolean client = entity().level().isClientSide();
        if (getLeashHolder() == player) {
            if (!client) {
                dropLeash(true, !player.getAbilities().instabuild);
                entity().gameEvent(GameEvent.ENTITY_INTERACT, player);
            }
            return InteractionResult.sidedSuccess(client);
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.LEAD) && canBeLeashed() && !isLeashed()) {
            if (!client) setLeashedTo(player, true);
            held.shrink(1);
            return InteractionResult.sidedSuccess(client);
        }
        return InteractionResult.PASS;
    }

    // Server, at the end of baseTick.
    default void tickLeash() {
        LeashData data = getLeashData();
        if (data == null) return;
        if (data.uuid != null || data.knot != null) restoreLeash(data);
        if (data.holder == null) return;
        Entity self = entity();
        if (!self.isAlive() || !data.holder.isAlive()) dropLeash(true, true);
        Entity holder = getLeashHolder();
        if (holder == null || holder.level() != self.level()) return;
        float distance = self.distanceTo(holder);
        if (distance > 10.0F) leashTooFarBehaviour();
        else if (distance > 6.0F) elasticRangeLeashBehaviour(holder, distance);
    }

    private void restoreLeash(LeashData data) {
        if (!(entity().level() instanceof ServerLevel level)) return;
        if (data.uuid != null) {
            Entity holder = level.getEntity(data.uuid);
            if (holder != null) {
                setLeashedTo(holder, true);
                return;
            }
        } else {
            setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, data.knot), true);
            return;
        }
        if (entity().tickCount > 100) {
            entity().spawnAtLocation(Items.LEAD);
            setLeashData(null);
        }
    }

    default void leashTooFarBehaviour() {
        dropLeash(true, true);
    }

    default void elasticRangeLeashBehaviour(Entity holder, float distance) {
    }

    default void writeLeashData(CompoundTag tag, @Nullable LeashData data) {
        if (data == null) return;
        UUID uuid = data.uuid;
        BlockPos knot = data.knot;
        if (data.holder instanceof LeashFenceKnotEntity k) {
            uuid = null;
            knot = k.getPos();
        } else if (data.holder != null) {
            uuid = data.holder.getUUID();
        }
        if (uuid != null) {
            CompoundTag leash = new CompoundTag();
            leash.putUUID("UUID", uuid);
            tag.put("leash", leash);
        } else if (knot != null) {
            tag.put("leash", new IntArrayTag(new int[] {knot.getX(), knot.getY(), knot.getZ()}));
        }
    }

    default @Nullable LeashData readLeashData(CompoundTag tag) {
        LeashData data = new LeashData();
        if (tag.contains("leash", Tag.TAG_COMPOUND)) {
            data.uuid = tag.getCompound("leash").getUUID("UUID");
            return data;
        }
        int[] pos = tag.getIntArray("leash");
        if (pos.length != 3) return null;
        data.knot = new BlockPos(pos[0], pos[1], pos[2]);
        return data;
    }

    final class LeashData {
        @Nullable Entity holder;
        @Nullable UUID uuid;
        @Nullable BlockPos knot;
    }

    record OfMob(Mob mob) implements Leashable {
        @Override
        public @Nullable LeashData getLeashData() {
            return null;
        }

        @Override
        public void setLeashData(@Nullable LeashData data) {
        }

        @Override
        public int syncedLeashHolder() {
            return 0;
        }

        @Override
        public void syncLeashHolder(int id) {
        }

        @Override
        public Entity entity() {
            return mob;
        }

        @Override
        public boolean canBeLeashed() {
            return !(mob instanceof Enemy);
        }

        @Override
        public @Nullable Entity getLeashHolder() {
            return mob.getLeashHolder();
        }

        @Override
        public void setLeashedTo(Entity holder, boolean broadcast) {
            mob.setLeashedTo(holder, broadcast);
        }

        @Override
        public void dropLeash(boolean broadcast, boolean dropItem) {
            mob.dropLeash(broadcast, dropItem);
        }
    }
}
*///?}
