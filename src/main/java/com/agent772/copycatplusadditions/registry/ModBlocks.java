package com.agent772.copycatplusadditions.registry;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(CopycatPlusAdditions.MOD_ID);

    // Vertical slope layer - registered here once the block class is implemented
    // public static final DeferredBlock<CopycatVerticalSlopeLayerBlock> VERTICAL_SLOPE_LAYER =
    //     BLOCKS.register("vertical_slope_layer", CopycatVerticalSlopeLayerBlock::new);
}
