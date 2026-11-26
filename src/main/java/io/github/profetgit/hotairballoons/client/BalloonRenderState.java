package io.github.profetgit.hotairballoons.client;

//? if >=26.1 {
import net.minecraft.client.renderer.block.BlockModelRenderState;
//?}
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}

//? if >=1.21.2 {
public class BalloonRenderState extends EntityRenderState {
//?} else {
/*// Before 1.21.2 there are no entity render states: the renderer fills this from the balloon each frame.
public class BalloonRenderState {
    public float ageInTicks;
*///?}
    public float yRot;
    public int hurtDir;
    public float hurtTime;
    public float damageTime;
    public float heat;
    public boolean burning;
    /** Envelope lean (radians, model x/z) and flame size, interpolated; inflate progress 0..1. */
    public float swayX, swayZ, flame, inflate = 1;
    /** Envelope dye colours (ARGB): the base gores and the stripe gores, from the balloon's synced look. */
    public int primary = 0xFFF9FFFE, secondary = 0xFFB02E26;
    /** Emblem layers bottom-up (banner base first), as envelope-sized textures and their tints; emblemCount used. */
    public final net.minecraft.resources.Identifier[] emblemTextures = new net.minecraft.resources.Identifier[1 + io.github.profetgit.hotairballoons.entity.EnvelopeLook.MAX_LAYERS];
    public final int[] emblemColors = new int[1 + io.github.profetgit.hotairballoons.entity.EnvelopeLook.MAX_LAYERS];
    public int emblemCount;
    /** The chest in the basket's back corner; empty without one. */
    //? if >=26.1 {
    public final BlockModelRenderState chest = new BlockModelRenderState();
    //?} else {
    /*public net.minecraft.world.level.block.state.BlockState chest = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    *///?}
}
