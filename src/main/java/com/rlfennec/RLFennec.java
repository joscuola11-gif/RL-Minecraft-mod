package com.rlfennec;

import com.rlfennec.net.ModNetwork;
import com.rlfennec.registry.ClientConfig;
import com.rlfennec.registry.FennecConfig;
import com.rlfennec.registry.ModEntities;
import com.rlfennec.registry.ModItems;
import com.rlfennec.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Rocket League Fennec: a drivable Fennec with a jump, a double jump / flip and a 100-point boost meter.
 */
@Mod(RLFennec.MOD_ID)
public class RLFennec {
    public static final String MOD_ID = "rlfennec";

    public RLFennec(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        modBus.addListener(ModNetwork::register);
        modBus.addListener(RLFennec::addToCreativeTab);

        container.registerConfig(ModConfig.Type.COMMON, FennecConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "rlfennec-client.toml");
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.FENNEC.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
