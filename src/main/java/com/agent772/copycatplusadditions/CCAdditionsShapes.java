package com.agent772.copycatplusadditions;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.agent772.copycatplusadditions.CornerWallRotation.Step;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVec3;

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

    /*
     * Wall-mounted (IN_WALL) variants are the floor shapes tipped 90 degrees onto a
     * vertical face (see CornerWallRotation). They are far less common than the floor
     * shapes and the state space is 4x larger (adds WALL_FLIPPED on top of IN_WALL),
     * so rather than inflate class-load time by precomputing all of them eagerly they
     * are built on first use and cached here — the same lazy pattern the straight
     * vertical slope layer uses.
     */
    private static final Map<Integer, VoxelShape> INNER_CORNER_WALL = new ConcurrentHashMap<>();
    private static final Map<Integer, VoxelShape> CORNER_WALL = new ConcurrentHashMap<>();

    static {
        for (Direction facing : HORIZONTALS) {
            int[] nc = notchCorner(facing);
            int[] ac = apexCorner(facing);
            for (Half half : Half.values()) {
                boolean top = half == Half.TOP;
                int i = index(facing, half);
                INNER_CORNER_SLOPE[i] = buildShape(nc[0], nc[1], top, 1.0, 0.0);
                CORNER_SLOPE[i] = buildCornerShape(ac[0], ac[1], top, 1.0, 0.0);
                for (int layers = 1; layers <= 8; layers++) {
                    int li = i * 8 + (layers - 1);
                    double apexTop = CornerLayerProfile.apexTop(layers, 1.0);
                    double floor = CornerLayerProfile.floor(layers, 1.0);
                    INNER_CORNER_SLOPE_LAYER[li] = buildShape(nc[0], nc[1], top, apexTop, floor);
                    CORNER_SLOPE_LAYER[li] = buildCornerShape(ac[0], ac[1], top, apexTop, floor);
                }
            }
        }
    }

    private static int index(Direction facing, Half half) {
        return facing.get2DDataValue() * 2 + half.ordinal();
    }

    public static VoxelShape innerCornerSlope(Direction facing, Half half, boolean inWall, boolean flipped) {
        if (!inWall) {
            return INNER_CORNER_SLOPE[index(facing, half)];
        }
        return INNER_CORNER_WALL.computeIfAbsent(wallKey(facing, half, 0, flipped),
            k -> buildInnerWall(facing, half, 0, flipped));
    }

    public static VoxelShape innerCornerSlopeLayer(Direction facing, Half half, int layers,
                                                   boolean inWall, boolean flipped) {
        if (!inWall) {
            return INNER_CORNER_SLOPE_LAYER[index(facing, half) * 8 + (layers - 1)];
        }
        return INNER_CORNER_WALL.computeIfAbsent(wallKey(facing, half, layers, flipped),
            k -> buildInnerWall(facing, half, layers, flipped));
    }

    public static VoxelShape cornerSlope(Direction facing, Half half, boolean inWall, boolean flipped) {
        if (!inWall) {
            return CORNER_SLOPE[index(facing, half)];
        }
        return CORNER_WALL.computeIfAbsent(wallKey(facing, half, 0, flipped),
            k -> buildCornerWall(facing, half, 0, flipped));
    }

    public static VoxelShape cornerSlopeLayer(Direction facing, Half half, int layers,
                                              boolean inWall, boolean flipped) {
        if (!inWall) {
            return CORNER_SLOPE_LAYER[index(facing, half) * 8 + (layers - 1)];
        }
        return CORNER_WALL.computeIfAbsent(wallKey(facing, half, layers, flipped),
            k -> buildCornerWall(facing, half, layers, flipped));
    }

    /** Packs (facing, half, layers 0=base|1..8, flipped) into a cache key. */
    private static int wallKey(Direction facing, Half half, int layers, boolean flipped) {
        return ((facing.get2DDataValue() * 2 + half.ordinal()) * 9 + layers) * 2 + (flipped ? 1 : 0);
    }

    private static VoxelShape buildShape(int nx, int nz, boolean topHalf, double apexTop, double floor) {
        List<double[]> boxes = innerBoxes(nx, nz, topHalf, apexTop, floor);
        double[] edges = buildEdges(nx, nz, topHalf, apexTop, floor);
        return new OutlinedShape(boxesToShape(boxes), edges);
    }

    private static VoxelShape buildCornerShape(int ax, int az, boolean topHalf, double apexTop, double floor) {
        List<double[]> boxes = cornerBoxes(ax, az, topHalf, apexTop, floor);
        double[] edges = buildCornerEdges(ax, az, topHalf, apexTop, floor);
        return new OutlinedShape(boxesToShape(boxes), edges);
    }

    // -------------------------------------------------------------------------
    // Wall-mounted (IN_WALL) variants
    // -------------------------------------------------------------------------

    /**
     * Builds an inner-corner wall shape: the floor shape for {@code (facing, half,
     * layers)} tipped onto the wall by the shared {@link CornerWallRotation} steps.
     * {@code layers == 0} selects the non-layer base shape.
     */
    private static VoxelShape buildInnerWall(Direction facing, Half half, int layers, boolean flipped) {
        int[] nc = notchCorner(facing);
        boolean top = half == Half.TOP;
        double apexTop = layers == 0 ? 1.0 : CornerLayerProfile.apexTop(layers, 1.0);
        double floor = layers == 0 ? 0.0 : CornerLayerProfile.floor(layers, 1.0);
        List<double[]> boxes = innerBoxes(nc[0], nc[1], top, apexTop, floor);
        double[] edges = buildEdges(nc[0], nc[1], top, apexTop, floor);
        return tipOntoWall(boxes, edges, facing, half, flipped);
    }

    /** Outer-corner counterpart of {@link #buildInnerWall}. */
    private static VoxelShape buildCornerWall(Direction facing, Half half, int layers, boolean flipped) {
        int[] ac = apexCorner(facing);
        boolean top = half == Half.TOP;
        double apexTop = layers == 0 ? 1.0 : CornerLayerProfile.apexTop(layers, 1.0);
        double floor = layers == 0 ? 0.0 : CornerLayerProfile.floor(layers, 1.0);
        List<double[]> boxes = cornerBoxes(ac[0], ac[1], top, apexTop, floor);
        double[] edges = buildCornerEdges(ac[0], ac[1], top, apexTop, floor);
        return tipOntoWall(boxes, edges, facing, half, flipped);
    }

    /**
     * Applies the shared {@link CornerWallRotation} steps to the floor staircase boxes
     * and wireframe edges, so the collision/outline geometry tips onto the wall in
     * lockstep with the rendered model (which applies the same steps to its
     * {@code AssemblyTransform}).
     */
    private static VoxelShape tipOntoWall(List<double[]> boxes, double[] edges, Direction facing,
                                          Half half, boolean flipped) {
        List<Step> steps = CornerWallRotation.steps(facing, half, flipped);
        return new OutlinedShape(boxesToShape(rotateBoxes(boxes, steps)), rotateEdges(edges, steps));
    }

    private static VoxelShape boxesToShape(List<double[]> boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            shape = Shapes.or(shape, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        return shape.optimize();
    }

    private static List<double[]> rotateBoxes(List<double[]> boxes, List<Step> steps) {
        List<double[]> out = new ArrayList<>(boxes.size());
        for (double[] b : boxes) {
            double[] p0 = {b[0], b[1], b[2]};
            double[] p1 = {b[3], b[4], b[5]};
            for (Step s : steps) {
                p0 = rotatePoint(p0, s);
                p1 = rotatePoint(p1, s);
            }
            // Axis-aligned 90/180/270 turns map a box to a box; recover min/max corners.
            out.add(new double[]{
                Math.min(p0[0], p1[0]), Math.min(p0[1], p1[1]), Math.min(p0[2], p1[2]),
                Math.max(p0[0], p1[0]), Math.max(p0[1], p1[1]), Math.max(p0[2], p1[2])});
        }
        return out;
    }

    private static double[] rotateEdges(double[] edges, List<Step> steps) {
        double[] out = new double[edges.length];
        for (int i = 0; i < edges.length; i += 6) {
            double[] a = {edges[i], edges[i + 1], edges[i + 2]};
            double[] b = {edges[i + 3], edges[i + 4], edges[i + 5]};
            for (Step s : steps) {
                a = rotatePoint(a, s);
                b = rotatePoint(b, s);
            }
            out[i] = a[0];
            out[i + 1] = a[1];
            out[i + 2] = a[2];
            out[i + 3] = b[0];
            out[i + 4] = b[1];
            out[i + 5] = b[2];
        }
        return out;
    }

    /**
     * Rotates a unit-space point about the block centre for one {@link Step} by
     * delegating to Copycats+' {@link MutableVec3#rotateX(int)}/{@link MutableVec3#rotateZ(int)}.
     * The model cores drive their {@code AssemblyTransform} through the very same
     * methods, so the collision shape built here and the rendered geometry share one
     * rotation implementation and cannot drift apart. {@code MutableVec3} rotates in
     * the same unit (0..1) space these boxes live in, so no scaling is needed.
     */
    private static double[] rotatePoint(double[] p, Step step) {
        // rotateBoxes recovers a box from just its two opposite corners, which is only
        // valid for axis-aligned (90/180/270) turns. MutableVec3 silently no-ops any
        // other angle, so guard here rather than let a bad step drift the hitbox.
        if (step.angle() % 90 != 0) {
            throw new IllegalArgumentException(
                "rotation step must be a multiple of 90 degrees; got " + step.angle());
        }
        MutableVec3 v = new MutableVec3(p[0], p[1], p[2]);
        switch (step.axis()) {
            case X -> v.rotateX(step.angle());
            case Z -> v.rotateZ(step.angle());
        }
        return new double[]{v.x, v.y, v.z};
    }

    // -------------------------------------------------------------------------
    // Staircase (physics / hidesNeighborFace)
    // -------------------------------------------------------------------------

    /**
     * 8-step staircase approximation of the inner-corner slope.
     *
     * <p>The slope surface at normalised coords (x,z) in [0,1]^2 is
     * {@code h(x,z) = floor + max(dx, dz) * (apexTop - floor)} where dx, dz are
     * distances from the notch corner. Each wedge step i uses threshold
     * {@code t = i/8} (bottom of the slice). The solid L-shape at that threshold
     * decomposes into two non-overlapping axis-aligned boxes: Box A covers the
     * z-arm, Box B the x-arm. When {@code floor > 0} (phase 2) a full-footprint
     * slab from 0 to {@code floor} is added so the raised notch reads as a solid
     * base rather than a floating wedge.
     */
    private static List<double[]> innerBoxes(int nx, int nz, boolean topHalf, double apexTop, double floor) {
        List<double[]> boxes = new ArrayList<>();
        addBaseSlab(boxes, topHalf, floor);
        double wedge = apexTop - floor;
        for (int i = 0; i < STEPS; i++) {
            double t       = (double) i / STEPS;
            double yLo     = floor + (double) i / STEPS * wedge;
            double yHi     = floor + (double) (i + 1) / STEPS * wedge;

            double blockYLo = topHalf ? (1.0 - yHi) : yLo;
            double blockYHi = topHalf ? (1.0 - yLo) : yHi;

            // Box A: full x, solid z arm (away from notch-z edge)
            double zA  = (nz == 0) ? t    : 0.0;
            double zAx = (nz == 0) ? 1.0  : (1.0 - t);
            if (zAx > zA && yHi > yLo) {
                boxes.add(new double[]{0.0, blockYLo, zA, 1.0, blockYHi, zAx});
            }

            // Box B: solid x arm, complementary z band (no overlap with Box A)
            double xB  = (nx == 0) ? t    : 0.0;
            double xBx = (nx == 0) ? 1.0  : (1.0 - t);
            double zB  = (nz == 0) ? 0.0  : (1.0 - t);
            double zBx = (nz == 0) ? t    : 1.0;
            if (xBx > xB && zBx > zB && yHi > yLo) {
                boxes.add(new double[]{xB, blockYLo, zB, xBx, blockYHi, zBx});
            }
        }
        return boxes;
    }

    /**
     * Adds a full-footprint solid slab from y=0 to y={@code floor} (flipped for the
     * TOP half) to {@code boxes}, or nothing when {@code floor <= 0}. Shared by the
     * inner and outer corner staircases for the phase-2 fill below the wedge.
     */
    private static void addBaseSlab(List<double[]> boxes, boolean topHalf, double floor) {
        if (floor <= 0.0) {
            return;
        }
        double blockYLo = topHalf ? (1.0 - floor) : 0.0;
        double blockYHi = topHalf ? 1.0 : floor;
        boxes.add(new double[]{0.0, blockYLo, 0.0, 1.0, blockYHi, 1.0});
    }

    // -------------------------------------------------------------------------
    // Outer corner staircase (physics / hidesNeighborFace)
    // -------------------------------------------------------------------------

    /**
     * 8-step staircase approximation of the outer corner slope.
     *
     * The slope surface at normalised coords (x,z) is
     * {@code floor + min(dx, dz) * (apexTop - floor)} where dx, dz are distances
     * from the apex corner measured toward the apex. At each wedge step i the
     * solid region is a single rectangle shrinking toward the apex -- one
     * Shapes.box per step, not an L-shape. When {@code floor > 0} (phase 2) a
     * full-footprint slab from 0 to {@code floor} is added below the wedge.
     */
    private static List<double[]> cornerBoxes(int ax, int az, boolean topHalf, double apexTop, double floor) {
        List<double[]> boxes = new ArrayList<>();
        addBaseSlab(boxes, topHalf, floor);
        double wedge = apexTop - floor;
        for (int i = 0; i < STEPS; i++) {
            double t       = (double) i / STEPS;
            double yLo     = floor + (double) i / STEPS * wedge;
            double yHi     = floor + (double) (i + 1) / STEPS * wedge;

            double blockYLo = topHalf ? (1.0 - yHi) : yLo;
            double blockYHi = topHalf ? (1.0 - yLo) : yHi;

            double xLo = (ax == 0) ? 0.0 : t;
            double xHi = (ax == 0) ? (1.0 - t) : 1.0;
            double zLo = (az == 0) ? 0.0 : t;
            double zHi = (az == 0) ? (1.0 - t) : 1.0;

            if (xHi > xLo && zHi > zLo && yHi > yLo) {
                boxes.add(new double[]{xLo, blockYLo, zLo, xHi, blockYHi, zHi});
            }
        }
        return boxes;
    }

    // -------------------------------------------------------------------------
    // Wireframe outline edges
    // -------------------------------------------------------------------------

    /**
     * The 12 wireframe edges of the inner-corner slope as a flat {@code double[]}
     * (6 doubles per edge: x1,y1,z1,x2,y2,z2).
     *
     * <p>The shape has four XZ corners: the notch corner A and three raised
     * corners B, C, D. In phase 1 ({@code floor == 0}) A sits on the flat face; in
     * phase 2 ({@code floor > 0}) A is raised to {@code floor} and a vertical edge
     * connects it down to the flat face. The edges are:
     * <ul>
     *   <li>4 edges on the flat face (floor for BOTTOM half, ceiling for TOP)</li>
     *   <li>3 vertical edges at B, C, D (full-height corners)</li>
     *   <li>2 horizontal edges connecting the three full-height tops</li>
     *   <li>3 diagonal edges meeting at the notch: B-top to A, A to C-top,
     *       and the slope ridge D-top to A</li>
     *   <li>phase 2 only: 1 vertical edge at the raised notch A (flat to floor)</li>
     * </ul>
     */
    private static double[] buildEdges(int nx, int nz, boolean topHalf, double apexTop, double floor) {
        // XZ positions of the four corners
        double ax = nx,     az = nz;      // A = notch corner
        double bx = 1 - nx, bz = nz;      // B = same z as notch, opposite x
        double cx = nx,     cz = 1 - nz;  // C = same x as notch, opposite z
        double dx = 1 - nx, dz = 1 - nz;  // D = far corner

        // Y positions: flat face, raised notch level, and full-height level
        double flat   = topHalf ? 1.0 : 0.0;
        double floorY = topHalf ? 1.0 - floor : floor;
        double fullY  = topHalf ? 1.0 - apexTop : apexTop;

        List<double[]> e = new ArrayList<>(13);

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
        // 3 diagonal edges meeting at the (possibly raised) notch corner
        e.add(new double[]{bx, fullY, bz,   ax, floorY, az}); // adjacent face diagonal
        e.add(new double[]{ax, floorY, az,  cx, fullY, cz}); // adjacent face diagonal
        e.add(new double[]{dx, fullY, dz,   ax, floorY, az}); // slope ridge
        // phase 2: vertical edge at the raised notch
        if (floor > 0.0) {
            e.add(new double[]{ax, flat, az,  ax, floorY, az});
        }

        double[] flat_array = new double[e.size() * 6];
        for (int i = 0; i < e.size(); i++) {
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
     * A = apex corner (full height), B and C = adjacent corners, D = opposite
     * corner (the slope ridge goes from D up to A). In phase 1 ({@code floor == 0})
     * B, C, D sit on the flat face; in phase 2 ({@code floor > 0}) they are raised
     * to {@code floor} and vertical edges connect them down to the flat face.
     */
    private static double[] buildCornerEdges(int ax, int az, boolean topHalf, double apexTop, double floor) {
        double aXp = ax,     aZp = az;
        double bXp = 1 - ax, bZp = az;
        double cXp = ax,     cZp = 1 - az;
        double dXp = 1 - ax, dZp = 1 - az;

        double flat   = topHalf ? 1.0 : 0.0;
        double floorY = topHalf ? (1.0 - floor) : floor;
        double fullY  = topHalf ? (1.0 - apexTop) : apexTop;

        List<double[]> e = new ArrayList<>(11);

        // 4 bottom (flat face) edges
        e.add(new double[]{0, flat, 0,   1, flat, 0});
        e.add(new double[]{1, flat, 0,   1, flat, 1});
        e.add(new double[]{1, flat, 1,   0, flat, 1});
        e.add(new double[]{0, flat, 1,   0, flat, 0});
        // Apex vertical edge (full height, flat to apex tip)
        e.add(new double[]{aXp, flat, aZp,   aXp, fullY, aZp});
        // Two adjacent face diagonals (B and C at eave level to apex top)
        e.add(new double[]{bXp, floorY, bZp,   aXp, fullY, aZp});
        e.add(new double[]{cXp, floorY, cZp,   aXp, fullY, aZp});
        // Slope ridge (opposite corner D at eave level to apex top)
        e.add(new double[]{dXp, floorY, dZp,   aXp, fullY, aZp});
        // phase 2: vertical edges at the raised eave corners B, C, D
        if (floor > 0.0) {
            e.add(new double[]{bXp, flat, bZp,   bXp, floorY, bZp});
            e.add(new double[]{cXp, flat, cZp,   cXp, floorY, cZp});
            e.add(new double[]{dXp, flat, dZp,   dXp, floorY, dZp});
        }

        double[] flat_array = new double[e.size() * 6];
        for (int i = 0; i < e.size(); i++) {
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
