package com.agent772.copycatplusadditions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Single source of truth for the 90-degree rotations that tip a floor corner slope
 * onto a vertical wall face.
 *
 * <p>The corner blocks store their wall orientation as {@code FACING} (the wall
 * normal), {@code HALF} (narrow edge up/down) and {@code WALL_FLIPPED} (which
 * horizontal side the apex wraps toward). Both the collision/outline shape
 * ({@code CCAdditionsShapes}) and the rendered geometry (the corner model cores)
 * must apply the <i>same</i> rotation to the same floor-oriented base, otherwise the
 * hitbox and the visual drift apart. To guarantee that, both consume the identical
 * ordered {@link Step} list produced here.
 *
 * <p>The rotation is expressed as at most two axis-aligned quarter/half turns applied
 * in order:
 * <ol>
 *   <li><b>Tip</b> — a 90/270 degree turn about the horizontal axis perpendicular to
 *       the wall normal, laying the block's floor face flat against the wall. The
 *       axis/angle table is taken verbatim from the straight vertical slope
 *       ({@code CopycatVerticalSlopeLayerBlock#wallAngle} /
 *       {@code #wallRotateAroundZ}), which was validated in-world in PR #273 — the
 *       geometric "which way to tip onto this wall" is identical for a slope and a
 *       corner, so the proven table is reused. {@code HALF} selects 90 vs 270,
 *       flipping the narrow edge up vs down exactly as it does for the straight
 *       slope.</li>
 *   <li><b>Apex spin</b> — when {@code WALL_FLIPPED}, an extra 180 degree turn about
 *       the wall-normal axis, which keeps the block flat on the wall but sends the
 *       apex to the opposite in-wall corner. A straight slope needs no such flag (it
 *       is symmetric along the wall), but a corner's two-axis apex has this extra
 *       degree of freedom.</li>
 * </ol>
 *
 * <p>All rotations are about the block centre in unit (0..1) space and match the
 * convention of Copycats+' {@code MutableVec3.rotateX/rotateZ}, so the shape helper's
 * point transform and the model core's {@code AssemblyTransform} stay in lockstep.
 */
public final class CornerWallRotation {

    private CornerWallRotation() {
    }

    /** The axis a quarter/half turn is taken about. Only X and Z are needed for walls. */
    public enum Axis {
        X, Z
    }

    /** One axis-aligned rotation of {@code angle} (90/180/270) degrees about {@code axis}. */
    public record Step(Axis axis, int angle) {
    }

    /**
     * The ordered rotation steps that move a floor-oriented corner (already rotated by
     * {@code FACING}/{@code HALF}) onto the wall whose normal is {@code facing}.
     *
     * @throws IllegalArgumentException if {@code facing} is vertical — wall corners are
     *     always mounted against a horizontal facing.
     */
    public static List<Step> steps(Direction facing, Half half, boolean flipped) {
        if (facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("wall rotation requires a horizontal facing; got " + facing);
        }
        List<Step> steps = new ArrayList<>(2);
        // Tip: pivot about the axis perpendicular to the wall normal (X for N/S walls,
        // Z for E/W walls), reusing the straight vertical slope's proven angle table.
        Axis tipAxis = facing.getAxis() == Direction.Axis.X ? Axis.Z : Axis.X;
        steps.add(new Step(tipAxis, tipAngle(facing, half)));
        // Apex spin: 180 about the wall normal to reach the opposite in-wall corner.
        if (flipped) {
            Axis normalAxis = facing.getAxis() == Direction.Axis.X ? Axis.X : Axis.Z;
            steps.add(new Step(normalAxis, 180));
        }
        return steps;
    }

    private static int tipAngle(Direction facing, Half half) {
        boolean top = half == Half.TOP;
        return switch (facing) {
            case NORTH -> top ? 270 : 90;
            case SOUTH -> top ? 90 : 270;
            case WEST -> top ? 90 : 270;
            case EAST -> top ? 270 : 90;
            default -> throw new IllegalArgumentException(
                "tipAngle requires a horizontal facing; got " + facing);
        };
    }
}
