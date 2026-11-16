package com.rlfennec.client;

import com.rlfennec.entity.FennecEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;

/** Client-only helpers called from the entity (only ever on the logical client). */
public final class RLFennecClient {
    private static final Map<FennecEntity, AbstractTickableSoundInstance> ENGINE = new IdentityHashMap<>();
    private static final Map<FennecEntity, AbstractTickableSoundInstance> BOOST = new IdentityHashMap<>();

    private RLFennecClient() {
    }

    /** Starts the looping engine / boost sounds of a car that has a driver. */
    public static void tickSounds(FennecEntity car) {
        if (!car.hasDriver()) {
            return;
        }
        ensurePlaying(ENGINE, car, () -> new FennecEngineSound(car));
        if (car.isBoosting()) {
            ensurePlaying(BOOST, car, () -> new FennecBoostSound(car));
        }
    }

    private static void ensurePlaying(Map<FennecEntity, AbstractTickableSoundInstance> map, FennecEntity car,
                                      Supplier<AbstractTickableSoundInstance> factory) {
        AbstractTickableSoundInstance current = map.get(car);
        if (current == null || current.isStopped()) {
            current = factory.get();
            map.put(car, current);
            Minecraft.getInstance().getSoundManager().play(current);
        }
    }

    public static void clear() {
        ENGINE.clear();
        BOOST.clear();
    }
}
