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

    /**
     * When {@code true}, the sloped-roof texture projection is turned a quarter
     * turn so directional grain (e.g. planks) runs up the slope instead of along
     * the eave. Only two states are visually distinct because the top-down roof
     * projection is 180-degree symmetric, so a boolean covers every meaningful
     * orientation. The flag describes the projection axis rather than a world
     * direction, so it is invariant under block rotation and mirroring.
     */
    public static final BooleanProperty ROOF_ROTATED = BooleanProperty.create("roof_rotated");

    /**
     * {@code true} when the corner wedge is mounted flat against a vertical (wall)
     * face rather than sitting on the floor/ceiling. Mirrors
     * {@code CopycatVerticalSlopeLayerBlock#IN_WALL} for the straight slope: when set,
     * {@link #FACING} is the wall normal, {@link #HALF} chooses the narrow edge
     * up/down, and {@link #WALL_FLIPPED} chooses which horizontal side the apex wraps
     * toward.
     */
    public static final BooleanProperty IN_WALL = BooleanProperty.create("in_wall");

    /**
     * Only meaningful when {@link #IN_WALL} is set. A straight slope needs only
     * FACING (wall normal) + HALF (up/down) to fix its 8 wall orientations, but a
     * corner wedge has a two-axis apex, so tipping it onto a wall leaves one extra
     * degree of freedom: which horizontal side of the wall face the apex points to.
     * This flag captures that choice from the horizontal cursor offset at placement,
     * giving the full 16 wall orientations (4 walls x up/down x apex-left/right).
     */
    public static final BooleanProperty WALL_FLIPPED = BooleanProperty.create("wall_flipped");

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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placement = super.getStateForPlacement(context);
        if (placement == null) {
            return null;
        }
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal()) {
            // Wall click: mount the corner flat against the clicked face. FACING is the wall
            // normal (clicked face); HALF and WALL_FLIPPED are derived together so the apex
            // lands in the exact quadrant of the face the player clicked (see wallPlacement).
            CornerWallPlacement.WallPlacement wall = CornerWallPlacement.wallPlacement(context);
            return placement
                .setValue(FACING, clickedFace)
                .setValue(HALF, wall.half())
                .setValue(IN_WALL, true)
                .setValue(WALL_FLIPPED, wall.flipped());
        }
        Direction facing = apexFacingFromViewAngle(context.getRotation());
        Half half = CornerWallPlacement.pickHalf(context);
        return placement
            .setValue(FACING, facing)
            .setValue(HALF, half)
            .setValue(IN_WALL, false)
            .setValue(WALL_FLIPPED, false);
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

    /**
     * Adds a "rotate the roof texture" gesture on top of the upstream copycat
     * interactions. The whole upstream chain runs first via {@code super}; we
     * only act when it declined (returned a non-consuming result). That happens
     * for the exact case this feature targets: the player right-clicked with the
     * block's <i>current</i> material and Copycats+ had no material property to
     * cycle (uniform materials such as planks). In that case we toggle
     * {@link #ROOF_ROTATED} instead. Every other path — applying a new material,
     * cycling a directional material (logs), wrench use, CT toggle — is handled
     * inside {@code super} and returns before we reach the toggle.
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
        // Non-copycat block items map to a material state; the block's own item and
        // anything unacceptable map to null. Filtering on a match with the stored
        // material reproduces the upstream "same material" branch exactly, so the
        // toggle only fires when the upstream cycle had nothing to do.
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CCAdditionsShapes.cornerSlope(state.getValue(FACING), state.getValue(HALF),
            state.getValue(IN_WALL), state.getValue(WALL_FLIPPED));
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        // A wall corner's visible shape has been tipped 90 degrees onto the wall, so
        // the parent shape-based occlusion (which assumes floor geometry from
        // FACING/HALF) would hide/show the wrong neighbour faces. Disable face hiding
        // for wall corners, matching CopycatVerticalSlopeLayerBlock's trade-off.
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
        BlockState transformed = BlockUtils.transformStepLikeHorizontal(state, transform, defaultBlockState());
        if (!state.getValue(IN_WALL)) {
            return transformed;
        }
        // transformStepLikeHorizontal seeds its result from defaultBlockState() (where
        // IN_WALL/WALL_FLIPPED are false) and copies shared properties back via
        // tryCopyProperties. Re-assert the wall booleans from the original state so
        // preservation never depends on that helper's internals -- otherwise a wall
        // corner on a rotating contraption could silently revert to a floor corner.
        transformed = transformed
            .setValue(IN_WALL, true)
            .setValue(WALL_FLIPPED, state.getValue(WALL_FLIPPED));
        // Y-axis rotation carries the wall normal around with the contraption (correct);
        // non-Y 90 degree rotations are pinned to no-op upstream. Defensive clamp: if an
        // exotic transform left FACING vertical there is no valid wall interpretation, so
        // fall back to a floor corner rather than render a wall corner facing nowhere.
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
        return ModBlockEntities.CORNER_SLOPE.get();
    }
}
