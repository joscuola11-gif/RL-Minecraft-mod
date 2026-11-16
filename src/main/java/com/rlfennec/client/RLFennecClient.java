package com.rlfennec.client;

import com.rlfennec.entity.FennecEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import com.rlfennec.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;

/** Client-only helpers called from the entity (only ever on the logical client). */
public final class RLFennecClient {
    private static final Map<FennecEntity, AbstractTickableSoundInstance> ENGINE = new IdentityHashMap<>();
    private static final Map<FennecEntity, AbstractTickableSoundInstance> BOOST = new IdentityHashMap<>();
    private static final Map<FennecEntity, EngineState> STATES = new IdentityHashMap<>();

    private RLFennecClient() {
    }

    private static final class EngineState {
        boolean seen;
        boolean hadDriver;
    }

    /**
     * Engine sounds of a car, called every client tick: the start-up when somebody gets in, the running loop while
     * there is a driver (idle, moving, speeding up, slowing down), the wind-down when the driver gets out, and the
     * boost roar.
     */
    public static void tickSounds(FennecEntity car) {
        EngineState state = STATES.computeIfAbsent(car, c -> new EngineState());
        boolean driver = car.hasDriver();
        SoundManager sounds = Minecraft.getInstance().getSoundManager();

        if (driver && !state.hadDriver) {
            if (state.seen) {
                // the engine is started now: start-up sound, the loop comes in once it is nearly over
                car.level().playLocalSound(car.getX(), car.getY(), car.getZ(), ModSounds.ENGINE_START.get(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
                ENGINE.put(car, play(sounds, new FennecEngineSound(car, FennecEngineSound.START_DELAY_TICKS)));
            } else {
                // first time we see this car, already driven by someone: it is simply running
                ENGINE.put(car, play(sounds, new FennecEngineSound(car, 0)));
            }
        } else if (!driver && state.hadDriver) {
            car.level().playLocalSound(car.getX(), car.getY(), car.getZ(), ModSounds.ENGINE_STOP.get(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        }
        state.seen = true;
        state.hadDriver = driver;

        if (!driver) {
            return;
        }
        AbstractTickableSoundInstance loop = ENGINE.get(car);
        if (loop == null || loop.isStopped()) {
            ENGINE.put(car, play(sounds, new FennecEngineSound(car, 0)));
        }
        if (car.isBoosting()) {
            AbstractTickableSoundInstance boost = BOOST.get(car);
            if (boost == null || boost.isStopped()) {
                BOOST.put(car, play(sounds, new FennecBoostSound(car)));
            }
        }
    }

    private static AbstractTickableSoundInstance play(SoundManager sounds, AbstractTickableSoundInstance sound) {
        sounds.play(sound);
        return sound;
    }

    public static void clear() {
        ENGINE.clear();
        BOOST.clear();
        STATES.clear();
    }
}
