package com.agent772.copycatplusadditions.blocks;

import com.copycatsplus.copycats.CCShapes;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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
        // When stacking onto an existing vertical slope layer the parent's
        // getStateForPlacement already returned the cycled state of that block, so
        // IN_WALL is carried through unchanged. Detecting this prevents a wall-placed
        // slope from flipping to a floor slope when another layer is added on top.
        if (context.getLevel().getBlockState(context.getClickedPos()).is(this)) {
            return placement;
        }
        boolean inWall = context.getClickedFace().getAxis().isHorizontal();
        return placement.setValue(IN_WALL, inWall);
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
     * The rotation (in degrees) applied to turn a floor slope layer into a wall one.
     *
     * <p>Shared with {@code CopycatVerticalSlopeLayerModelCore} so that the collision
     * shape and the rendered geometry always use the exact same transform.
     */
    public static int wallAngle(Half half) {
        return half == Half.TOP ? 270 : 90;
    }

    private static VoxelShape buildWallShape(Direction facing, Half half, int layers) {
        // Start from the parent slope-layer shape (already oriented by FACING/HALF)
        // and tip it 90 degrees onto a vertical face. NORTH/SOUTH facings rotate
        // around Z, EAST/WEST facings around X. copy() yields a fresh, unfrozen
        // MutableShape so the shared CCShapes entry is never mutated.
        CCShapes.MutableShape shape = CCShapes.SLOPE_LAYER.get(facing).get(half).get(layers).copy();
        if (facing.getAxis() == Direction.Axis.Z) {
            shape.rotateZ(wallAngle(half));
        } else {
            shape.rotateX(wallAngle(half));
        }
        return shape.toShape();
    }
}
