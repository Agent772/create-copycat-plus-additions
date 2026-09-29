package com.agent772.copycatplusadditions.blocks;

import java.util.Optional;

import com.agent772.copycatplusadditions.SlopeCTFamily;
import com.copycatsplus.copycats.foundation.copycat.ICustomCTBlocking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets our full corner slopes share connected textures with each other and with
 * upstream {@code copycat_slope}s (issue #61). Implemented once here and mixed
 * into both {@link CopycatCornerSlopeBlock} and {@link CopycatInnerCornerSlopeBlock}
 * so the logic — and the {@link SlopeCTFamily} membership rule — lives in one place.
 *
 * <p>Copycats+ patches Create's CT blocking via {@code ConnectedTextureBehaviourMixin},
 * which consults {@link ICustomCTBlocking} in two passes:
 * <ol>
 *   <li>the block being rendered answers {@link #isCTBlocked} for the neighbour it
 *       is connecting toward;</li>
 *   <li>if that returns empty, the block sitting in front answers
 *       {@link #blockCTTowards} for the block trying to connect to it.</li>
 * </ol>
 * Implementing both makes a corner ↔ slope connection resolve from whichever side
 * owns an {@link ICustomCTBlocking} block, so no mixin into upstream is needed.
 *
 * <p>Both methods first check that <i>this</i> block's own state is in the family.
 * The layer subclasses inherit this interface, but a layer's state is not in the
 * family, so they fall through to {@code Optional.empty()} (the default blocking
 * behaviour) — layers never unblock, matching upstream's {@code copycat_slope_layer}.
 *
 * <p>Connections to a real block of the material are separate from the family and
 * apply to layers too: see {@link FlushMaterialCT}, whose per-face check
 * {@link #isCTBlocked} runs first.
 */
public interface SlopeCTBlocking extends FlushMaterialCT {

    /**
     * Answers for the block being rendered ({@code state} at {@code pos}) whether
     * its connected texture toward {@code otherPos} is blocked. Unblocked
     * ({@code Optional.of(false)}) when the block at {@code blockingPos} is a
     * family member; otherwise defer to the default logic ({@code Optional.empty()}).
     */
    @Override
    default Optional<Boolean> isCTBlocked(BlockAndTintGetter reader, BlockState state, BlockPos pos,
                                          BlockPos otherPos, BlockPos blockingPos, Direction face) {
        Optional<Boolean> material = FlushMaterialCT.blockedTowardMaterial(reader, state, pos, otherPos, face);
        if (material.isPresent()) {
            return material;
        }
        if (!SlopeCTFamily.isSlopeFamily(state)) {
            return Optional.empty();
        }
        return SlopeCTFamily.isSlopeFamily(reader.getBlockState(blockingPos))
            ? Optional.of(false)
            : Optional.empty();
    }

    /**
     * Answers for the block sitting in front ({@code state} at {@code blockingPos})
     * whether it blocks CT from the block at {@code pos}. Unblocked when that block
     * is a family member; otherwise defer to the default logic. This is the mirror
     * of {@link #isCTBlocked} and is what lets an upstream slope connect toward one
     * of our corners.
     */
    @Override
    default Optional<Boolean> blockCTTowards(BlockAndTintGetter reader, BlockState state, BlockPos blockingPos,
                                             BlockPos pos, BlockPos otherPos, Direction face) {
        if (!SlopeCTFamily.isSlopeFamily(state)) {
            return Optional.empty();
        }
        return SlopeCTFamily.isSlopeFamily(reader.getBlockState(pos))
            ? Optional.of(false)
            : Optional.empty();
    }
}
