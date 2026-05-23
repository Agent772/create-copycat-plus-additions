package com.agent772.copycatplusadditions.registry;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import com.agent772.copycatplusadditions.blocks.CopycatVerticalSlopeLayerBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(CopycatPlusAdditions.MOD_ID);

    // The advanced slope layer: a copycat slope layer that can also mount on
    // vertical wall faces. Positioned as a drop-in replacement for the upstream
    // copycats:copycat_slope_layer (see CopycatPlusAdditions for the tab/recipe
    // suppression that backs that positioning).
    public static final DeferredBlock<CopycatVerticalSlopeLayerBlock> ADV_SLOPE_LAYER =
        BLOCKS.register("adv_slope_layer", CopycatVerticalSlopeLayerBlock::new);
}
