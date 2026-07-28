package com.agent772.copycatplusadditions.client;

import com.agent772.copycatplusadditions.blocks.CopycatCornerSlopeBlock;
import com.agent772.copycatplusadditions.blocks.CopycatCornerSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only model core for {@link CopycatCornerSlopeLayerBlock}.
 *
 * Same geometry as {@link CopycatCornerSlopeModelCore} with max height driven
 * by the LAYERS property (2 voxels per layer).
 */
@OnlyIn(Dist.CLIENT)
public class CopycatCornerSlopeLayerModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatCornerSlopeBlock.FACING);
        Half half = state.getValue(CopycatCornerSlopeBlock.HALF);
        int layers = state.getValue(CopycatCornerSlopeLayerBlock.LAYERS);
        CopycatCornerSlopeModelCore.assembleCornerSlope(context, facing, half, layers * 2.0);
    }
}
