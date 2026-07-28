package com.agent772.copycatplusadditions.blocks;

import com.agent772.copycatplusadditions.CCAdditionsShapes;
import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.CCWaterloggedCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.utility.BlockUtils;
import com.simibubi.create.content.contraptions.StructureTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outer corner slope copycat: a wedge that fills one triangular quadrant of a
 * full block. The complement of {@link CopycatInnerCornerSlopeBlock} -- the two
 * together tile a full 1x1x1 block when placed with the same FACING and HALF.
 *
 * FACING indicates the corner direction of the apex (the single full-height
 * corner). For FACING=SOUTH the apex is at the SE world corner of the block
 * space (matching the notch of the inner corner slope at FACING=SOUTH), so the
 * two blocks interlock seamlessly.
 */
public class CopycatCornerSlopeBlock extends CCWaterloggedCopycatBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    public CopycatCornerSlopeBlock() {
        this(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0F, 6.0F)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion()
            .forceSolidOn());
    }

    protected CopycatCornerSlopeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(HALF, Half.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placement = super.getStateForPlacement(context);
        if (placement == null) {
            return null;
        }
        Direction facing = apexFacingFromViewAngle(context.getRotation());
        Half half = CopycatInnerCornerSlopeBlock.pickHalf(context);
        return placement.setValue(FACING, facing).setValue(HALF, half);
    }

    /**
     * Picks FACING so the apex lands at the block corner the player is looking
     * toward. The exact yaw decides between the two candidate corners: looking
     * east-and-slightly-north puts the apex at NE, east-and-slightly-south at SE.
     * Mapping is the inverse of {@code CCAdditionsShapes#apexCorner}.
     */
    private static Direction apexFacingFromViewAngle(float yaw) {
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));
        if (lookX > 0) {
            return lookZ > 0 ? Direction.WEST : Direction.SOUTH; // apex SE : NE
        }
        return lookZ > 0 ? Direction.NORTH : Direction.EAST;     // apex SW : NW
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CCAdditionsShapes.cornerSlope(state.getValue(FACING), state.getValue(HALF));
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
        return BlockUtils.transformStepLikeHorizontal(state, transform, defaultBlockState());
    }

    @Override
    public Class<CCCopycatBlockEntity> getBlockEntityClass() {
        return CCCopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CCCopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CORNER_SLOPE.get();
    }
}
