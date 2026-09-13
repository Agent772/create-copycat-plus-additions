package com.agent772.copycatplusadditions.mixin;

import com.agent772.copycatplusadditions.schematics.UpstreamSlopeLayerRemap;
import com.simibubi.create.content.schematics.SchematicPrinter;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps the superseded {@code copycats:copycat_slope_layer} for this mod's
 * {@code adv_slope_layer} while a schematic is printed.
 *
 * <p>Every targeted method reads the schematic's block state through
 * {@code blockReader.getBlockState(pos)} and stores it in the first local of type
 * {@link BlockState}, so the remap is applied on that store. The block entity is
 * deliberately left alone: Create copies the schematic's NBT (which carries the
 * mimicked material and the consumed item) onto whatever block entity ends up in
 * the world, and this mod's copycat block entity reads the same format.
 */
@Mixin(SchematicPrinter.class)
public class SchematicPrinterMixin {

    /**
     * Placement path: {@code handleCurrentTarget} hands the state to the
     * schematicannon, {@code shouldPlaceBlock} decides whether the target needs
     * replacing (remapping here also makes an already-printed adv. slope layer
     * compare equal, so it is not placed twice).
     */
    @ModifyVariable(method = { "handleCurrentTarget", "shouldPlaceBlock" }, at = @At("STORE"), ordinal = 0)
    private BlockState copycatplusadditions$remapPlacedState(BlockState state) {
        return UpstreamSlopeLayerRemap.remap(state);
    }

    /**
     * Requirement path: the schematicannon consumes, and the material checklist
     * lists, the item of the state read here. Without the remap both would ask for
     * the upstream slope layer item, which is no longer obtainable.
     */
    @ModifyVariable(method = { "getCurrentRequirement", "markAllBlockRequirements" }, at = @At("STORE"), ordinal = 0)
    private BlockState copycatplusadditions$remapRequiredState(BlockState state) {
        return UpstreamSlopeLayerRemap.remap(state);
    }
}
