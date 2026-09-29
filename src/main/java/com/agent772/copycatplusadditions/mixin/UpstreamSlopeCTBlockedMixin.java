package com.agent772.copycatplusadditions.mixin;

import java.util.Optional;

import com.agent772.copycatplusadditions.blocks.FlushMaterialCT;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeBlock;
import com.copycatsplus.copycats.content.copycat.vertical_slope.CopycatVerticalSlopeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the per-face half of {@link FlushMaterialCT} ahead of the upstream slopes'
 * own {@code isCTBlocked}. {@link UpstreamSlopeFlushCTMixin} lets their material
 * CT model see a real material neighbour, and without this check a sloped roof
 * would connect straight into that neighbour's wall.
 */
@Mixin({ CopycatSlopeBlock.class, CopycatVerticalSlopeBlock.class })
public class UpstreamSlopeCTBlockedMixin {

    @Inject(method = "isCTBlocked", at = @At("HEAD"), cancellable = true)
    private void copycatplusadditions$blockNonFlushMaterial(BlockAndTintGetter reader, BlockState state, BlockPos pos,
                                                          BlockPos connectingPos, BlockPos blockingPos,
                                                          Direction face,
                                                          CallbackInfoReturnable<Optional<Boolean>> cir) {
        Optional<Boolean> blocked = FlushMaterialCT.blockedTowardMaterial(reader, state, pos, connectingPos, face);
        if (blocked.isPresent()) {
            cir.setReturnValue(blocked);
        }
    }
}
