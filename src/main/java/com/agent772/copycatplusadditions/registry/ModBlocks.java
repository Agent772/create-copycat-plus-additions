package com.agent772.copycatplusadditions.registry;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeBlock;
import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeLayerBlock;
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

    // Inner corner slope: a full block with one corner wedge cut diagonally — the
    // complement of the outer corner slope (issue #11). Together they tile a full
    // block. See CopycatInnerCornerSlopeBlock / CopycatInnerCornerSlopeModelCore.
    public static final DeferredBlock<CopycatInnerCornerSlopeBlock> INNER_CORNER_SLOPE =
        BLOCKS.register("inner_corner_slope", CopycatInnerCornerSlopeBlock::new);

    // Stackable 1-8 layer variant of the inner corner slope.
    public static final DeferredBlock<CopycatInnerCornerSlopeLayerBlock> INNER_CORNER_SLOPE_LAYER =
        BLOCKS.register("inner_corner_slope_layer", CopycatInnerCornerSlopeLayerBlock::new);
}
