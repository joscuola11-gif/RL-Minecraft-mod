package com.rlfennec.entity;

import com.rlfennec.client.RLFennecClient;
import com.rlfennec.net.CarControlPayload;
import com.rlfennec.physics.CarPhysics;
import com.rlfennec.registry.FennecConfig;
import com.rlfennec.registry.ModItems;
import com.rlfennec.registry.ModSounds;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The drivable Fennec.
 *
 * <p>Same networking model as boats: the client of the driver simulates the car and the vanilla
 * "move vehicle" packets keep the server in sync. {@link CarControlPayload} adds the boost meter and
 * jump events. The boost meter has 100 points.</p>
 */
public class FennecEntity extends VehicleEntity {
    private static final EntityDataAccessor<Float> DATA_BOOST = SynchedEntityData.defineId(FennecEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BOOSTING = SynchedEntityData.defineId(FennecEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_JUMP_CODE = SynchedEntityData.defineId(FennecEntity.class, EntityDataSerializers.INT);
    /** Roll in degrees (right side down = positive). Yaw and pitch travel with the vanilla entity rotation. */
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(FennecEntity.class, EntityDataSerializers.FLOAT);

    public static final int FLIP_DURATION_TICKS = 14;
    /** Radius of the wheels in blocks (rear / front are nearly equal). */
    public static final float WHEEL_RADIUS = 0.32F;

    private final CarPhysics physics = new CarPhysics();
    private final CarPhysics.Input emptyInput = new CarPhysics.Input();
    private final CarPhysics.Input clientInput = new CarPhysics.Input();

    // smoothing of remote cars (same idea as Boat)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // what was last sent to the server
    private float sentBoost = -1.0F;
    private boolean sentBoosting;
    private int sentJumpCode;
    private float sentRoll;
    private int jumpCounter;
    private int localJumpCode;
    private int ticksSinceSend;

    private int underwaterTicks;

    private float roll;
    private float rollO;

    // client-only visuals (plain fields, never touched on a dedicated server)
    private float wheelRoll;
    private float wheelRollO;
    private float steerAngle;
    private float steerAngleO;
    private int flipType = CarPhysics.FLIP_NONE;
    private int flipTick;
    private boolean wasBoosting;

    public FennecEntity(EntityType<? extends FennecEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        // The car does its own gravity. This flag also stops the server from kicking drivers
        // for "flying" while they are in the air with the boost.
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOOST, CarPhysics.MAX_BOOST);
        builder.define(DATA_BOOSTING, false);
        builder.define(DATA_JUMP_CODE, 0);
        builder.define(DATA_ROLL, 0.0F);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }
        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }
        super.tick();
        this.tickLerp();
        this.rollO = this.roll;

        if (this.isControlledByLocalInstance()) {
            this.controlledTick();
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            // keep the local copy of the meter current so that a new driver continues from it
            this.physics.boost = this.entityData.get(DATA_BOOST);
            // cars driven by someone else: follow the roll that the server tells us
            float target = this.entityData.get(DATA_ROLL);
            this.roll = this.level().isClientSide ? this.roll + Mth.wrapDegrees(target - this.roll) * 0.6F : target;
        }

        this.resetFallDistance();
        for (Entity passenger : this.getPassengers()) {
            passenger.resetFallDistance();
        }

