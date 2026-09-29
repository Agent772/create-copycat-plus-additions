package com.agent772.copycatplusadditions.blocks;

import java.util.Optional;

import javax.annotation.Nullable;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICustomCTBlocking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a slope-shaped copycat share connected textures with a neighbouring
 * <i>real</i> block of its own material, on the faces where the two sit flush.
 *
 * <p>Copycats+ only connects a copycat to a non-copycat block when the two faces
 * <i>between</i> them match ({@code ICopycatBlock.checkConnection}). A slope's
 * triangular side never matches a full cube, so its back wall stayed framed next
 * to the very block it mimics, even though both faces are full and coplanar. This
 * rule is face-aware instead: a face connects when it is a full square on the
 * copycat and on the neighbour, and the neighbour lies in that face's plane. Full
 * walls and bottoms connect. Sloped roofs and triangular sides keep their border,
 * so the texture never runs from a roof into the neighbour's wall.
 *
 * <p>Create consults three hooks, and all three are needed:
 * <ol>
 *   <li>{@link #canConnectTexturesToward} filters which neighbours the material's
 *       CT model can see at all. It has no face, so it admits every material
 *       neighbour;</li>
 *   <li>{@link #isCTBlocked} then rejects, per face, the connections this block
 *       draws toward a material neighbour that is not flush (unless upstream's own
 *       rule already connected them);</li>
 *   <li>{@link #isIgnoredConnectivitySide} answers the neighbour's own CT: it
 *       decides, per face, whether this block shows its material to the real block
 *       rendering next to it.</li>
 * </ol>
 *
 * <p>Our corners get this through {@link SlopeCTBlocking}. Upstream slopes get it
 * through {@code UpstreamSlopeFlushCTMixin} and {@code UpstreamSlopeCTBlockedMixin}.
 */
public interface FlushMaterialCT extends ICopycatBlock, ICustomCTBlocking {

    @Override
    default boolean canConnectTexturesToward(BlockAndTintGetter reader, BlockPos fromPos, BlockPos toPos,
                                             BlockState fromState) {
        return isMaterialBlock(reader, fromPos, toPos)
            || ICopycatBlock.super.canConnectTexturesToward(reader, fromPos, toPos, fromState);
    }

    @Override
    default boolean isIgnoredConnectivitySide(BlockAndTintGetter reader, BlockState fromState, Direction face,
                                              BlockPos fromPos, @Nullable BlockPos toPos,
                                              @Nullable BlockState toState) {
        if (toPos != null && isCTEnabled(fromState, reader, fromPos)
            && connectsFlush(reader, fromState, fromPos, toPos, face)) {
            return false;
        }
        return ICopycatBlock.super.isIgnoredConnectivitySide(reader, fromState, face, fromPos, toPos, toState);
    }

    @Override
    default Optional<Boolean> isCTBlocked(BlockAndTintGetter reader, BlockState state, BlockPos pos,
                                          BlockPos connectingPos, BlockPos blockingPos, Direction face) {
        return blockedTowardMaterial(reader, state, pos, connectingPos, face);
    }

    /**
     * The {@link #isCTBlocked} answer for a material neighbour: blocked when the
     * faces are not flush and upstream's own rule would not have connected them
     * either, otherwise empty so the usual blocking rules (a block covering the
     * neighbour's face) still apply. Empty for any other neighbour.
     *
     * <p>Upstream's rule (the faces between the two blocks match) connects every
     * face, and stays in force: next to an inner corner's full ridge wall the roof's
     * ridge edge sits flush with the neighbour's top, so the roof should connect
     * there. This only takes back what {@link #canConnectTexturesToward} newly lets
     * through.
     */
    static Optional<Boolean> blockedTowardMaterial(BlockAndTintGetter reader, BlockState state, BlockPos pos,
                                                   BlockPos connectingPos, Direction face) {
        if (!inPlane(pos, connectingPos, face) || !isMaterialBlock(reader, pos, connectingPos)
            || connectsFlush(reader, state, pos, connectingPos, face)) {
            return Optional.empty();
        }
        boolean upstreamConnects = state.getBlock() instanceof ICopycatBlock copycat
            && copycat.checkConnection(reader, pos, connectingPos, state);
        return upstreamConnects ? Optional.empty() : Optional.of(true);
    }

    /**
     * Whether {@code otherPos} holds a real (non-copycat) block of the material the
     * copycat at {@code copycatPos} mimics.
     */
    static boolean isMaterialBlock(BlockAndTintGetter reader, BlockPos copycatPos, BlockPos otherPos) {
        if (otherPos.equals(copycatPos)) {
            return false;
        }
        BlockState other = reader.getBlockState(otherPos);
        if (other.isAir() || other.getBlock() instanceof ICopycatBlock) {
            return false;
        }
        BlockState material = ICopycatBlock.getMaterial(reader, copycatPos);
        return !material.isAir() && other.is(material.getBlock());
    }

    /**
     * Whether {@code face} of the copycat and the same face of the material block
     * at {@code otherPos} are both full squares in one plane.
     */
    static boolean connectsFlush(BlockAndTintGetter reader, BlockState copycatState, BlockPos copycatPos,
                                 BlockPos otherPos, @Nullable Direction face) {
        if (face == null || !inPlane(copycatPos, otherPos, face) || !isMaterialBlock(reader, copycatPos, otherPos)) {
            return false;
        }
        BlockState other = reader.getBlockState(otherPos);
        return Block.isFaceFull(copycatState.getShape(reader, copycatPos), face)
            && Block.isFaceFull(other.getShape(reader, otherPos), face);
    }

    /** Whether {@code otherPos} is a different block in the plane of {@code face} through {@code pos}. */
    private static boolean inPlane(BlockPos pos, BlockPos otherPos, Direction face) {
        return !otherPos.equals(pos)
            && face.getAxis().choose(pos.getX(), pos.getY(), pos.getZ())
                == face.getAxis().choose(otherPos.getX(), otherPos.getY(), otherPos.getZ());
    }
}
