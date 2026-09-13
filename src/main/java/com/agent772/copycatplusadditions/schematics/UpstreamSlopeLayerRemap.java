package com.agent772.copycatplusadditions.schematics;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import com.agent772.copycatplusadditions.blocks.CopycatVerticalSlopeLayerBlock;
import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Maps the superseded {@code copycats:copycat_slope_layer} onto this mod's
 * {@code adv_slope_layer} when a schematic is printed.
 *
 * <p>Schematics saved before this mod was installed still store the upstream block.
 * Copycats+ stays a dependency, so the upstream block remains registered and
 * {@code MissingMappingsEvent} never fires for it — but it is no longer obtainable
 * (its recipe is disabled and its loot table drops the adv. slope layer), so a
 * schematicannon printing the old id would stall on an item nobody can supply.
 * The remap is applied inside Create's schematic printer; see
 * {@code SchematicPrinterMixin}.
 */
public final class UpstreamSlopeLayerRemap {

    private static Block upstreamSlopeLayer;

    private UpstreamSlopeLayerRemap() {
    }

    public static BlockState remap(BlockState original) {
        if (!original.is(upstreamSlopeLayer())) {
            return original;
        }
        return ModBlocks.ADV_SLOPE_LAYER.get()
            .defaultBlockState()
            .setValue(CopycatSlopeLayerBlock.FACING, original.getValue(CopycatSlopeLayerBlock.FACING))
            .setValue(CopycatSlopeLayerBlock.HALF, original.getValue(CopycatSlopeLayerBlock.HALF))
            .setValue(CopycatSlopeLayerBlock.LAYERS, original.getValue(CopycatSlopeLayerBlock.LAYERS))
            .setValue(BlockStateProperties.WATERLOGGED, original.getValue(BlockStateProperties.WATERLOGGED))
            // Both are set explicitly rather than left to the default state: the wall
            // properties are added by this mod's subclass after Copycats+' constructor
            // has already registered its default, so the default state carries whatever
            // StateDefinition#any() picked for them, not false.
            .setValue(CopycatVerticalSlopeLayerBlock.IN_WALL, false)
            .setValue(CopycatVerticalSlopeLayerBlock.WALL_SIDEWAYS, false);
    }

    // Resolved on first use: the registry is still being filled while this mod is
    // constructed, so the lookup cannot happen in a static initialiser.
    private static Block upstreamSlopeLayer() {
        Block resolved = upstreamSlopeLayer;
        if (resolved == null) {
            resolved = BuiltInRegistries.BLOCK.get(CopycatPlusAdditions.UPSTREAM_SLOPE_LAYER_ID);
            upstreamSlopeLayer = resolved;
        }
        return resolved;
    }
}