        if (!this.level().isClientSide) {
            this.underwaterTicks = this.isUnderWater() ? this.underwaterTicks + 1 : 0;
            if (this.underwaterTicks > 40) {
                this.ejectPassengers();
            }
        } else {
            this.clientVisualTick();
        }
        this.checkInsideBlocks();
    }

    /** The side that owns the car this tick (the driving client, or the server for an empty car). */
    private void controlledTick() {
        boolean driven = this.getControllingPassenger() instanceof Player;
        CarPhysics.Input in = driven ? this.clientInput : this.emptyInput;

        Vec3 dm = this.getDeltaMovement();
        this.physics.vx = dm.x;
        this.physics.vy = dm.y;
        this.physics.vz = dm.z;
        this.physics.yaw = this.getYRot();
        this.physics.pitch = this.getXRot();
        this.physics.roll = this.roll;
        this.physics.onGround = this.onGround();
        this.physics.inWater = this.isInWater();
        this.physics.regenPerTick = FennecConfig.regenPerTick();
        this.physics.regenDelayTicks = FennecConfig.regenDelayTicks();

        this.physics.step(in);
        in.jump = false;

        this.setYRot(this.physics.yaw);
        this.setXRot(this.physics.pitch);
        this.roll = this.physics.roll;
        this.setDeltaMovement(this.physics.vx, this.physics.vy, this.physics.vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.physics.event != CarPhysics.EVENT_NONE) {
            this.jumpCounter++;
            this.localJumpCode = (this.jumpCounter << 6) | (this.physics.event << 3) | this.physics.flip;
            if (this.level().isClientSide) {
                this.playJumpEffects(this.physics.event, this.physics.flip);
            }
        }

        if (this.level().isClientSide) {
            if (driven) {
                this.sendControlToServer();
            }
        } else {
            this.entityData.set(DATA_BOOST, this.physics.boost);
            this.entityData.set(DATA_BOOSTING, false);
            this.entityData.set(DATA_ROLL, this.roll);
        }
    }

    private void sendControlToServer() {
        int code = this.localJumpCode;
        boolean changed = this.physics.boosting != this.sentBoosting
                || code != this.sentJumpCode
                || Math.abs(this.physics.boost - this.sentBoost) >= 1.0F
                || Math.abs(Mth.wrapDegrees(this.roll - this.sentRoll)) >= 1.0F;
        this.ticksSinceSend++;
        if (changed || this.ticksSinceSend >= 20) {
            PacketDistributor.sendToServer(new CarControlPayload(this.physics.boost, this.physics.boosting, code, this.roll));
            this.sentBoost = this.physics.boost;
            this.sentBoosting = this.physics.boosting;
            this.sentJumpCode = code;
            this.sentRoll = this.roll;
            this.ticksSinceSend = 0;
        }
    }

    /** Called on the server when the driver's client reports its meter and jumps. */
    public void applyClientControl(float boost, boolean boosting, int jumpCode, float roll) {
        if (!Float.isFinite(boost) || !Float.isFinite(roll)) {
            return;
        }
        this.entityData.set(DATA_ROLL, Mth.wrapDegrees(roll));
        this.entityData.set(DATA_BOOST, Mth.clamp(boost, 0.0F, CarPhysics.MAX_BOOST));
        this.entityData.set(DATA_BOOSTING, boosting);
        this.entityData.set(DATA_JUMP_CODE, jumpCode);
    }

    // ------------------------------------------------------------------ client input

    /** Called every client tick by the input handler of the local driver. */
    public void setClientInput(float throttle, float steer, float pitch, float roll, boolean airRollFree, boolean jumpPressed, boolean boost) {
        this.clientInput.throttle = throttle;
        this.clientInput.steer = steer;
        this.clientInput.pitch = pitch;
        this.clientInput.roll = roll;
        this.clientInput.airRollFree = airRollFree;
        this.clientInput.boost = boost;
        if (jumpPressed) {
            this.clientInput.jump = true;
        }
    }

    // ------------------------------------------------------------------ visuals / effects

    private void clientVisualTick() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double yawRad = Math.toRadians(this.getYRot());
        double forward = dx * -Math.sin(yawRad) + dz * Math.cos(yawRad);

        this.wheelRollO = this.wheelRoll;
        this.wheelRoll += (float) (forward / WHEEL_RADIUS);

        float dyaw = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        float target = Mth.clamp(dyaw * 7.0F, -32.0F, 32.0F);
        this.steerAngleO = this.steerAngle;
        this.steerAngle += (target - this.steerAngle) * 0.4F;

        if (this.flipTick > 0) {
            this.flipTick--;
            if (this.flipTick == 0) {
                this.flipType = CarPhysics.FLIP_NONE;
            }
        }

        boolean boosting = this.isBoosting();
        if (boosting) {
            this.spawnBoostFlames();
        }
        if (boosting && !this.wasBoosting) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), ModSounds.BOOST_START.get(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        }
        this.wasBoosting = boosting;

        RLFennecClient.tickSounds(this);
    }

    private void spawnBoostFlames() {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        CarPhysics.basis(this.getYRot(), this.getXRot(), this.getRoll(1.0F), f, u, r);
        Vec3 forward = new Vec3(f[0], f[1], f[2]);
        Vec3 right = new Vec3(r[0], r[1], r[2]);
        Vec3 up = new Vec3(u[0], u[1], u[2]);
        Vec3 velocity = this.getControllingPassenger() instanceof Player ? this.getDeltaMovement() : Vec3.ZERO;
        // the car pitches / rolls around its centre of mass, 0.45 blocks above the ground
        Vec3 centre = this.position().add(0.0D, 0.45D, 0.0D);

        // The two round boost nozzles in the rear fascia of the model are at x = -1.67, y = 0.67, z = +-0.316
        // (model space, measured on the mesh); the flames start a hand's width behind them.
        for (int side = -1; side <= 1; side += 2) {
            Vec3 origin = centre
                    .add(forward.scale(-1.75D))
                    .add(right.scale(0.316D * side))
                    .add(up.scale(0.67D - 0.45D));
            for (int i = 0; i < 3; i++) {
                Vec3 spread = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian()).scale(0.03D);
                Vec3 v = forward.scale(-0.35D).add(velocity).add(spread);
                this.level().addParticle(ParticleTypes.FLAME, origin.x, origin.y, origin.z, v.x, v.y, v.z);
            }
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, origin.x, origin.y, origin.z,
                        forward.x * -0.2D, forward.y * -0.2D + 0.02D, forward.z * -0.2D);
            }
        }
    }

    private void playJumpEffects(int event, int flip) {
        SoundEvent sound = flip != CarPhysics.FLIP_NONE ? ModSounds.FLIP.get() : ModSounds.JUMP.get();
        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound, SoundSource.NEUTRAL, 1.0F,
                event == CarPhysics.EVENT_DOUBLE_JUMP ? 1.15F : 1.0F, false);
        if (flip != CarPhysics.FLIP_NONE) {
            this.flipType = flip;
            this.flipTick = FLIP_DURATION_TICKS;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        // cars driven by someone else: replay the jump from the synced code
        if (accessor.equals(DATA_JUMP_CODE) && this.level().isClientSide && this.tickCount > 1 && !this.isControlledByLocalInstance()) {
            int code = this.entityData.get(DATA_JUMP_CODE);
            int event = (code >> 3) & 7;
            if (event != CarPhysics.EVENT_NONE) {
                this.playJumpEffects(event, code & 7);
            }
        }
    }

    // ------------------------------------------------------------------ queries for renderer / HUD / sounds

    public float getBoostAmount() {
        return this.isLocallyDriven() ? this.physics.boost : this.entityData.get(DATA_BOOST);
    }

    public boolean isBoosting() {
        return this.isLocallyDriven() ? this.physics.boosting : this.entityData.get(DATA_BOOSTING);
    }

    private boolean isLocallyDriven() {
        return this.level().isClientSide && this.getControllingPassenger() instanceof Player && this.isControlledByLocalInstance();
    }

    public boolean hasDriver() {
        return this.getControllingPassenger() instanceof Player;
    }

    /** Distance travelled over the last tick, in blocks. */
    public double getSpeed() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public float getWheelRoll(float partialTick) {
        return Mth.lerp(partialTick, this.wheelRollO, this.wheelRoll);
    }

    public float getSteerAngle(float partialTick) {
        return Mth.lerp(partialTick, this.steerAngleO, this.steerAngle);
    }

    /** Roll in degrees, right side down = positive. */
    public float getRoll(float partialTick) {
        return Mth.rotLerp(partialTick, this.rollO, this.roll);
    }

    public int getFlipType() {
        return this.flipType;
    }

    /** 0..1 progress of the flip animation, 0 when not flipping. */
    public float getFlipProgress(float partialTick) {
        if (this.flipTick <= 0) {
            return 0.0F;
        }
        return Mth.clamp(1.0F - (this.flipTick - partialTick) / FLIP_DURATION_TICKS, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ interpolation of other players' cars

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = 3;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float) this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot();
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        }
    }

    // ------------------------------------------------------------------ riding

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult result = super.interact(player, hand);
        if (result != InteractionResult.PASS) {
            return result;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, 0.25D, 0.0D);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = getCollisionHorizontalEscapeVector(this.getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), passenger.getYRot());
        double x = this.getX() + side.x;
        double z = this.getZ() + side.z;
        BlockPos pos = BlockPos.containing(x, this.getBoundingBox().maxY, z);
        BlockPos below = pos.below();
        if (!this.level().isWaterAt(below)) {
            List<Vec3> candidates = Lists.newArrayList();
            double floor = this.level().getBlockFloorHeight(pos);
            if (DismountHelper.isBlockFloorValid(floor)) {
                candidates.add(new Vec3(x, pos.getY() + floor, z));
            }
            double floorBelow = this.level().getBlockFloorHeight(below);
            if (DismountHelper.isBlockFloorValid(floorBelow)) {
                candidates.add(new Vec3(x, below.getY() + floorBelow, z));
            }
            for (Pose pose : passenger.getDismountPoses()) {
                for (Vec3 candidate : candidates) {
                    if (DismountHelper.canDismountTo(this.level(), candidate, passenger, pose)) {
                        passenger.setPose(pose);
                        return candidate;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ------------------------------------------------------------------ misc entity plumbing

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public float maxUpStep() {
        return 0.6F;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.FENNEC.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(this.getDropItem());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Boost")) {
            this.entityData.set(DATA_BOOST, Mth.clamp(tag.getFloat("Boost"), 0.0F, CarPhysics.MAX_BOOST));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Boost", this.entityData.get(DATA_BOOST));
    }
}
