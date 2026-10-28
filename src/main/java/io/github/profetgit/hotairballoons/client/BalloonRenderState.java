package io.github.profetgit.hotairballoons.client;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class BalloonRenderState extends EntityRenderState {
    public float yRot;
    public int hurtDir;
    public float hurtTime;
    public float damageTime;
    public float heat;
    public boolean burning;
    /** Envelope lean (radians, model x/z) and flame size, interpolated; inflate progress 0..1. */
    public float swayX, swayZ, flame, inflate = 1;
    /** Envelope dye colours (ARGB): the base gores and the stripe gores. M5 syncs these per balloon. */
    public int primary = 0xFFF9FFFE, secondary = 0xFFB02E26;
    /** The chest in the basket's back corner; empty without one. */
    public final BlockModelRenderState chest = new BlockModelRenderState();
}
