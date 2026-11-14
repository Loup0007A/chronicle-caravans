package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The buildings of a merchant town: what each one needs, and its blocks, in the order the builders lay them.
 * Plans are drawn in local coordinates (x across, z in depth, the front at z = depth - 1 facing south, y = 0 the
 * level where one stands) and turned to face the milestone.
 */
public final class Buildings {
    private Buildings() {}

    /** What the builders ask for, in plain materials (any planks, any logs, any wool...). */
    public enum Material {
        PLANKS(Items.OAK_PLANKS), LOGS(Items.OAK_LOG), STONE(Items.COBBLESTONE), GLASS(Items.GLASS_PANE), WOOL(Items.WHITE_WOOL), IRON(Items.IRON_INGOT);

        public final Item icon;

        Material(Item icon) {
            this.icon = icon;
        }

        public boolean accepts(ItemStack s) {
            return switch (this) {
                case PLANKS -> s.is(ItemTags.PLANKS);
                case LOGS -> s.is(ItemTags.LOGS);
                case STONE -> s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.STONE_BRICKS) || s.is(Items.COBBLED_DEEPSLATE)
                        || s.is(Items.SANDSTONE);
                case GLASS -> s.is(Items.GLASS) || s.is(Items.GLASS_PANE);
                case WOOL -> s.is(ItemTags.WOOL);
                case IRON -> s.is(Items.IRON_INGOT);
            };
        }
    }

    /** The buildings, with their size (across, in depth), the most blocks they rise and what they need. */
    public enum Type {
        TOWN_HALL(9, 9, 11, 0, new int[] {40, 16, 32, 6, 0, 2}),
        STALL(5, 5, 5, 0, new int[] {12, 4, 0, 0, 10, 0}),
        HOUSE(7, 7, 9, 0, new int[] {30, 10, 16, 4, 0, 0}),
        WELL(5, 5, 5, 0, new int[] {6, 4, 24, 0, 0, 1}),
        WAREHOUSE(9, 7, 9, 0, new int[] {40, 16, 24, 0, 0, 2}),
        INN(9, 9, 11, 0, new int[] {44, 16, 32, 6, 6, 0}),
        STABLE(9, 7, 5, 18, new int[] {24, 14, 8, 0, 0, 2}),
        WATCHTOWER(5, 5, 12, 0, new int[] {12, 8, 48, 0, 0, 2});

        public final int w, d, height;
        /** Farthest from the milestone (0: anywhere in the town). */
        public final int maxDist;
        private final int[] needs;

        Type(int w, int d, int height, int maxDist, int[] needs) {
            this.w = w;
            this.d = d;
            this.height = height;
            this.maxDist = maxDist;
            this.needs = needs;
        }

        public int need(Material m) {
            return needs[m.ordinal()];
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** What the town's menu shows for it. */
        public Item icon() {
            return switch (this) {
                case TOWN_HALL -> Items.BELL;
                case STALL -> Items.RED_WOOL;
                case HOUSE -> Items.RED_BED;
                case WELL -> Items.WATER_BUCKET;
                case WAREHOUSE -> Items.BARREL;
                case INN -> ChronicleCaravans.INN_SIGN_ITEM.get();
                case STABLE -> ChronicleCaravans.HITCHING_POST_ITEM.get();
                case WATCHTOWER -> Items.LANTERN;
            };
        }

        public static Type byIndex(int i) {
            Type[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** The town's style, after its climate. */
    public enum Palette {
        OAK(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_DOOR, Blocks.COBBLESTONE, Blocks.STONE_BRICKS),
        SPRUCE(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR, Blocks.COBBLESTONE, Blocks.STONE_BRICKS),
        DESERT(Blocks.CUT_SANDSTONE, Blocks.ACACIA_LOG, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE, Blocks.ACACIA_DOOR, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE);

        final Block planks, log, stairs, slab, fence, door, stone, fine;

        Palette(Block planks, Block log, Block stairs, Block slab, Block fence, Block door, Block stone, Block fine) {
            this.planks = planks;
            this.log = log;
            this.stairs = stairs;
            this.slab = slab;
            this.fence = fence;
            this.door = door;
            this.stone = stone;
            this.fine = fine;
        }

        /** Hot lands build in sandstone, cold ones in spruce, the rest in oak. */
        public static Palette forTemperature(float t) {
            return t >= 1.5F ? DESERT : t < 0.25F ? SPRUCE : OAK;
        }
    }

    /** One step of the work: clear a block, fill under the floor, or lay a block of the building. */
    public record Op(BlockPos pos, BlockState state, int kind) {}

    public static final int CLEAR = 0, FILL = 1, BUILD = 2;

    // ---------------------------------------------------------------- footprint and turning

    /** The rotation that turns the plan's front (south) to {@code front}. */
    public static Rotation rotationFor(Direction front) {
        return switch (front) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Size on the ground once turned: {across x, across z}. */
    public static int[] size(Type t, Rotation r) {
        return r == Rotation.CLOCKWISE_90 || r == Rotation.COUNTERCLOCKWISE_90 ? new int[] {t.d, t.w} : new int[] {t.w, t.d};
    }

    /** World position of a local position, {@code origin} being the north-west corner of the turned footprint. */
    public static BlockPos place(Type t, Rotation r, BlockPos origin, int x, int y, int z) {
        int wx, wz;
        switch (r) {
            case CLOCKWISE_90 -> {
                wx = t.d - 1 - z;
                wz = x;
            }
            case CLOCKWISE_180 -> {
                wx = t.w - 1 - x;
                wz = t.d - 1 - z;
            }
            case COUNTERCLOCKWISE_90 -> {
                wx = z;
                wz = t.w - 1 - x;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        return origin.offset(wx, y, wz);
    }

    /** Where the door opens (just outside it), in the world. */
    public static BlockPos door(Type t, Rotation r, BlockPos origin) {
        return place(t, r, origin, t.w / 2, 0, t.d);
    }

    // ---------------------------------------------------------------- the work, step by step

    /**
     * Every step of a building: the ground cleared (top down), the foundation filled, then the building from the
     * floor up, details last. {@code variant} picks small differences (a stall's trade, a house's beds).
     */
    public static List<Op> ops(Type t, Palette p, Rotation r, BlockPos origin, int variant) {
        Plan plan = new Plan(p);
        switch (t) {
            case TOWN_HALL -> hall(plan);
            case STALL -> stall(plan, variant);
            case HOUSE -> house(plan, variant);
            case WELL -> well(plan);
            case WAREHOUSE -> warehouse(plan);
            case INN -> inn(plan);
            case STABLE -> stable(plan);
            case WATCHTOWER -> tower(plan);
        }
        List<Op> out = new ArrayList<>();
        // Clear: the footprint and a ring around it, from the top
        for (int y = t.height + 1; y >= 0; y--) {
            for (int x = -1; x <= t.w; x++) {
                for (int z = -1; z <= t.d; z++) {
                    boolean ring = x < 0 || z < 0 || x >= t.w || z >= t.d;
                    if (ring && y > 3) continue;
                    out.add(new Op(place(t, r, origin, x, y, z), Blocks.AIR.defaultBlockState(), CLEAR));
                }
            }
        }
        // Foundation: firm ground under the floor
        for (int y = -2; y >= -5; y--) {
            for (int x = 0; x < t.w; x++) {
                for (int z = 0; z < t.d; z++) out.add(new Op(place(t, r, origin, x, y, z), p.stone.defaultBlockState(), FILL));
            }
        }
        for (int i = 0; i < plan.pos.size(); i++) {
            int[] q = plan.pos.get(i);
            out.add(new Op(place(t, r, origin, q[0], q[1], q[2]), plan.states.get(i).rotate(r), BUILD));
        }
        return out;
    }

    /** The blocks of a plan, in local coordinates and laying order. */
    static final class Plan {
        final Palette p;
        final List<int[]> pos = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        /** A block set again (a window in a wall, a door) replaces the first one, keeping its place in the order. */
        private final java.util.Map<Long, Integer> index = new java.util.HashMap<>();

        Plan(Palette p) {
            this.p = p;
        }

        void set(int x, int y, int z, BlockState s) {
            long key = BlockPos.asLong(x, y, z);
            Integer i = index.get(key);
            if (i != null) {
                states.set(i, s);
                return;
            }
            index.put(key, pos.size());
            pos.add(new int[] {x, y, z});
            states.add(s);
        }

        void set(int x, int y, int z, Block b) {
            set(x, y, z, b.defaultBlockState());
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(x, y, z, s);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
            fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
        }

        /** The outline of a rectangle at height y. */
        void ring(int x0, int z0, int x1, int z1, int y, BlockState s) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, s);
                set(x, y, z1, s);
            }
            for (int z = z0 + 1; z < z1; z++) {
                set(x0, y, z, s);
                set(x1, y, z, s);
            }
        }

        void ring(int x0, int z0, int x1, int z1, int y, Block b) {
            ring(x0, z0, x1, z1, y, b.defaultBlockState());
        }

        BlockState stairs(Direction facing, boolean top) {
            return p.stairs.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        }

        BlockState slab(boolean top) {
            return p.slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
        }

        BlockState log(Direction.Axis axis) {
            return p.log.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
        }

        void door(int x, int y, int z, Direction facing) {
            BlockState d = p.door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            set(x, y, z, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        }

        /** A bed, its foot at (x, z) and its head towards {@code head}. */
        void bed(int x, int y, int z, Direction head, Block bed) {
            BlockState b = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, head);
            set(x, y, z, b.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
            set(x + head.getStepX(), y, z + head.getStepZ(), b.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        }

        void torch(int x, int y, int z, Direction facing) {
            set(x, y, z, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
        }

        void lantern(int x, int y, int z, boolean hanging) {
            set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging));
        }

        /**
         * A gable roof over x0..x1, z0..z1 (walls), ridge along x: the eaves one block out, their lowest row at
         * {@code y} (the top of the walls), the gable ends filled in.
         */
        void gable(int x0, int z0, int x1, int z1, int y) {
            int depth = z1 - z0 + 3;
            int rows = depth / 2;
            for (int i = 0; i < rows; i++) {
                int zn = z0 - 1 + i, zs = z1 + 1 - i;
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    set(x, y + i, zn, stairs(Direction.SOUTH, false));
                    set(x, y + i, zs, stairs(Direction.NORTH, false));
                }
                // Gable ends under this row
                if (i > 0) {
                    for (int z = zn + 1; z < zs; z++) {
                        set(x0, y + i, z, p.planks.defaultBlockState());
                        set(x1, y + i, z, p.planks.defaultBlockState());
                    }
                }
            }
            if (depth % 2 == 1) {
                int zr = z0 - 1 + rows;
                for (int x = x0 - 1; x <= x1 + 1; x++) set(x, y + rows, zr, slab(false));
                set(x0, y + rows - 1, zr, p.planks.defaultBlockState());
                set(x1, y + rows - 1, zr, p.planks.defaultBlockState());
            }
        }

        /** Walls of a timber-framed building: a stone course, planks above, logs at the corners. */
        void walls(int w, int d, int top) {
            ring(0, 0, w - 1, d - 1, 0, p.stone);
            for (int y = 1; y <= top; y++) ring(0, 0, w - 1, d - 1, y, p.planks);
            for (int y = 0; y <= top; y++) {
                set(0, y, 0, log(Direction.Axis.Y));
                set(w - 1, y, 0, log(Direction.Axis.Y));
                set(0, y, d - 1, log(Direction.Axis.Y));
                set(w - 1, y, d - 1, log(Direction.Axis.Y));
            }
        }

        void floor(int w, int d) {
            ring(0, 0, w - 1, d - 1, -1, p.stone);
            fill(1, -1, 1, w - 2, -1, d - 2, p.planks);
        }
    }

    // ---------------------------------------------------------------- the buildings

    private static void house(Plan b, int variant) {
        b.floor(7, 7);
        b.walls(7, 7, 3);
        // Windows, then the door in the front (south) wall
        b.set(0, 1, 3, Blocks.GLASS_PANE);
        b.set(6, 1, 3, Blocks.GLASS_PANE);
        b.set(3, 1, 0, Blocks.GLASS_PANE);
        b.set(1, 1, 6, Blocks.GLASS_PANE);
        b.set(5, 1, 6, Blocks.GLASS_PANE);
        b.door(3, 0, 6, Direction.SOUTH);
        b.gable(0, 0, 6, 6, 3);
        // Two beds, a table, light
        Block bed = variant % 2 == 0 ? Blocks.RED_BED : Blocks.BLUE_BED;
        b.bed(1, 0, 2, Direction.NORTH, bed);
        b.bed(5, 0, 2, Direction.NORTH, bed);
        b.set(3, 0, 1, Blocks.CRAFTING_TABLE);
        b.set(5, 0, 5, Blocks.BARREL);
        b.torch(3, 2, 1, Direction.SOUTH);
        b.torch(1, 2, 5, Direction.NORTH);
        b.set(2, 0, 7, Blocks.POTTED_POPPY);
    }

    private static final Block[] TRADES = {Blocks.SMOKER, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.LOOM, Blocks.SMITHING_TABLE};

    private static void stall(Plan b, int variant) {
        b.fill(0, -1, 0, 4, -1, 4, Blocks.COARSE_DIRT);
        for (int y = 0; y <= 2; y++) {
            b.set(0, y, 0, b.log(Direction.Axis.Y));
            b.set(4, y, 0, b.log(Direction.Axis.Y));
            b.set(0, y, 4, b.log(Direction.Axis.Y));
            b.set(4, y, 4, b.log(Direction.Axis.Y));
        }
        // The striped awning
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) b.set(x, 3, z, x % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL);
        }
        // The counter, the trade's workstation, the goods
        b.set(1, 0, 3, b.slab(true));
        b.set(2, 0, 3, b.slab(true));
        b.set(3, 0, 3, TRADES[Math.floorMod(variant, TRADES.length)]);
        b.set(1, 0, 1, Blocks.BARREL);
        b.set(3, 0, 1, Blocks.HAY_BLOCK);
        b.lantern(2, 2, 2, true);
    }

    private static void well(Plan b) {
        b.ring(0, 0, 4, 4, -1, b.p.stone);
        b.ring(1, 1, 3, 3, -1, b.p.stone);
        b.set(2, -2, 2, b.p.stone);
        b.set(2, -1, 2, Blocks.WATER);
        b.ring(1, 1, 3, 3, 0, b.p.stone);
        for (int y = 1; y <= 2; y++) {
            b.set(1, y, 1, b.p.fence);
            b.set(3, y, 1, b.p.fence);
            b.set(1, y, 3, b.p.fence);
            b.set(3, y, 3, b.p.fence);
        }
        b.fill(1, 3, 1, 3, 3, 3, b.slab(false));
        b.set(2, 3, 2, b.p.planks);
        b.lantern(2, 2, 2, true);
    }

    private static void hall(Plan b) {
        b.ring(0, 0, 8, 8, -1, b.p.fine);
        b.fill(1, -1, 1, 7, -1, 7, b.p.planks);
        b.ring(0, 0, 8, 8, 0, b.p.fine);
        for (int y = 1; y <= 4; y++) b.ring(0, 0, 8, 8, y, b.p.planks);
        for (int y = 0; y <= 4; y++) {
            for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 8}, {8, 8}, {4, 0}, {0, 4}, {8, 4}}) b.set(c[0], y, c[1], b.log(Direction.Axis.Y));
        }
        for (int y = 1; y <= 2; y++) {
            for (int k : new int[] {2, 6}) {
                b.set(k, y, 0, Blocks.GLASS_PANE);
                b.set(0, y, k, Blocks.GLASS_PANE);
                b.set(8, y, k, Blocks.GLASS_PANE);
            }
            b.set(2, y, 8, Blocks.GLASS_PANE);
            b.set(6, y, 8, Blocks.GLASS_PANE);
        }
        b.door(4, 0, 8, Direction.SOUTH);
        b.gable(0, 0, 8, 8, 4);
        // The council room: a lectern, the town's bell, a red carpet to the door
        b.set(4, 0, 2, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        for (int z = 3; z <= 7; z++) b.set(4, 0, z, Blocks.RED_CARPET);
        b.set(2, 0, 2, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        b.set(6, 0, 2, Blocks.BARREL);
        b.set(7, 0, 1, Blocks.CRAFTING_TABLE);
        b.torch(4, 3, 1, Direction.SOUTH);
        b.torch(1, 2, 4, Direction.EAST);
        b.torch(7, 2, 4, Direction.WEST);
        b.lantern(3, 0, 9, false);
        b.lantern(5, 0, 9, false);
    }

    private static void warehouse(Plan b) {
        b.floor(9, 7);
        b.walls(9, 7, 4);
        for (int y = 0; y <= 4; y++) b.set(4, y, 0, b.log(Direction.Axis.Y));
        b.door(4, 0, 6, Direction.SOUTH);
        b.set(2, 2, 6, Blocks.GLASS_PANE);
        b.set(6, 2, 6, Blocks.GLASS_PANE);
        b.gable(0, 0, 8, 6, 4);
        // Barrels along the back wall, two high
        for (int x = 1; x <= 7; x++) {
            if (x == 4) continue;
            b.set(x, 0, 1, Blocks.BARREL);
            if (x % 2 == 1) b.set(x, 1, 1, Blocks.BARREL);
        }
        b.set(1, 0, 4, Blocks.HAY_BLOCK);
        b.set(7, 0, 4, Blocks.CRAFTING_TABLE);
        b.torch(4, 2, 1, Direction.SOUTH);
    }

    private static void inn(Plan b) {
        b.floor(9, 9);
        b.walls(9, 9, 4);
        for (int y = 1; y <= 2; y++) {
            for (int k : new int[] {2, 6}) {
                b.set(0, y, k, Blocks.GLASS_PANE);
                b.set(8, y, k, Blocks.GLASS_PANE);
            }
            b.set(2, y, 8, Blocks.GLASS_PANE);
            b.set(6, y, 8, Blocks.GLASS_PANE);
        }
        b.door(4, 0, 8, Direction.SOUTH);
        b.gable(0, 0, 8, 8, 4);
        // Three beds for the travellers, a table and the fire
        b.bed(1, 0, 2, Direction.NORTH, Blocks.WHITE_BED);
        b.bed(4, 0, 2, Direction.NORTH, Blocks.WHITE_BED);
        b.bed(7, 0, 2, Direction.NORTH, Blocks.WHITE_BED);
        b.set(2, 0, 5, Blocks.CRAFTING_TABLE);
        b.set(6, 0, 5, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        b.set(4, 0, 5, b.slab(true));
        b.torch(4, 2, 1, Direction.SOUTH);
        b.torch(1, 2, 7, Direction.NORTH);
        b.torch(7, 2, 7, Direction.NORTH);
        // The sign out front, and the till beside it
        b.set(6, 0, 9, ChronicleCaravans.INN_SIGN.get());
        b.set(7, 0, 9, Blocks.BARREL);
    }

    private static void stable(Plan b) {
        b.fill(0, -1, 0, 8, -1, 6, Blocks.COARSE_DIRT);
        for (int y = 0; y <= 2; y++) {
            for (int x : new int[] {0, 4, 8}) {
                b.set(x, y, 0, b.log(Direction.Axis.Y));
                b.set(x, y, 6, b.log(Direction.Axis.Y));
            }
        }
        for (int y = 0; y <= 2; y++) {
            for (int x = 1; x <= 7; x++) if (x != 4) b.set(x, y, 0, b.p.planks);
            for (int z = 1; z <= 5; z++) {
                b.set(0, y, z, b.p.planks);
                b.set(8, y, z, b.p.planks);
            }
        }
        b.fill(-1, 3, -1, 9, 3, 7, b.slab(false));
        // Stalls: hay, troughs, fences between them
        for (int z = 1; z <= 3; z++) {
            b.set(3, 0, z, b.p.fence);
            b.set(5, 0, z, b.p.fence);
        }
        b.set(1, 0, 1, Blocks.HAY_BLOCK);
        b.set(2, 0, 1, Blocks.COMPOSTER);
        b.set(6, 0, 1, Blocks.HAY_BLOCK);
        b.set(7, 0, 1, Blocks.HAY_BLOCK);
        b.lantern(4, 2, 3, true);
        // The hitching posts, out front
        b.set(1, 0, 7, ChronicleCaravans.HITCHING_POST.get());
        b.set(7, 0, 7, ChronicleCaravans.HITCHING_POST.get());
    }

    private static void tower(Plan b) {
        b.fill(0, -1, 0, 4, -1, 4, b.p.stone);
        for (int y = 0; y <= 7; y++) b.ring(0, 0, 4, 4, y, b.p.stone);
        b.fill(1, 8, 1, 3, 8, 3, b.p.planks);
        b.ring(0, 0, 4, 4, 8, b.p.stone);
        b.set(2, 4, 0, Blocks.GLASS_PANE);
        b.set(0, 4, 2, Blocks.GLASS_PANE);
        b.set(4, 4, 2, Blocks.GLASS_PANE);
        b.door(2, 0, 4, Direction.SOUTH);
        // A ladder up the back wall, through a hatch in the platform
        for (int y = 0; y <= 8; y++) b.set(2, y, 1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        b.ring(0, 0, 4, 4, 9, b.p.fence.defaultBlockState());
        for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) b.lantern(c[0], 10, c[1], false);
        b.torch(2, 2, 3, Direction.NORTH);
    }
}
