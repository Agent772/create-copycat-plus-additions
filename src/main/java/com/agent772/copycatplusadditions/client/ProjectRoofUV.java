package com.agent772.copycatplusadditions.client;

import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableQuad;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVertex;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadTransform;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@link QuadTransform} that overwrites the UV of every UP-face vertex with a
 * top-down projection of the material's top sprite, <b>rotated so the sprite's
 * bottom edge lies along the wing's own eave</b> ({@code eave} is the LOCAL
 * canonical-frame direction the sprite bottom faces).
 *
 * <p>The projection is computed from the vertex's canonical (un-rotated) XZ
 * position, so it co-rotates with the block exactly like the side-wall
 * textures do — there is no facing-dependent code path at all, and all four
 * facings render identically up to rotation.
 *
 * <p>Why per-wing rotation matters: the two wings of the outer corner slope
 * down along <i>perpendicular</i> axes. A single fixed projection (the
 * previous {@code u = worldX, v = worldZ} approach) renders directional
 * textures such as plank grain parallel to one wing's eave but straight
 * <i>up the slope</i> of the other wing — the "vertical lines on one side"
 * artifact. Rotating each wing's projection a quarter turn so the sprite's
 * bottom edge sits on that wing's eave makes grain run parallel to the eave
 * on <i>both</i> wings, the same way a straight slope textures its sloped
 * face. Rotation-symmetric sprites (e.g. log growth rings) are unaffected.
 *
 * <p>The {@code eave} passed in is chosen per block from the
 * {@code ROOF_ROTATED} blockstate property: the default look uses each wing's
 * natural eave, and the rotated look advances both eaves one quarter turn so
 * the grain runs up the slope instead. This class stays a pure projection —
 * the orientation decision lives in the caller.
 *
 * <p>Properties preserved from the plain projection: the sprite's border rows
 * land on the block's outer edges (eave + sides), never on the ridge; the
 * ridge lies on a sprite diagonal. The mappings are proper rotations (no
 * mirroring), one per eave:
 *
 * <pre>
 *   eave SOUTH: u = x,     v = z        (identity — sprite bottom at z=1)
 *   eave EAST:  u = 1 - z, v = x        (90° — sprite bottom at x=1)
 *   eave NORTH: u = 1 - x, v = 1 - z    (180° — sprite bottom at z=0)
 *   eave WEST:  u = z,     v = 1 - x    (270° — sprite bottom at x=0)
 * </pre>
 *
 * <h2>Enhanced slopes: 1:1 surface density</h2>
 *
 * <p>Each wing is emitted as two pieces split at its horizontal midpoint (see
 * {@link RoofWing}), and this transform projects one of them: {@code downhill} is
 * the direction the wing geometrically slopes down towards, {@code ridgeHalf}
 * says which piece this is, and {@code scale} compresses the projection along
 * the slope so each half shows the same texels as an upstream enhanced slope
 * anchored to the same texture edge (see {@link RoofUVMath#alongSlope}). Without
 * it the roof stretched one tile over the whole incline, so borders and grain
 * read {@code L / 16} times larger than on the adjacent Copycats+ slope. A scale
 * of 1 (enhanced models off, or a shallow wing) is the plain projection.
 *
 * <p>Runs <i>before</i> {@link CollapseVertex}: the half pieces are cropped
 * rectangles with four genuine corners, which the CT path needs to recover the
 * material's UV map. {@link CollapseVertex} then trims the rectangle to the wing
 * and re-evaluates the moved vertex's UV from the same affine map, so the
 * degenerate vertex still gets the apex UV and the unwanted triangle is zero-area
 * in UV space too.
 *
 * <p>Same filtering rule as {@link CollapseVertex}: only quads with
 * {@code cullFace == Direction.UP} are touched (the canonical-frame roof for
 * both halves), leaving side walls and the bottom face alone. {@code flipY}
 * for {@code HALF=TOP} mirrors only Y, so the XZ-based projection is
 * unaffected.
 *
 * <h2>Connected textures (issue #61)</h2>
 *
 * <p>The projection targets whatever tile the material model chose for this quad
 * rather than always the base sprite. When they are on, the material model shifts
 * the quad's UVs to a CT sub-tile <i>outside</i> the base sprite's bounds; earlier
 * this class overwrote those with absolute base sprite UVs and the roof always
 * showed the unconnected tile whatever the toggle said.
 *
 * <p>CT tiles are chosen from <i>world</i> neighbours, so while CT is on the
 * per-wing eave rotation is dropped and the projection reuses the material quad's
 * own position-to-UV map, fitted from its incoming vertices (see
 * {@link RoofUVMath#fitAffine}). That map carries both the CT tile and the
 * facing / half / wall-mount rotation; a fixed canonical frame would turn the tile
 * with the block's facing and show its open edges on the wrong side. The
 * plank-grain rotation only applies to the non-CT look.
 */
@OnlyIn(Dist.CLIENT)
public record ProjectRoofUV(Direction eave, Direction downhill, boolean ridgeHalf, double scale)
    implements QuadTransform {

    /**
     * Slack for deciding a UV lies outside the base sprite tile. A CT shift moves
     * UVs a whole tile or more, far beyond this; within-tile slope rounding stays
     * inside the bounds, so it never trips the check.
     */
    private static final float CT_EPSILON = 1.0e-5f;

    @Override
    public boolean transformQuad(MutableQuad quad, TextureAtlasSprite sprite) {
        if (quad.cullFace != Direction.UP) {
            return true;
        }

        int n = quad.vertices.size();
        double[] xs = new double[n];
        double[] zs = new double[n];
        double[] us = new double[n];
        double[] vs = new double[n];
        for (int i = 0; i < n; i++) {
            MutableVertex vertex = quad.vertices.get(i);
            xs[i] = vertex.xyz.x;
            zs[i] = vertex.xyz.z;
            us[i] = vertex.uv.u;
            vs[i] = vertex.uv.v;
        }

        float su0 = sprite.getU(0.0F);
        float su1 = sprite.getU(1.0F);
        float sv0 = sprite.getV(0.0F);
        float sv1 = sprite.getV(1.0F);

        // CT shifts the tile outside the base sprite; a plain sub-rect inside the
        // base bounds (non-CT) keeps the eave-rotated projection.
        boolean ctTile = false;
        for (int i = 0; i < n; i++) {
            ctTile |= us[i] < su0 - CT_EPSILON || us[i] > su1 + CT_EPSILON
                || vs[i] < sv0 - CT_EPSILON || vs[i] > sv1 + CT_EPSILON;
        }

        // With CT on, the material already mapped this quad in world orientation onto
        // its CT tile. The quad is a cropped rectangle whose UVs are affine in XZ, so
        // the fit recovers that map for the whole block, not just this half.
        double[] fitU = ctTile ? RoofUVMath.fitAffine(xs, zs, us) : null;
        double[] fitV = ctTile ? RoofUVMath.fitAffine(xs, zs, vs) : null;
        if (ctTile && (fitU == null || fitV == null)) {
            return true;
        }

        RoofUVMath.Frame frame = eave(eave).frame();
        RoofUVMath.Eave slope = eave(downhill);
        for (int i = 0; i < n; i++) {
            double[] p = RoofUVMath.alongSlope(xs[i], zs[i], slope, scale, ridgeHalf);
            MutableVertex vertex = quad.vertices.get(i);
            if (ctTile) {
                vertex.uv.u = (float) RoofUVMath.evalAffine(fitU, p[0], p[1]);
                vertex.uv.v = (float) RoofUVMath.evalAffine(fitV, p[0], p[1]);
            } else {
                double[] uv = RoofUVMath.project(p[0], p[1], frame, su0, su1, sv0, sv1);
                vertex.uv.u = (float) uv[0];
                vertex.uv.v = (float) uv[1];
            }
        }
        return true;
    }

    private static RoofUVMath.Eave eave(Direction direction) {
        return switch (direction) {
            case EAST -> RoofUVMath.Eave.EAST;
            case NORTH -> RoofUVMath.Eave.NORTH;
            case WEST -> RoofUVMath.Eave.WEST;
            default -> RoofUVMath.Eave.SOUTH;
        };
    }
}
