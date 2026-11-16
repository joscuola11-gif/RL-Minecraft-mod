package com.rlfennec.world;

import com.rlfennec.entity.BoostPadEntity;
import com.rlfennec.physics.Arena;
import com.rlfennec.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** The boost pads of the arena: 6 big and 28 small ones, in the places of the standard Rocket League map. */
public final class ArenaPads {
    private ArenaPads() {
    }

    /** Makes sure that every pad exists exactly once (loads the chunks of the pitch if they are not loaded). */
    public static void ensure(ServerLevel level) {
        // the pads live in the chunks of the pitch: load them all (flat chunks, cheap)
        int minX = (int) Math.floor((ArenaWorld.ORIGIN_X - Arena.HALF_WIDTH) / 16.0D);
        int maxX = (int) Math.floor((ArenaWorld.ORIGIN_X + Arena.HALF_WIDTH) / 16.0D);
        int minZ = (int) Math.floor((ArenaWorld.ORIGIN_Z - Arena.HALF_LENGTH) / 16.0D);
        int maxZ = (int) Math.floor((ArenaWorld.ORIGIN_Z + Arena.HALF_LENGTH) / 16.0D);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                level.getChunk(cx, cz);
            }
        }

        List<BoostPadEntity> existing = new ArrayList<>(level.getEntities(ModEntities.BOOST_PAD.get(), pad -> true));
        for (double[] pos : Arena.BIG_PADS) {
            place(level, existing, pos[0], pos[1], true);
        }
        for (double[] pos : Arena.SMALL_PADS) {
            place(level, existing, pos[0], pos[1], false);
        }
        // pads that are not in the layout (an older layout, duplicates) go away
        for (BoostPadEntity extra : existing) {
            extra.discard();
        }
    }

    private static void place(ServerLevel level, List<BoostPadEntity> existing, double x, double z, boolean big) {
        double wx = ArenaWorld.ORIGIN_X + x;
        double wz = ArenaWorld.ORIGIN_Z + z;
        for (int i = 0; i < existing.size(); i++) {
            BoostPadEntity pad = existing.get(i);
            if (Math.abs(pad.getX() - wx) < 0.5D && Math.abs(pad.getZ() - wz) < 0.5D) {
                pad.setBig(big);
                existing.remove(i);
                return;
            }
        }
        BoostPadEntity pad = ModEntities.BOOST_PAD.get().create(level);
        if (pad != null) {
            pad.moveTo(wx, ArenaWorld.ORIGIN_Y, wz, 0.0F, 0.0F);
            pad.setBig(big);
            level.addFreshEntity(pad);
        }
    }
}
