package io.github.profetgit.hotairballoons.item;

import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Places a balloon on the clicked ground or water, facing the way the player looks (boat-style). */
public class BalloonItem extends Item {
    public BalloonItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (!(hit instanceof BlockHitResult bhit) || hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        HotAirBalloon balloon = ModContent.BALLOON.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (balloon == null) return InteractionResult.FAIL;
        Vec3 at = bhit.getLocation();
        balloon.setInitialPos(at.x, at.y, at.z);
        balloon.setYRot(player.getYRot());
        if (!level.noCollision(balloon, balloon.getBoundingBox())) return InteractionResult.FAIL;
        if (level instanceof ServerLevel sl) {
            EntityType.<HotAirBalloon>createDefaultStackConfig(sl, stack, player).apply(balloon);
            balloon.startInflating();
            level.addFreshEntity(balloon);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }
}
