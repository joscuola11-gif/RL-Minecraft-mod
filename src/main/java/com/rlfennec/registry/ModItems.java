package com.rlfennec.registry;

import com.rlfennec.RLFennec;
import com.rlfennec.item.FennecItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RLFennec.MOD_ID);

    public static final DeferredItem<Item> FENNEC = ITEMS.register("fennec",
            () -> new FennecItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
