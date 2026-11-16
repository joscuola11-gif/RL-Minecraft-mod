package com.rlfennec.net;

import com.rlfennec.RLFennec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent every few ticks by the driving client. Like boats and horses, the car is simulated by the
 * client that drives it (so the steering feels instant); this packet tells the server about the
 * parts that other players need to see: the boost meter, the boost flames and jumps / flips.
 *
 * @param boost    boost left, 0..100
 * @param boosting whether the boost is firing right now
 * @param jumpCode {@code (counter << 6) | (event << 3) | flip}, changes on every jump
 */
public record CarControlPayload(float boost, boolean boosting, int jumpCode) implements CustomPacketPayload {
    public static final Type<CarControlPayload> TYPE = new Type<>(RLFennec.id("car_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CarControlPayload::boost,
            ByteBufCodecs.BOOL, CarControlPayload::boosting,
            ByteBufCodecs.VAR_INT, CarControlPayload::jumpCode,
            CarControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
