package com.agent772.copycatplusadditions.compat.sable;

import com.agent772.copycatplusadditions.CornerLayerProfile;

/**
 * Pure (Minecraft-free) per-layer volume fractions for this mod's layer blocks,
 * expressed as a fraction of a full 1x1x1 block. This is the single source of
 * truth the shipped {@code physics_block_properties/*.json} files must mirror;
 * {@code VolumeFractionsSyncTest} enforces that they stay in sync.
 *
 * <p>The corner layer blocks follow the two-phase {@link CornerLayerProfile}: a
 * bottom-anchored wedge for layers 1-4, then a filled base under a top-anchored
 * cut for layers 5-8, reaching a full block at layer 8. Keeping the math here
 * (with no {@code BlockState}/registry dependency) makes it unit-testable
 * without bootstrapping the game.
 */
final class LayerVolumes {

    private static final int PHASE_ONE_MAX = CornerLayerProfile.PHASE_ONE_MAX;

    private LayerVolumes() {
    }

    /** {@code corner_slope_layer}: full corner wedge (1/3) at layer 4, full block at layer 8. */
    static double cornerLayer(int layers) {
        return layers <= PHASE_ONE_MAX
            ? layers / 12.0
            : (layers - PHASE_ONE_MAX) / 6.0 + 1.0 / 3.0;
    }

    /** {@code inner_corner_slope_layer}: full inner corner (2/3) at layer 4, full block at layer 8. */
    static double innerCornerLayer(int layers) {
        return layers <= PHASE_ONE_MAX
            ? layers / 6.0
            : (layers - PHASE_ONE_MAX) / 12.0 + 2.0 / 3.0;
    }

    /** {@code adv_slope_layer}: linear ramp to the half-block straight slope at layer 8. */
    static double advSlopeLayer(int layers) {
        return layers / 16.0;
    }
}
