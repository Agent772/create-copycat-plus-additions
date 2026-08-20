package com.agent772.copycatplusadditions.client;

import com.agent772.copycatplusadditions.CornerLayerProfile;
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
 * Same geometry as {@link CopycatCornerSlopeModelCore}, stacked with the same
 * two-phase profile as the straight slope layer
 * ({@link CopycatVerticalSlopeLayerModelCore}):
 * <ul>
 *   <li>layers 1-4: apex rises 4 voxels per layer, eave pinned at the floor
 *       (bottom-anchored) — 45&deg; is reached at layer 4;</li>
 *   <li>layers 5-8: apex pinned at the top, eave (floor) rises 4 voxels per
 *       layer (top-anchored), filling out to a full block at layer 8.</li>
 * </ul>
 * This gives a hip corner the same top-anchored shallow pitches a partial-pitch
 * straight-slope roof has, so {@code corner-N} matches {@code straight-N}
 * layer-for-layer.
 */
@OnlyIn(Dist.CLIENT)
public class CopycatCornerSlopeLayerModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatCornerSlopeBlock.FACING);
        Half half = state.getValue(CopycatCornerSlopeBlock.HALF);
        int layers = state.getValue(CopycatCornerSlopeLayerBlock.LAYERS);
        boolean roofRotated = state.getValue(CopycatCornerSlopeBlock.ROOF_ROTATED);
        boolean inWall = state.getValue(CopycatCornerSlopeBlock.IN_WALL);
        boolean flipped = state.getValue(CopycatCornerSlopeBlock.WALL_FLIPPED);
        double apexTop = CornerLayerProfile.apexTop(layers, 16.0);
        double floor = CornerLayerProfile.floor(layers, 16.0);
        CopycatCornerSlopeModelCore.assembleCornerSlope(context, facing, half, apexTop, floor, roofRotated,
            inWall, flipped);
    }
}
