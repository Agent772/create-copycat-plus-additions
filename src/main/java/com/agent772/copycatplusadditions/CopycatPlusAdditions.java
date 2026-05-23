package com.agent772.copycatplusadditions;

import com.agent772.copycatplusadditions.client.CopycatPlusAdditionsClient;
import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.agent772.copycatplusadditions.registry.ModItems;
import com.copycatsplus.copycats.CCCreativeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(CopycatPlusAdditions.MOD_ID)
public class CopycatPlusAdditions {

    public static final String MOD_ID = "copycatplusadditions";

    public CopycatPlusAdditions(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(CopycatPlusAdditions::onBuildCreativeTabContents);

        // Client-only: wire the custom slope-layer model swap. The client class is
        // referenced solely inside this branch so it is never loaded on a dedicated
        // server, where its Copycats+ rendering imports would not resolve.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CopycatPlusAdditionsClient.init(modEventBus);
        }
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // The block belongs alongside Copycats+' own copycats, so display it in their
        // "main" (decorative) creative tab rather than the vanilla building-blocks tab.
        if (event.getTabKey() == CCCreativeTabs.getBaseTabKey()) {
            event.accept(ModItems.VERTICAL_SLOPE_LAYER);
        }
    }
}
