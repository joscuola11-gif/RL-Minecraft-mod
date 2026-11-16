package com.rlfennec.net;

import com.rlfennec.RLFennec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by the controller's Y button to get out of the car (a gamepad has no Shift key). */
public record DismountPayload() implements CustomPacketPayload {
    public static final Type<DismountPayload> TYPE = new Type<>(RLFennec.id("dismount"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DismountPayload> STREAM_CODEC = StreamCodec.unit(new DismountPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
