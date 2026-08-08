package com.agent772.copycatplusadditions.compat.sable;

import com.mojang.logging.LogUtils;

import me.yassigame.sable_beyond.api.mass.DynamicMass;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;

/**
 * The only class that imports Sable Beyond. Loaded lazily by
 * {@link SableBeyondCompat} after the mod-present guard, so its
 * {@code DynamicMass} reference is never linked when Sable Beyond is absent.
 *
 * <p>Implements the agreed formula:
 * <pre>mass = DynamicMass.getDefaultBlockMass(level, pos, material) &times; volumeFraction</pre>
 * so a copycat's Sable mass reflects the material it mimics (obsidian is heavy,
 * wool is light), scaled by how much of the block its geometry fills. Volume /
 * buoyancy is left to the Stage 1 static datapack — {@code DynamicMass} overrides
 * mass only, and the displaced volume of a shape does not depend on its material.
 */
final class SableBeyondHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * {@code DynamicMass.setBlockMass} throws for a non-positive mass (and Sable
     * rejects a sub-level total that would drop to zero), so a material whose
     * resolved mass is 0 is clamped up to this floor. We deliberately still push a
     * (tiny) override rather than clearing, so a skinned block always reads as
     * distinct from an unskinned one; falling through to the Stage 1 static value
     * would be the other defensible choice.
     */
    private static final double MIN_MASS = 0.001;

    private SableBeyondHandler() {
    }

    static void update(Level level, BlockPos pos, BlockState blockState,
                       BlockState material, boolean hasCustomMaterial) {
        // Dynamic mass is disabled by default in Sable Beyond's config; every call
        // must respect that switch.
        if (!DynamicMass.isEnabled()) {
            return;
        }
        if (!hasCustomMaterial) {
            // Fall back to the Stage 1 static values by removing our override.
            try {
                DynamicMass.clearBlockMass(level, pos);
            } catch (IllegalArgumentException e) {
                LOGGER.debug("Sable Beyond rejected clearBlockMass at {}", pos, e);
            }
            return;
        }
        double materialMass = DynamicMass.getDefaultBlockMass(level, pos, material);
        double mass = Math.max(materialMass * VolumeFractions.fraction(blockState), MIN_MASS);
        try {
            DynamicMass.setBlockMass(level, pos, mass);
        } catch (IllegalArgumentException e) {
            // Defensive: a physics edge case (e.g. a sub-level already at minimal
            // mass) must never crash gameplay. Log so a systematic rejection is
            // diagnosable from the log rather than silently leaving mass wrong.
            LOGGER.debug("Sable Beyond rejected setBlockMass {} at {} (material {})",
                mass, pos, material, e);
        }
    }
}
