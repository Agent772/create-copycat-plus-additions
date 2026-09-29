package com.agent772.copycatplusadditions.client;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope.map;

import java.util.List;

import com.agent772.copycatplusadditions.CornerWallRotation;
import com.agent772.copycatplusadditions.CornerWallRotation.Step;
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
        boolean roofRotated = state.getValue(CopycatInnerCornerSlopeBlock.ROOF_ROTATED);
        boolean inWall = state.getValue(CopycatInnerCornerSlopeBlock.IN_WALL);
        boolean flipped = state.getValue(CopycatInnerCornerSlopeBlock.WALL_FLIPPED);
        assembleInnerCorner(context, facing, half, 16.0, 0.0, roofRotated, inWall, flipped, enhanced);
    }

    /**
     * Emits the inner corner (valley) geometry with the same two-phase profile as
     * the outer corner and the straight slope layer: the three raised corners run
     * from {@code floor} at the notch to {@code apexTop}. Passing {@code floor = 0}
     * gives a bottom-anchored wedge (phase 1); raising {@code floor} while keeping
     * {@code apexTop = 16} raises the notch, filling out to a full block at
     * {@code floor = 16}. The solid body below the slope is supplied by the
     * full-height {@code aabb} prism, so no separate base slab is needed.
     */
    static void assembleInnerCorner(CopycatRenderContext context, Direction facing, Half half, double apexTop,
                                    double floor, boolean roofRotated, boolean inWall, boolean flipped,
                                    boolean enhanced) {
        int yRot = (int) facing.toYRot();
        boolean topHalf = half == Half.TOP;
        // Wall mount: tip the oriented floor geometry onto the wall with the shared
        // CornerWallRotation steps (identical to the collision shape in
        // CCAdditionsShapes), appended after the FACING/HALF orientation.
        List<Step> wallSteps = inWall ? CornerWallRotation.steps(facing, half, flipped) : List.of();
        AssemblyTransform transform = t -> {
            t.rotateY(yRot).flipY(topHalf);
            for (Step s : wallSteps) {
                if (s.axis() == CornerWallRotation.Axis.X) {
                    t.rotateX(s.angle());
                } else {
                    t.rotateZ(s.angle());
                }
            }
        };

        // Roof UV: per-wing top-down projection rotated so the sprite's bottom edge
        // lies on each wing's own LOCAL eave — grain runs parallel to the eave on
        // both wings, so the two visible slope faces read identically (like a
        // straight slope) instead of one showing grain across the slope. See the
        // ProjectRoofUV Javadoc. When ROOF_ROTATED is set, each wing's eave is
        // advanced one quarter turn (getClockWise), turning both projections 90
        // degrees so directional grain runs up the slope instead. The projection is
        // 180-degree symmetric, so a single boolean covers both distinct looks.
        // Piece 1 slopes down to the LOCAL north edge; piece 2 to the LOCAL east edge.
        Direction eave1 = roofRotated ? Direction.NORTH.getClockWise() : Direction.NORTH;
        Direction eave2 = roofRotated ? Direction.EAST.getClockWise() : Direction.EAST;
        // With Copycats+ enhanced models on, texture the roof at 1:1 surface density
        // like upstream slopes instead of stretching one tile over the incline.
        double scale = enhanced ? RoofUVMath.enhancedSlopeScale(apexTop - floor) : 1.0;
        // Bottom-anchored layers leave the ridge walls (SOUTH on piece 1, WEST on
        // piece 2) short of a full block; enhanced models build those short walls
        // from the side texture's bottom and top halves, like upstream slope layers.
        boolean anchoredRidges = RoofWing.anchorsWall(enhanced, apexTop);
        int ridgeWall1 = anchoredRidges ? MutableCullFace.SOUTH : 0;
        int ridgeWall2 = anchoredRidges ? MutableCullFace.WEST : 0;

        // Piece 1: planar slope along Z. Low at z=0 (north), high at z=1 (south).
        // The slope function only depends on b (=z), so the UP face quad's heights
        // are [0, 0, h, h] — strictly planar, no triangulation ambiguity. RoofWing
        // splits it at z=0.5 so each half is textured from its own texture edge.
        RoofWing.assemble(
            context,
            transform,
            MutableCullFace.NORTH | MutableCullFace.WEST | ridgeWall1,
            (a, b) -> map(0, 16, floor, apexTop, b),
            eave1,
            Direction.NORTH,
            scale,
            List.of(),
            List.of()
        );

        // Piece 2: planar slope along X. Low at x=1 (east), high at x=0 (west).
        // Together with Piece 1 the z-buffer resolves to max(b, h - a*h/16), which
        // is the inner-corner profile with the notch landing at the NE local corner.
        RoofWing.assemble(
            context,
            transform,
            MutableCullFace.EAST | MutableCullFace.SOUTH | ridgeWall2,
            (a, b) -> map(0, 16, apexTop, floor, a),
            eave2,
            Direction.EAST,
            scale,
            List.of(),
            List.of()
        );

        if (anchoredRidges) {
            RoofWing.anchoredWall(context, transform, Direction.SOUTH, apexTop);
            RoofWing.anchoredWall(context, transform, Direction.WEST, apexTop);
        }
    }
}
