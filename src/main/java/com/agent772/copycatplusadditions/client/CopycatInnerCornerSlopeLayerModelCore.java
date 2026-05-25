package com.agent772.copycatplusadditions.client;

import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeBlock;
import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only model core for {@link CopycatInnerCornerSlopeLayerBlock}.
 *
 * <p>Same geometry as {@link CopycatInnerCornerSlopeModelCore}, but the max slope
 * height is driven by the {@code LAYERS} property (2 voxels per layer) so the block
 * grows incrementally from a single-layer slice into the full solid inner corner.
 */
@OnlyIn(Dist.CLIENT)
public class CopycatInnerCornerSlopeLayerModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatInnerCornerSlopeBlock.FACING);
        Half half = state.getValue(CopycatInnerCornerSlopeBlock.HALF);
        int layers = state.getValue(CopycatInnerCornerSlopeLayerBlock.LAYERS);
        CopycatInnerCornerSlopeModelCore.assembleInnerCorner(context, facing, half, layers * 2.0);
    }
}
