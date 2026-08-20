package com.agent772.copycatplusadditions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Shared placement math for the corner slope family (outer + inner corner). Both
 * blocks map the same click context to the same {@code (HALF, WALL_FLIPPED)} pair;
 * only their interpretation of the apex vs. notch differs (the inner corner inverts
 * {@code WALL_FLIPPED} at its call site). Kept here — next to
 * {@link CornerWallRotation} — rather than on one block so neither corner reaches
 * into the other for the helper.
 */
public final class CornerWallPlacement {

    private CornerWallPlacement() {
    }

    /**
     * Mirrors {@code CopycatSlopeBlock#getStateForPlacement}'s HALF logic so the corners
     * place consistently with the existing slope family: floor click → BOTTOM, ceiling
     * click → TOP, side click → cursor-Y midpoint.
     */
    public static Half pickHalf(BlockPlaceContext context) {
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

    /** The {@code (HALF, WALL_FLIPPED)} pair that mounts a wall corner in one quadrant. */
    public record WallPlacement(Half half, boolean flipped) {
    }

    /**
     * Derives {@code HALF} and {@code WALL_FLIPPED} together so the wedge's HIGH corner
     * (the outer corner's apex) lands in the exact quadrant of the wall face the player
     * clicked — measured in the player's own view frame, so wall placement is as intuitive
     * as the floor: click upper-left, high corner goes upper-left, and so on for all four
     * walls. The inner corner, whose distinctive corner is the low notch instead, inverts
     * {@code WALL_FLIPPED} at the call site so its high corner still lands under the cursor.
     *
     * <p>Why the two properties are computed together: the shared {@link CornerWallRotation}
     * tip maps {@code (HALF, WALL_FLIPPED)} to player-view quadrants identically for every
     * wall — {@code (BOTTOM, unflipped)} = upper-right, {@code (TOP, unflipped)} = lower-right,
     * {@code (BOTTOM, flipped)} = lower-left, {@code (TOP, flipped)} = upper-left. That is
     * a 180-degree apex spin, not an axis-aligned flip, so {@code HALF} alone does not map
     * to "up/down" — it depends on the horizontal click too. Inverting the table gives
     * {@code flipped = clicked-on-the-left} and {@code half = (upper == right) ? BOTTOM : TOP}.
     *
     * <p>The face's horizontal "right" is taken from the player's viewpoint
     * ({@code clickedFace.getCounterClockWise()}), not a world axis, which is exactly the
     * bug the old world-axis test had: {@code +X}/{@code +Z} is the player's right on two
     * walls and their left on the other two, so the apex went to the wrong side on half
     * the walls.
     */
    public static WallPlacement wallPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        boolean upper = context.getClickLocation().y - pos.getY() > 0.5D;
        // Horizontal axis of the face, pointing to the player's right as they look at it.
        Direction playerRight = clickedFace.getCounterClockWise();
        double along = playerRight.getAxis() == Direction.Axis.X
            ? context.getClickLocation().x - pos.getX()
            : context.getClickLocation().z - pos.getZ();
        double rightCoord = playerRight.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? along
            : 1.0D - along;
        boolean right = rightCoord > 0.5D;
        return new WallPlacement(upper == right ? Half.BOTTOM : Half.TOP, !right);
    }
}
