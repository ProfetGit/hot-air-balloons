package io.github.profetgit.hotairballoons.item;

import io.github.profetgit.hotairballoons.ModContent;
import io.github.profetgit.hotairballoons.entity.EnvelopeLook;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Places a balloon on the clicked ground or water, facing the way the player looks (boat-style), in the item's look. */
public class BalloonItem extends Item {
    public BalloonItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (!(hit instanceof BlockHitResult bhit) || hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        HotAirBalloon balloon = ModContent.BALLOON.create(level);
        if (balloon == null) return InteractionResultHolder.fail(stack);
        Vec3 at = bhit.getLocation();
        balloon.setInitialPos(at.x, at.y, at.z);
        balloon.setYRot(player.getYRot());
        if (!level.noCollision(balloon, balloon.getBoundingBox())) {
            player.displayClientMessage(Component.translatable("hotairballoons.hint.no_room"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel sl) {
            EntityType.<HotAirBalloon>createDefaultStackConfig(sl, stack, player).accept(balloon);
            balloon.setLook(stack.getOrDefault(ModContent.ENVELOPE, EnvelopeLook.DEFAULT));
            balloon.startInflating();
            level.addFreshEntity(balloon);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        stack.getOrDefault(ModContent.ENVELOPE, EnvelopeLook.DEFAULT).addToTooltip(tooltip::add);
    }
}
