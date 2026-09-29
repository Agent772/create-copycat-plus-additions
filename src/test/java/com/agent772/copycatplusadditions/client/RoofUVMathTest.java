package com.agent772.copycatplusadditions.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import com.agent772.copycatplusadditions.client.RoofUVMath.Eave;
import com.agent772.copycatplusadditions.client.RoofUVMath.Frame;

import org.junit.jupiter.api.Test;

/**
 * Pure-math guard for {@link RoofUVMath}, the roof projection extracted from
 * {@link ProjectRoofUV} for issue #61. Verifies the plain projection is unchanged
 * (no regression), the enhanced-slope scale and per-half remap match upstream
 * Copycats+ slopes, and the CT path recovers the material quad's UV map.
 */
class RoofUVMathTest {

    private static final double EPS = 1.0e-9;

    // Canonical corners: NW=(0,0), NE=(1,0), SE=(1,1), SW=(0,1).
    private static final double[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static final double U_MIN = 0.25, U_MAX = 0.5, V_MIN = 0.6, V_MAX = 0.8;

    /**
     * With the full-sprite rect and no surface scale, every eave/corner must
     * reproduce the exact mapping table documented on {@link ProjectRoofUV} — the
     * pre-#61 look.
     */
    @Test
    void fullSpriteMatchesLegacyTable() {
        // Expected [u, v] per eave, indexed by CORNERS order (NW, NE, SE, SW).
        assertMapping(Eave.SOUTH, new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
        assertMapping(Eave.EAST, new double[][] {{1, 0}, {1, 1}, {0, 1}, {0, 0}});
        assertMapping(Eave.NORTH, new double[][] {{1, 1}, {0, 1}, {0, 0}, {1, 0}});
        assertMapping(Eave.WEST, new double[][] {{0, 1}, {0, 0}, {1, 0}, {1, 1}});
    }

    private void assertMapping(Eave eave, double[][] expected) {
        for (int i = 0; i < CORNERS.length; i++) {
            double[] uv = RoofUVMath.project(CORNERS[i][0], CORNERS[i][1], eave.frame(), 0, 1, 0, 1);
            assertEquals(expected[i][0], uv[0], EPS, eave + " corner " + i + " u");
            assertEquals(expected[i][1], uv[1], EPS, eave + " corner " + i + " v");
        }
    }

    /** A CT sub-tile rect: every projected UV must stay inside the rect, in any frame. */
    @Test
    void ctTileStaysInsideRect() {
        Random rng = new Random(61);
        for (Eave eave : Eave.values()) {
            for (int i = 0; i < 1000; i++) {
                double[] uv = RoofUVMath.project(rng.nextDouble(), rng.nextDouble(), eave.frame(),
                    U_MIN, U_MAX, V_MIN, V_MAX);
                assertTrue(uv[0] >= U_MIN - EPS && uv[0] <= U_MAX + EPS, "u out of tile: " + uv[0]);
                assertTrue(uv[1] >= V_MIN - EPS && uv[1] <= V_MAX + EPS, "v out of tile: " + uv[1]);
            }
        }
    }

    /** The collapsed apex vertex (0,0) maps to the rect's min corner in the identity frame. */
    @Test
    void collapsedApexMapsToRectMin() {
        double[] uv = RoofUVMath.project(0, 0, Frame.IDENTITY, U_MIN, U_MAX, V_MIN, V_MAX);
        assertEquals(U_MIN, uv[0], EPS);
        assertEquals(V_MIN, uv[1], EPS);
    }

    /**
     * Upstream enhanced slopes show {@code floor(L / 2)} texels per half: 11 for a
     * full-height wing ({@code L = 16√2}), and the flat 8 for shallow layers.
     */
    @Test
    void enhancedScaleMatchesUpstreamTexelCounts() {
        assertEquals(11.0 / 8.0, RoofUVMath.enhancedSlopeScale(16), EPS);
        assertEquals(10.0 / 8.0, RoofUVMath.enhancedSlopeScale(12), EPS);
        assertEquals(1.0, RoofUVMath.enhancedSlopeScale(8), EPS);
        assertEquals(1.0, RoofUVMath.enhancedSlopeScale(4), EPS);
        assertEquals(1.0, RoofUVMath.enhancedSlopeScale(0), EPS);
    }

    /** A scale of 1 is the identity on both halves, for every slope direction. */
    @Test
    void unitScaleIsIdentity() {
        Random rng = new Random(64);
        for (Eave downhill : Eave.values()) {
            for (int i = 0; i < 100; i++) {
                double x = rng.nextDouble(), z = rng.nextDouble();
                for (boolean ridgeHalf : new boolean[] {true, false}) {
                    double[] p = RoofUVMath.alongSlope(x, z, downhill, 1.0, ridgeHalf);
                    assertEquals(x, p[0], EPS);
                    assertEquals(z, p[1], EPS);
                }
            }
        }
    }

    /**
     * Each half is anchored to its own edge: the apex and eave edges keep their
     * texture edge, the cross-slope axis is untouched, and each half spans exactly
     * {@code scale / 2} of the tile along the slope — the upstream texel count.
     */
    @Test
    void halvesAnchorToTheirOwnEdge() {
        double scale = RoofUVMath.enhancedSlopeScale(16);
        // Downhill EAST: apex edge at x=0, eave edge at x=1, split at x=0.5.
        assertEquals(0.0, RoofUVMath.alongSlope(0, 0.3, Eave.EAST, scale, true)[0], EPS);
        assertEquals(0.5 * scale, RoofUVMath.alongSlope(0.5, 0.3, Eave.EAST, scale, true)[0], EPS);
        assertEquals(1 - 0.5 * scale, RoofUVMath.alongSlope(0.5, 0.3, Eave.EAST, scale, false)[0], EPS);
        assertEquals(1.0, RoofUVMath.alongSlope(1, 0.3, Eave.EAST, scale, false)[0], EPS);
        assertEquals(0.3, RoofUVMath.alongSlope(0.5, 0.3, Eave.EAST, scale, false)[1], EPS);
        // Downhill NORTH: apex edge at z=1, eave edge at z=0.
        assertEquals(1.0, RoofUVMath.alongSlope(0.3, 1, Eave.NORTH, scale, true)[1], EPS);
        assertEquals(1 - 0.5 * scale, RoofUVMath.alongSlope(0.3, 0.5, Eave.NORTH, scale, true)[1], EPS);
        assertEquals(0.5 * scale, RoofUVMath.alongSlope(0.3, 0.5, Eave.NORTH, scale, false)[1], EPS);
        assertEquals(0.0, RoofUVMath.alongSlope(0.3, 0, Eave.NORTH, scale, false)[1], EPS);
        assertEquals(0.3, RoofUVMath.alongSlope(0.3, 0.5, Eave.NORTH, scale, true)[0], EPS);
    }

    /**
     * The CT path: a half-block crop of a quad the material textured in any frame
     * (the canonical view of any facing, TOP-half mirror or wall mount) carries
     * only half the tile, but the affine fit recovers the full-block map, so the
     * projection still reaches the tile's far edge with its world orientation.
     */
    @Test
    void affineFitRecoversFullTileFromCroppedHalf() {
        Frame[] frames = {
            Frame.IDENTITY, Eave.EAST.frame(), Eave.NORTH.frame(), Eave.WEST.frame(),
            new Frame(false, false, true), new Frame(true, true, true)
        };
        for (Frame frame : frames) {
            // Eave half of an X-split wing: x in [0.5, 1].
            double[] xs = {0.5, 1, 1, 0.5};
            double[] zs = {0, 0, 1, 1};
            double[] us = new double[4];
            double[] vs = new double[4];
            for (int i = 0; i < 4; i++) {
                double[] uv = RoofUVMath.project(xs[i], zs[i], frame, U_MIN, U_MAX, V_MIN, V_MAX);
                us[i] = uv[0];
                vs[i] = uv[1];
            }
            double[] fitU = RoofUVMath.fitAffine(xs, zs, us);
            double[] fitV = RoofUVMath.fitAffine(xs, zs, vs);
            for (double[] corner : CORNERS) {
                double[] expected = RoofUVMath.project(corner[0], corner[1], frame, U_MIN, U_MAX, V_MIN, V_MAX);
                assertEquals(expected[0], RoofUVMath.evalAffine(fitU, corner[0], corner[1]), 1.0e-7, frame + " u");
                assertEquals(expected[1], RoofUVMath.evalAffine(fitV, corner[0], corner[1]), 1.0e-7, frame + " v");
            }
        }
    }

    /**
     * {@link CollapseVertex} refits after an earlier move in the same quad: the
     * ridge half of an outer-corner wing has one vertex already stacked on the apex,
     * and the fit over the remaining points must still be exact.
     */
    @Test
    void affineFitSurvivesCollapsedVertex() {
        double[] xs = {0, 0.5, 0.5, 0};
        double[] zs = {0, 0, 1, 0};
        double[] values = new double[4];
        for (int i = 0; i < 4; i++) {
            values[i] = 0.3 + 0.2 * xs[i] - 0.7 * zs[i];
        }
        double[] fit = RoofUVMath.fitAffine(xs, zs, values);
        assertEquals(0.3 + 0.2 * 0.5 - 0.7 * 0.5, RoofUVMath.evalAffine(fit, 0.5, 0.5), 1.0e-9);
    }

    /** Collinear points (a fully degenerate quad) can't define a plane. */
    @Test
    void affineFitRejectsCollinearPoints() {
        assertNull(RoofUVMath.fitAffine(new double[] {0, 0.5, 1}, new double[] {0, 0.5, 1}, new double[] {1, 2, 3}));
    }
}
