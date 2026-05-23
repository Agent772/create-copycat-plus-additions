package com.agent772.copycatplusadditions.client;

import com.agent772.copycatplusadditions.blocks.CopycatVerticalSlopeLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeModelCore;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Client-only model core for {@link CopycatVerticalSlopeLayerBlock}.
 *
 * <p>For {@code IN_WALL = false} it renders exactly like the parent slope layer.
 * For {@code IN_WALL = true} it applies the same extra 90-degree rotation that the
 * block's collision shape uses, so the rendered geometry and the hitbox stay aligned.
 *
 * <p>This class references Copycats+ client rendering classes and must therefore only
 * ever be loaded on the physical client (see {@code CopycatPlusAdditionsClient}).
 */
public class CopycatVerticalSlopeLayerModelCore extends CopycatSlopeLayerModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        if (!state.getValue(CopycatVerticalSlopeLayerBlock.IN_WALL)) {
            super.emitCopycatQuads(key, state, context, material);
            return;
        }

        int layer = state.getValue(CopycatSlopeLayerBlock.LAYERS);
        Direction facing = state.getValue(CopycatSlopeLayerBlock.FACING);
        Half half = state.getValue(CopycatSlopeLayerBlock.HALF);
        int wallAngle = CopycatVerticalSlopeLayerBlock.wallAngle(facing, half);
        boolean rotateAroundZ = CopycatVerticalSlopeLayerBlock.wallRotateAroundZ(facing);

        // Mirrors CopycatSlopeLayerModelCore's transform, with the extra wall rotation
        // appended after the standard FACING/HALF orientation. The rotation axis/angle
        // are sourced from CopycatVerticalSlopeLayerBlock so the visual geometry stays
        // pinned to the block's collision shape.
        AssemblyTransform transform = t -> {
            t.rotateY((int) facing.toYRot()).flipY(half == Half.TOP);
            if (rotateAroundZ) {
                t.rotateZ(wallAngle);
            } else {
                t.rotateX(wallAngle);
            }
        };

        if (layer <= 4) {
            CopycatSlopeModelCore.assembleSlope(context, transform, 0.0, layer * 4.0, this.enhanced);
        } else {
            CopycatSlopeModelCore.assembleSlope(context, transform, (layer - 4) * 4.0, 16.0, this.enhanced);
        }
    }
}
