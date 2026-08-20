package com.agent772.copycatplusadditions.blocks;

import com.agent772.copycatplusadditions.registry.ModBlockEntities;
import com.copycatsplus.copycats.CCShapes;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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
     * Cursor-Y threshold for choosing {@link Half#TOP} vs {@link Half#BOTTOM} when
     * placing onto a wall: anything above the block's vertical midpoint puts the
     * narrow edge up, anything at or below puts it down.
     */
    private static final double WALL_HALF_THRESHOLD = 0.5D;

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
        // Stacking another layer onto an existing adv_slope_layer: the parent's
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
        Half half = yOffset > WALL_HALF_THRESHOLD ? Half.TOP : Half.BOTTOM;
        return placement
            .setValue(FACING, facing)
            .setValue(HALF, half)
            .setValue(IN_WALL, true);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        // Any click with the matching layer item grows the stack while it is below the
        // cap — the clicked face is irrelevant. Face gating used to decide increment vs.
        // place-adjacent (and had to be flipped for IN_WALL, since a wall slope's FACING
        // points out of the wall), but the accepted face shifted with the wedge each
        // layer, so the "sweet spot" moved between clicks (issue #45). Once the block is
        // full (LAYERS==8) this returns false and a new block is placed on the clicked
        // side — for both floor and wall variants, preserving FACING/HALF/IN_WALL.
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

    // CCCopycatBlock implements Create's IBE<CCCopycatBlockEntity>; placement calls
    // getBlockEntityType().create(pos, state), which returns null if this block is not
    // in that type's validBlocks set. Copycats+' own copycat BE type is registered with
    // only its own blocks, so we publish our own type — registered with ADV_SLOPE_LAYER
    // in ModBlockEntities — to avoid a null BE (and the NPE that follows) on placement.

    @Override
    public Class<CCCopycatBlockEntity> getBlockEntityClass() {
        return CCCopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CCCopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ADV_SLOPE_LAYER.get();
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
        return wedgeShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return wedgeShape(state, level, pos, context);
    }

    private VoxelShape wedgeShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        // Parent face-culling assumes geometry follows FACING/HALF alone, but a wall
        // slope's visible shape has been rotated 90° around X or Z. The parent would
        // hide/show the wrong neighbour faces against adjacent solids, producing
        // Z-fighting or holes. Threading the wall rotation through ICopycatBlock's
        // shape-based occlusion would mean re-deriving every shape's face projections
        // post-rotation, which isn't worth the complexity for a single layer of
        // hidden faces — disable face hiding for wall slopes instead.
        if (state.getValue(IN_WALL)) {
            return false;
        }
        return super.hidesNeighborFace(level, pos, state, neighborState, dir);
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        // Parent's transform carries the upstream "todo: vertical slope layer not
        // supported yet" caveat and only knows how to rotate horizontal FACING
        // (via BlockUtils.transformStepLikeHorizontal). It pins non-Y-axis 90°
        // rotations to Rotation.NONE before delegating, which matches the upstream
        // pattern we want for wall slopes too.
        //
        // For wall slopes the relevant cases work out:
        //   - Y-axis rotation: parent rotates FACING horizontally — the wall normal
        //     follows the contraption rotation, which is exactly what we want.
        //   - Non-Y-axis 90°: parent pins to Rotation.NONE → state unchanged. The
        //     visual won't follow the contraption, but the block isn't left in a
        //     contradictory state either (same punt upstream makes for vertical slopes).
        //   - Mirrors: parent either cycles HALF (INVERT_Y mirror — flips the narrow
        //     edge up↔down, correct for a wall slope) or mirrors FACING through the
        //     mirror plane (the wall normal mirrors, correct).
        //
        // IN_WALL is preserved because the parent rebuilds the returned state via
        // tryCopyProperties, which copies every state property from the input.
        BlockState transformed = super.transform(state, transform);
        if (!state.getValue(IN_WALL)) {
            return transformed;
        }
        // Defensive clamp: an exotic transform could leave FACING vertical (UP/DOWN),
        // which has no valid wall-slope interpretation. Coerce back to a floor slope
        // rather than render as a broken wall slope facing nowhere.
        if (transformed.getValue(FACING).getAxis().isVertical()) {
            return transformed.setValue(IN_WALL, false);
        }
        return transformed;
    }

    /**
     * The rotation (in degrees) that tips the floor slope layer onto a vertical wall
     * for the given FACING/HALF. Values are taken verbatim from upstream Copycats+
     * PR #273's {@code CopycatSlopeLayerModelCore} (the reference design for vertical
     * slope layers), so the collision shape here and the rendered geometry in
     * {@code CopycatVerticalSlopeLayerModelCore} stay aligned.
     *
     * @throws IllegalArgumentException if {@code facing} is vertical — wall slopes are
     *     always mounted against a horizontal facing, so a vertical FACING here means
     *     a caller forgot the {@code IN_WALL} / horizontal-facing guard upstream.
     */
    public static int wallAngle(Direction facing, Half half) {
        return switch (facing) {
            case NORTH -> half == Half.TOP ? 270 : 90;
            case SOUTH -> half == Half.TOP ? 90 : 270;
            case WEST -> half == Half.TOP ? 90 : 270;
            case EAST -> half == Half.TOP ? 270 : 90;
            default -> throw new IllegalArgumentException(
                "wallAngle requires a horizontal FACING; got " + facing);
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
