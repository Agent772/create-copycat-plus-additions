package com.agent772.copycatplusadditions.mixin;

import com.agent772.copycatplusadditions.blocks.FlushMaterialCT;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.content.copycat.vertical_slope.CopycatVerticalSlopeBlock;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives the upstream slope blocks the same flush-face CT toward a real block of
 * their material as our corners (see {@link FlushMaterialCT}), so a mixed roof
 * behaves the same whichever blocks it is built from. Implementing the interface
 * supplies its {@code canConnectTexturesToward} and
 * {@code isIgnoredConnectivitySide} defaults, which none of these classes declare.
 * The slope layer covers this mod's {@code adv_slope_layer}, which extends it.
 *
 * <p>{@code isCTBlocked} comes from the interface only on the slope layer. The
 * other two declare their own, so {@link UpstreamSlopeCTBlockedMixin} injects the
 * per-face check there.
 */
@Mixin({ CopycatSlopeBlock.class, CopycatSlopeLayerBlock.class, CopycatVerticalSlopeBlock.class })
public abstract class UpstreamSlopeFlushCTMixin implements FlushMaterialCT {
}
