package com.flash.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Physical layout of the transformation chamber. The model is about 4 x 5.4 x 4
 * blocks, so the controller block is accompanied by invisible "part" blocks that
 * carry the collision of every cell the machine touches.
 *
 * <p>Everything is described once, in model space for {@code facing=south}
 * (+x east, +z south, controller cell = [0,1] x [0,1] x [0,1]). Shapes and cell
 * offsets for the other facings are derived with the same rotation the
 * blockstate uses, so collision, selection box and redstone area always match
 * what is drawn.</p>
 */
final class MachineLayout {

    /** Model cells along x (0..4), z (-3..1, stored as 0..4) and y (0..5). */
    static final int CELLS_X = 5;
    static final int CELLS_Z = 5;
    static final int CELLS_Y = 6;
    static final int Z_MIN = -3;

    /** Solid volumes: minX, minY, minZ, maxX, maxY, maxZ (blocks, model space). */
    private static final double[][] SOLID = {
            // v10 (tools/gen_machines_v3.py): platform, deck, ramp, shell, pillars, crown, towers, side port
            {0.19, 0, -0.9, 3.81, 0.25, 1},
            {1.05, 0, -1.76, 2.95, 0.25, 1.86},
            {0.65, 0, -1.3, 3.35, 0.25, 1.4},
            {0.66, 0.25, -0.65, 3.34, 0.5, 0.75},
            {1.3, 0.25, -1.29, 2.7, 0.5, 1.39},
            {1, 0.25, -0.95, 3, 0.5, 1.05},
            {1.44, 0.25, 1.36, 2.56, 0.39, 1.74},
            {1.44, 0, 1.74, 2.56, 0.25, 1.89},
            {1.6, 0.5, -1.4, 2.4, 3.9, -1.2},
            {2.45, 0.5, -1.35, 2.9, 3.9, -0.9},
            {1.1, 0.5, -1.35, 1.55, 3.9, -0.9},
            {3.25, 0.5, -0.35, 3.45, 3.9, 0.45},
            {0.55, 0.5, -0.35, 0.75, 3.9, 0.45},
            {2.95, 0.5, -0.85, 3.4, 3.9, -0.4},
            {0.6, 0.5, -0.85, 1.05, 3.9, -0.4},
            {2.95, 0.5, 0.5, 3.4, 3.9, 0.95},
            {0.6, 0.5, 0.5, 1.05, 3.9, 0.95},
            {2.8, 0.25, 0.85, 3.15, 4.25, 1.2},
            {0.85, 0.25, 0.85, 1.2, 4.25, 1.2},
            {0.5, 3.875, -0.75, 3.5, 4.25, 0.85},
            {1.2, 3.875, -1.45, 2.8, 4.25, 1.55},
            {0.9, 3.875, -1.05, 3.1, 4.25, 1.15},
            {0.875, 4.25, -0.55, 3.125, 4.5, 0.65},
            {1.4, 4.25, -1.075, 2.6, 4.5, 1.175},
            {1.25, 4.5, -0.35, 2.75, 4.75, 0.45},
            {1.6, 4.5, -0.7, 2.4, 4.75, 0.8},
            {1.69, 4.75, -0.26, 2.31, 5.125, 0.36},
            {1.95, 5.125, 0, 2.05, 5.375, 0.1},
            {3.1, 0, -1.73, 3.72, 3.94, -1.11},
            {0.28, 0, -1.73, 0.9, 3.94, -1.11},
            {0, 0, -0.22, 0.31, 1.875, 0.66},
            {0.31, 1, -0.04, 0.66, 1.31, 0.33},
    };

    private static final double EPS = 1.0E-4D;

