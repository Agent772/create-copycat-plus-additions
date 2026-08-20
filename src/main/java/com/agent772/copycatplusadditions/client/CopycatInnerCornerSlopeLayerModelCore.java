package com.agent772.copycatplusadditions.client;

import com.agent772.copycatplusadditions.CornerLayerProfile;
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
 * <p>Same geometry as {@link CopycatInnerCornerSlopeModelCore}, stacked with the
 * same two-phase profile as the straight slope layer: layers 1-4 raise the three
 * corners 4 voxels per layer with the notch pinned at the floor (bottom-anchored),
 * then layers 5-8 pin the corners at the top and raise the notch (top-anchored),
 * filling out to a full block at layer 8. Keeps the valley counterpart in lockstep
 * with the outer corner so hip+valley roof kits stay coherent.
 */
@OnlyIn(Dist.CLIENT)
public class CopycatInnerCornerSlopeLayerModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatInnerCornerSlopeBlock.FACING);
        Half half = state.getValue(CopycatInnerCornerSlopeBlock.HALF);
        int layers = state.getValue(CopycatInnerCornerSlopeLayerBlock.LAYERS);
        boolean roofRotated = state.getValue(CopycatInnerCornerSlopeBlock.ROOF_ROTATED);
        double apexTop = CornerLayerProfile.apexTop(layers, 16.0);
        double floor = CornerLayerProfile.floor(layers, 16.0);
        CopycatInnerCornerSlopeModelCore.assembleInnerCorner(context, facing, half, apexTop, floor, roofRotated);
    }
}
