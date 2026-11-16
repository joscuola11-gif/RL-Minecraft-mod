package com.rlfennec.client;

import com.rlfennec.RLFennec;
import com.rlfennec.entity.FennecEntity;
import com.rlfennec.input.GamepadMapper;
import com.rlfennec.net.DismountPayload;
import com.rlfennec.registry.FennecConfig;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keyboard + gamepad input, chase camera and small client-side polish while driving. */
@EventBusSubscriber(modid = RLFennec.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    /** Camera pitch at which the car is level: looking 12 degrees down onto it. */
    private static final float NEUTRAL_VIEW_PITCH = 12.0F;
    private static final double ENTER_RANGE = 4.0D;

    private static boolean wasDriving;
    private static CameraType previousCamera = CameraType.FIRST_PERSON;
    private static float cameraDistance = 5.5F;
    private static float fovBoost;
    private static float cameraYaw;
    private static float cameraYawO;
    private static boolean padJumpHeld;
    private static boolean padEnterExitHeld;

    private ClientGameEvents() {
    }

    private static FennecEntity drivenCar(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && player.getVehicle() instanceof FennecEntity car && car.getControllingPassenger() == player) {
            return car;
        }
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;
        FennecEntity car = drivenCar(mc);

        GamepadMapper.Controls pad = null;
        if (mc.player != null && mc.screen == null && mc.isWindowActive()) {
            pad = GamepadPoller.poll();
        }
        boolean padEnterExit = pad != null && pad.enterExit;
        boolean enterExitPressed = padEnterExit && !padEnterExitHeld;
        padEnterExitHeld = padEnterExit;

        if (car == null) {
            // drain presses made while walking so that they do not jump the car the moment it is entered
            while (options.keyJump.consumeClick()) {
                // discard
            }
            padJumpHeld = pad != null && pad.jump;
            if (wasDriving) {
                wasDriving = false;
                options.setCameraType(previousCamera);
            }
            fovBoost *= 0.8F;
            if (enterExitPressed) {
                enterNearestCar(mc);
            }
            return;
        }

        if (!wasDriving) {
            wasDriving = true;
            previousCamera = options.getCameraType();
            cameraDistance = 5.5F;
            cameraYaw = car.getYRot();
            cameraYawO = cameraYaw;
            mc.player.setXRot(NEUTRAL_VIEW_PITCH);
        }
        // the car has no first-person view: you would sit inside its roof
        if (options.getCameraType().isFirstPerson()) {
            options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (enterExitPressed) {
            PacketDistributor.sendToServer(new DismountPayload());
        }

        // Q / E / Left Ctrl overlap with vanilla "drop item", "inventory" and "swap hands": swallow those presses
        swallowVanillaPresses(options);

        float throttle = axis(options.keyUp, options.keyDown);
        float steer = axis(options.keyRight, options.keyLeft);
        float pitch = throttle * (FennecConfig.invertAirPitch() ? -1.0F : 1.0F);
        float roll = axis(FennecKeys.ROLL_RIGHT, FennecKeys.ROLL_LEFT);
        boolean airRollFree = FennecKeys.AIR_ROLL.isDown();
        boolean boost = FennecKeys.BOOST.isDown();
        boolean jump = false;
        while (options.keyJump.consumeClick()) {
            jump = true;
        }

        if (pad != null) {
            throttle = Mth.clamp(throttle + pad.throttle, -1.0F, 1.0F);
            steer = Mth.clamp(steer + pad.steer, -1.0F, 1.0F);
            pitch = Mth.clamp(pitch + pad.pitch * (FennecConfig.invertAirPitch() ? -1.0F : 1.0F), -1.0F, 1.0F);
            roll = Mth.clamp(roll + pad.roll, -1.0F, 1.0F);
            airRollFree |= pad.airRollFree;
            boost |= pad.boost;
            jump |= pad.jump && !padJumpHeld;
        }
        padJumpHeld = pad != null && pad.jump;

        car.setClientInput(throttle, steer, pitch, roll, airRollFree, jump, boost);

        updateCamera(car);
    }

    private static float axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1.0F : 0.0F) - (negative.isDown() ? 1.0F : 0.0F);
    }

    /** A vanilla action must not fire when its key is also one of ours (default Q, E). */
    private static void swallowVanillaPresses(Options options) {
        for (KeyMapping vanilla : new KeyMapping[] {options.keyDrop, options.keyInventory, options.keySwapOffhand}) {
            for (KeyMapping ours : new KeyMapping[] {FennecKeys.BOOST, FennecKeys.ROLL_LEFT, FennecKeys.ROLL_RIGHT, FennecKeys.AIR_ROLL}) {
                if (vanilla.getKey().equals(ours.getKey())) {
                    while (vanilla.consumeClick()) {
                        // discard
                    }
                    break;
                }
            }
        }
    }

    private static void enterNearestCar(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        List<FennecEntity> cars = mc.level.getEntitiesOfClass(FennecEntity.class, mc.player.getBoundingBox().inflate(ENTER_RANGE),
                car -> !car.hasDriver());
        FennecEntity nearest = null;
        for (FennecEntity candidate : cars) {
            if (nearest == null || candidate.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player)) {
                nearest = candidate;
            }
        }
        if (nearest != null) {
            mc.gameMode.interact(mc.player, nearest, InteractionHand.MAIN_HAND);
        }
    }

    /**
     * The camera sits behind the car. On the ground it follows the nose; in the air it follows where the car is
     * travelling, so rolling and looping do not whip the camera around.
     */
    private static void updateCamera(FennecEntity car) {
        float carYaw = car.getYRot();
        float target = carYaw;
        if (!car.onGround()) {
            Vec3 v = car.getDeltaMovement();
            double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
            if (horizontal > 0.15D) {
                float travelYaw = (float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG);
                float weight = (float) Mth.clamp((horizontal - 0.15D) / 0.35D, 0.0D, 1.0D);
                target = carYaw + Mth.wrapDegrees(travelYaw - carYaw) * weight;
            }
        }
        cameraYawO = cameraYaw;
        cameraYaw += Mth.wrapDegrees(target - cameraYaw) * 0.35F;

        float speedFrac = Mth.clamp((float) car.getSpeed() / 1.7F, 0.0F, 1.0F);
        float fovTarget = (car.isBoosting() ? 12.0F : 0.0F) + speedFrac * speedFrac * 6.0F;
        fovBoost += (fovTarget - fovBoost) * 0.12F;
        float distTarget = car.isBoosting() ? 6.5F : 5.5F;
        cameraDistance += (distTarget - cameraDistance) * 0.1F;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        // nobody sits visibly in the car: it would clip through the roof (and Rocket League cars have no driver either)
        if (event.getEntity().getVehicle() instanceof FennecEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        FennecEntity car = drivenCar(Minecraft.getInstance());
        if (car != null) {
            // the camera follows the car instead of the mouse; looking up / down with the mouse still works
            event.setYaw(Mth.rotLerp((float) event.getPartialTick(), cameraYawO, cameraYaw));
            event.setPitch(Mth.clamp(event.getPitch(), -45.0F, 85.0F));
        }
    }

    @SubscribeEvent
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (drivenCar(Minecraft.getInstance()) != null) {
            event.setDistance(cameraDistance);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (fovBoost > 0.01F && Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof FennecEntity) {
            event.setFOV(event.getFOV() + fovBoost);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RLFennecClient.clear();
        wasDriving = false;
        fovBoost = 0.0F;
    }
}
