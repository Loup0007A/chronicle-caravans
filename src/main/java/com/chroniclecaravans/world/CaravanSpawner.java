package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.entity.CaravanMaster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Sends caravans to the milestones, and watches what happens to them. */
@EventBusSubscriber(modid = ChronicleCaravans.MODID)
public final class CaravanSpawner {
    private CaravanSpawner() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 0) return;
        CaravanData data = CaravanData.get(level);
        if (data.stops().isEmpty()) return;
        long now = level.getGameTime();
        List<CaravanMaster> active = caravans(level);
        List<BlockPos> stale = new ArrayList<>();
        for (CaravanData.Stop stop : data.stops()) {
            if (now < stop.nextVisit || !level.isLoaded(stop.pos)) continue;
            if (!level.getBlockState(stop.pos).is(ChronicleCaravans.MILESTONE.get())) {
                stale.add(stop.pos);
                continue;
            }
            if (level.getNearestPlayer(stop.pos.getX(), stop.pos.getY(), stop.pos.getZ(), 128, false) == null) continue;
            if (active.size() >= CCConfig.MAX_CARAVANS.get()) break;
            if (headingTo(active, stop.pos) != null) {
                stop.nextVisit = now + 1200;
                continue;
            }
            CaravanMaster master = spawnCaravan(level, stop, null);
            long interval = CCConfig.VISIT_INTERVAL.get() * 20L;
            // No free spot around the milestone: try again in a minute
            stop.nextVisit = now + (master == null ? 1200 : (long) (interval * (0.75 + level.random.nextDouble() * 0.5)));
            data.setDirty();
            if (master != null) active.add(master);
        }
        for (BlockPos p : stale) data.removeStop(p);
    }

    /** A bandit of an ambush dies: credit the player who killed it. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;
        if (!e.getEntity().getPersistentData().hasUUID(CaravanMaster.AMBUSH_TAG)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (level.getEntity(e.getEntity().getPersistentData().getUUID(CaravanMaster.AMBUSH_TAG)) instanceof CaravanMaster master) {
            master.onBanditKilled(player);
        }
    }

    /** Hitting a pack llama costs reputation. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof TraderLlama llama) || !(llama.level() instanceof ServerLevel level)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        long now = level.getGameTime();
        if (now - llama.getPersistentData().getLong("ChronicleCaravansScold") < 40) return;
        for (CaravanMaster master : caravans(level)) {
            if (master.llamaIds().contains(llama.getUUID())) {
                llama.getPersistentData().putLong("ChronicleCaravansScold", now);
                CaravanData.get(level).addReputation(player, -3, "reason.chroniclecaravans.attack");
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
        for (CaravanMaster m : list) {
            if (m.state() != CaravanMaster.State.LEAVING && stop.equals(m.target())) return m;
        }
        return null;
    }

    /** A new caravan appears 30 to 44 blocks from the milestone and walks to it. */
    public static CaravanMaster spawnCaravan(ServerLevel level, CaravanData.Stop stop, Region region) {
        BlockPos at = findGround(level, stop.pos, 30, 44, level.random);
        if (at == null) at = findGround(level, stop.pos, 12, 30, level.random);
        if (at == null) return null;
        if (region == null) region = Region.foreignTo(Region.at(level, stop.pos), level.random);
        return spawnCaravanAt(level, at, stop.pos, region);
    }

    public static CaravanMaster spawnCaravanAt(ServerLevel level, BlockPos at, BlockPos target, Region region) {
        CaravanMaster master = ChronicleCaravans.CARAVAN_MASTER.get().create(level);
        if (master == null) return null;
        RandomSource random = level.random;
        float yaw = target == null ? random.nextFloat() * 360F
                : (float) (Math.toDegrees(Math.atan2(target.getZ() - at.getZ(), target.getX() - at.getX())) - 90.0);
        master.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
        master.setup(region, target);
        master.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        level.addFreshEntity(master);

        int min = CCConfig.MIN_LLAMAS.get();
        int max = Math.max(min, CCConfig.MAX_LLAMAS.get());
        int count = min + random.nextInt(max - min + 1);
        // The llamas line up behind the master, away from the destination
        double back = Math.toRadians(yaw - 90.0);
        for (int i = 0; i < count; i++) {
            TraderLlama llama = EntityType.TRADER_LLAMA.create(level);
            if (llama == null) continue;
            int x = (int) Math.floor(at.getX() + 0.5 + Math.cos(back) * 3.0 * (i + 1));
            int z = (int) Math.floor(at.getZ() + 0.5 + Math.sin(back) * 3.0 * (i + 1));
            BlockPos p = standable(level, x, z, at.getY());
            if (p == null) p = findGround(level, at, 2, 6, random);
            if (p == null) p = at;
            llama.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0F);
            llama.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
            llama.setChest(true);
            llama.setDespawnDelay(6000);
            level.addFreshEntity(llama);
            if (i == 0) llama.setLeashedTo(master, true);
            master.addLlama(llama);
        }
        return master;
    }

    /** A random dry, free spot on the surface, between {@code min} and {@code max} blocks from the centre. */
    public static BlockPos findGround(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
        BlockPos fallback = null;
        for (int i = 0; i < 40; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(center.getX() + 0.5 + Math.cos(a) * d);
            int z = (int) Math.floor(center.getZ() + 0.5 + Math.sin(a) * d);
            BlockPos p = standable(level, x, z, center.getY());
            if (p == null) continue;
            if (Math.abs(p.getY() - center.getY()) <= 10) return p;
            if (fallback == null) fallback = p;
        }
        return fallback;
    }

    private static BlockPos standable(ServerLevel level, int x, int z, int nearY) {
        if (!level.isLoaded(new BlockPos(x, nearY, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        BlockPos below = p.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP)) return null;
        if (!level.getFluidState(p).isEmpty() || !level.getFluidState(below).isEmpty()) return null;
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return null;
        if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return null;
        return p;
    }
}
