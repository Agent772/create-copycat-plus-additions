package com.agent772.copycatplusadditions.blocks;

import java.util.List;

import com.agent772.copycatplusadditions.CCAdditionsShapes;
import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stackable 1–8 layer variant of {@link CopycatInnerCornerSlopeBlock}. Stacking follows
 * the same two-phase profile as the straight slope layer (issue #43): layers 1-4 raise the
 * three corners 4 voxels per layer (bottom-anchored, reaching the full inner corner at
 * layer 4), then layers 5-8 raise the notch to fill out to a full block at layer 8.
 *
 * <p>Layer stacking: placing another inner-corner-slope-layer item against an existing
 * block of the same kind cycles the {@link #LAYERS} value instead of placing a new block,
 * matching the upstream {@code copycats:copycat_slope_layer} behaviour.
 */
public class CopycatInnerCornerSlopeLayerBlock extends CopycatInnerCornerSlopeBlock {

    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    public CopycatInnerCornerSlopeLayerBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0F, 6.0F)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion()
            .forceSolidOn());
        registerDefaultState(defaultBlockState().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LAYERS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placement = super.getStateForPlacement(context);
        if (placement == null) {
            return null;
        }
        // Stack onto an existing matching block (same block + same FACING/HALF) by
        // incrementing LAYERS instead of replacing it. canBeReplaced has already gated
        // the click to compatible faces; here we only need to honour the LAYERS cap
        // and preserve the existing FACING/HALF rather than overwriting them from the
        // player's current look direction.
        BlockPos pos = context.getClickedPos();
        BlockState existing = context.getLevel().getBlockState(pos);
        if (existing.is(this) && existing.getValue(LAYERS) < 8) {
            return existing.cycle(LAYERS);
        }
        return placement.setValue(LAYERS, 1);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        ItemStack itemInHand = context.getItemInHand();
        if (!itemInHand.is(this.asItem())) {
            return false;
        }
        if (state.getValue(LAYERS) == 8) {
            return false;
        }
        // Mirror CopycatSlopeLayerBlock's replacement gating: the player can extend a
        // layer by clicking the slope's "back" (FACING opposite) or its flat top/bottom
        // face on the matching half. No new logic is needed for the inner corner here
        // because the layer growth direction is purely along the FACING/HALF axes.
        Half half = state.getValue(HALF);
        Direction facing = state.getValue(FACING);
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == facing.getOpposite()) {
            return true;
        }
        if (half == Half.TOP && clickedFace == Direction.DOWN) {
            return true;
        }
        if (half == Half.BOTTOM && clickedFace == Direction.UP) {
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        // Mirror CopycatSlopeLayerBlock.onSneakWrenched: peel off one layer at a time
        // and only fall through to the parent (which breaks the block and returns the
        // mimic material) once the final layer is being removed. Survival players get
        // a single layer item back per wrench use; creative gets none, same as upstream.
        if (state.getValue(LAYERS) <= 1) {
            return super.onSneakWrenched(state, context);
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level instanceof ServerLevel server) {
            if (player != null && !player.isCreative()) {
                // Drop ONE layer's worth — Block.getDrops on a LAYERS=1 copy resolves
                // to the single-layer loot pool, which is exactly one item.
                BlockState singleLayer = state.setValue(LAYERS, 1);
                List<ItemStack> drops = Block.getDrops(singleLayer, server, pos,
                    level.getBlockEntity(pos), player, context.getItemInHand());
                for (ItemStack drop : drops) {
                    player.getInventory().placeItemBackInInventory(drop);
                }
            }
            BlockPos above = pos.relative(Direction.UP);
            BlockState reduced = state.setValue(LAYERS, state.getValue(LAYERS) - 1);
            level.setBlockAndUpdate(pos,
                reduced.updateShape(Direction.UP, level.getBlockState(above), level, pos, above));
            IWrenchable.playRemoveSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CCAdditionsShapes.innerCornerSlopeLayer(
            state.getValue(FACING), state.getValue(HALF), state.getValue(LAYERS));
    }

    @Override
    public Class<CCCopycatBlockEntity> getBlockEntityClass() {
        return CCCopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CCCopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.INNER_CORNER_SLOPE_LAYER.get();
    }
}
