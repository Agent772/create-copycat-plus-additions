package com.agent772.copycatplusadditions.blocks;

import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.copycatsplus.copycats.CCShapes;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A copycat slope layer that can additionally be mounted on vertical wall faces.
 *
 * <p>This extends Copycats+' {@link CopycatSlopeLayerBlock} (consumed as a compiled,
 * All-Rights-Reserved dependency) and only adds the {@link #IN_WALL} state property
 * plus the geometry needed to rotate the slope onto a vertical face.
 */
public class CopycatVerticalSlopeLayerBlock extends CopycatSlopeLayerBlock {

    /** {@code true} when the slope is mounted against a vertical (wall) face. */
    public static final BooleanProperty IN_WALL = BooleanProperty.create("in_wall");

    /**
     * Cache of the rotated wall shapes: {@code FACING -> HALF -> LAYERS -> VoxelShape}.
     * Built lazily because {@link CCShapes#SLOPE_LAYER} is only safe to read once the
     * Copycats+ classes have initialised.
     */
    private static final Map<Direction, Map<Half, Map<Integer, VoxelShape>>> WALL_SHAPES =
        new ConcurrentHashMap<>();

    public CopycatVerticalSlopeLayerBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0F, 6.0F)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion()
            .forceSolidOn());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IN_WALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placement = super.getStateForPlacement(context);
        if (placement == null) {
            return null;
        }
        // Stacking another layer onto an existing vertical_slope_layer: the parent's
        // getStateForPlacement already returned the cycled state of that block (which
        // preserves FACING/HALF/IN_WALL/WATERLOGGED). Return it as-is so a wall-placed
        // slope keeps IN_WALL=true when another layer is added.
        if (context.getLevel().getBlockState(context.getClickedPos()).is(this)) {
            return placement;
        }

        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal()) {
            // Floor (UP) or ceiling (DOWN) click — parent's FACING/HALF are already correct.
            return placement.setValue(IN_WALL, false);
        }

        // Wall click. The parent's FACING/HALF here come from the player's horizontal
        // look direction and the cursor y, which has no relation to which wall face was
        // actually clicked — we have to derive them ourselves. This mirrors the design
        // of upstream Copycats+ PR #273 (the vertical-slope-layer reference): the slope
        // FACING points outward from the wall (same direction as the clicked face) and
        // HALF is chosen by cursor Y, putting the narrow edge of the slope toward
        // whichever side of the block the player was aiming at.
        Direction facing = clickedFace;
        double yOffset = context.getClickLocation().y - context.getClickedPos().getY();
        Half half = yOffset > 0.5D ? Half.TOP : Half.BOTTOM;
        return placement
            .setValue(FACING, facing)
            .setValue(HALF, half)
            .setValue(IN_WALL, true);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        // The parent's canBeReplaced only allows stacking when the clicked face is the
        // opposite of FACING (i.e. clicking the "back" of a floor slope). For wall-mounted
        // slopes FACING already points outward from the wall, so the player is hitting
        // the FACING face directly when they try to extend the slope outward — match
        // upstream Copycats+ PR #273 by flipping the comparison for IN_WALL=true.
        ItemStack itemInHand = context.getItemInHand();
        if (!itemInHand.is(this.asItem())) {
            return false;
        }
        if (state.getValue(LAYERS) == 8) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        Half half = state.getValue(HALF);
        boolean inWall = state.getValue(IN_WALL);
        Direction clickedFace = context.getClickedFace();
        if (inWall) {
            if (clickedFace == facing) return true;
        } else {
            if (clickedFace == facing.getOpposite()) return true;
        }
        if (half == Half.TOP && clickedFace == Direction.DOWN) return true;
        if (half == Half.BOTTOM && clickedFace == Direction.UP) return true;
        return false;
    }

    // CCCopycatBlock implements Create's IBE<CCCopycatBlockEntity>; placement calls
    // getBlockEntityType().create(pos, state), which returns null if this block is not
    // in that type's validBlocks set. Copycats+' own copycat BE type is registered with
    // only its own blocks, so we publish our own type — registered with VERTICAL_SLOPE_LAYER
    // in ModBlockEntities — to avoid a null BE (and the NPE that follows) on placement.

    @Override
    public Class<CCCopycatBlockEntity> getBlockEntityClass() {
        return CCCopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CCCopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.VERTICAL_SLOPE_LAYER.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(IN_WALL)) {
            return super.getShape(state, level, pos, context);
        }
        Direction facing = state.getValue(FACING);
        Half half = state.getValue(HALF);
        int layers = state.getValue(LAYERS);
        return WALL_SHAPES
            .computeIfAbsent(facing, f -> new ConcurrentHashMap<>())
            .computeIfAbsent(half, h -> new ConcurrentHashMap<>())
            .computeIfAbsent(layers, l -> buildWallShape(facing, half, layers));
    }

    /**
     * The rotation (in degrees) that tips the floor slope layer onto a vertical wall
     * for the given FACING/HALF. Values are taken verbatim from upstream Copycats+
     * PR #273's {@code CopycatSlopeLayerModelCore} (the reference design for vertical
     * slope layers), so the collision shape here and the rendered geometry in
     * {@code CopycatVerticalSlopeLayerModelCore} stay aligned.
     */
    public static int wallAngle(Direction facing, Half half) {
        return switch (facing) {
            case NORTH -> half == Half.TOP ? 270 : 90;
            case SOUTH -> half == Half.TOP ? 90 : 270;
            case WEST -> half == Half.TOP ? 90 : 270;
            case EAST -> half == Half.TOP ? 270 : 90;
            default -> 0;
        };
    }

    /**
     * Which axis to rotate around when tipping the slope onto a wall. NORTH/SOUTH
     * facings (Z-axis) pivot around X; EAST/WEST facings (X-axis) pivot around Z —
     * i.e. always the axis perpendicular to the wall normal.
     */
    public static boolean wallRotateAroundZ(Direction facing) {
        return facing.getAxis() == Direction.Axis.X;
    }

    private static VoxelShape buildWallShape(Direction facing, Half half, int layers) {
        // Start from the parent slope-layer shape (already oriented by FACING/HALF)
        // and tip it onto a vertical face using the same axis/angle the model core
        // uses, so hitbox and visual geometry agree. copy() yields a fresh, unfrozen
        // MutableShape so the shared CCShapes entry is never mutated.
        CCShapes.MutableShape shape = CCShapes.SLOPE_LAYER.get(facing).get(half).get(layers).copy();
        int angle = wallAngle(facing, half);
        if (wallRotateAroundZ(facing)) {
            shape.rotateZ(angle);
        } else {
            shape.rotateX(angle);
        }
        return shape.toShape();
    }
}
