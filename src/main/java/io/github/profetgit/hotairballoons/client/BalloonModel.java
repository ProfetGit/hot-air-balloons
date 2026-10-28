package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.HotAirBalloons;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Placeholder balloon (M1): basket walls, floor, four ropes, burner, flame and a box envelope. Model space is the
 * renderer's flipped space: -y is up, 16 units per block, origin at the basket floor.
 */
public class BalloonModel extends EntityModel<BalloonRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HotAirBalloons.id("hot_air_balloon"), "main");
    private final ModelPart flame;

    public BalloonModel(ModelPart root) {
        super(root);
        this.flame = root.getChild("flame");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("envelope", CubeListBuilder.create().texOffs(0, 0).addBox(-36, -136, -36, 72, 80, 72), PartPose.ZERO);
        root.addOrReplaceChild("wall_n", CubeListBuilder.create().texOffs(300, 0).addBox(-12, -14, -12, 24, 14, 2), PartPose.ZERO);
        root.addOrReplaceChild("wall_s", CubeListBuilder.create().texOffs(300, 20).addBox(-12, -14, 10, 24, 14, 2), PartPose.ZERO);
        root.addOrReplaceChild("wall_e", CubeListBuilder.create().texOffs(300, 40).addBox(10, -14, -10, 2, 14, 20), PartPose.ZERO);
        root.addOrReplaceChild("wall_w", CubeListBuilder.create().texOffs(300, 80).addBox(-12, -14, -10, 2, 14, 20), PartPose.ZERO);
        root.addOrReplaceChild("floor", CubeListBuilder.create().texOffs(0, 160).addBox(-12, -1, -12, 24, 1, 24), PartPose.ZERO);
        int[][] corners = {{-11, -11}, {10, -11}, {10, 10}, {-11, 10}};
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("rope" + i, CubeListBuilder.create().texOffs(100 + i * 10, 160)
                .addBox(corners[i][0], -56, corners[i][1], 1, 42, 1), PartPose.ZERO);
        }
        root.addOrReplaceChild("burner", CubeListBuilder.create().texOffs(140, 160).addBox(-4, -46, -4, 8, 6, 8), PartPose.ZERO);
        root.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(180, 160).addBox(-2, -54, -2, 4, 8, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 512, 256);
    }

    @Override
    public void setupAnim(BalloonRenderState state) {
        super.setupAnim(state);
        flame.visible = state.burning;
    }
}
