package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// The balloon's rope, as 1.21.1's EntityRenderer draws it (before 1.20.5 only mob renderers draw ropes). Client only.
public final class LeashRope {
    private LeashRope() {}

    public static void draw(HotAirBalloon entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffers) {
        Entity holder = entity.getLeashHolder();
        if (holder == null) return;
        poseStack.pushPose();
        Vec3 to = holder.getRopeHoldPosition(partialTicks);
        double yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) * (float) (Math.PI / 180.0) + (Math.PI / 2);
        Vec3 off = entity.getLeashOffset(partialTicks);
        double ox = Math.cos(yaw) * off.z + Math.sin(yaw) * off.x;
        double oz = Math.sin(yaw) * off.z - Math.cos(yaw) * off.x;
        double fx = Mth.lerp(partialTicks, entity.xo, entity.getX()) + ox;
        double fy = Mth.lerp(partialTicks, entity.yo, entity.getY()) + off.y;
        double fz = Mth.lerp(partialTicks, entity.zo, entity.getZ()) + oz;
        poseStack.translate(ox, off.y, oz);
        float x = (float) (to.x - fx);
        float y = (float) (to.y - fy);
        float z = (float) (to.z - fz);
        VertexConsumer buffer = buffers.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();
        float width = Mth.invSqrt(x * x + z * z) * 0.025F / 2.0F;
        float wx = z * width;
        float wz = x * width;
        BlockPos from = BlockPos.containing(entity.getEyePosition(partialTicks));
        BlockPos at = BlockPos.containing(holder.getEyePosition(partialTicks));
        int blockFrom = blockLight(entity, from), blockTo = blockLight(holder, at);
        int skyFrom = entity.level().getBrightness(LightLayer.SKY, from), skyTo = entity.level().getBrightness(LightLayer.SKY, at);
        for (int i = 0; i <= 24; i++) {
            pair(buffer, pose, x, y, z, blockFrom, blockTo, skyFrom, skyTo, 0.025F, wx, wz, i, false);
        }
        for (int i = 24; i >= 0; i--) {
            pair(buffer, pose, x, y, z, blockFrom, blockTo, skyFrom, skyTo, 0.0F, wx, wz, i, true);
        }
        poseStack.popPose();
    }

    private static int blockLight(Entity e, BlockPos pos) {
        return e.isOnFire() ? 15 : e.level().getBrightness(LightLayer.BLOCK, pos);
    }

    private static void pair(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int blockFrom, int blockTo, int skyFrom, int skyTo,
                             float rise, float wx, float wz, int i, boolean back) {
        float t = i / 24.0F;
        int light = LightTexture.pack((int) Mth.lerp(t, blockFrom, blockTo), (int) Mth.lerp(t, skyFrom, skyTo));
        float shade = i % 2 == (back ? 1 : 0) ? 0.7F : 1.0F;
        float r = 0.5F * shade, g = 0.4F * shade, b = 0.3F * shade;
        float px = x * t;
        float py = y > 0.0F ? y * t * t : y - y * (1.0F - t) * (1.0F - t);
        float pz = z * t;
        buffer.vertex(pose, px - wx, py + rise, pz + wz).color(r, g, b, 1.0F).uv2(light).endVertex();
        buffer.vertex(pose, px + wx, py + 0.025F - rise, pz - wz).color(r, g, b, 1.0F).uv2(light).endVertex();
    }
}
*///?}
