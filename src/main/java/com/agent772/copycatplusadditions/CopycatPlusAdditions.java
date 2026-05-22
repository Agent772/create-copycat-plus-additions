package com.agent772.copycatplusadditions;

import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.agent772.copycatplusadditions.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CopycatPlusAdditions.MOD_ID)
public class CopycatPlusAdditions {

    public static final String MOD_ID = "copycatplusadditions";

    public CopycatPlusAdditions(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
    }
}
