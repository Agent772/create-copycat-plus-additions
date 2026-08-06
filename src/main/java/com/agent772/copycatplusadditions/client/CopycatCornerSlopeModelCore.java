package com.agent772.copycatplusadditions.client;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.aabb;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.cull;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.slope;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.updateUV;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.vec3;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope.map;

import com.agent772.copycatplusadditions.blocks.CopycatCornerSlopeBlock;
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
 * Client-only model core for {@link CopycatCornerSlopeBlock}.
 *
 * <p><b>Two-piece + {@link CollapseVertex} construction.</b> The outer corner
 * is rendered as <i>two</i> planar slope pieces, each with the off-diagonal
 * corner of the slope plane collapsed onto the apex via {@link CollapseVertex}.
 * The collapsed quad degenerates into a single planar triangle along the apex-
 * to-opposite-corner ridge, so the GPU's fixed {@code v0–v2} triangulation
 * can no longer produce the half-flat artifact that affected the single-piece
 * non-planar approach on NORTH/SOUTH facings.
 *
 * <h2>Why the single non-planar slope didn't work</h2>
 *
 * <p>The outer corner shape is {@code min(slopeA, slopeB)}: one apex at full
 * height, three ground corners. Expressed as one {@code QuadSlope} this is a
 * non-planar quad with corner heights {@code [h, 0, 0, 0]}. Non-planar quads
 * get split into two triangles along the array-order {@code v0–v2} diagonal:
 * when the apex landed at {@code v0}/{@code v2} the result was a clean tent;
 * when it landed at {@code v1}/{@code v3} one triangle collapsed to flat
 * ground. With {@code yRot = facing.getClockWise().toYRot()} the apex cycled
 * through every index for the four facings, so two facings always rendered
 * the half-flat shape regardless of which lambda was tried.
 *
 * <h2>Why two planar triangles work</h2>
 *
 * <p>Each piece's UP face is a planar slope along a single axis (X for piece
 * 1, Z for piece 2). A planar quad — even one with a degenerate fourth vertex —
 * has no triangulation ambiguity: both triangles lie on the same plane, and
 * the visible triangle is geometrically correct regardless of which diagonal
 * the GPU happens to pick. {@link CollapseVertex} zero-areas the unwanted
 * triangle, so each piece contributes exactly one visible triangular wing.
 * Together they tile the full apex surface; they share only the apex-to-
 * opposite-corner ridge, with identical Y values along it, so there is no
 * z-fighting.
 *
 * <h2>Why each slope needs the collapse</h2>
 *
 * <p>A planar X-slope's apex is the entire <i>west edge</i> of the block
 * (both NW and SW at full height). A planar Z-slope's apex is the entire
 * <i>north edge</i> (NW and NE at full height). Without {@link CollapseVertex}
 * the two pieces would render two raised edges — a butterfly/tent shape —
 * instead of a single apex point at NW. Collapsing the spare apex corner of
 * each piece onto NW reduces each slope to a triangle whose apex is the
 * single NW corner.
 *
 * <h2>Geometry</h2>
 *
 * <p>Pre-rotation apex stays at LOCAL NW so the existing
 * {@code yRot = facing.getClockWise().toYRot()} mapping (EAST→NW, SOUTH→NE,
 * WEST→SE, NORTH→SW world apex) is preserved. Coordinates below are in the
 * canonical 0..1 unit space where {@code NW=(0,0)}, {@code NE=(1,0)},
 * {@code SE=(1,1)}, {@code SW=(0,1)}.
 *
 * <p><b>Two coordinate conventions live in one call site.</b> The
 * {@link CollapseVertex} arguments are canonical 0..1 XZ positions (a vertex is
 * at {@code NW=(0,0)} … {@code SW=(0,1)}), while {@code aabb(16, …)} and the
 * {@code map(0, 16, …)} slope range are in 0..16 block-pixel space. They are not
 * interchangeable — the {@code CollapseVertex(0, 1, 0, 0)} below reads as
 * "collapse SW(0,1) onto NW(0,0)", not pixel coordinates.
 *
 * <pre>
 *   Piece 1 — NE wing (planar X-slope, apex along the LOCAL west edge)
 *     UP heights pre-collapse: NW=h, NE=0, SE=0, SW=h
 *     CollapseVertex SW(0,1) → NW(0,0)
 *     Visible top triangle: NW–NE–SE (the x ≥ z half)
 *     Outer wall: NORTH (kept). Culled: SOUTH | WEST.
 *
 *   Piece 2 — SW wing (planar Z-slope, apex along the LOCAL north edge)
 *     UP heights pre-collapse: NW=h, NE=h, SE=0, SW=0
 *     CollapseVertex NE(1,0) → NW(0,0)
 *     Visible top triangle: NW–SE–SW (the x ≤ z half)
 *     Outer wall: WEST (kept). Culled: NORTH | EAST.
 * </pre>
 *
 * <p>The {@code slope(...)} transform runs before {@code CollapseVertex} in
 * the argument list so the apex Y is already set when the off-diagonal vertex
 * is collapsed onto it; reordering would leave the degenerate vertex at y=0
 * and produce a visible flap.
 *
 * <p>{@code HALF=TOP} is handled by the same {@code flipY(topHalf)} step as
 * the inner corner. {@link CollapseVertex} addresses vertices by XZ position
 * rather than array index, so it's robust to the {@code reverseWinding} swap
 * that flipY applies on TOP-half blocks.
 *
 * <h2>Roof texture — per-wing eave-aligned projection</h2>
 *
 * <p>The roof UV is set <i>structurally</i> by {@link ProjectRoofUV}: each
 * wing gets a top-down projection of the material's top sprite, rotated a
 * quarter turn per wing so the sprite's bottom edge lies along that wing's
 * own eave (piece 1 slopes down to the LOCAL east eave, piece 2 to the LOCAL
 * south eave). The projection reads canonical-frame XZ, so it co-rotates
 * with the block like the wall textures do — no facing-dependent code path
 * exists, and all four facings render identically up to rotation.
 *
 * <ul>
 *   <li>directional textures (plank grain) run parallel to the eave on
 *       <i>both</i> wings, like a straight slope's sloped face — a single
 *       fixed projection instead renders one wing's grain up the slope
 *       ("vertical lines" artifact);</li>
 *   <li>the sprite's border rows land on the block's outer edges (eave +
 *       sides), never on the ridge; the ridge lies on a sprite diagonal;</li>
 *   <li>texel density is 1:1 with a straight slope (the approved 1.0.1
 *       look).</li>
 * </ul>
 *
 * <p>{@link ProjectRoofUV} runs <i>after</i> {@link CollapseVertex}, so the
 * degenerate vertex — already stacked on the apex — receives the apex UV and
 * the unwanted triangle is zero-area in UV space too. Only quads with
 * {@code cullFace == Direction.UP} are touched; the vertical walls and the
 * flat DOWN face keep their pipeline UVs ({@code updateUV(slope(...))}
 * already crops the walls correctly).
 *
 * <h2>Double bottom face (deliberate)</h2>
 *
 * <p>Both pieces emit a full 16×16 DOWN face at y=0, so the two overlap and
 * z-fight; the overlap is visible through a transparent material from directly
 * below. This is the same accepted trade-off as
 * {@code CopycatInnerCornerSlopeModelCore}: culling DOWN on one piece breaks
 * {@code HALF=TOP}, because the cull mask's {@code flipY} would then target the
 * sloped face rather than the flat one. The bottom of a block is rarely visible,
 * so the overlap is left in place.
 */
