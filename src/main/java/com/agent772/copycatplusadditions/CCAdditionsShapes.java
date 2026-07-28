package com.agent772.copycatplusadditions;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared collision/outline shapes for blocks added by this mod.
 *
 * <p>The shape returned here is also what {@code ICopycatBlock.hidesNeighborFace}
 * uses to decide which neighbour faces are occluded, so it must track the visible
 * geometry's footprint rather than a conservative bounding box.
 *
 * <p>Each shape is an {@link OutlinedShape} — the same pattern Copycats+ uses for
 * its own slope blocks — that stores two things:
 * <ul>
 *   <li>An 8-step staircase of axis-aligned boxes, used for physics, collision,
 *       and {@code hidesNeighborFace}.</li>
 *   <li>12 explicit wireframe edges for the selection highlight, giving a true
 *       diagonal outline rather than a stepped one.</li>
 * </ul>
 *
 * <p>Copycats+ achieves its smooth outline by wrapping the box-based
 * {@code VoxelShape} in an {@code OutlinedVoxelShape} that overrides
 * {@code forAllEdges()} — the exact method Minecraft calls when drawing the
 * selection highlight — to emit explicit line segments instead of the default
 * box-derived edges. We do the same via {@link OutlinedShape}, a self-contained
 * subclass of {@link ArrayVoxelShape} that avoids any dependency on Catnip's
 * {@code Pair} type.
 */
public final class CCAdditionsShapes {

    private CCAdditionsShapes() {
    }

    private static final int STEPS = 8;

