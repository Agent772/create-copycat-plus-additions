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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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
        // incrementing LAYERS instead of replacing it. canBeReplaced has already accepted
        // the click; here we only need to honour the LAYERS cap and preserve the existing
        // FACING/HALF rather than overwriting them from the player's current look direction.
        BlockPos pos = context.getClickedPos();
        BlockState existing = context.getLevel().getBlockState(pos);
        if (existing.is(this) && existing.getValue(LAYERS) < 8) {
            return existing.cycle(LAYERS);
        }
        return placement.setValue(LAYERS, 1);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        // Any click with the matching layer item grows the stack while it is below the
        // cap — the clicked face is irrelevant. Face gating used to decide increment vs.
        // place-adjacent, but the accepted face shifted with the wedge each layer, so the
        // "sweet spot" moved between clicks (issue #45). Once the block is full (LAYERS==8)
        // this returns false and a new block is placed on the clicked side as before.
        // Sneak-placing opts out of stacking entirely: it behaves like a full block, so a
        // new block is placed on the clicked side even below the cap.
        if (context.isSecondaryUseActive()) {
            return false;
        }
        if (!context.getItemInHand().is(this.asItem())) {
            return false;
        }
        return state.getValue(LAYERS) < 8;
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
        // While the player holds this layer's item, expand the pick/outline shape to the
        // full cell (scaffolding-style) so aiming anywhere on the block targets THIS block
        // and adds a layer, instead of the ray slipping past the thin wedge to a neighbour
        // (issue #45). Everyone else still sees the diagonal wedge outline. Collision stays
        // the wedge via getCollisionShape below.
        if (context.isHoldingItem(this.asItem())) {
            return Shapes.block();
        }
        return wedgeShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return wedgeShape(state);
    }

    private VoxelShape wedgeShape(BlockState state) {
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
