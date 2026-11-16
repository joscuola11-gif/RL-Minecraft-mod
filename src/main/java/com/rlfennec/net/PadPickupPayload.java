package com.rlfennec.net;

import com.rlfennec.RLFennec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The driver's client took the boost of a pad: the server starts the pad's respawn timer. */
public record PadPickupPayload(int padId) implements CustomPacketPayload {
    public static final Type<PadPickupPayload> TYPE = new Type<>(RLFennec.id("pad_pickup"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PadPickupPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PadPickupPayload::padId, PadPickupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
