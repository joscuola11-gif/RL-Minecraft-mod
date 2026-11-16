package com.rlfennec.client;

import com.rlfennec.entity.FennecEntity;
import com.rlfennec.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The running engine, looped from the recording. It idles when the car stands still, rises with the speed (and
 * punches up when you step on it), falls back slowly when you let go, and fades out when the driver gets out.
 */
final class FennecEngineSound extends AbstractTickableSoundInstance {
    /** Ticks the start-up sound plays alone before the loop fades in. */
    static final int START_DELAY_TICKS = 24;
    private static final int FADE_IN_TICKS = 8;
    private static final int FADE_OUT_TICKS = 4;

    private static final float IDLE_PITCH = 0.80F;
    private static final float TOP_PITCH = 1.90F;
    private static final float IDLE_VOLUME = 0.50F;

    private final FennecEntity car;
    private final int startDelay;
    private int age;
    private int fadeOut = -1;
    private float lastSpeed;

    /** @param delayTicks how long to wait before the loop becomes audible (0 for a car that is already running) */
    FennecEngineSound(FennecEntity car, int delayTicks) {
        super(ModSounds.ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.car = car;
        this.startDelay = delayTicks;
        this.looping = true;
        this.delay = 0;
        // after the start-up the revs settle down from the running tone to idle
        this.pitch = delayTicks > 0 ? 1.0F : IDLE_PITCH;
        this.volume = delayTicks > 0 ? 0.0F : IDLE_VOLUME;
        this.updatePosition();
    }

    /** The loop starts at volume 0 while the start-up sound plays; Minecraft drops a silent sound unless it is allowed to start silent. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    private void updatePosition() {
        this.x = this.car.getX();
        this.y = this.car.getY();
        this.z = this.car.getZ();
    }

    @Override
    public void tick() {
        if (this.car.isRemoved()) {
            this.stop();
            return;
        }
        this.updatePosition();
        if (!this.car.hasDriver()) {
            if (this.fadeOut < 0) {
                this.fadeOut = FADE_OUT_TICKS;
            }
            this.volume *= (float) this.fadeOut / (this.fadeOut + 1);
            if (--this.fadeOut <= 0) {
                this.stop();
            }
            return;
        }

        this.age++;
        float speed = (float) this.car.getSpeed();                 // blocks per tick, up to ~1.7
        float accel = speed - this.lastSpeed;
        this.lastSpeed = speed;
        float frac = Mth.clamp(speed / 1.7F, 0.0F, 1.0F);

        // revs: quick to climb, slow to fall (the engine has inertia)
        float target = IDLE_PITCH + (TOP_PITCH - IDLE_PITCH) * (float) Math.pow(frac, 0.85D) + (this.car.isBoosting() ? 0.1F : 0.0F);
        target = Mth.clamp(target, 0.5F, 2.0F);
        this.pitch += (target - this.pitch) * (target > this.pitch ? 0.25F : 0.07F);

        // stepping on it: a punch of volume while the car is picking up speed
        float punch = Mth.clamp(accel * 5.0F, 0.0F, 0.25F);
        float targetVolume = Mth.clamp(IDLE_VOLUME + 0.5F * frac + punch, 0.0F, 1.0F);

        if (this.age <= this.startDelay) {
            this.volume = 0.0F;                                    // the start-up sound is playing
        } else {
            float fade = Mth.clamp((this.age - this.startDelay) / (float) FADE_IN_TICKS, 0.0F, 1.0F);
            this.volume += (targetVolume * fade - this.volume) * 0.3F;
        }
    }
}
