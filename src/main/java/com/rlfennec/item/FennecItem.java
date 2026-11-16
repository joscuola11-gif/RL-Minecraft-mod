package com.rlfennec.item;

import com.rlfennec.entity.FennecEntity;
import com.rlfennec.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Right-click a block to place a Fennec, like a boat. */
public class FennecItem extends Item {
    public FennecItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        BlockPos target = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());

        FennecEntity car = ModEntities.FENNEC.get().create(serverLevel);
        if (car == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        double y = context.getClickedFace() == Direction.UP && !state.getCollisionShape(level, clicked).isEmpty()
                ? context.getClickLocation().y
                : target.getY();
        Vec3 pos = new Vec3(target.getX() + 0.5D, y, target.getZ() + 0.5D);
        car.moveTo(pos.x, pos.y, pos.z, player != null ? player.getYRot() : 0.0F, 0.0F);
        if (!level.noCollision(car, car.getBoundingBox())) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            car.setCustomName(stack.getHoverName());
        }
        serverLevel.addFreshEntity(car);
        serverLevel.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }
}
