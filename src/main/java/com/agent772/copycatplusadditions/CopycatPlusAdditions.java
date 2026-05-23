package com.agent772.copycatplusadditions;

import com.agent772.copycatplusadditions.client.CopycatPlusAdditionsClient;
import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.agent772.copycatplusadditions.registry.ModItems;
import com.copycatsplus.copycats.CCCreativeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(CopycatPlusAdditions.MOD_ID)
public class CopycatPlusAdditions {

    public static final String MOD_ID = "copycatplusadditions";

    private static final ResourceLocation UPSTREAM_SLOPE_LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("copycats", "copycat_slope_layer");

    public CopycatPlusAdditions(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // Single listener at LOWEST priority: Copycats+ populates the Copycats+ tab
        // at NORMAL, so by the time this fires the upstream copycat_slope_layer entry
        // already exists and is safe to remove. Doing the add+remove together makes
        // the relationship between the two operations obvious and avoids the hidden
        // coupling on Copycats+' own listener priority.
        modEventBus.addListener(EventPriority.LOWEST, CopycatPlusAdditions::onBuildCreativeTabContents);

        // Client-only: wire the custom slope-layer model swap. The client class is
        // referenced solely inside this branch so it is never loaded on a dedicated
        // server, where its Copycats+ rendering imports would not resolve.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CopycatPlusAdditionsClient.init(modEventBus);
        }
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // The adv. slope layer belongs alongside Copycats+' own copycats, so display
        // it in their "main" (decorative) creative tab. The upstream copycat_slope_layer
        // is fully superseded (same item model, same stonecutting recipe), so it's
        // pulled from the tab to stop players ending up with the "old" version. We
        // don't unregister the upstream block — existing worlds keep working — only
        // its creative-tab visibility is removed. The item is looked up by id rather
        // than via CCBlocks because the latter's BlockEntry type comes from Registrate,
        // which isn't on our compile classpath.
        if (!CCCreativeTabs.getBaseTabKey().equals(event.getTabKey())) {
            return;
        }
        event.accept(ModItems.ADV_SLOPE_LAYER);
        Item upstream = BuiltInRegistries.ITEM.get(UPSTREAM_SLOPE_LAYER_ID);
        if (upstream != Items.AIR) {
            event.remove(new ItemStack(upstream), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
