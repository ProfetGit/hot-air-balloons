package io.github.profetgit.hotairballoons.client;

import net.minecraft.resources.ResourceLocation;

/** What the model and renderer read, filled from the balloon each frame (1.21.1 has no entity render states). */
public class BalloonRenderState {
    public float ageInTicks;
    public float yRot;
    public int hurtDir;
    public float hurtTime;
    public float damageTime;
    public float heat;
    public boolean burning;
    public boolean hasChest;
    /** Envelope lean (radians, model x/z) and flame size, interpolated; inflate progress 0..1. */
    public float swayX, swayZ, flame, inflate = 1;
    /** Envelope dye colours (ARGB): the base gores and the stripe gores, from the balloon's synced look. */
    public int primary = 0xFFF9FFFE, secondary = 0xFFB02E26;
    /** Emblem layers bottom-up (banner base first), as envelope-sized textures and their tints; emblemCount used. */
    public final ResourceLocation[] emblemTextures = new ResourceLocation[1 + io.github.profetgit.hotairballoons.entity.EnvelopeLook.MAX_LAYERS];
    public final int[] emblemColors = new int[1 + io.github.profetgit.hotairballoons.entity.EnvelopeLook.MAX_LAYERS];
    public int emblemCount;
}
