package io.github.profetgit.hotairballoons.entity;

/** The keys a balloon reads from its pilot: steering, burner (jump) and vent (sprint). */
public record PilotKeys(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sprint) {
    public static final PilotKeys EMPTY = new PilotKeys(false, false, false, false, false, false);
}
