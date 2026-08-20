package com.agent772.copycatplusadditions.compat.sable;

import com.agent772.copycatplusadditions.registry.ModBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The fraction of a full 1x1x1 block that each of this mod's shapes actually
 * occupies. Used to scale a mimicked material's Sable mass down to what this
 * block's geometry displaces.
 *
 * <p>The corner wedges are geometric constants ({@code corner_slope} = 1/3,
 * {@code inner_corner_slope} = 2/3); the per-layer fractions are delegated to
 * {@link LayerVolumes}, the Minecraft-free single source of truth. Those values
 * MUST stay in sync with the static {@code sable:volume} values shipped in
 * {@code data/copycatplusadditions/physics_block_properties/*.json} (Stage 1) —
 * {@code VolumeFractionsSyncTest} asserts that automatically.
 */
final class VolumeFractions {

    private VolumeFractions() {
    }

    static double fraction(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.CORNER_SLOPE.get()) {
            return 1.0 / 3.0;
        }
        if (block == ModBlocks.INNER_CORNER_SLOPE.get()) {
            return 2.0 / 3.0;
        }
        if (block == ModBlocks.CORNER_SLOPE_LAYER.get()) {
            return LayerVolumes.cornerLayer(layers(state));
        }
        if (block == ModBlocks.INNER_CORNER_SLOPE_LAYER.get()) {
            return LayerVolumes.innerCornerLayer(layers(state));
        }
        if (block == ModBlocks.ADV_SLOPE_LAYER.get()) {
            return LayerVolumes.advSlopeLayer(layers(state));
        }
        return 1.0;
    }

    private static int layers(BlockState state) {
        return state.getValue(BlockStateProperties.LAYERS);
    }
}
