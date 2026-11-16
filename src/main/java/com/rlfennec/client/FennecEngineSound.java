package com.rlfennec.client;

import com.rlfennec.entity.FennecEntity;
import com.rlfennec.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Looping engine; pitch and volume follow the speed of the car. */
final class FennecEngineSound extends AbstractTickableSoundInstance {
    private final FennecEntity car;

    FennecEngineSound(FennecEntity car) {
        super(ModSounds.ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35F;
        this.pitch = 0.6F;
        this.updatePosition();
    }

    private void updatePosition() {
        this.x = this.car.getX();
        this.y = this.car.getY();
        this.z = this.car.getZ();
    }

    @Override
    public void tick() {
        if (this.car.isRemoved() || !this.car.hasDriver()) {
            this.stop();
            return;
        }
        this.updatePosition();
        float speed = (float) this.car.getSpeed();               // blocks per tick, up to ~1.7
        float frac = Mth.clamp(speed / 1.7F, 0.0F, 1.0F);
        float target = 0.6F + 1.0F * (float) Math.pow(frac, 0.8D) + (this.car.isBoosting() ? 0.12F : 0.0F);
        this.pitch += (Mth.clamp(target, 0.5F, 2.0F) - this.pitch) * 0.25F;
        this.volume = 0.35F + 0.65F * Mth.clamp(speed / 1.1F, 0.0F, 1.0F);
    }
}
