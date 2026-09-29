package com.agent772.copycatplusadditions.client;

import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableQuad;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVertex;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadTransform;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@link QuadTransform} that moves one vertex of the UP face to a new XZ
 * position, trimming the rectangular slope quad to the wing's shape. The Y
 * coordinate is left untouched: every move runs along a line of constant
 * height on the planar slope (onto the apex for the degenerate collapse, or
 * across the slope onto the diagonal ridge for the half pieces), so the vertex
 * stays on the slope plane. A full collapse onto another vertex leaves one
 * visible triangle (the wing) plus one zero-area triangle.
 *
 * <p>The moved vertex's UV is re-evaluated from the quad's affine
 * position-to-UV map (fitted over all vertices before the move), so it lands on
 * the texel {@link ProjectRoofUV} would have given that position: the collapsed
 * vertex gets the apex UV, and the unwanted triangle is zero-area in UV space too.
 *
 * <p>Vertices are addressed by their XZ position in the canonical 0..1 unit
 * space, not by array index. Baked top-face quads use CCW-from-above winding
 * ({@code v0=NW, v1=SW, v2=SE, v3=NE}) and the order is permuted again by
 * {@code reverseWinding} on {@code HALF=TOP}, so index-based addressing is
 * brittle. XZ positions are unaffected by both: rotateY is undone before the
 * transform runs, and flipY only mirrors Y. Positions are matched with an
 * epsilon to absorb baked-model float noise.
 *
 * <p>Filter is by {@code quad.cullFace == Direction.UP}, which identifies the
 * canonical-frame up-face for both halves: in canonical frame, the source
 * top face's cullFace is UP for {@code HALF=BOTTOM}, and the source bottom
 * face is flipped onto canonical-up (cullFace also UP) for {@code HALF=TOP}
 * by {@code AssemblyTransform.flipY}'s MIRROR mutation. Side walls and the
 * bottom face are untouched.
 */
@OnlyIn(Dist.CLIENT)
public record CollapseVertex(double fromX, double fromZ, double toX, double toZ) implements QuadTransform {

    private static final double EPSILON = 1.0e-3;

    @Override
    public boolean transformQuad(MutableQuad quad, TextureAtlasSprite sprite) {
        if (quad.cullFace != Direction.UP) {
            return true;
        }
        MutableVertex from = null;
        for (MutableVertex v : quad.vertices) {
            if (matches(v, fromX, fromZ)) {
                from = v;
            }
        }
        if (from == null) {
            return true;
        }

        int n = quad.vertices.size();
        double[] xs = new double[n];
        double[] zs = new double[n];
        double[] us = new double[n];
        double[] vs = new double[n];
        for (int i = 0; i < n; i++) {
            MutableVertex v = quad.vertices.get(i);
            xs[i] = v.xyz.x;
            zs[i] = v.xyz.z;
            us[i] = v.uv.u;
            vs[i] = v.uv.v;
        }
        double[] fitU = RoofUVMath.fitAffine(xs, zs, us);
        double[] fitV = RoofUVMath.fitAffine(xs, zs, vs);

        from.xyz.x = toX;
        from.xyz.z = toZ;
        if (fitU != null && fitV != null) {
            from.uv.u = (float) RoofUVMath.evalAffine(fitU, toX, toZ);
            from.uv.v = (float) RoofUVMath.evalAffine(fitV, toX, toZ);
        }
        return true;
    }

    private static boolean matches(MutableVertex v, double x, double z) {
        return Math.abs(v.xyz.x - x) < EPSILON && Math.abs(v.xyz.z - z) < EPSILON;
    }
}
