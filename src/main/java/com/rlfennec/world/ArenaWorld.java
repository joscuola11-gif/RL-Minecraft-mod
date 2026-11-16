package com.rlfennec.world;

import com.rlfennec.RLFennec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Where the arena is in the world: its own dimension, always night, never rain. */
public final class ArenaWorld {
    /** The dimension "rlfennec:salty_shores" (see data/rlfennec/dimension). */
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, RLFennec.id("salty_shores"));

    /** The middle of the pitch, on the floor, in world coordinates (the flat world around it has its sand at y = 64). */
    public static final double ORIGIN_X = 0.0D;
    public static final double ORIGIN_Y = 64.0D;
    public static final double ORIGIN_Z = 0.0D;

    private ArenaWorld() {
    }

    public static boolean isArena(Level level) {
        return level != null && level.dimension().equals(KEY);
    }
}
