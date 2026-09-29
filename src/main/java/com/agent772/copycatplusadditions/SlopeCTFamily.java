package com.agent772.copycatplusadditions;

import com.agent772.copycatplusadditions.blocks.CopycatCornerSlopeBlock;
import com.agent772.copycatplusadditions.blocks.CopycatCornerSlopeLayerBlock;
import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeBlock;
import com.agent772.copycatplusadditions.blocks.CopycatInnerCornerSlopeLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The set of full-height slope wedges whose connected textures are allowed to
 * flow across each other. Membership drives {@link SlopeCTBlocking}: a member
 * block does not block CT toward another member, so a hip roof built from our
 * outer/inner corners and upstream {@code copycat_slope}s reads as one connected
 * surface (issue #61, matching @Agent772's request).
 *
 * <p>Mirrors upstream {@code CopycatSlopeBlock.isCTBlocked}, which unblocks CT
 * only toward another {@code CopycatSlopeBlock}. We widen that set to include our
 * two full corners so the three connect in every direction, then rely on the
 * upstream mixin's two-path dispatch (block-being-rendered, then block-in-front)
 * to make the connection mutual without touching upstream code.
 *
 * <p><b>Layers are deliberately excluded.</b> A layer wedge sits at a different
 * height than a full slope, so letting their tiles connect would expose the
 * height step. Upstream's {@code copycat_slope_layer} does not implement
 * CT-blocking either, so excluding ours keeps parity: layers fall through to the
 * default blocking logic in both directions.
 */
public final class SlopeCTFamily {

    private SlopeCTFamily() {
    }

    /**
     * {@code true} when {@code state} is a full-height slope wedge that shares
     * connected textures with the rest of the family: upstream
     * {@code copycat_slope}, our outer corner, or our inner corner. Layer
     * variants ({@code corner_slope_layer}, {@code inner_corner_slope_layer},
     * upstream {@code copycat_slope_layer}) are excluded.
     */
    public static boolean isSlopeFamily(BlockState state) {
        Block block = state.getBlock();
        // Layers extend the full-corner classes, so reject them before the
        // instanceof checks below would otherwise accept them.
        if (block instanceof CopycatCornerSlopeLayerBlock || block instanceof CopycatInnerCornerSlopeLayerBlock) {
            return false;
        }
        return block instanceof CopycatSlopeBlock
            || block instanceof CopycatCornerSlopeBlock
            || block instanceof CopycatInnerCornerSlopeBlock;
    }
}
