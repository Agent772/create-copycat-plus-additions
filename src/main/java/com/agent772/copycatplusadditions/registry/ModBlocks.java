package com.agent772.copycatplusadditions.registry;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import com.agent772.copycatplusadditions.blocks.CopycatVerticalSlopeLayerBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(CopycatPlusAdditions.MOD_ID);

    // Copycat slope layer that can also mount on vertical wall faces.
    public static final DeferredBlock<CopycatVerticalSlopeLayerBlock> VERTICAL_SLOPE_LAYER =
        BLOCKS.register("vertical_slope_layer", CopycatVerticalSlopeLayerBlock::new);
}
