package com.agent772.copycatplusadditions.blocks;

import com.agent772.copycatplusadditions.CCAdditionsShapes;
import com.agent772.copycatplusadditions.config.ServerConfig;
import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.CCWaterloggedCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.utility.BlockUtils;
import com.simibubi.create.content.contraptions.StructureTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Solid inner-corner slope copycat: a full block with one corner wedge cut out
 * diagonally. The complement of {@code CopycatCornerSlopeBlock} from issue #11 — the
 * outer wedge fills one triangular quadrant, this inner block fills the remaining
 * three quadrants, and the two together tile a full block.
 */
public class CopycatInnerCornerSlopeBlock extends CCWaterloggedCopycatBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    /**
     * When {@code true}, both wings' eave-aligned roof projections are advanced a
     * quarter turn so directional grain (e.g. planks) runs up the slope instead of
     * along the eave. Mirrors {@code CopycatCornerSlopeBlock#ROOF_ROTATED}: the roof
     * projection is 180-degree symmetric, so a boolean covers every meaningful
     * orientation, and the flag describes the projection axis rather than a world
     * direction, so it is invariant under block rotation and mirroring.
     */
    public static final BooleanProperty ROOF_ROTATED = BooleanProperty.create("roof_rotated");

    public CopycatInnerCornerSlopeBlock() {
        this(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0F, 6.0F)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion()
            .forceSolidOn());
    }

    protected CopycatInnerCornerSlopeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(HALF, Half.BOTTOM)
            .setValue(ROOF_ROTATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ROOF_ROTATED);
        super.createBlockStateDefinition(builder);
    }

    /**
     * Adds the same "rotate the roof texture" gesture as
     * {@code CopycatCornerSlopeBlock}: the upstream copycat chain runs first via
     * {@code super}, and we only toggle {@link #ROOF_ROTATED} when it declined —
     * i.e. the player right-clicked with the block's <i>current</i> material and
     * Copycats+ had no material property to cycle (uniform materials such as
     * planks). Every other path is handled inside {@code super}.
     */
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult upstream = super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (upstream.consumesAction()) {
            return upstream;
        }
        if (!ServerConfig.enableExtraRotation() || player == null || !player.mayBuild()) {
            return upstream;
        }
        BlockState material = getAcceptedBlockState(level, pos, stack, hitResult.getDirection());
        if (material == null) {
            return upstream;
        }
        ICopycatBlockEntity copycatBE = getCopycatBlockEntity(level, pos);
        if (copycatBE == null || !copycatBE.getMaterial().is(material.getBlock())) {
            return upstream;
        }
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, state.cycle(ROOF_ROTATED));
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.75F, 0.95F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placement = super.getStateForPlacement(context);
        if (placement == null) {
            return null;
        }
        Direction facing = context.getHorizontalDirection();
        Half half = pickHalf(context);
        return placement.setValue(FACING, facing).setValue(HALF, half);
    }

    /**
     * Mirrors {@code CopycatSlopeBlock#getStateForPlacement}'s HALF logic so the inner
     * corner places consistently with the existing slope family: floor click → BOTTOM,
     * ceiling click → TOP, side click → cursor-Y midpoint.
     */
    protected static Half pickHalf(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.DOWN) {
            return Half.TOP;
        }
        if (clickedFace == Direction.UP) {
            return Half.BOTTOM;
        }
        double yOffset = context.getClickLocation().y - context.getClickedPos().getY();
        return yOffset > 0.5D ? Half.TOP : Half.BOTTOM;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CCAdditionsShapes.innerCornerSlope(state.getValue(FACING), state.getValue(HALF));
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        return ICopycatBlock.hidesNeighborFace(level, pos, state, neighborState, dir);
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        // Use the same horizontal step-like transform as CopycatSlopeBlock: 90-degree
        // rotations around non-Y axes are pinned to no-op (upstream's choice), while
        // Y-axis rotation and mirrors rotate/mirror FACING and flip HALF as needed.
        return BlockUtils.transformStepLikeHorizontal(state, transform, defaultBlockState());
    }

    @Override
    public Class<CCCopycatBlockEntity> getBlockEntityClass() {
        return CCCopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CCCopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.INNER_CORNER_SLOPE.get();
    }
}
