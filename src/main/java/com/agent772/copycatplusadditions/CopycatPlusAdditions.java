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

    public CopycatPlusAdditions(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(CopycatPlusAdditions::onBuildCreativeTabContents);
        // Run after Copycats+' own tab population (which is NORMAL priority) so the
        // upstream copycat_slope_layer entry exists by the time we try to remove it.
        modEventBus.addListener(EventPriority.LOWEST, CopycatPlusAdditions::removeUpstreamSlopeLayerFromTab);

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
            event.accept(ModItems.ADV_SLOPE_LAYER);
        }
    }

    private static final ResourceLocation UPSTREAM_SLOPE_LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("copycats", "copycat_slope_layer");

    private static void removeUpstreamSlopeLayerFromTab(BuildCreativeModeTabContentsEvent event) {
        // This mod's adv. slope layer fully supersedes the upstream copycat_slope_layer
        // (same item model, same recipes), so hide the upstream entry to stop players
        // ending up with the "old" version in the menu. We don't unregister the block —
        // existing worlds keep working — only its creative-tab visibility is removed.
        // The item is looked up by id instead of via CCBlocks because the latter's
        // BlockEntry type comes from Registrate, which isn't on our compile classpath.
        if (event.getTabKey() != CCCreativeTabs.getBaseTabKey()) {
            return;
        }
        Item upstream = BuiltInRegistries.ITEM.get(UPSTREAM_SLOPE_LAYER_ID);
        if (upstream == Items.AIR) {
            return;
        }
        event.remove(new ItemStack(upstream), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
