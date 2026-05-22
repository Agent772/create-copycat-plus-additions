package com.agent772.copycatplusadditions.registry;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(CopycatPlusAdditions.MOD_ID);

    // Registered alongside each block once blocks are implemented
    // public static final DeferredItem<BlockItem> VERTICAL_SLOPE_LAYER =
    //     ITEMS.registerSimpleBlockItem(ModBlocks.VERTICAL_SLOPE_LAYER);
}
