package com.rlfennec.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rlfennec.entity.FennecEntity;
import com.rlfennec.physics.Arena;
import com.rlfennec.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /rlfennec arena}: go to the arena (the dimension "rlfennec:salty_shores") in a Fennec at the kickoff spot.
 * {@code /rlfennec arena leave}: back to the overworld.
 */
public final class ArenaCommand {
    private ArenaCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rlfennec")
                .then(Commands.literal("arena")
                        .executes(ctx -> enter(ctx.getSource()))
                        .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))));
    }

    private static int enter(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel arena = source.getServer().getLevel(ArenaWorld.KEY);
        if (arena == null) {
            source.sendFailure(Component.translatable("commands.rlfennec.arena.missing"));
            return 0;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            player.stopRiding();
            if (vehicle instanceof FennecEntity && vehicle.level() == arena) {
                vehicle.discard(); // the car the player had in the arena before is replaced by a new one at the kickoff spot
            }
        }

        double[] kickoff = Arena.KICKOFFS[0];
        double x = ArenaWorld.ORIGIN_X + kickoff[0];
        double y = ArenaWorld.ORIGIN_Y;
        double z = ArenaWorld.ORIGIN_Z + kickoff[1];
        float yaw = (float) kickoff[2];
        // load the chunks around the kickoff spot now, so that the car and the pads exist when the player arrives
        BlockPos pos = BlockPos.containing(x, y, z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                arena.getChunk((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
            }
        }
        ArenaPads.ensure(arena);

        player.teleportTo(arena, x, y + 3.0D, z, yaw, 0.0F);
        FennecEntity car = ModEntities.FENNEC.get().create(arena);
        if (car == null) {
            return 0;
        }
        car.moveTo(x, y, z, yaw, 0.0F);
        arena.addFreshEntity(car);
        player.startRiding(car, true);
        source.sendSuccess(() -> Component.translatable("commands.rlfennec.arena.entered"), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.stopRiding();
        ServerLevel overworld = source.getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), 0.0F);
        return 1;
    }
}
