package io.github.profetgit.hotairballoons.client;

import io.github.profetgit.hotairballoons.entity.HotAirBalloon;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/** Burner whoosh and roar plus basket creaks, played locally on every client from the balloon's synced state. */
public final class BalloonSounds {
    private static final Map<HotAirBalloon, State> STATES = new WeakHashMap<>();
    private static final SoundEvent[] CREAKS = {SoundEvents.SCAFFOLDING_STEP, SoundEvents.BAMBOO_WOOD_HIT, SoundEvents.LADDER_STEP};

    private BalloonSounds() {}

    private static final class State {
        boolean burning;
        int passengers;
        int creakIn = 60;
        @Nullable Roar roar;
    }

    public static void tick(HotAirBalloon b) {
        Minecraft mc = Minecraft.getInstance();
        State s = STATES.computeIfAbsent(b, k -> {
            State n = new State();
            n.burning = k.burnerLit();
            n.passengers = k.getPassengers().size();
            return n;
        });
        RandomSource rnd = b.getRandom();

        boolean burning = b.burnerLit();
        if (burning && !s.burning) {
            b.level().playLocalSound(b.getX(), b.getY() + 2.5, b.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.2F, 0.55F, false);
        }
        s.burning = burning;
        if (burning && (s.roar == null || s.roar.isStopped())) {
            s.roar = new Roar(b);
            mc.getSoundManager().play(s.roar);
        }

        int passengers = b.getPassengers().size();
        boolean changed = passengers != s.passengers;
        s.passengers = passengers;
        if (changed || (!b.onGround() && --s.creakIn <= 0)) {
            if (!changed || !b.onGround()) creak(b, rnd, changed ? 0.35F : 0.22F);
            s.creakIn = 60 + rnd.nextInt(140);
        }
    }

    private static void creak(HotAirBalloon b, RandomSource rnd, float volume) {
        SoundEvent e = CREAKS[rnd.nextInt(CREAKS.length)];
        b.level().playLocalSound(b.getX(), b.getY() + 0.5, b.getZ(), e, SoundSource.NEUTRAL, volume, 0.5F + rnd.nextFloat() * 0.2F, false);
    }

    /** Looping burner roar bound to the balloon, fading over about four ticks. */
    private static final class Roar extends AbstractTickableSoundInstance {
        private static final float MAX = 0.8F;
        private final HotAirBalloon balloon;

        Roar(HotAirBalloon balloon) {
            super(SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.balloon = balloon;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = 0.55F;
            place();
        }

        private void place() {
            this.x = balloon.getX();
            this.y = balloon.getY() + 2.6;
            this.z = balloon.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (balloon.isRemoved()) {
                stop();
                return;
            }
            place();
            float step = MAX / 4.0F;
            volume = Mth.clamp(volume + (balloon.burnerLit() ? step : -step), 0.0F, MAX);
            if (volume <= 0.0F && !balloon.burnerLit()) stop();
        }
    }
}
