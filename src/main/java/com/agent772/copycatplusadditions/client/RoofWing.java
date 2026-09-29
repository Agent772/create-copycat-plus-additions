package com.agent772.copycatplusadditions.client;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.aabb;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.cull;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.slope;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.updateUV;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.vec3;

import java.util.ArrayList;
import java.util.List;

import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableAABB;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVec3;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadTransform;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Emits one planar roof wing of a corner slope as two pieces split at the wing's
 * horizontal midpoint: a ridge half and an eave half. Each half gets its own
 * {@link ProjectRoofUV}, which is what lets the roof match Copycats+ enhanced
 * slopes: the ridge half is textured from the texture's ridge edge and the eave
 * half from its eave edge at 1:1 surface density, the same two-half layout an
 * upstream slope uses (see {@link RoofUVMath#alongSlope}). A single quad can't
 * express that — its UVs are interpolated linearly across the whole wing.
 *
 * <p>The split is a crop in the canonical frame, so both halves stay on the same
 * slope plane and share the mid line's heights exactly. The face each half
 * exposes on the split plane is interior and culled on top of the wing's own
 * cull mask.
 */
@OnlyIn(Dist.CLIENT)
final class RoofWing {

    private RoofWing() {
    }

    /**
     * @param cullMask   the wing's own {@link MutableCullFace} mask
     * @param height     the wing's slope function, in canonical 0..16 pixel space
     * @param eave       the sprite-bottom direction for the non-CT projection
     * @param downhill   the direction the wing slopes down towards (horizontal)
     * @param scale      the along-slope texture scale, see
     *                   {@link RoofUVMath#enhancedSlopeScale}; 1 for the plain projection
     * @param ridgeMoves {@link CollapseVertex} trims for the ridge half, run after the projection
     * @param eaveMoves  {@link CollapseVertex} trims for the eave half, run after the projection
     */
    static void assemble(CopycatRenderContext context, AssemblyTransform transform, int cullMask,
                         QuadSlope.QuadSlopeFunction height, Direction eave, Direction downhill, double scale,
                         List<CollapseVertex> ridgeMoves, List<CollapseVertex> eaveMoves) {
        // The apex edge sits at the low end of the axis when the wing slopes down
        // towards the positive direction, so the ridge half is the one starting at 0.
        boolean apexAtMin = downhill.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double ridgeStart = apexAtMin ? 0.0 : 8.0;
        double eaveStart = apexAtMin ? 8.0 : 0.0;
        assembleHalf(context, transform, cullMask | cullBit(downhill), height, eave, downhill, scale,
            true, ridgeStart, ridgeMoves);
        assembleHalf(context, transform, cullMask | cullBit(downhill.getOpposite()), height, eave, downhill, scale,
            false, eaveStart, eaveMoves);
    }

    private static void assembleHalf(CopycatRenderContext context, AssemblyTransform transform, int cullMask,
                                     QuadSlope.QuadSlopeFunction height, Direction eave, Direction downhill,
                                     double scale, boolean ridgeHalf, double start, List<CollapseVertex> moves) {
        boolean alongX = downhill.getAxis() == Direction.Axis.X;
        MutableVec3 offset = alongX ? vec3(start, 0, 0) : vec3(0, 0, start);
        MutableAABB crop = alongX
            ? aabb(8, 16, 16).move(start, 0, 0)
            : aabb(16, 16, 8).move(0, 0, start);

        List<QuadTransform> transforms = new ArrayList<>();
        transforms.add(updateUV(slope(Direction.UP, height)));
        transforms.add(new ProjectRoofUV(eave, downhill, ridgeHalf, scale));
        transforms.addAll(moves);
        context.assemblePiece(transform, offset, crop, cull(cullMask), transforms.toArray(QuadTransform[]::new));
    }

    /**
     * Whether a straight wall of {@code height} pixels should be emitted by
     * {@link #anchoredWall} instead of as part of its wing: upstream enhanced
     * slopes anchor every wall shorter than a block, and a zero-height wall does
     * not exist.
     */
    static boolean anchorsWall(boolean enhanced, double height) {
        return enhanced && height > 0 && height < 16;
    }

    /**
     * Emits the rectangular wall on canonical side {@code face}, rising
     * {@code height} pixels from the floor, the way upstream enhanced slopes build
     * their short eave and ridge walls: the lower half shows the bottom of the side
     * texture and the upper half its top, so the wall keeps both texture edges
     * (borders) at 1:1 instead of being a crop off the bottom of the side. The
     * wing emitting this side must cull {@code face}.
     */
    static void anchoredWall(CopycatRenderContext context, AssemblyTransform transform, Direction face,
                             double height) {
        double half = height / 2;
        int cullMask = MutableCullFace.UP | MutableCullFace.DOWN | MutableCullFace.NORTH
            | MutableCullFace.SOUTH | MutableCullFace.EAST | MutableCullFace.WEST;
        cullMask &= ~cullBit(face);
        // A one-pixel slab against the face selects just that face's texels.
        boolean alongX = face.getAxis() == Direction.Axis.X;
        double slab = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15.0 : 0.0;
        double x = alongX ? slab : 0.0;
        double z = alongX ? 0.0 : slab;
        double sizeX = alongX ? 1.0 : 16.0;
        double sizeZ = alongX ? 16.0 : 1.0;
        // Lower half: the bottom of the side texture, in place.
        context.assemblePiece(transform, vec3(x, 0, z), aabb(sizeX, half, sizeZ).move(x, 0, z), cull(cullMask));
        // Upper half: the top of the side texture, moved down onto the lower half.
        context.assemblePiece(transform, vec3(x, half, z), aabb(sizeX, half, sizeZ).move(x, 16 - half, z),
            cull(cullMask));
    }

    private static int cullBit(Direction direction) {
        return switch (direction) {
            case NORTH -> MutableCullFace.NORTH;
            case SOUTH -> MutableCullFace.SOUTH;
            case EAST -> MutableCullFace.EAST;
            case WEST -> MutableCullFace.WEST;
            case UP -> MutableCullFace.UP;
            case DOWN -> MutableCullFace.DOWN;
        };
    }
}
