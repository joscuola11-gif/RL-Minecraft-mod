package com.rlfennec.client;

import com.rlfennec.RLFennec;
import com.rlfennec.registry.ModEntities;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.rlfennec.registry.ClientConfig;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = RLFennec.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FENNEC.get(), FennecRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        FennecKeys.ALL.forEach(event::register);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BoostHud.ID, new BoostHud());
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // Mods > Rocket League Fennec > Config opens the gamepad bindings screen
        ModList.get().getModContainerById(RLFennec.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new PadBindingsScreen(parent)));
    }

    @SubscribeEvent
    public static void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            ClientConfig.invalidate();
        }
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> FennecModel.reset());
    }
}
