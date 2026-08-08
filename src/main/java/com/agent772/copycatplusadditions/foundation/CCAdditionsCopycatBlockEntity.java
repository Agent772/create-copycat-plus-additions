package com.agent772.copycatplusadditions.foundation;

import com.agent772.copycatplusadditions.compat.sable.SableBeyondCompat;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A {@link CCCopycatBlockEntity} that notifies the Sable Beyond dynamic-mass
 * integration whenever the mimicked material or the block's layer count changes
 * (issue #27, Stage 2).
 *
 * <p>Two hooks cover every mutation:
 * <ul>
 *   <li>{@link #setMaterialInternal} — the funnel through which Copycats+ routes
 *       every material change (apply, cycle, wrench-clear, BE merge, NBT read,
 *       contraption disassembly).</li>
 *   <li>{@link #setBlockState} — fired when the block state changes but the BE
 *       survives, i.e. stacking/un-stacking layers. A pushed override supersedes
 *       Sable's static per-state value, so it must be recomputed for the new
 *       layer's volume fraction while a material is present.</li>
 *   <li>{@link #onLoad} — re-pushes on chunk load. Material read during world load
 *       happens in {@code read} while {@code level} is still {@code null}, so the
 *       change hooks push nothing then; this closes the gap for copycats skinned
 *       before Sable Beyond was installed / dynamic mass was enabled, and
 *       self-heals if Sable Beyond's SavedData is ever lost. Gated on
 *       {@code hasCustomMaterial()} so unskinned copycats don't churn clears.</li>
 * </ul>
 *
 * <p>All hooks are guarded server-side: {@code setMaterialInternal} is also
 * called from the constructor (level still {@code null}) and on the client
 * ({@code read} with a client packet), and Sable Beyond's API is server-only.
 */
public class CCAdditionsCopycatBlockEntity extends CCCopycatBlockEntity {

    public CCAdditionsCopycatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void setMaterialInternal(BlockState material) {
        super.setMaterialInternal(material);
        notifySable();
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        notifySable();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (hasCustomMaterial()) {
            notifySable();
        }
    }

    private void notifySable() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        SableBeyondCompat.onCopycatChanged(level, getBlockPos(), getBlockState(),
            getMaterial(), hasCustomMaterial());
    }
}