    /** [facing 2D index][mx][mz][my], null where the cell is empty. */
    private static final VoxelShape[][][][] SHAPES = new VoxelShape[4][CELLS_X][CELLS_Z][CELLS_Y];
    /** [facing 2D index][mx][mz] -> world {dx, dz} of the cell relative to the controller. */
    private static final int[][][][] OFFSETS = new int[4][CELLS_X][CELLS_Z][2];
    /** [facing 2D index] -> offsets {dx, dy, dz} of every part cell (controller excluded). */
    private static final int[][][] PART_OFFSETS = new int[4][][];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int f = facing.get2DDataValue();
            List<int[]> parts = new ArrayList<>();
            for (int mx = 0; mx < CELLS_X; mx++) {
                for (int mz = 0; mz < CELLS_Z; mz++) {
                    int cz = mz + Z_MIN;
                    double[] a = TransformationChamberBlock.rotate(facing, mx, cz);
                    double[] b = TransformationChamberBlock.rotate(facing, mx + 1, cz + 1);
                    int dx = (int) Math.floor(Math.min(a[0], b[0]) + EPS);
                    int dz = (int) Math.floor(Math.min(a[1], b[1]) + EPS);
                    OFFSETS[f][mx][mz][0] = dx;
                    OFFSETS[f][mx][mz][1] = dz;
                    for (int my = 0; my < CELLS_Y; my++) {
                        VoxelShape shape = build(facing, mx, cz, my, dx, dz);
                        SHAPES[f][mx][mz][my] = shape;
                        boolean controller = mx == 0 && cz == 0 && my == 0;
                        if (shape != null && !controller) {
                            parts.add(new int[] {dx, my, dz});
                        }
                    }
                }
            }
            PART_OFFSETS[f] = parts.toArray(new int[0][]);
        }
    }

    private MachineLayout() {
    }

    private static VoxelShape build(Direction facing, int mx, int cz, int my, int dx, int dz) {
        VoxelShape result = null;
        for (double[] box : SOLID) {
            double x0 = Math.max(box[0], mx);
            double x1 = Math.min(box[3], mx + 1);
            double y0 = Math.max(box[1], my);
            double y1 = Math.min(box[4], my + 1);
            double z0 = Math.max(box[2], cz);
            double z1 = Math.min(box[5], cz + 1);
            if (x1 - x0 < EPS || y1 - y0 < EPS || z1 - z0 < EPS) {
                continue;
            }
            double[] a = TransformationChamberBlock.rotate(facing, x0, z0);
            double[] b = TransformationChamberBlock.rotate(facing, x1, z1);
            VoxelShape piece = Shapes.box(
                    clamp(Math.min(a[0], b[0]) - dx), y0 - my, clamp(Math.min(a[1], b[1]) - dz),
                    clamp(Math.max(a[0], b[0]) - dx), y1 - my, clamp(Math.max(a[1], b[1]) - dz));
            result = result == null ? piece : Shapes.or(result, piece);
        }
        return result == null ? null : result.optimize();
    }

    private static double clamp(double v) {
        return Math.max(0.0D, Math.min(1.0D, v));
    }

    static VoxelShape shape(Direction facing, int mx, int mz, int my) {
        VoxelShape shape = SHAPES[facing.get2DDataValue()][mx][mz][my];
        return shape == null ? Shapes.empty() : shape;
    }

    /** Shape of the controller block itself. */
    static VoxelShape controllerShape(Direction facing) {
        return shape(facing, 0, 3, 0);
    }

    /** World offset {dx, dz} of the part cell (mx, mz) relative to the controller. */
    static int[] cellOffset(Direction facing, int mx, int mz) {
        return OFFSETS[facing.get2DDataValue()][mx][mz];
    }

    /** All part cells relative to the controller: {dx, dy, dz}. */
    static int[][] partOffsets(Direction facing) {
        return PART_OFFSETS[facing.get2DDataValue()];
    }

    /** Model cell indices {mx, mz, my} that sit at the given world offset, or null. */
    static int[] cellAt(Direction facing, int dx, int dy, int dz) {
        int f = facing.get2DDataValue();
        for (int mx = 0; mx < CELLS_X; mx++) {
            for (int mz = 0; mz < CELLS_Z; mz++) {
                if (OFFSETS[f][mx][mz][0] == dx && OFFSETS[f][mx][mz][1] == dz) {
                    return new int[] {mx, mz, dy};
                }
            }
        }
        return null;
    }

    static List<BlockPos> partPositions(BlockPos origin, Direction facing) {
        int[][] offsets = partOffsets(facing);
        List<BlockPos> list = new ArrayList<>(offsets.length);
        for (int[] o : offsets) {
            list.add(origin.offset(o[0], o[1], o[2]));
        }
        return list;
    }
}
