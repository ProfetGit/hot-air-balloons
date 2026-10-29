package io.github.profetgit.hotairballoons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.hotairballoons.entity.BasketShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entities collide with balloon baskets as a floor and walls ({@link BasketShape}), on both sides. */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {
    @WrapOperation(method = "collide",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> hotairballoons$baskets(Level level, Entity source, AABB area, Operation<List<VoxelShape>> original) {
        List<VoxelShape> shapes = original.call(level, source, area);
        List<VoxelShape> baskets = BasketShape.near(level, source, area);
        if (baskets.isEmpty()) return shapes;
        List<VoxelShape> all = new ArrayList<>(shapes.size() + baskets.size());
        all.addAll(shapes);
        all.addAll(baskets);
        return all;
    }
}
