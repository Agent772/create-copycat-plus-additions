package com.agent772.copycatplusadditions;

/**
 * Single source of truth for the two-phase stacking profile shared by the corner
 * and inner-corner slope <i>layer</i> blocks (issue #43). Keeps the model cores,
 * the collision/outline shapes, and any other consumer in lockstep so the
 * geometry, hitbox, and phase boundary can never silently diverge.
 *
 * <p>The profile mirrors the straight slope layer:
 * <ul>
 *   <li>layers 1-4: apex rises one {@link #STEP} per layer, eave/notch pinned at
 *       the floor (bottom-anchored) — the full corner is reached at layer 4;</li>
 *   <li>layers 5-8: apex pinned at the top, eave/notch (floor) rises one
 *       {@link #STEP} per layer (top-anchored) — a full block at layer 8.</li>
 * </ul>
 *
 * <p>Both accessors take a {@code scale}: pass {@code 16.0} for model pixel space
 * or {@code 1.0} for the normalised 0..1 space the shapes use.
 */
public final class CornerLayerProfile {

    /** Last layer of phase 1 (bottom-anchored apex growth). */
    public static final int PHASE_ONE_MAX = 4;

    /** Fraction of full height added per layer, in normalised 0..1 units. */
    private static final double STEP = 1.0 / PHASE_ONE_MAX;

    private CornerLayerProfile() {
    }

    /** Height of the apex (high) edge for the given layer count, in {@code scale} units. */
    public static double apexTop(int layers, double scale) {
        double normalized = layers <= PHASE_ONE_MAX ? layers * STEP : 1.0;
        return normalized * scale;
    }

    /** Height of the eave/notch (low) edge for the given layer count, in {@code scale} units. */
    public static double floor(int layers, double scale) {
        double normalized = layers <= PHASE_ONE_MAX ? 0.0 : (layers - PHASE_ONE_MAX) * STEP;
        return normalized * scale;
    }
}
