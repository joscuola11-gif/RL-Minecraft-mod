package com.rlfennec.registry;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Gameplay tuning that is safe to change without touching the physics code. */
public final class FennecConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue BOOST_REGEN_PER_SECOND;
    private static final ModConfigSpec.DoubleValue BOOST_REGEN_DELAY_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("boost");
        BOOST_REGEN_PER_SECOND = builder
                .comment("Boost points regained per second while not boosting. 0 = no regeneration (like Rocket League without pads).")
                .defineInRange("regenPerSecond", 6.0D, 0.0D, 100.0D);
        BOOST_REGEN_DELAY_SECONDS = builder
                .comment("Seconds to wait after the last boost before the meter starts refilling.")
                .defineInRange("regenDelaySeconds", 2.0D, 0.0D, 60.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private FennecConfig() {
    }

    public static float regenPerTick() {
        return SPEC.isLoaded() ? (float) (BOOST_REGEN_PER_SECOND.get() / 20.0D) : 0.3F;
    }

    public static int regenDelayTicks() {
        return SPEC.isLoaded() ? (int) Math.round(BOOST_REGEN_DELAY_SECONDS.get() * 20.0D) : 40;
    }
}
