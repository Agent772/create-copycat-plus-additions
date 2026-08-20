package com.agent772.copycatplusadditions.blocks;

import com.agent772.copycatplusadditions.CCAdditionsShapes;
import com.agent772.copycatplusadditions.CornerWallPlacement;
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

    /**
     * {@code true} when the valley wedge is mounted flat against a vertical (wall)
     * face. Mirrors {@code CopycatCornerSlopeBlock#IN_WALL}: FACING is the wall
     * normal, HALF chooses the narrow edge up/down, and {@link #WALL_FLIPPED} chooses
     * which horizontal side the notch wraps toward.
     */
    public static final BooleanProperty IN_WALL = BooleanProperty.create("in_wall");

    /**
     * Only meaningful when {@link #IN_WALL} is set: the extra apex-side degree of
     * freedom a corner wedge has on a wall (see
     * {@code CopycatCornerSlopeBlock#WALL_FLIPPED}). Derived from the horizontal
     * cursor offset at placement.
     */
    public static final BooleanProperty WALL_FLIPPED = BooleanProperty.create("wall_flipped");

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
            .setValue(ROOF_ROTATED, false)
            .setValue(IN_WALL, false)
            .setValue(WALL_FLIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ROOF_ROTATED, IN_WALL, WALL_FLIPPED);
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
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal()) {
            // Wall click: mount the valley wedge flat against the clicked face. FACING is the
            // wall normal; HALF and WALL_FLIPPED come from the clicked quadrant. Unlike every
            // other wedge, the inner corner's distinctive corner is its NOTCH (the low cut),
            // which is the complement of the outer corner's apex. To stay consistent with the
            // rest of the family — the HIGH corner lands where you point — the notch must go to
            // the OPPOSITE quadrant, so the solid high corner diagonally across from it sits
            // under the cursor. Toggling WALL_FLIPPED is exactly that 180-degree in-face spin
            // (it moves the notch to the diagonally-opposite quadrant), with HALF unchanged.
            CornerWallPlacement.WallPlacement wall = CornerWallPlacement.wallPlacement(context);
            return placement
                .setValue(FACING, clickedFace)
                .setValue(HALF, wall.half())
                .setValue(IN_WALL, true)
                .setValue(WALL_FLIPPED, !wall.flipped());
        }
        Direction facing = context.getHorizontalDirection();
        Half half = CornerWallPlacement.pickHalf(context);
        return placement
            .setValue(FACING, facing)
            .setValue(HALF, half)
            .setValue(IN_WALL, false)
            .setValue(WALL_FLIPPED, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CCAdditionsShapes.innerCornerSlope(state.getValue(FACING), state.getValue(HALF),
            state.getValue(IN_WALL), state.getValue(WALL_FLIPPED));
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        // See CopycatCornerSlopeBlock#hidesNeighborFace: a wall corner's tipped geometry
        // no longer matches the floor face-culling assumptions, so disable face hiding.
        if (state.getValue(IN_WALL)) {
            return false;
        }
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
        BlockState transformed = BlockUtils.transformStepLikeHorizontal(state, transform, defaultBlockState());
        if (!state.getValue(IN_WALL)) {
            return transformed;
        }
        // Defensive clamp mirroring CopycatCornerSlopeBlock#transform: a wall corner has
        // no meaning with a vertical FACING, so coerce back to a floor corner.
        if (transformed.getValue(FACING).getAxis().isVertical()) {
            return transformed.setValue(IN_WALL, false);
        }
        return transformed;
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
