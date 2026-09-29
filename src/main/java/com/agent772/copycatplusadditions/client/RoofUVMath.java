package com.agent772.copycatplusadditions.client;

/**
 * Pure UV-projection math for the corner slope roof, extracted from
 * {@link ProjectRoofUV} so it can be unit-tested without a running client (no
 * Minecraft classes, doubles only).
 *
 * <p>Maps a vertex's canonical (un-rotated) XZ position to a UV inside a target
 * rectangle. Passing the full-sprite bounds with a surface scale of 1 reproduces
 * the original absolute top-down projection exactly.
 *
 * <p><b>Frames.</b> With CT off each wing uses its eave's {@link Frame} so the
 * sprite's bottom edge lies on that wing's eave (the plank-grain fix, see the
 * {@link ProjectRoofUV} Javadoc). With CT on the projection instead reuses the
 * material quad's own position-to-UV map, recovered with {@link #fitAffine}: CT
 * tiles are picked from <i>world</i> neighbours, and the quad reaches the roof
 * transforms un-rotated into the canonical frame with its world UVs still
 * attached, so that map keeps each tile edge facing the neighbour it represents
 * on every facing, half and wall mount.
 *
 * <p><b>Surface scale.</b> Copycats+ "enhanced" slopes texture the sloped face at
 * 1:1 surface density in two halves: the eave half is anchored to the texture's
 * eave edge and the ridge half to its ridge edge, each showing
 * {@code floor(L / 2)} texels for a surface of length {@code L}. A top-down
 * projection instead stretches one tile over the whole incline, so borders and
 * grain come out {@code L / 16} times too large next to an upstream slope.
 * {@link #alongSlope} reproduces the upstream mapping per half.
 */
public final class RoofUVMath {

    private RoofUVMath() {
    }

    /**
     * One of the eight symmetries of the unit square, mapping canonical
     * {@code (x, z)} to normalised {@code (nu, nv)}: swap the axes first, then
     * mirror each output axis.
     */
    public record Frame(boolean swap, boolean flipU, boolean flipV) {

        public static final Frame IDENTITY = new Frame(false, false, false);

        double nu(double x, double z) {
            double n = swap ? z : x;
            return flipU ? 1 - n : n;
        }

        double nv(double x, double z) {
            double n = swap ? x : z;
            return flipV ? 1 - n : n;
        }
    }

    /**
     * A canonical horizontal direction, used both for the texture eave a non-CT
     * projection aligns its sprite bottom edge to and for the direction a wing
     * geometrically slopes down towards. Values mirror {@code Direction} but stay
     * Minecraft-free for testing. The four frames are proper rotations (no
     * mirroring), matching the table in the {@link ProjectRoofUV} Javadoc.
     */
    public enum Eave {
        /** {@code u = x, v = z} — identity, sprite bottom at z=1. */
        SOUTH(Frame.IDENTITY),
        /** {@code u = 1 - z, v = x} — 90 degrees, sprite bottom at x=1. */
        EAST(new Frame(true, true, false)),
        /** {@code u = 1 - x, v = 1 - z} — 180 degrees, sprite bottom at z=0. */
        NORTH(new Frame(false, true, true)),
        /** {@code u = z, v = 1 - x} — 270 degrees, sprite bottom at x=0. */
        WEST(new Frame(true, false, true));

        private final Frame frame;

        Eave(Frame frame) {
            this.frame = frame;
        }

        public Frame frame() {
            return frame;
        }
    }

    /**
     * Projects the canonical XZ position {@code (x, z)} (each in {@code [0, 1]})
     * to a UV inside the rectangle {@code [uMin, uMax] x [vMin, vMax]}.
     *
     * @return a two-element {@code [u, v]} array
     */
    public static double[] project(double x, double z, Frame frame,
                                   double uMin, double uMax, double vMin, double vMax) {
        return new double[] {
            uMin + frame.nu(x, z) * (uMax - uMin),
            vMin + frame.nv(x, z) * (vMax - vMin)
        };
    }

