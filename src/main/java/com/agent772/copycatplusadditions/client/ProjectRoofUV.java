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
 * canonical-frame direction the wing slopes down towards).
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
 * <p>Properties preserved from the plain projection: the sprite's border rows
 * land on the block's outer edges (eave + sides), never on the ridge; the
 * ridge lies on a sprite diagonal; texel density is 1:1 with a straight
 * slope. The mappings are proper rotations (no mirroring), one per eave:
 *
 * <pre>
 *   eave SOUTH: u = x,     v = z        (identity — sprite bottom at z=1)
 *   eave EAST:  u = 1 - z, v = x        (90° — sprite bottom at x=1)
 *   eave NORTH: u = 1 - x, v = 1 - z    (180° — sprite bottom at z=0)
 *   eave WEST:  u = z,     v = 1 - x    (270° — sprite bottom at x=0)
 * </pre>
 *
 * <p>Runs <i>after</i> {@link CollapseVertex} so the degenerate vertex — now
 * sitting at the apex position — receives the apex UV: the unwanted triangle
 * is zero-area in UV space too, and can neither bleed nor shimmer.
 *
 * <p>Same filtering rule as {@link CollapseVertex}: only quads with
 * {@code cullFace == Direction.UP} are touched (the canonical-frame roof for
 * both halves), leaving side walls and the bottom face alone. {@code flipY}
 * for {@code HALF=TOP} mirrors only Y, so the XZ-based projection is
 * unaffected.
 */
@OnlyIn(Dist.CLIENT)
public record ProjectRoofUV(Direction eave) implements QuadTransform {

    @Override
    public boolean transformQuad(MutableQuad quad, TextureAtlasSprite sprite) {
        if (quad.cullFace != Direction.UP) {
            return true;
        }
        for (MutableVertex vertex : quad.vertices) {
            double x = vertex.xyz.x;
            double z = vertex.xyz.z;
            double u;
            double v;
            switch (eave) {
                case EAST -> {
                    u = 1 - z;
                    v = x;
                }
                case NORTH -> {
                    u = 1 - x;
                    v = 1 - z;
                }
                case WEST -> {
                    u = z;
                    v = 1 - x;
                }
                default -> {
                    u = x;
                    v = z;
                }
            }
            vertex.uv.u = sprite.getU((float) u);
            vertex.uv.v = sprite.getV((float) v);
        }
        return true;
    }
}
