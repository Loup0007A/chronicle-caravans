package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.entity.CaravanGuard;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
//#if neoforge
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
//#else
//$$ import net.minecraftforge.event.TickEvent;
//$$ import net.minecraftforge.event.entity.living.LivingAttackEvent;
//#endif

/** Sends caravans to the milestones, and watches what happens to them. */
@EventBusSubscriber(modid = ChronicleCaravans.MODID)
public final class CaravanSpawner {
    private CaravanSpawner() {}

    //#if neoforge
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post e) {
        tickLevel(e.getLevel());
    }
    //#elif mc120
    //$$ @SubscribeEvent
    //$$ public static void onLevelTick(TickEvent.LevelTickEvent e) {
    //$$     if (e.phase == TickEvent.Phase.END) tickLevel(e.level);
    //$$ }
    //#else
    //$$ @SubscribeEvent
    //$$ public static void onLevelTick(TickEvent.LevelTickEvent.Post e) {
    //$$     tickLevel(e.level);
    //$$ }
    //#endif

    /** A caravan not seen for this long is asleep in unloaded land: it may be moved where it is needed. */
    private static final long ASLEEP = 2400;
    /** A caravan not seen for this long is forgotten for good. */
    private static final long LOST = 72000 * 3;

    /** Last comparator signal of each milestone (the comparators are told when it changes). */
    private static final java.util.Map<BlockPos, Integer> SIGNALS = new java.util.HashMap<>();

    /** Comparator signal of a milestone: 15 a caravan halted here, 1 to 7 a caravan coming (closer = stronger), else 0. */
    public static int signal(ServerLevel level, BlockPos stop) {
        Integer v = SIGNALS.get(stop);
        return v != null ? v : computeSignal(stop, caravans(level));
    }

    private static int computeSignal(BlockPos stop, List<CaravanMaster> all) {
        int best = 0;
        for (CaravanMaster m : all) {
            if (stop.equals(m.currentStop())) return 15;
            if (stop.equals(m.target()) && m.state() != CaravanMaster.State.LEAVING && !m.headingToInn()) {
                double d = Math.sqrt(m.distanceToSqr(stop.getX() + 0.5, stop.getY(), stop.getZ() + 0.5));
                best = Math.max(best, 1 + (int) Math.round(6 * Math.max(0, 1 - d / CCConfig.LINK_RANGE.get())));
            }
        }
        return best;
    }

    private static void updateSignals(ServerLevel level) {
        if (!CCConfig.REDSTONE.get()) return;
        List<CaravanMaster> all = caravans(level);
        for (CaravanData.Stop s : CaravanData.get(level).stops()) {
            if (s.village || !level.isLoaded(s.pos)) continue;
            int v = computeSignal(s.pos, all);
            Integer old = SIGNALS.put(s.pos, v);
            if (old == null || old != v) level.updateNeighbourForOutputSignal(s.pos, level.getBlockState(s.pos).getBlock());
        }
    }

    private static void tickLevel(Level world) {
        if (!(world instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 20 == 0) updateSignals(level);
        if (level.getGameTime() % 1200 == 0) Logistics.cleanDepots(level);
        if (level.getGameTime() % 100 != 0) return;
        CaravanData data = CaravanData.get(level);
        if (CCConfig.VILLAGE_STOPS.get() && level.getGameTime() % 600 == 0) {
            for (ServerPlayer p : level.players()) scanVillages(level, p.blockPosition());
        }
        long now = level.getGameTime();
        data.caravans().values().removeIf(c -> now - c.lastSeen > LOST || c.lastSeen > now + 1000);
        if (data.stops().isEmpty()) return;
        List<CaravanMaster> active = caravans(level);
        List<BlockPos> stale = new ArrayList<>();
        for (CaravanData.Stop stop : data.stops()) {
            if (now < stop.nextVisit || !level.isLoaded(stop.pos)) continue;
            boolean valid = stop.village ? level.getBlockState(stop.pos).is(Blocks.BELL) && CCConfig.VILLAGE_STOPS.get()
                    : level.getBlockState(stop.pos).is(ChronicleCaravans.MILESTONE.get());
            if (!valid) {
                stale.add(stop.pos);
                continue;
            }
            if (level.getNearestPlayer(stop.pos.getX(), stop.pos.getY(), stop.pos.getZ(), 128, false) == null) continue;
            if (dispatch(level, data, stop, active) != null) data.setDirty();
        }
        for (BlockPos p : stale) data.removeStopAndRoutes(p);
    }

    /** How many caravans a stop takes at once: its tier, with one hitching post nearby for each caravan beyond the first. */
    public static int capacity(ServerLevel level, CaravanData.Stop stop) {
        if (stop.village || stop.tier <= 1 || !CCConfig.RELAYS.get()) return 1;
        int posts = CaravanData.get(level).postsNear(level, stop.pos, com.chroniclecaravans.block.HitchingPostBlock.RANGE).size();
        return Math.max(1, Math.min(Math.min(stop.tier, CCConfig.MAX_TIER.get()), 1 + posts));
    }

    /** The caravans serving a stop: travelling there, halted there, or resting (inn, camp) on their way there. */
    public static List<CaravanMaster> serving(List<CaravanMaster> all, BlockPos stop) {
        List<CaravanMaster> out = new ArrayList<>();
        for (CaravanMaster m : all) if (m.state() != CaravanMaster.State.LEAVING && stop.equals(m.destination())) out.add(m);
        return out;
    }

    /** The stop already has as many caravans as it can take. */
    public static boolean full(ServerLevel level, CaravanData.Stop stop) {
        return serving(caravans(level), stop.pos).size() >= capacity(level, stop);
    }

    /** Extra caravans the upgraded milestones make room for, on top of the configured number. */
    private static int extraRoom(ServerLevel level, CaravanData data) {
        int extra = 0;
        for (CaravanData.Stop s : data.stops()) if (s.tier > 1 && !s.village) extra += capacity(level, s) - 1;
        return Math.min(extra, 16);
    }

    /**
     * A stop wants a caravan. In order: one already heading there (or resting on its way); a free caravan close by,
     * sent over; a caravan asleep in faraway land, moved here; a brand new one (rarely: at most one every few minutes,
     * and never more than the configured number). An upgraded milestone takes several. Nobody sets out from afar at
     * night: the caravans of the area sleep at the inns or camp. Returns the caravan that comes, or null (try again later).
     */
    public static CaravanMaster dispatch(ServerLevel level, CaravanData data, CaravanData.Stop stop, List<CaravanMaster> active) {
        long now = level.getGameTime();
        List<CaravanMaster> coming = serving(active, stop.pos);
        if (coming.size() >= capacity(level, stop)) {
            stop.nextVisit = now + 1200;
            return coming.get(0);
        }
        // 1. A loaded caravan with nothing better to do (one resting at an inn comes in the morning)
        double reach = CCConfig.LINK_RANGE.get() * (stop.hired ? 2.0 : 1.25);
        CaravanMaster best = null;
        for (CaravanMaster m : active) {
            if (coming.contains(m) || !m.available() || m.distanceToSqr(stop.pos.getX(), stop.pos.getY(), stop.pos.getZ()) > reach * reach) continue;
            if (best == null || m.distanceToSqr(stop.pos.getX(), stop.pos.getY(), stop.pos.getZ()) < best.distanceToSqr(stop.pos.getX(), stop.pos.getY(), stop.pos.getZ())) best = m;
        }
        if (best != null) {
            best.redirect(level, stop.pos);
            stop.nextVisit = now + 1200;
            return best;
        }
        if (Compat.isNight(level) && !stop.hired) {
            stop.nextVisit = now + 1200;
            return null;
        }
        // 2. A caravan asleep far away is moved here (the old one vanishes when its land wakes up)
        int known = data.caravans().size();
        java.util.UUID sleeper = null;
        for (java.util.Map.Entry<java.util.UUID, CaravanData.Caravan> e : data.caravans().entrySet()) {
            if (now - e.getValue().lastSeen > ASLEEP && !e.getValue().cargo && level.getEntity(e.getKey()) == null) {
                sleeper = e.getKey();
                break;
            }
        }
        Region region = null;
        boolean fresh;
        if (sleeper != null) {
            region = data.caravans().get(sleeper).region;
            fresh = false;
        } else {
            // 3. A new caravan, now and then
            int max = CCConfig.MAX_CARAVANS.get() + extraRoom(level, data) + (stop.hired ? 1 : 0);
            if (known >= max || (now < data.nextSpawn && !stop.hired)) {
                stop.nextVisit = now + 1200;
                return null;
            }
            fresh = true;
        }
        CaravanMaster master = spawnCaravan(level, stop, region);
        if (master == null) {
            stop.nextVisit = now + 1200;
            return null;
        }
        if (sleeper != null) data.forget(sleeper);
        if (fresh) data.nextSpawn = now + CCConfig.NEW_CARAVAN_COOLDOWN.get() * 20L;
        data.seen(master.getUUID(), now, master.blockPosition(), master.region(), stop.pos);
        stop.nextVisit = now + 1200;
        active.add(master);
        return master;
    }

    /**
     * Registers the village bells around {@code center} as stops (unless a stop is already close by).
     * The first caravan comes after a random part of the usual interval. Returns the number of new stops.
     */
    public static int scanVillages(ServerLevel level, BlockPos center) {
        CaravanData data = CaravanData.get(level);
        long interval = CCConfig.VISIT_INTERVAL.get() * 20L;
        int[] added = {0};
        level.getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), p -> true, center, 128, PoiManager.Occupancy.ANY)
                .forEach(bell -> {
                    if (!level.getBlockState(bell).is(Blocks.BELL) || data.nearestStop(bell, 48, null) != null) return;
                    data.addVillage(bell, level.getGameTime() + 200 + (long) (level.getRandom().nextDouble() * interval));
                    added[0]++;
                });
        return added[0];
    }

    /** A bandit of an ambush dies: credit the player who killed it. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;
        if (!Nbt.hasUUID(e.getEntity().getPersistentData(), CaravanMaster.AMBUSH_TAG)) return;
        java.util.UUID killer = e.getSource().getEntity() instanceof ServerPlayer player ? player.getUUID()
                : e.getSource().getEntity() instanceof CaravanGuard g ? g.buyer() : null;
        if (killer == null) return;
        if (level.getEntity(Nbt.getUUID(e.getEntity().getPersistentData(), CaravanMaster.AMBUSH_TAG)) instanceof CaravanMaster master) {
            master.onBanditKilled(level, killer);
            // Bounty Hunter: every bandit is worth a few emeralds
            if (Guild.has(CaravanData.get(level).skills(killer), Guild.Skill.BOUNTY_HUNTER)) {
                net.minecraft.world.level.block.Block.popResource(level, e.getEntity().blockPosition(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 2));
            }
        }
    }

    /** Hitting a pack llama costs reputation. */
    //#if neoforge
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        damaged(e.getEntity(), e.getSource());
    }
    //#else
    //$$ @SubscribeEvent
    //$$ public static void onDamage(LivingAttackEvent e) {
    //$$     damaged(e.getEntity(), e.getSource());
    //$$ }
    //#endif

    private static void damaged(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (!(entity instanceof TraderLlama llama) || !(llama.level() instanceof ServerLevel level)) return;
        if (!(source.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        long now = level.getGameTime();
        if (now - Nbt.getLong(llama.getPersistentData(), "ChronicleCaravansScold") < 40) return;
        for (CaravanMaster master : caravans(level)) {
            if (master.llamaIds().contains(llama.getUUID())) {
                llama.getPersistentData().putLong("ChronicleCaravansScold", now);
                CaravanData.get(level).addXp(player, -3, "reason.chroniclecaravans.attack");
                return;
            }
        }
    }

    public static List<CaravanMaster> caravans(ServerLevel level) {
        return new ArrayList<>(level.getEntities(ChronicleCaravans.CARAVAN_MASTER.get(), CaravanMaster::isAlive));
    }

    /** The caravan currently travelling to (or halted at) this milestone, if any. */
    public static CaravanMaster caravanHeadingTo(ServerLevel level, BlockPos stop) {
        return headingTo(caravans(level), stop);
    }

    private static CaravanMaster headingTo(List<CaravanMaster> list, BlockPos stop) {
        List<CaravanMaster> s = serving(list, stop);
        return s.isEmpty() ? null : s.get(0);
    }

    /** A caravan comes into view: it appears 48 to 80 blocks from the stop, out of sight of players, and walks to it. */
    public static CaravanMaster spawnCaravan(ServerLevel level, CaravanData.Stop stop, Region region) {
        BlockPos at = null;
        for (int i = 0; i < 6 && at == null; i++) {
            BlockPos p = findGround(level, stop.pos, 48, 80, level.getRandom());
            if (p != null && level.isPositionEntityTicking(p) && level.getNearestPlayer(p.getX(), p.getY(), p.getZ(), 40, false) == null) at = p;
        }
        if (at == null) at = findGround(level, stop.pos, 30, 44, level.getRandom());
        if (at == null) at = findGround(level, stop.pos, 12, 30, level.getRandom());
        if (at == null) return null;
        if (region == null) region = Region.foreignTo(Region.at(level, stop.pos), level.getRandom());
        return spawnCaravanAt(level, at, stop.pos, region);
    }

    public static CaravanMaster spawnCaravanAt(ServerLevel level, BlockPos at, BlockPos target, Region region) {
        CaravanMaster master = Compat.create(ChronicleCaravans.CARAVAN_MASTER.get(), level);
        if (master == null) return null;
        RandomSource random = level.getRandom();
        float yaw = target == null ? random.nextFloat() * 360F
                : (float) (Math.toDegrees(Math.atan2(target.getZ() - at.getZ(), target.getX() - at.getX())) - 90.0);
        master.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
        master.setup(region, target);
        Compat.finalizeSpawn(master, level, at);
        level.addFreshEntity(master);
        CaravanData.get(level).seen(master.getUUID(), level.getGameTime(), at, region, target);

        // The train: draft llama, covered wagon, then the pack llamas; the master lays it out on his trail
        master.seedTrail(level);
        int min = CCConfig.MIN_LLAMAS.get();
        int max = Math.max(min, CCConfig.MAX_LLAMAS.get());
        int packs = min + random.nextInt(max - min + 1);
        boolean withWagon = CCConfig.WAGON_ENABLED.get();
        int total = packs + (withWagon ? 1 : 0);
        for (int i = 0; i < total; i++) {
            TraderLlama llama = Compat.create(EntityType.TRADER_LLAMA, level);
            if (llama == null) continue;
            llama.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
            Compat.finalizeSpawn(llama, level, at);
            llama.setChest(!(withWagon && i == 0));
            llama.setDespawnDelay(6000);
            llama.setNoAi(true);
            level.addFreshEntity(llama);
            master.addLlama(llama);
            if (withWagon && i == 0) {
                CaravanWagon wagon = Compat.create(ChronicleCaravans.WAGON.get(), level);
                if (wagon != null) {
                    wagon.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
                    wagon.setRegion(region);
                    wagon.setMaster(master.getUUID());
                    level.addFreshEntity(wagon);
                    master.setWagon(wagon);
                }
            }
        }
        master.settleTrain(level);
        return master;
    }

    /**
     * A dry, free spot on the surface between {@code min} and {@code max} blocks from the centre: on a path when
     * there is one, otherwise on natural ground, never on a roof.
     */
    public static BlockPos findGround(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(center.getX() + 0.5 + Math.cos(a) * d);
            int z = (int) Math.floor(center.getZ() + 0.5 + Math.sin(a) * d);
            BlockPos p = standable(level, x, z, center.getY());
            if (p == null) continue;
            BlockState ground = level.getBlockState(p.below());
            if (ground.is(BlockTags.PLANKS) || ground.is(BlockTags.LOGS) || ground.is(BlockTags.STAIRS)
                    || ground.is(BlockTags.SLABS) || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.WOOL)) continue;
            int score = ground.is(ChronicleCaravans.ROADS) ? 0
                    : ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.SNOW) ? 10 : 20;
            score += Math.abs(p.getY() - center.getY()) > 10 ? 30 : Math.abs(p.getY() - center.getY());
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private static BlockPos standable(ServerLevel level, int x, int z, int nearY) {
        if (!level.isLoaded(new BlockPos(x, nearY, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        BlockPos below = p.below();
        BlockState ground = level.getBlockState(below);
        // Dirt paths are 15/16 high: any ground with a solid top half will do
        VoxelShape shape = ground.getCollisionShape(level, below);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.5) return null;
        if (!level.getFluidState(p).isEmpty() || !level.getFluidState(below).isEmpty()) return null;
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return null;
        if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return null;
        return p;
    }
}
