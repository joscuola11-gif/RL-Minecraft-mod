package com.rlfennec.net;

import com.rlfennec.entity.FennecEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(CarControlPayload.TYPE, CarControlPayload.STREAM_CODEC, ModNetwork::handleCarControl);
    }

    private static void handleCarControl(CarControlPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof FennecEntity car && car.getControllingPassenger() == player) {
            car.applyClientControl(payload.boost(), payload.boosting(), payload.jumpCode());
        }
    }
}
