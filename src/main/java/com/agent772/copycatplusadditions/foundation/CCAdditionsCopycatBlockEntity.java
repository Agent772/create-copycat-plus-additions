package com.agent772.copycatplusadditions.foundation;

import com.agent772.copycatplusadditions.compat.sable.SableBeyondCompat;
import com.agent772.copycatplusadditions.compat.sable.SableClientCompat;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.copycatsplus.copycats.utility.BlockEntityUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
 *       {@code hasCustomMaterial()} so unskinned copycats don't churn clears.
 *       {@link #onLoad} also drives the client-side Sable sub-level re-mesh for
 *       issue #30 (see {@link SableClientCompat}); that path is ungated because
 *       unskinned copycats are invisible on join too.</li>
 *   <li>{@link #onDataPacket} — client-only, forces the render re-mesh that
 *       Copycats+ skips for unskinned copycats after a Create contraption is
 *       disassembled (issue #38). See that method for the full explanation.</li>
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
        // Issue #30: on the client, re-mesh the owning Sable sub-level section (see
        // SableClientHandler). No-op off the client, without Sable, or outside a sub-level.
        SableClientCompat.onCopycatLoadedClient(getLevel(), getBlockPos());
    }

    /**
     * Repairs the invisible-copycat render bug after a Create contraption is disassembled
     * (issue #38), for copycats that have no mimic material applied.
     *
     * <p>Every block in this addon uses a {@code minecraft:block/air} blockstate; the real
     * geometry is emitted at render time by {@code CopycatModelCore} from the block entity's
     * NeoForge {@code ModelData}. When Create places the block back and the client rebuilds the
     * section before the block entity's {@code ModelData} is published, the model falls through
     * to the wrapped air model and emits zero quads → the copycat is invisible (its outline still
     * draws, since the {@code VoxelShape} comes from the blockstate alone).
     *
     * <p>Copycats+ heals this in {@code ICopycatBlockEntity.read} — but only when the synced
     * material actually changed ({@code prevMaterial != getMaterial()} → {@code redraw()}). An
     * unskinned copycat syncs the same interned {@code COPYCAT_BASE} default state the freshly
     * created client block entity already holds, so the condition is {@code false} and the redraw
     * never fires; the model-data-less first mesh is never repaired. Skinned copycats self-heal
     * because their material differs. That is why only copycats without a mimic block are affected.
     *
     * <p>We complement that check: after the client data sync, if the material did <em>not</em>
     * change we issue the redraw upstream skipped. {@code onDataPacket} is only reached from
     * {@code ClientPacketListener.handleBlockEntityData}, so it never fires on chunk load or in
     * Create/Ponder virtual levels — no level guard is needed and no redundant rebuilds are forced
     * on world join (unlike overriding {@code read}). The complementary {@code ==} guard means
     * skinned copycats, which already redrew via {@code read}, get no extra redraw here.
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        BlockState prevMaterial = getMaterial();
        super.onDataPacket(net, pkt, registries);
        if (prevMaterial == getMaterial()) {
            BlockEntityUtils.redraw(this);
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
