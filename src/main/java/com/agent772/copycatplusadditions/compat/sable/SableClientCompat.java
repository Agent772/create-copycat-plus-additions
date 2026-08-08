package com.agent772.copycatplusadditions.compat.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Soft-dependency gateway to <a href="https://github.com/ryanhcode/sable">Sable</a>'s
 * client renderer, used to heal issue #30: on world join, this mod's copycats sitting on a
 * Sable sub-level render nothing (outline only) until any block on the sub-level is updated.
 * See {@link SableClientHandler} for the root cause and the re-mesh mechanism.
 *
 * <p>Every public signature here uses only vanilla types, and the sole reference to a Sable
 * type lives in {@link SableClientHandler}. That class is only touched after the
 * {@link #LOADED} guard passes, so its {@code dev.ryanhcode.sable.*} imports are never linked
 * when Sable is absent — a player without Sable can never hit a {@code NoClassDefFoundError}.
 * This heals only <em>our</em> blocks; base Copycats+/Create blocks on the same sub-level
 * remain invisible until Sable fixes the underlying mesh-timing bug upstream.
 */
public final class SableClientCompat {

    private static final boolean LOADED = ModList.get().isLoaded("sable");

    private SableClientCompat() {
    }

    /**
     * Queue a re-mesh of the Sable sub-level section owning a freshly-loaded copycat (see
     * {@link SableClientHandler}). No-op when Sable is absent, off the client, or the block is
     * not part of any sub-level. Fires for skinned and unskinned copycats alike, since both
     * depend on the block entity's model data at mesh time.
     *
     * @param level the copycat's level
     * @param pos   the copycat's position
     */
    public static void onCopycatLoadedClient(Level level, BlockPos pos) {
        if (!LOADED || level == null || !level.isClientSide) {
            return;
        }
        SableClientHandler.markSubLevelSectionDirty(level, pos);
    }
}
