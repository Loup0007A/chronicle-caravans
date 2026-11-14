package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.RoadBuilder;
import com.chroniclecaravans.entity.TownMayor;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.Buildings.Material;
import com.chroniclecaravans.world.Buildings.Op;
import com.chroniclecaravans.world.Buildings.Type;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Merchant towns. Trade at a milestone brings it prosperity; a prosperous relay is founded as a hamlet, then grows
 * level by level (hamlet, market village, town, trade city). Each level plans buildings around the milestone; each
 * building waits at its plot for materials (left in its barrel, handed over at the town's ledger, or brought by the
 * caravans and paid from the town's treasury), then builders raise it block by block.
 */
public final class Towns {
    private Towns() {}

    public static final int MAX_LEVEL = 4;
    /** Prosperity needed for each level (scaled by the config). */
    private static final int[] LEVELS = {0, 60, 250, 600, 1200};
    /** What each level adds to the town. */
    private static final Type[][] PLANS = {
            {},
            {Type.TOWN_HALL, Type.STALL},
            {Type.HOUSE, Type.HOUSE, Type.WELL, Type.WAREHOUSE},
            {Type.INN, Type.STABLE, Type.STALL, Type.HOUSE},
            {Type.WATCHTOWER, Type.HOUSE, Type.STALL}};
    /** Nobody builds this close to the milestone: the market square. */
    private static final int SQUARE = 5;
    /** No ambush this close to a town with a watchtower. */
    public static final int WATCH_RANGE = 64;
    /** A caravan delivers this many blocks of materials per visit (twice with a warehouse); one emerald per eight. */
    public static final int SUPPLY_BATCH = 24, ITEMS_PER_EMERALD = 8;

    public static int threshold(int level) {
        if (level <= 0) return 0;
        if (level > MAX_LEVEL) return Integer.MAX_VALUE;
        return Math.max(1, (int) Math.round(LEVELS[level] * CCConfig.TOWN_GROWTH.get()));
    }

    public static Type[] plan(int level) {
        return level < 0 || level >= PLANS.length ? new Type[0] : PLANS[level];
    }

    // ---------------------------------------------------------------- lookups

    /** The founded town at a milestone, or null. */
    public static Town at(ServerLevel level, BlockPos stop) {
        if (stop == null || !CCConfig.TOWNS.get()) return null;
        Town t = CaravanData.get(level).town(stop);
        return t != null && t.level > 0 ? t : null;
    }

    public static int levelOf(ServerLevel level, BlockPos stop) {
        Town t = at(level, stop);
        return t == null ? 0 : t.level;
    }

    /** Finished market stalls (each makes the town's market better, up to three). */
    public static int stalls(ServerLevel level, BlockPos stop) {
        Town t = at(level, stop);
        return t == null ? 0 : Math.min(3, t.count(Type.STALL, true));
    }

    /** The stalls' effect on a deal at a caravan halted in the town: goods 4% cheaper, requests 4% better paid, per stall. */
    public static float priceFactor(ServerLevel level, BlockPos stop, MerchantOffer o) {
        int n = stalls(level, stop);
        if (n == 0) return 1F;
        return Region.kindOf(o) == Region.KIND_SELL ? 1F + 0.04F * n : 1F - 0.04F * n;
    }

    /** Within sight of a town's watchtower: bandits stay away. */
    public static boolean safe(ServerLevel level, BlockPos pos) {
        if (!CCConfig.TOWNS.get()) return false;
        for (Town t : CaravanData.get(level).towns()) {
            if (t.level <= 0 || t.count(Type.WATCHTOWER, true) == 0) continue;
            if (t.pos.distSqr(pos) < (double) WATCH_RANGE * WATCH_RANGE) return true;
        }
        return false;
    }

    /** How far a milestone's hitching posts may stand: farther in a town (its stable). */
    public static int postRange(ServerLevel level, BlockPos center) {
        return levelOf(level, center) > 0 ? 40 : com.chroniclecaravans.block.HitchingPostBlock.RANGE;
    }

    /** Extra caravans the towns make room for. */
    public static int extraRoom(ServerLevel level) {
        if (!CCConfig.TOWNS.get()) return 0;
        int n = 0;
        for (Town t : CaravanData.get(level).towns()) n += t.level;
        return Math.min(n, 12);
    }

    /** Caravans come this many times as often to a town. */
    public static double visitBoost(ServerLevel level, BlockPos stop) {
        return 1.0 + 0.25 * levelOf(level, stop);
    }

    // ---------------------------------------------------------------- prosperity and dues

    /** Trade at a milestone makes it prosper (and may found a town). */
    public static void prosper(ServerLevel level, BlockPos stop, int amount) {
        if (stop == null || amount <= 0 || !CCConfig.TOWNS.get() || level.dimension() != Level.OVERWORLD) return;
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop s = data.stop(stop);
        if (s == null || s.village) return;
        Town t = data.townOrCreate(stop);
        t.prosperity = Math.min(30000, t.prosperity + amount);
        data.setDirty();
    }

    /** A caravan halts at a milestone: prosperity, the town's dues, and the materials it brings for the works. */
    public static void caravanArrived(ServerLevel level, CaravanMaster m, BlockPos stop) {
        prosper(level, stop, 10);
        Town t = at(level, stop);
        if (t == null) return;
        t.treasury = Math.min(30000, t.treasury + t.level);
        supply(level, t, m);
        CaravanData.get(level).setDirty();
    }

    /** The caravan sells the town the materials its works lack, paid from the treasury. */
    public static int supply(ServerLevel level, Town t, CaravanMaster m) {
        if (!t.supply) return 0;
        // The first plot still waiting for materials (the builders may be at work on another one)
        Town.Building b = null;
        for (Town.Building x : t.buildings) {
            if (x.state == Town.WAITING && x.planned() && !x.supplied()) {
                b = x;
                break;
            }
        }
        if (b == null) return 0;
        int batch = SUPPLY_BATCH * (t.count(Type.WAREHOUSE, true) > 0 ? 2 : 1);
        batch = Math.min(batch, t.treasury * ITEMS_PER_EMERALD);
        int brought = 0;
        for (Material mat : Material.values()) {
            int n = Math.min(b.missing(mat), batch - brought);
            if (n <= 0) continue;
            b.got[mat.ordinal()] += n;
            brought += n;
        }
        if (brought == 0) return 0;
        int cost = (brought + ITEMS_PER_EMERALD - 1) / ITEMS_PER_EMERALD;
        t.treasury = Math.max(0, t.treasury - cost);
        Component msg = Component.translatable("msg.chroniclecaravans.town_supplied", m == null ? Component.literal("?") : m.getDisplayName(),
                brought, Component.translatable("building.chroniclecaravans." + b.type.key()), cost).withStyle(ChatFormatting.GRAY);
        tell(level, t, msg, 48);
        return brought;
    }

    // ---------------------------------------------------------------- materials

    /** Takes from {@code from} the materials the building still lacks. Returns how many items it took. */
    public static int deliver(Town.Building b, Container from) {
        if (b == null || b.state != Town.WAITING) return 0;
        int moved = 0;
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack s = from.getItem(i);
            if (s.isEmpty()) continue;
            for (Material m : Material.values()) {
                int want = b.missing(m);
                if (want <= 0 || !m.accepts(s)) continue;
                int n = Math.min(want, s.getCount());
                s.shrink(n);
                b.got[m.ordinal()] += n;
                moved += n;
                if (s.isEmpty()) from.setItem(i, ItemStack.EMPTY);
                break;
            }
        }
        if (moved > 0) from.setChanged();
        return moved;
    }

    // ---------------------------------------------------------------- the town's life

    public static void tick(ServerLevel level) {
        if (!CCConfig.TOWNS.get() || level.dimension() != Level.OVERWORLD) return;
        CaravanData data = CaravanData.get(level);
        if (data.towns().isEmpty()) return;
        long now = level.getGameTime();
        for (Town t : new ArrayList<>(data.towns())) {
            if (!level.isLoaded(t.pos)) continue;
            CaravanData.Stop stop = data.stop(t.pos);
            if (stop == null) continue;
            if (now % 20 == 0) slowTick(level, data, t, stop, now);
            if (t.level > 0) build(level, data, t);
        }
    }

    private static void slowTick(ServerLevel level, CaravanData data, Town t, CaravanData.Stop stop, long now) {
        if (t.level == 0) {
            if (t.prosperity >= threshold(1) && stop.tier >= CCConfig.TOWN_TIER.get()) found(level, data, t, stop);
            return;
        }
        for (Town.Building b : t.buildings) {
            if (b.state != Town.WAITING) continue;
            if (!b.planned()) {
                if (now >= t.nextPlanTry) {
                    t.nextPlanTry = now + 600;
                    if (plot(level, data, t, b)) data.setDirty();
                }
                continue;
            }
            // Materials left in the building's barrel
            if (b.crate != null && level.isLoaded(b.crate) && level.getBlockEntity(b.crate) instanceof Container c) {
                if (deliver(b, c) > 0) data.setDirty();
            }
            if (b.supplied()) startWork(level, data, t, b);
        }
        // A town with all its works done grows when it is prosperous enough
        if (t.level < MAX_LEVEL && t.allDone() && t.prosperity >= threshold(t.level + 1)) levelUp(level, data, t, stop);
        if (now % 200 == 0) keepMayor(level, data, t, now);
    }

    /** A prosperous relay becomes a hamlet. */
    static void found(ServerLevel level, CaravanData data, Town t, CaravanData.Stop stop) {
        t.level = 1;
        t.founded = level.getGameTime();
        t.palette = Buildings.Palette.forTemperature(level.getBiome(t.pos).value().getBaseTemperature());
        planLevel(level, data, t);
        data.setDirty();
        level.playSound(null, t.pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
        Component msg = Component.translatable("msg.chroniclecaravans.town_founded", stop.displayName()).withStyle(ChatFormatting.GOLD);
        tell(level, t, msg, 160);
        ServerPlayer owner = stop.owner == null ? null : level.getServer().getPlayerList().getPlayer(stop.owner);
        if (owner != null && owner.distanceToSqr(t.pos.getX(), t.pos.getY(), t.pos.getZ()) > 160 * 160) owner.sendSystemMessage(msg);
    }

    static void levelUp(ServerLevel level, CaravanData data, Town t, CaravanData.Stop stop) {
        t.level++;
        planLevel(level, data, t);
        data.setDirty();
        level.playSound(null, t.pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, t.pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.0F);
        Component msg = Component.translatable("msg.chroniclecaravans.town_level", stop.displayName(),
                Component.translatable("town.chroniclecaravans.level." + t.level)).withStyle(ChatFormatting.GOLD);
        tell(level, t, msg, 160);
        ServerPlayer owner = stop.owner == null ? null : level.getServer().getPlayerList().getPlayer(stop.owner);
        if (owner != null && owner.distanceToSqr(t.pos.getX(), t.pos.getY(), t.pos.getZ()) > 160 * 160) owner.sendSystemMessage(msg);
    }

    /** The buildings of the town's (new) level, each with its plot if one is free. */
    static void planLevel(ServerLevel level, CaravanData data, Town t) {
        for (Type type : plan(t.level)) {
            Town.Building b = new Town.Building(type, t.count(type, false) + t.buildings.size());
            t.buildings.add(b);
            plot(level, data, t, b);
        }
    }

    // ---------------------------------------------------------------- plots

    /** Finds a plot for a building around the milestone, facing it, and leaves the barrel for its materials. */
    static boolean plot(ServerLevel level, CaravanData data, Town t, Town.Building b) {
        Type type = b.type;
        RandomSource rnd = RandomSource.create(t.pos.asLong() * 31 + t.buildings.indexOf(b) * 7919L);
        int minR = SQUARE + 2 + Math.max(type.w, type.d) / 2;
        int maxR = type.maxDist > 0 ? Math.max(minR + 2, type.maxDist) : 44;
        for (int r = minR; r <= maxR; r += 2) {
            int steps = Math.max(8, r);
            double a0 = rnd.nextDouble() * Math.PI * 2;
            for (int k = 0; k < steps; k++) {
                double a = a0 + k * Math.PI * 2 / steps;
                int cx = t.pos.getX() + (int) Math.round(Math.cos(a) * r), cz = t.pos.getZ() + (int) Math.round(Math.sin(a) * r);
                int dx = t.pos.getX() - cx, dz = t.pos.getZ() - cz;
                Direction front = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                Rotation rot = Buildings.rotationFor(front);
                int[] sz = Buildings.size(type, rot);
                int ox = cx - sz[0] / 2, oz = cz - sz[1] / 2;
                Integer base = site(level, t, b, ox, oz, sz[0], sz[1]);
                if (base == null) continue;
                b.origin = new BlockPos(ox, base, oz);
                b.rot = rot;
                b.ops = null;
                placeCrate(level, b);
                return true;
            }
        }
        return false;
    }

    /** The plot's floor level if a building fits there (flat enough, nothing built, no road, no water); else null. */
    private static Integer site(ServerLevel level, Town t, Town.Building self, int ox, int oz, int sx, int sz) {
        // Clear of the square, of the other buildings (and a lane between them), of the town's edge
        int mx = t.pos.getX(), mz = t.pos.getZ();
        int nx = Math.max(ox, Math.min(mx, ox + sx - 1)), nz = Math.max(oz, Math.min(mz, oz + sz - 1));
        if (Math.max(Math.abs(nx - mx), Math.abs(nz - mz)) <= SQUARE) return null;
        for (Town.Building o : t.buildings) {
            if (o == self || !o.planned()) continue;
            int[] os = Buildings.size(o.type, o.rot);
            if (ox - 3 < o.origin.getX() + os[0] && o.origin.getX() < ox + sx + 3 && oz - 3 < o.origin.getZ() + os[1] && o.origin.getZ() < oz + sz + 3) return null;
        }
        for (int x = ox - 1; x <= ox + sx; x++) {
            for (int z = oz - 1; z <= oz + sz; z++) if (!level.isLoaded(new BlockPos(x, t.pos.getY(), z))) return null;
        }
        // Flat enough
        int[] heights = new int[sx * sz];
        int i = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = ox; x < ox + sx; x++) {
            for (int z = oz; z < oz + sz; z++) {
                int g = ground(level, x, z);
                heights[i++] = g;
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
            }
        }
        if (hi - lo > 3 || Math.abs(lo - t.pos.getY()) > 12) return null;
        java.util.Arrays.sort(heights);
        int base = heights[heights.length / 2] + 1;
        int height = self.type.height;
        // Nothing but nature in the way: no road, no player's block, no water
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = ox - 1; x <= ox + sx; x++) {
            for (int z = oz - 1; z <= oz + sz; z++) {
                boolean ring = x < ox || z < oz || x >= ox + sx || z >= oz + sz;
                BlockState g = level.getBlockState(p.set(x, base - 1, z));
                if (g.is(ChronicleCaravans.ROADS) || g.is(Blocks.DIRT_PATH)) return null;
                int top = ring ? base + 3 : base + height + 1;
                for (int y = ring ? base - 1 : base - 3; y <= top; y++) {
                    if (!natural(level.getBlockState(p.set(x, y, z)))) return null;
                }
            }
        }
        return base;
    }

    /** Nature only: air, ground, rock, plants, trees, snow. The towns never build over what players made. */
    public static boolean natural(BlockState s) {
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return false;
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.SNOW) || s.is(BlockTags.TERRACOTTA)) {
            return true;
        }
        // (26.x: the dirt tag no longer holds the grass block and its kin)
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK)) return true;
        if (s.is(Blocks.GRAVEL) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CLAY) || s.is(Blocks.ICE)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)) {
            return true;
        }
        return s.canBeReplaced();
    }

    /** The top of the ground in a column (under trees, plants and snow). */
    static int ground(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 32; i++, y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || (s.canBeReplaced() && s.getFluidState().isEmpty())) continue;
            return y;
        }
        return y;
    }

    private static void placeCrate(ServerLevel level, Town.Building b) {
        // Just outside the front corner of the plot
        for (int dx = 0; dx < 3; dx++) {
            BlockPos c = Buildings.place(b.type, b.rot, b.origin, dx, 0, b.type.d + 1);
            BlockState here = level.getBlockState(c);
            if (!natural(here) || !level.getBlockState(c.below()).isSolid()) continue;
            level.setBlock(c, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP), 3);
            b.crate = c;
            return;
        }
        b.crate = null;
    }

    // ---------------------------------------------------------------- building

    static List<Op> ops(Town t, Town.Building b) {
        if (b.ops == null && b.planned()) b.ops = Buildings.ops(b.type, t.palette, b.rot, b.origin, b.variant);
        return b.ops == null ? List.of() : b.ops;
    }

    /** All the materials are in: the barrel goes, a builder comes. */
    static void startWork(ServerLevel level, CaravanData data, Town t, Town.Building b) {
        b.state = Town.BUILDING;
        b.progress = 0;
        if (b.crate != null && level.getBlockState(b.crate).is(Blocks.BARREL)) {
            // What was left over in the barrel stays on the ground
            if (level.getBlockEntity(b.crate) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty()) Block.popResource(level, b.crate, s.copy());
                    c.setItem(i, ItemStack.EMPTY);
                }
            }
            level.setBlock(b.crate, Blocks.AIR.defaultBlockState(), 3);
        }
        b.crate = null;
        BlockPos door = Buildings.door(b.type, b.rot, b.origin);
        RoadBuilder builder = RoadBuilder.atSite(level, door, b.origin);
        b.builder = builder == null ? null : builder.getUUID();
        data.setDirty();
        Component msg = Component.translatable("msg.chroniclecaravans.town_works", Component.translatable("building.chroniclecaravans." + b.type.key()))
                .withStyle(ChatFormatting.GRAY);
        tell(level, t, msg, 48);
    }

    private static void build(ServerLevel level, CaravanData data, Town t) {
        Town.Building b = null;
        for (Town.Building x : t.buildings) if (x.state == Town.BUILDING) {
            b = x;
            break;
        }
        if (b == null) return;
        t.budget += CCConfig.TOWN_BUILD_SPEED.get() / 20F;
        List<Op> ops = ops(t, b);
        int checks = 0;
        BlockPos last = null;
        while (t.budget >= 1F && b.progress < ops.size() && checks < 256) {
            Op op = ops.get(b.progress);
            if (!level.isLoaded(op.pos())) return;
            checks++;
            b.progress++;
            if (apply(level, op)) {
                t.budget -= 1F;
                last = op.pos();
            }
        }
        if (t.budget > 4F) t.budget = 4F;
        if (last != null && b.builder != null && level.getEntity(b.builder) instanceof RoadBuilder rb) rb.workAt(last);
        if (b.progress >= ops.size()) complete(level, data, t, b);
        else if (level.getGameTime() % 100 == 0) data.setDirty();
    }

    /** One step of the work, if the block there is still nature (or air). Returns whether the world changed. */
    static boolean apply(ServerLevel level, Op op) {
        BlockState here = level.getBlockState(op.pos());
        switch (op.kind()) {
            case Buildings.CLEAR -> {
                if (here.isAir() || !natural(here)) return false;
                level.setBlock(op.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
                return true;
            }
            case Buildings.FILL -> {
                if (here.isSolid() || !(here.isAir() || here.canBeReplaced())) return false;
                level.setBlock(op.pos(), op.state(), 2);
                return true;
            }
            default -> {
                // Never over a player's block (natural() also refuses water that flowed in)
                if (here == op.state() || !natural(here)) return false;
                BlockState s = op.state();
                if (s.getBlock() instanceof CrossCollisionBlock || s.getBlock() instanceof WallBlock) s = Block.updateFromNeighbourShapes(s, level, op.pos());
                level.setBlock(op.pos(), s, 3);
                Block block = s.getBlock();
                if (block == ChronicleCaravans.INN_SIGN.get()) block.setPlacedBy(level, op.pos(), s, null, new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()));
                if (block == ChronicleCaravans.HITCHING_POST.get()) block.setPlacedBy(level, op.pos(), s, null, new ItemStack(ChronicleCaravans.HITCHING_POST_ITEM.get()));
                return true;
            }
        }
    }

    /** The building stands: its people move in, a path leads to the milestone. */
    static void complete(ServerLevel level, CaravanData data, Town t, Town.Building b) {
        b.state = Town.DONE;
        b.ops = null;
        if (b.builder != null && level.getEntity(b.builder) instanceof RoadBuilder rb) rb.leaveSite(level);
        b.builder = null;
        BlockPos door = Buildings.door(b.type, b.rot, b.origin);
        path(level, door, t.pos);
        switch (b.type) {
            case HOUSE -> {
                if (CCConfig.TOWN_VILLAGERS.get()) {
                    for (int i = 0; i < 2; i++) {
                        Mob v = Compat.create(EntityType.VILLAGER, level);
                        if (v == null) continue;
                        v.moveTo(door.getX() + 0.5 + i * 0.6, door.getY(), door.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
                        v.setPersistenceRequired();
                        level.addFreshEntity(v);
                        t.villagers++;
                    }
                }
            }
            case TOWN_HALL -> spawnMayor(level, t, b);
            default -> {
            }
        }
        level.playSound(null, door, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, door.getX() + 0.5, door.getY() + 1.5, door.getZ() + 0.5, 20, 1.5, 1.0, 1.5, 0.0);
        data.setDirty();
        CaravanData.Stop stop = data.stop(t.pos);
        Component msg = Component.translatable("msg.chroniclecaravans.town_built", Component.translatable("building.chroniclecaravans." + b.type.key()),
                stop == null ? Component.literal("?") : stop.displayName()).withStyle(ChatFormatting.GREEN);
        tell(level, t, msg, 64);
    }

    /** A dirt path from a door to the milestone, over the grass only (never over a roof: it follows the ground from the door). */
    static void path(ServerLevel level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int n = Math.max(Math.abs(dx), Math.abs(dz));
        int y = from.getY() - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 0; k <= n - 2; k++) {
            int x = from.getX() + Math.round((float) dx * k / Math.max(1, n)), z = from.getZ() + Math.round((float) dz * k / Math.max(1, n));
            // The ground near the last step: a block or two up or down
            int g = Integer.MIN_VALUE;
            for (int gy = y + 2; gy >= y - 3; gy--) {
                BlockState s = level.getBlockState(p.set(x, gy, z));
                if (!s.isAir() && !s.canBeReplaced()) {
                    g = gy;
                    break;
                }
            }
            if (g == Integer.MIN_VALUE) continue;
            y = g;
            BlockPos at = new BlockPos(x, g, z);
            BlockState s = level.getBlockState(at);
            if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL))) continue;
            if (!level.getBlockState(at.above()).isAir()) continue;
            level.setBlock(at, Blocks.DIRT_PATH.defaultBlockState(), 2);
        }
    }

    /** Finishes every building of the town at once (tests, and the builders' catching up). */
    public static void finishAll(ServerLevel level, Town t) {
        CaravanData data = CaravanData.get(level);
        for (Town.Building b : new ArrayList<>(t.buildings)) {
            if (b.state == Town.DONE) continue;
            if (!b.planned() && !plot(level, data, t, b)) continue;
            for (Material m : Material.values()) b.got[m.ordinal()] = Math.max(b.got[m.ordinal()], b.type.need(m));
            if (b.state == Town.WAITING) startWork(level, data, t, b);
            List<Op> ops = ops(t, b);
            while (b.progress < ops.size()) apply(level, ops.get(b.progress++));
            complete(level, data, t, b);
        }
    }

    // ---------------------------------------------------------------- the mayor

    static void spawnMayor(ServerLevel level, Town t, Town.Building hall) {
        BlockPos door = Buildings.door(hall.type, hall.rot, hall.origin);
        TownMayor m = TownMayor.spawn(level, door, t.pos);
        t.mayor = m == null ? null : m.getUUID();
    }

    private static void keepMayor(ServerLevel level, CaravanData data, Town t, long now) {
        Town.Building hall = null;
        for (Town.Building b : t.buildings) if (b.type == Type.TOWN_HALL && b.state == Town.DONE) hall = b;
        if (hall == null) return;
        Entity m = t.mayor == null ? null : level.getEntity(t.mayor);
        if (m != null && m.isAlive()) {
            t.mayorSeen = now;
            return;
        }
        BlockPos door = Buildings.door(hall.type, hall.rot, hall.origin);
        if (!level.isLoaded(door)) return;
        // Not around for a minute (and his door is loaded): a new mayor is elected
        if (now - t.mayorSeen < 1200) return;
        spawnMayor(level, t, hall);
        t.mayorSeen = now;
        data.setDirty();
    }

    // ---------------------------------------------------------------- news

    static void tell(ServerLevel level, Town t, Component msg, double range) {
        if (!CCConfig.ANNOUNCEMENTS.get()) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(t.pos.getX() + 0.5, t.pos.getY(), t.pos.getZ() + 0.5) < range * range) p.sendSystemMessage(msg);
        }
    }

    /** Villagers living around the town (counted, not only those who came with the houses). */
    public static int residents(ServerLevel level, Town t) {
        return level.getEntities(EntityType.VILLAGER, new AABB(t.pos).inflate(48), e -> true).size();
    }
}
