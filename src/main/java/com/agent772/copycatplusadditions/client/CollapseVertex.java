package com.agent772.copycatplusadditions.client;

import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableQuad;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVertex;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadTransform;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@link QuadTransform} that collapses one vertex of the UP face onto another
 * by overwriting its XZ position. The Y coordinate is left untouched so the
 * collapsed vertex stays at the apex height of the slope plane — and the
 * resulting degenerate quad triangulates into one visible triangle (the wing)
 * plus one zero-area triangle.
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
        MutableVertex to = null;
        for (MutableVertex v : quad.vertices) {
            if (matches(v, fromX, fromZ)) {
                from = v;
            } else if (matches(v, toX, toZ)) {
                to = v;
            }
        }
        if (from != null && to != null) {
            from.xyz.x = to.xyz.x;
            from.xyz.z = to.xyz.z;
        }
        return true;
    }

    private static boolean matches(MutableVertex v, double x, double z) {
        return Math.abs(v.xyz.x - x) < EPSILON && Math.abs(v.xyz.z - z) < EPSILON;
    }
}