    /**
     * The along-slope texture scale for a roof wing rising {@code rise} pixels over
     * one block, matching upstream enhanced slopes: each half of the surface
     * (length {@code L / 2}, {@code L = sqrt(rise² + 16²)}) shows
     * {@code floor(L / 2)} texels, so the cut between halves lands on a texel
     * boundary. Returns the texels per half relative to the flat 8, i.e. 1 for a
     * flat or shallow wing.
     */
    public static double enhancedSlopeScale(double rise) {
        double halfLength = Math.sqrt(rise * rise + 256.0) / 2.0;
        return Math.floor(halfLength) / 8.0;
    }

    /**
     * Remaps a canonical position along a wing's slope axis so a top-down
     * projection shows the upstream enhanced-slope texture. {@code downhill} is the
     * direction the wing slopes down towards; the apex edge is opposite it. Within
     * the ridge half the texture is anchored to the apex edge, within the eave half
     * to the eave edge, each compressed by {@code scale} (see
     * {@link #enhancedSlopeScale}). The cross-slope axis is untouched, and a scale
     * of 1 is the identity.
     *
     * @param ridgeHalf whether the vertex belongs to the apex-side half; a vertex on
     *                  the mid line maps differently for each half, so the caller
     *                  must say which quad it is projecting
     * @return a two-element {@code [x, z]} array
     */
    public static double[] alongSlope(double x, double z, Eave downhill, double scale, boolean ridgeHalf) {
        return switch (downhill) {
            case EAST -> new double[] {anchor(x, scale, ridgeHalf), z};
            case WEST -> new double[] {1 - anchor(1 - x, scale, ridgeHalf), z};
            case SOUTH -> new double[] {x, anchor(z, scale, ridgeHalf)};
            case NORTH -> new double[] {x, 1 - anchor(1 - z, scale, ridgeHalf)};
        };
    }

    /** {@code s} is the horizontal distance from the apex edge, in {@code [0, 1]}. */
    private static double anchor(double s, double scale, boolean ridgeHalf) {
        return ridgeHalf ? s * scale : 1 - (1 - s) * scale;
    }

    /**
     * Least-squares fit of {@code value = a + b·x + c·z} over the given points.
     * Roof quads carry UVs that are exactly affine in canonical XZ (cropping and
     * {@code slope} never break that), so the fit recovers the material's
     * position-to-UV map, including any rotation or mirroring.
     *
     * @return {@code [a, b, c]}, or {@code null} if the points do not span a plane
     */
    public static double[] fitAffine(double[] xs, double[] zs, double[] values) {
        double n = xs.length;
        double sx = 0, sz = 0, sxx = 0, sxz = 0, szz = 0, sv = 0, sxv = 0, szv = 0;
        for (int i = 0; i < xs.length; i++) {
            sx += xs[i];
            sz += zs[i];
            sxx += xs[i] * xs[i];
            sxz += xs[i] * zs[i];
            szz += zs[i] * zs[i];
            sv += values[i];
            sxv += xs[i] * values[i];
            szv += zs[i] * values[i];
        }
        // Normal equations [[n, sx, sz], [sx, sxx, sxz], [sz, sxz, szz]] · [a, b, c] = [sv, sxv, szv].
        double det = det3(n, sx, sz, sx, sxx, sxz, sz, sxz, szz);
        if (Math.abs(det) < 1.0e-12) {
            return null;
        }
        return new double[] {
            det3(sv, sx, sz, sxv, sxx, sxz, szv, sxz, szz) / det,
            det3(n, sv, sz, sx, sxv, sxz, sz, szv, szz) / det,
            det3(n, sx, sv, sx, sxx, sxv, sz, sxz, szv) / det
        };
    }

    /** Evaluates an {@link #fitAffine} result at {@code (x, z)}. */
    public static double evalAffine(double[] coeffs, double x, double z) {
        return coeffs[0] + coeffs[1] * x + coeffs[2] * z;
    }

    private static double det3(double a, double b, double c,
                               double d, double e, double f,
                               double g, double h, double i) {
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    }
}
