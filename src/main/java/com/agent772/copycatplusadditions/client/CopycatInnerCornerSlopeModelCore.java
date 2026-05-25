package com.agent772.copycatplusadditions.client;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.aabb;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.cull;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.slope;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.updateUV;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.vec3;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope.map;

import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeBlock;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only model core for {@link CopycatInnerCornerSlopeBlock}.
 *
 * <p><b>Round 3 refactor.</b> The block is rendered as <i>two</i> planar slope pieces
 * instead of one non-planar slope. Each piece is a regular {@code QuadSlope} along a
 * single axis (Z for piece 1, X for piece 2), so its UP face is genuinely planar and
 * the renderer's fixed {@code v0–v2} triangulation has no ambiguity. Where the two
 * pieces overlap, the z-buffer resolves to the higher surface — i.e. the geometric
 * {@code max} of the two ramps — which is exactly the inner-corner shape with the
 * notch at the corner where both slopes collapse to zero.
 *
 * <p>This unblocks the round-2 trade-off (the per-facing formula that paired
 * SOUTH+EAST and NORTH+WEST into one orientation each): with planar pieces, the
 * notch position can be the same in local space for every facing and the rotation
 * handles all four world orientations cleanly, putting the notch at NE/SE/SW/NW for
 * SOUTH/WEST/NORTH/EAST. It also fixes HALF=TOP, which previously hit the same
 * triangulation issue on the post-flip face.
 *
 * <p>Cull masks suppress the wall overlaps between the two pieces:
 * <ul>
 *   <li>Piece 1 (slope along Z): cull NORTH (degenerate low edge) and WEST
 *       (replaced by Piece 2's full west wall)</li>
 *   <li>Piece 2 (slope along X): cull EAST (degenerate low edge) and SOUTH
 *       (replaced by Piece 1's full south wall)</li>
 * </ul>
 * The bottom faces of both pieces overlap at y=0, but the bottom of a block is
 * rarely visible from outside, so the z-fighting there is acceptable — culling
 * DOWN on one piece breaks HALF=TOP because the cull mask's flipY would target the
 * sloped face rather than the flat one.
 */
@OnlyIn(Dist.CLIENT)
public class CopycatInnerCornerSlopeModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatInnerCornerSlopeBlock.FACING);
        Half half = state.getValue(CopycatInnerCornerSlopeBlock.HALF);
        assembleInnerCorner(context, facing, half, 16.0);
    }

    static void assembleInnerCorner(CopycatRenderContext context, Direction facing, Half half, double maxHeight) {
        int yRot = (int) facing.toYRot();
        boolean topHalf = half == Half.TOP;
        final double h = maxHeight;
        AssemblyTransform transform = t -> t.rotateY(yRot).flipY(topHalf);

        // Piece 1: planar slope along Z. Low at z=0 (north), high at z=1 (south).
        // The slope function only depends on b (=z), so the UP face quad's heights
        // are [0, 0, h, h] — strictly planar, no triangulation ambiguity.
        context.assemblePiece(
            transform,
            vec3(0, 0, 0),
            aabb(16, 16, 16),
            cull(MutableCullFace.NORTH | MutableCullFace.WEST),
            updateUV(slope(Direction.UP, (a, b) -> map(0, 16, 0, h, b)))
        );

        // Piece 2: planar slope along X. Low at x=1 (east), high at x=0 (west).
        // Together with Piece 1 the z-buffer resolves to max(b, h - a*h/16), which
        // is the inner-corner profile with the notch landing at the NE local corner.
        context.assemblePiece(
            transform,
            vec3(0, 0, 0),
            aabb(16, 16, 16),
            cull(MutableCullFace.EAST | MutableCullFace.SOUTH),
            updateUV(slope(Direction.UP, (a, b) -> map(0, 16, h, 0, a)))
        );
    }
}
