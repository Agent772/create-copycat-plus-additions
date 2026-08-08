package com.agent772.copycatplusadditions.compat.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Soft-dependency gateway to <a href="https://github.com/YassiGame/SableBeyond">Sable
 * Beyond</a>'s dynamic-mass API (issue #27, Stage 2).
 *
 * <p>Every public signature here uses only vanilla types, and the sole reference
 * to a Sable Beyond type lives in {@link SableBeyondHandler}. That class is only
 * touched after the {@link #LOADED} guard passes, so its
 * {@code me.yassigame.sable_beyond.*} imports are never linked when the mod is
 * absent — a player without Sable Beyond can never hit a {@code NoClassDefFoundError}.
 */
public final class SableBeyondCompat {

    private static final boolean LOADED = ModList.get().isLoaded("sable_beyond");

    private SableBeyondCompat() {
    }

    /**
     * Push (or clear) the per-position mass override for a copycat whose material
     * or layer count just changed. No-op when Sable Beyond is absent or off-thread
     * client-side. Callers must invoke this server-side only.
     *
     * @param level             the copycat's level
     * @param pos               the copycat's position
     * @param blockState        the copycat's current block state (drives the volume fraction)
     * @param material          the mimicked material's block state
     * @param hasCustomMaterial whether a material is actually applied; {@code false} clears the override
     */
    public static void onCopycatChanged(Level level, BlockPos pos, BlockState blockState,
                                        BlockState material, boolean hasCustomMaterial) {
        if (!LOADED || level == null || level.isClientSide) {
            return;
        }
        SableBeyondHandler.update(level, pos, blockState, material, hasCustomMaterial);
    }
}