    /*
     * All shapes are precomputed once per block-state combination. getShape is
     * queried per collision check and per frame (block outline, particle
     * physics, hidesNeighborFace); building an OutlinedShape on every call
     * (8x Shapes.or + optimize + reflection) caused visible client lag, e.g.
     * from the destroy-particle burst when wrenching off the mimic material.
     */
    private static final Direction[] HORIZONTALS =
        {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    private static final VoxelShape[] INNER_CORNER_SLOPE = new VoxelShape[8];
    private static final VoxelShape[] CORNER_SLOPE = new VoxelShape[8];
    private static final VoxelShape[] INNER_CORNER_SLOPE_LAYER = new VoxelShape[64];
    private static final VoxelShape[] CORNER_SLOPE_LAYER = new VoxelShape[64];

    static {
        for (Direction facing : HORIZONTALS) {
            int[] nc = notchCorner(facing);
            int[] ac = apexCorner(facing);
            for (Half half : Half.values()) {
                boolean top = half == Half.TOP;
                int i = index(facing, half);
                INNER_CORNER_SLOPE[i] = buildShape(nc[0], nc[1], top, 1.0);
                CORNER_SLOPE[i] = buildCornerShape(ac[0], ac[1], top, 1.0);
                for (int layers = 1; layers <= 8; layers++) {
                    int li = i * 8 + (layers - 1);
                    INNER_CORNER_SLOPE_LAYER[li] = buildShape(nc[0], nc[1], top, layers * 2.0 / 16.0);
                    CORNER_SLOPE_LAYER[li] = buildCornerShape(ac[0], ac[1], top, layers * 2.0 / 16.0);
                }
            }
        }
    }

    private static int index(Direction facing, Half half) {
        return facing.get2DDataValue() * 2 + half.ordinal();
    }

    public static VoxelShape innerCornerSlope(Direction facing, Half half) {
        return INNER_CORNER_SLOPE[index(facing, half)];
    }

    public static VoxelShape innerCornerSlopeLayer(Direction facing, Half half, int layers) {
        return INNER_CORNER_SLOPE_LAYER[index(facing, half) * 8 + (layers - 1)];
    }

    public static VoxelShape cornerSlope(Direction facing, Half half) {
        return CORNER_SLOPE[index(facing, half)];
    }

    public static VoxelShape cornerSlopeLayer(Direction facing, Half half, int layers) {
        return CORNER_SLOPE_LAYER[index(facing, half) * 8 + (layers - 1)];
    }

    private static VoxelShape buildShape(int nx, int nz, boolean topHalf, double maxH) {
        VoxelShape staircase = buildStaircase(nx, nz, topHalf, maxH).optimize();
        double[] edges = buildEdges(nx, nz, topHalf, maxH);
        return new OutlinedShape(staircase, edges);
    }

    private static VoxelShape buildCornerShape(int ax, int az, boolean topHalf, double maxH) {
        VoxelShape staircase = buildCornerStaircase(ax, az, topHalf, maxH).optimize();
        double[] edges = buildCornerEdges(ax, az, topHalf, maxH);
        return new OutlinedShape(staircase, edges);
    }

    // -------------------------------------------------------------------------
    // Staircase (physics / hidesNeighborFace)
    // -------------------------------------------------------------------------

    /**
     * 8-step staircase approximation of the inner-corner slope.
     *
     * <p>The slope surface at normalised coords (x,z) in [0,1]^2 is
     * {@code h(x,z) = max(dx, dz) * maxH} where dx, dz are distances from the
     * notch corner. Each step i uses threshold {@code t = i/8} (bottom of the
     * slice). The solid L-shape at that threshold decomposes into two
     * non-overlapping axis-aligned boxes: Box A covers the z-arm, Box B the x-arm.
     */
    private static VoxelShape buildStaircase(int nx, int nz, boolean topHalf, double maxH) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < STEPS; i++) {
            double t       = (double) i / STEPS;
            double yLo     = (double) i / STEPS * maxH;
            double yHi     = (double) (i + 1) / STEPS * maxH;

            double blockYLo = topHalf ? (1.0 - yHi) : yLo;
            double blockYHi = topHalf ? (1.0 - yLo) : yHi;

            // Box A: full x, solid z arm (away from notch-z edge)
            double zA  = (nz == 0) ? t    : 0.0;
            double zAx = (nz == 0) ? 1.0  : (1.0 - t);
            if (zAx > zA) {
                shape = Shapes.or(shape, Shapes.box(0.0, blockYLo, zA, 1.0, blockYHi, zAx));
            }

            // Box B: solid x arm, complementary z band (no overlap with Box A)
            double xB  = (nx == 0) ? t    : 0.0;
            double xBx = (nx == 0) ? 1.0  : (1.0 - t);
            double zB  = (nz == 0) ? 0.0  : (1.0 - t);
            double zBx = (nz == 0) ? t    : 1.0;
            if (xBx > xB && zBx > zB) {
                shape = Shapes.or(shape, Shapes.box(xB, blockYLo, zB, xBx, blockYHi, zBx));
            }
        }
        return shape;
    }

    // -------------------------------------------------------------------------
    // Outer corner staircase (physics / hidesNeighborFace)
    // -------------------------------------------------------------------------

    /**
     * 8-step staircase approximation of the outer corner slope.
     *
     * The slope surface at normalised coords (x,z) is min(dx, dz) * maxH where
     * dx, dz are distances from the apex corner measured toward the apex. At
     * each step i the solid region is a single rectangle shrinking toward the
     * apex -- one Shapes.box per step, not an L-shape.
     */
    private static VoxelShape buildCornerStaircase(int ax, int az, boolean topHalf, double maxH) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < STEPS; i++) {
            double t       = (double) i / STEPS;
            double yLo     = (double) i / STEPS * maxH;
            double yHi     = (double) (i + 1) / STEPS * maxH;

            double blockYLo = topHalf ? (1.0 - yHi) : yLo;
            double blockYHi = topHalf ? (1.0 - yLo) : yHi;

            double xLo = (ax == 0) ? 0.0 : t;
            double xHi = (ax == 0) ? (1.0 - t) : 1.0;
            double zLo = (az == 0) ? 0.0 : t;
            double zHi = (az == 0) ? (1.0 - t) : 1.0;

            if (xHi > xLo && zHi > zLo) {
                shape = Shapes.or(shape, Shapes.box(xLo, blockYLo, zLo, xHi, blockYHi, zHi));
            }
        }
        return shape;
    }

    // -------------------------------------------------------------------------
    // Wireframe outline edges
    // -------------------------------------------------------------------------

    /**
     * The 12 wireframe edges of the inner-corner slope as a flat {@code double[]}
     * (6 doubles per edge: x1,y1,z1,x2,y2,z2).
     *
     * <p>The shape has four XZ corners: the notch corner A (zero height) and three
     * full-height corners B, C, D. The 12 edges are:
     * <ul>
     *   <li>4 edges on the flat face (floor for BOTTOM half, ceiling for TOP)</li>
     *   <li>3 vertical edges at B, C, D (none at A which has zero height)</li>
     *   <li>2 horizontal edges connecting the three full-height tops</li>
     *   <li>3 diagonal edges meeting at the notch: B-top to A, A to C-top,
     *       and the slope ridge D-top to A</li>
     * </ul>
     */
    private static double[] buildEdges(int nx, int nz, boolean topHalf, double maxH) {
        // XZ positions of the four corners
        double ax = nx,     az = nz;      // A = notch corner
        double bx = 1 - nx, bz = nz;      // B = same z as notch, opposite x
        double cx = nx,     cz = 1 - nz;  // C = same x as notch, opposite z
        double dx = 1 - nx, dz = 1 - nz;  // D = far corner

        // Y positions: flat face and full-height level
        double flat  = topHalf ? 1.0 : 0.0;
        double fullY = topHalf ? 1.0 - maxH : maxH;

        List<double[]> e = new ArrayList<>(12);

        // 4 edges on the flat face
        e.add(new double[]{ax, flat, az,  bx, flat, bz});
        e.add(new double[]{ax, flat, az,  cx, flat, cz});
        e.add(new double[]{cx, flat, cz,  dx, flat, dz});
        e.add(new double[]{dx, flat, dz,  bx, flat, bz});
        // 3 vertical edges at B, C, D
        e.add(new double[]{bx, flat, bz,  bx, fullY, bz});
        e.add(new double[]{cx, flat, cz,  cx, fullY, cz});
        e.add(new double[]{dx, flat, dz,  dx, fullY, dz});
        // 2 horizontal edges connecting the three full-height tops
        e.add(new double[]{bx, fullY, bz,  dx, fullY, dz});
        e.add(new double[]{dx, fullY, dz,  cx, fullY, cz});
        // 3 diagonal edges meeting at the notch corner
        e.add(new double[]{bx, fullY, bz,  ax, flat, az}); // adjacent face diagonal
        e.add(new double[]{ax, flat, az,   cx, fullY, cz}); // adjacent face diagonal
        e.add(new double[]{dx, fullY, dz,  ax, flat, az}); // slope ridge

        double[] flat_array = new double[12 * 6];
        for (int i = 0; i < 12; i++) {
            System.arraycopy(e.get(i), 0, flat_array, i * 6, 6);
        }
        return flat_array;
    }

    // -------------------------------------------------------------------------
    // Outer corner wireframe edges
    // -------------------------------------------------------------------------

    /**
     * The 8 wireframe edges of the outer corner slope as a flat double[] (6
     * doubles per edge: x1,y1,z1,x2,y2,z2).
     *
     * A = apex corner (full height), B and C = adjacent corners (zero height,
     * form the two visible triangular faces), D = opposite corner (zero height,
     * the slope ridge goes from D at the bottom to A at the top).
     */
    private static double[] buildCornerEdges(int ax, int az, boolean topHalf, double maxH) {
        double aXp = ax,     aZp = az;
        double bXp = 1 - ax, bZp = az;
        double cXp = ax,     cZp = 1 - az;
        double dXp = 1 - ax, dZp = 1 - az;

        double flat  = topHalf ? 1.0 : 0.0;
        double fullY = topHalf ? (1.0 - maxH) : maxH;

        List<double[]> e = new ArrayList<>(8);

        // 4 bottom (flat face) edges
        e.add(new double[]{0, flat, 0,   1, flat, 0});
        e.add(new double[]{1, flat, 0,   1, flat, 1});
        e.add(new double[]{1, flat, 1,   0, flat, 1});
        e.add(new double[]{0, flat, 1,   0, flat, 0});
        // Apex vertical edge
        e.add(new double[]{aXp, flat, aZp,   aXp, fullY, aZp});
        // Two adjacent face diagonals (B and C to apex top)
        e.add(new double[]{bXp, flat, bZp,   aXp, fullY, aZp});
        e.add(new double[]{cXp, flat, cZp,   aXp, fullY, aZp});
        // Slope ridge (opposite corner D to apex top)
        e.add(new double[]{dXp, flat, dZp,   aXp, fullY, aZp});

        double[] flat_array = new double[8 * 6];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(e.get(i), 0, flat_array, i * 6, 6);
        }
        return flat_array;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a horizontal facing to its notch corner: the block corner where the
     * slope collapses to zero height. Matches the render rotation in
     * {@code CopycatInnerCornerSlopeModelCore}: pre-rotation the notch is at NE
     * (x=1, z=0), and each 90 deg CW Y-rotation steps it to the next corner.
     *
     * @return int[]{nx, nz} where 1 means the notch is at the high end of that axis
     */
    private static int[] notchCorner(Direction facing) {
        return switch (facing) {
            case SOUTH -> new int[]{1, 0}; // NE
            case WEST  -> new int[]{1, 1}; // SE
            case NORTH -> new int[]{0, 1}; // SW
            case EAST  -> new int[]{0, 0}; // NW
            default    -> new int[]{1, 0};
        };
    }

    /**
     * Maps a horizontal facing to the apex corner of the outer corner slope --
     * the corner that reaches full height. The apex is the same corner as the
     * inner corner's notch for the same FACING, so that paired inner and outer
     * corner slopes with identical FACING and HALF interlock into a full block.
     *
     * @return int[]{ax, az} where 1 means the apex is at the high end of that axis
     */
    private static int[] apexCorner(Direction facing) {
        return switch (facing) {
            case SOUTH -> new int[]{1, 0}; // NE
            case WEST  -> new int[]{1, 1}; // SE
            case NORTH -> new int[]{0, 1}; // SW
            case EAST  -> new int[]{0, 0}; // NW
            default    -> new int[]{1, 0};
        };
    }

    // -------------------------------------------------------------------------
    // OutlinedShape
    // -------------------------------------------------------------------------

    /**
     * A {@link ArrayVoxelShape} that overrides {@link #forAllEdges} to emit
     * explicit diagonal line segments for the selection highlight, while keeping
     * the underlying staircase boxes for physics and {@code hidesNeighborFace}.
     *
     * <p>This replicates the technique used by Copycats+ ({@code OutlinedVoxelShape}
     * backed by {@code MutableShape.outlines}) without depending on Catnip's
     * {@code Pair} type: the edges are stored as a flat {@code double[]} (6 doubles
     * per edge: x1,y1,z1,x2,y2,z2). The {@code DiscreteVoxelShape} needed by the
     * {@code ArrayVoxelShape} constructor is read from the staircase's protected
     * {@code VoxelShape.shape} field via reflection — a one-time operation per
     * block-state combination that happens at world-load time.
     */
    private static final class OutlinedShape extends ArrayVoxelShape {

        private static final Field VOXEL_SHAPE_FIELD;
        static {
            try {
                VOXEL_SHAPE_FIELD = VoxelShape.class.getDeclaredField("shape");
                VOXEL_SHAPE_FIELD.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final double[] edgeData;

        OutlinedShape(VoxelShape staircase, double[] edgeData) {
            super(extractDiscreteShape(staircase),
                  staircase.getCoords(Direction.Axis.X).toDoubleArray(),
                  staircase.getCoords(Direction.Axis.Y).toDoubleArray(),
                  staircase.getCoords(Direction.Axis.Z).toDoubleArray());
            this.edgeData = edgeData;
        }

        private static DiscreteVoxelShape extractDiscreteShape(VoxelShape vs) {
            try {
                return (DiscreteVoxelShape) VOXEL_SHAPE_FIELD.get(vs);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not read VoxelShape.shape", e);
            }
        }

        @Override
        public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
            for (int i = 0; i < edgeData.length; i += 6) {
                consumer.consume(
                    edgeData[i],     edgeData[i + 1], edgeData[i + 2],
                    edgeData[i + 3], edgeData[i + 4], edgeData[i + 5]
                );
            }
        }
    }
}
