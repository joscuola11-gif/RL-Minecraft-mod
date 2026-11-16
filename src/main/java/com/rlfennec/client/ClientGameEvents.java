package com.rlfennec.client;

import com.rlfennec.RLFennec;
import com.rlfennec.entity.FennecEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Input, chase camera and small client-side polish while driving. */
@EventBusSubscriber(modid = RLFennec.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    /** Camera pitch at which the car is level: looking 12 degrees down onto it. */
    private static final float NEUTRAL_VIEW_PITCH = 12.0F;

    private static boolean wasDriving;
    private static CameraType previousCamera = CameraType.FIRST_PERSON;
    private static float cameraDistance = 6.5F;
    private static float fovBoost;

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

        if (car == null) {
            // drain presses made while walking so that they do not jump the car the moment it is entered
            while (options.keyJump.consumeClick()) {
                // discard
            }
            if (wasDriving) {
                wasDriving = false;
                options.setCameraType(previousCamera);
            }
            fovBoost *= 0.8F;
            return;
        }

        if (!wasDriving) {
            wasDriving = true;
            previousCamera = options.getCameraType();
            cameraDistance = 5.5F;
            mc.player.setXRot(NEUTRAL_VIEW_PITCH);
        }
        // the car has no first-person view: you would sit inside its roof
        if (options.getCameraType().isFirstPerson()) {
            options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        float throttle = (options.keyUp.isDown() ? 1.0F : 0.0F) - (options.keyDown.isDown() ? 1.0F : 0.0F);
        float steer = (options.keyRight.isDown() ? 1.0F : 0.0F) - (options.keyLeft.isDown() ? 1.0F : 0.0F);
        boolean jump = false;
        while (options.keyJump.consumeClick()) {
            jump = true;
        }
        // in the air the nose points where the camera looks (mouse up / down)
        float aim = mc.player.getXRot() - NEUTRAL_VIEW_PITCH;
        car.setClientInput(throttle, steer, aim, jump, FennecKeys.BOOST.isDown());

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
            // the camera follows the car instead of the mouse
            event.setYaw(Mth.rotLerp((float) event.getPartialTick(), car.yRotO, car.getYRot()));
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
