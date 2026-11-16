package com.rlfennec.client;

import com.rlfennec.entity.FennecEntity;
import com.rlfennec.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping boost roar, fades in quickly and stops as soon as the boost is released or empty. */
final class FennecBoostSound extends AbstractTickableSoundInstance {
    private final FennecEntity car;
    private int ticks;

    FennecBoostSound(FennecEntity car) {
        super(ModSounds.BOOST.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F;
        this.pitch = 1.0F;
        this.x = car.getX();
        this.y = car.getY();
        this.z = car.getZ();
    }

    @Override
    public void tick() {
        if (this.car.isRemoved() || !this.car.hasDriver() || !this.car.isBoosting()) {
            this.stop();
            return;
        }
        this.x = this.car.getX();
        this.y = this.car.getY();
        this.z = this.car.getZ();
        this.ticks++;
        this.volume = Math.min(1.0F, 0.15F + this.ticks * 0.2F);
        this.pitch = 0.95F + (float) Math.min(this.car.getSpeed() / 1.7D, 1.0D) * 0.15F;
    }
}