@OnlyIn(Dist.CLIENT)
public class CopycatCornerSlopeModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatCornerSlopeBlock.FACING);
        Half half = state.getValue(CopycatCornerSlopeBlock.HALF);
        boolean roofRotated = state.getValue(CopycatCornerSlopeBlock.ROOF_ROTATED);
        assembleCornerSlope(context, facing, half, 16.0, roofRotated);
    }

    static void assembleCornerSlope(CopycatRenderContext context, Direction facing, Half half, double maxHeight,
                                    boolean roofRotated) {
        int yRot = (int) facing.getClockWise().toYRot();
        boolean topHalf = half == Half.TOP;
        final double h = maxHeight;
        AssemblyTransform transform = t -> t.rotateY(yRot).flipY(topHalf);

        // Roof UV: per-wing top-down projection rotated so the sprite's bottom
        // edge lies on each wing's own LOCAL eave — grain runs parallel to the
        // eave on both wings, on every facing. See the ProjectRoofUV Javadoc.
        // When ROOF_ROTATED is set, each wing's eave is advanced one quarter turn
        // (getClockWise), turning the projection 90 degrees so directional grain
        // runs up the slope instead of along the eave. The roof projection is
        // 180-degree symmetric, so a single boolean covers both distinct looks.
        Direction eave1 = roofRotated ? Direction.EAST.getClockWise() : Direction.EAST;
        Direction eave2 = roofRotated ? Direction.SOUTH.getClockWise() : Direction.SOUTH;
        ProjectRoofUV roofUV1 = new ProjectRoofUV(eave1);
        ProjectRoofUV roofUV2 = new ProjectRoofUV(eave2);

        // Piece 1 — NE wing: planar X-slope. Slope's full apex is the west edge
        // (NW + SW both at y=h). Collapsing SW(0,1) onto NW(0,0) leaves a single
        // planar triangle NW–NE–SE covering the x ≥ z half of the square, with
        // its apex only at NW.
        // Outer wall: NORTH (slope-edge triangle). Culled: SOUTH | WEST — both
        // are interior (Piece 2 supplies the matching outer walls).
        context.assemblePiece(
            transform,
            vec3(0, 0, 0),
            aabb(16, 16, 16),
            cull(MutableCullFace.SOUTH | MutableCullFace.WEST),
            updateUV(slope(Direction.UP, (a, b) -> map(0, 16, h, 0, a))),
            new CollapseVertex(0, 1, 0, 0),
            roofUV1
        );

        // Piece 2 — SW wing: planar Z-slope. Slope's full apex is the north edge
        // (NW + NE both at y=h). Collapsing NE(1,0) onto NW(0,0) leaves a single
        // planar triangle NW–SE–SW covering the x ≤ z half of the square, with
        // its apex only at NW.
        // Outer wall: WEST (slope-edge triangle). Culled: NORTH | EAST — both
        // are interior (Piece 1 supplies the matching outer walls).
        context.assemblePiece(
            transform,
            vec3(0, 0, 0),
            aabb(16, 16, 16),
            cull(MutableCullFace.NORTH | MutableCullFace.EAST),
            updateUV(slope(Direction.UP, (a, b) -> map(0, 16, h, 0, b))),
            new CollapseVertex(1, 0, 0, 0),
            roofUV2
        );
    }
}
