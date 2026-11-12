package com.chroniclecaravans.world;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How a road crew builds: a three-block-wide road that follows the ground in a straight line between two
 * stops. Natural ground becomes a dirt path (stone bricks for a paved road), water gets a plank bridge, plants,
 * snow and tree trunks in the way are cleared. Blocks placed by players are never touched.
 */
public final class RoadWorks {
    private RoadWorks() {}

    public static double length(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Point of the road {@code t} blocks from the start (x, z). */
    public static double[] at(BlockPos from, BlockPos to, double t) {
        double len = Math.max(1e-3, length(from, to));
        double k = Math.min(1.0, t / len);
        return new double[] {from.getX() + 0.5 + (to.getX() - from.getX()) * k, from.getZ() + 0.5 + (to.getZ() - from.getZ()) * k};
    }

    /** Is the column at {@code t} loaded (the crew waits otherwise)? */
    public static boolean loaded(ServerLevel level, BlockPos from, BlockPos to, double t) {
        double[] p = at(from, to, t);
        return level.isLoaded(BlockPos.containing(p[0], from.getY(), p[1]));
    }

    /** Builds the slice of road at {@code t}. Returns the height the builder should stand at. */
    public static int buildSlice(ServerLevel level, BlockPos from, BlockPos to, double t, boolean paved) {
        double len = Math.max(1e-3, length(from, to));
        double dx = (to.getX() - from.getX()) / len, dz = (to.getZ() - from.getZ()) / len;
        double[] c = at(from, to, t);
        int standY = from.getY();
        for (int w = -1; w <= 1; w++) {
            int x = Mth.floor(c[0] - dz * w), z = Mth.floor(c[1] + dx * w);
            int y = buildCell(level, x, z, paved, w == 0);
            if (w == 0) standY = y + 1;
        }
        // Keep clear of the stops themselves
        if (paved && ((int) t) % 16 == 8 && t > 4 && t < len - 4) {
            int x = Mth.floor(c[0] - dz * 2.4), z = Mth.floor(c[1] + dx * 2.4);
            lampPost(level, x, z);
        }
        return standY;
    }

    /** Height of the ground at (x, z): under plants, snow, leaves and tree trunks. */
    public static BlockPos ground(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int min = y - 24;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        while (p.getY() > min) {
            BlockState s = level.getBlockState(p);
            if (!level.getFluidState(p).isEmpty()) break;
            if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.SNOW)
                    || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.BAMBOO) || s.is(Blocks.VINE)) {
                p.move(0, -1, 0);
                continue;
            }
            break;
        }
        return p.immutable();
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.STONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE)
                || s.is(Blocks.GRANITE) || s.is(Blocks.TUFF) || s.is(Blocks.CALCITE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.PACKED_ICE)
                || s.is(Blocks.ICE) || s.is(Blocks.POWDER_SNOW);
    }

    private static int buildCell(ServerLevel level, int x, int z, boolean paved, boolean centre) {
        BlockPos g = ground(level, x, z);
        BlockState s = level.getBlockState(g);
        if (level.getFluidState(g).is(FluidTags.WATER)) {
            // A bridge at the water line
            if (level.getFluidState(g).isSource() || s.is(Blocks.WATER)) level.setBlock(g, Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            clearAbove(level, g);
            return g.getY();
        }
        if (!level.getFluidState(g).isEmpty()) return g.getY();
        if (s.is(ChronicleCaravans.ROADS)) {
            clearAbove(level, g);
            return g.getY();
        }
        if (!natural(s)) return g.getY();
        BlockState road;
        if (paved) {
            float r = level.getRandom().nextFloat();
            road = r < 0.12F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : r < 0.24F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState();
        } else if (s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.CLAY)) {
            road = Blocks.DIRT_PATH.defaultBlockState();
        } else if (s.is(Blocks.SAND) || s.is(Blocks.SANDSTONE)) {
            road = centre ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
        } else if (s.is(Blocks.RED_SAND) || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.TERRACOTTA)) {
            road = Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        } else {
            road = Blocks.GRAVEL.defaultBlockState();
        }
        clearAbove(level, g);
        // A dirt path needs air above it, and gravel something below it
        if (road.is(Blocks.GRAVEL) && level.getBlockState(g.below()).canBeReplaced()) road = Blocks.COBBLESTONE.defaultBlockState();
        level.setBlock(g, road, Block.UPDATE_ALL);
        return g.getY();
    }

    /** Plants, snow, leaves and tree trunks above the road go; anything else stays. */
    private static void clearAbove(ServerLevel level, BlockPos g) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos p = g.above(dy);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.is(BlockTags.LOGS)) {
                // Fell the whole trunk: the leaves decay by themselves
                BlockPos.MutableBlockPos q = p.mutable();
                for (int i = 0; i < 24 && level.getBlockState(q).is(BlockTags.LOGS); i++) {
                    level.destroyBlock(q, false);
                    q.move(0, 1, 0);
                }
            } else if (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                    || s.is(Blocks.SNOW) || s.is(Blocks.BAMBOO) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.CACTUS)) {
                if (level.getFluidState(p).isEmpty()) level.destroyBlock(p, false);
            }
        }
    }

    private static void lampPost(ServerLevel level, int x, int z) {
        BlockPos g = ground(level, x, z);
        if (!natural(level.getBlockState(g))) return;
        BlockPos post = g.above();
        if (!level.getBlockState(post).canBeReplaced() || !level.getBlockState(post.above()).canBeReplaced()
                || !level.getBlockState(post.above(2)).canBeReplaced()) return;
        level.setBlock(post, Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.above(2), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
    }
}
