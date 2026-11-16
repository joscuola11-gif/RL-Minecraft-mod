package com.rlfennec.registry;

import com.rlfennec.RLFennec;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, RLFennec.MOD_ID);

    /** Looping engine hum, pitch follows the speed of the car. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE = register("fennec.engine");
    /** Looping boost roar while the boost button is held. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOST = register("fennec.boost");
    /** One-shot ignition thump when the boost starts. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOST_START = register("fennec.boost_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> JUMP = register("fennec.jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLIP = register("fennec.flip");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(RLFennec.id(name)));
    }
}
