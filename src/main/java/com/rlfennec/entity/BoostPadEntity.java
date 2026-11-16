package com.rlfennec.entity;

import com.rlfennec.physics.Arena;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A boost pad of the arena: big (100 boost, back after 10 s) or small (12 boost, back after 4 s). The pad itself is
 * the same on every machine; the driving client adds the boost when its car touches an active pad and tells the server,
 * which starts the respawn timer.
 */
public class BoostPadEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_BIG = SynchedEntityData.defineId(BoostPadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(BoostPadEntity.class, EntityDataSerializers.BOOLEAN);

    private int cooldown;

    public BoostPadEntity(EntityType<? extends BoostPadEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BIG, false);
        builder.define(DATA_ACTIVE, true);
    }

    public void setBig(boolean big) {
        this.entityData.set(DATA_BIG, big);
    }

    public boolean isBig() {
        return this.entityData.get(DATA_BIG);
    }

    public boolean isActive() {
        return this.entityData.get(DATA_ACTIVE);
    }

    /** Pickup radius around the middle of the pad, in blocks. */
    public double pickupRadius() {
        return this.isBig() ? Arena.BIG_PAD_RADIUS : Arena.SMALL_PAD_RADIUS;
    }

    /** Boost points a car gets from this pad. */
    public float boostAmount() {
        return this.isBig() ? Arena.BIG_PAD_BOOST : Arena.SMALL_PAD_BOOST;
    }

    /** The client that picked the boost up hides the pad at once, the server value arrives a moment later. */
    public void setActiveLocally(boolean active) {
        this.entityData.set(DATA_ACTIVE, active);
    }

    /** Server: somebody took the boost, the pad is empty until its timer runs out. */
    public void collect() {
        if (!this.level().isClientSide && this.isActive()) {
            this.entityData.set(DATA_ACTIVE, false);
            this.cooldown = this.isBig() ? Arena.BIG_PAD_RESPAWN_TICKS : Arena.SMALL_PAD_RESPAWN_TICKS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !this.isActive()) {
            if (--this.cooldown <= 0) {
                this.entityData.set(DATA_ACTIVE, true);
            }
        }
    }

    // the pad is part of the floor: nothing can hit, push or pick it
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    /** The beam and the orb are tall: the pad must not be culled when only they are in view. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(this.getX() - 6.0D, this.getY() - 0.5D, this.getZ() - 6.0D, this.getX() + 6.0D, this.getY() + 7.0D, this.getZ() + 6.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_BIG, tag.getBoolean("Big"));
        this.entityData.set(DATA_ACTIVE, !tag.contains("Active") || tag.getBoolean("Active"));
        this.cooldown = tag.getInt("Cooldown");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Big", this.isBig());
        tag.putBoolean("Active", this.isActive());
        tag.putInt("Cooldown", this.cooldown);
    }

    public BlockPos padBlockPos() {
        return this.blockPosition();
    }
}
