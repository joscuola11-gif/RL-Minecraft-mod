package com.rlfennec.registry;

import com.rlfennec.RLFennec;
import com.rlfennec.entity.BoostPadEntity;
import com.rlfennec.entity.FennecEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RLFennec.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FennecEntity>> FENNEC = ENTITIES.register("fennec",
            () -> EntityType.Builder.<FennecEntity>of(FennecEntity::new, MobCategory.MISC)
                    .sized(1.9F, 1.2F)
                    .eyeHeight(1.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("fennec"));

    public static final DeferredHolder<EntityType<?>, EntityType<BoostPadEntity>> BOOST_PAD = ENTITIES.register("boost_pad",
            () -> EntityType.Builder.<BoostPadEntity>of(BoostPadEntity::new, MobCategory.MISC)
                    .sized(2.0F, 0.2F)
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .noSummon()
                    .fireImmune()
                    .build("boost_pad"));

    private ModEntities() {
    }
}
