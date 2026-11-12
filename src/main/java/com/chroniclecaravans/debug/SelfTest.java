package com.chroniclecaravans.debug;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * In-game test for the CI (JVM flag -Dchroniclecaravans.selftest=true, dedicated server only).
 * It builds a short road and a milestone at the world spawn, sends a caravan down the road, checks that it
 * arrives, trades and can be ambushed, then replays the scene for the test client that takes the screenshots.
 */
@EventBusSubscriber(modid = ChronicleCaravans.MODID)
public final class SelfTest {
    private static final boolean ON = Boolean.getBoolean("chroniclecaravans.selftest");
    private static final int ROAD = 34;

    private static int tick = -1;
    private static BlockPos stop;
    private static CaravanMaster master;
    private static final List<String> failures = new ArrayList<>();
    private static int joinTick = -1;
    private static ServerPlayer viewer;

    private SelfTest() {}

    private static void log(String s) {
        ChronicleCaravans.LOG.info("[SELFTEST] {}", s);
    }

    private static void check(boolean ok, String what) {
        log((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures.add(what);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (ON) tick = 0;
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent e) {
        if (ON && e.getEntity() instanceof ServerPlayer p) {
            viewer = p;
            joinTick = tick;
            p.setGameMode(GameType.SPECTATOR);
            log("viewer joined at tick " + tick);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (!ON || tick < 0) return;
        ServerLevel level = e.getServer().overworld();
        tick++;
        try {
            if (tick == 1) prepare(level);
            if (tick == 100) stage(level);
            if (tick == 110) check(master != null && master.isAlive(), "caravan spawned");
            if (tick == 800 && master != null) afterTravel(level);
            if (tick == 820 && master != null) check(master.startAmbush(level) > 0, "ambush started");
            if (tick == 900) {
                clearBandits(level);
                log("RESULT: " + (failures.isEmpty() ? "OK" : "FAIL " + failures));
                log("READY");
            }
            if (joinTick >= 0) scene(level, tick - joinTick);
        } catch (Throwable t) {
            ChronicleCaravans.LOG.error("[SELFTEST] exception", t);
            failures.add(t.toString());
            log("RESULT: FAIL " + failures);
            log("READY");
            tick = -1;
        }
    }

    private static void prepare(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        ChunkPos c = new ChunkPos(spawn);
        for (int dx = -4; dx <= 5; dx++) for (int dz = -4; dz <= 4; dz++) level.setChunkForced(c.x + dx, c.z + dz, true);
        level.setDayTime(1000);
        level.setWeatherParameters(100000, 0, false, false);
        // A still, empty stage: no night, no rain, no wandering monsters
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
    }

    /** A milestone at the spawn and a straight dirt path leading east from it. */
    private static void stage(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        y = Math.max(y, level.getSeaLevel() + 1);
        stop = new BlockPos(spawn.getX(), y, spawn.getZ());
        // A wide clearing, so the cameras of the test client are never inside a tree or a hill
        for (int dx = -14; dx <= ROAD + 16; dx++) {
            for (int dz = -12; dz <= 18; dz++) {
                BlockPos p = new BlockPos(stop.getX() + dx, y - 1, stop.getZ() + dz);
                boolean path = Math.abs(dz) <= 1 && dx >= 2;
                level.setBlock(p, path ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 1; dy <= 3; dy++) level.setBlock(p.below(dy), Blocks.DIRT.defaultBlockState(), 2);
                for (int dy = 1; dy <= 26; dy++) {
                    if (!level.getBlockState(p.above(dy)).isAir()) level.setBlock(p.above(dy), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        level.setBlockAndUpdate(stop, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        CaravanData data = CaravanData.get(level);
        data.addStop(stop, null, "SelfTest", "", Long.MAX_VALUE / 4);
        check(data.stop(stop) != null, "milestone registered");
        villages(level, data);
        spawn(level, Region.DUNES);
    }

    /** A bell 56 blocks away is found and registered as a village stop, then removed again. */
    private static void villages(ServerLevel level, CaravanData data) {
        int bx = stop.getX(), bz = stop.getZ() - 56;
        int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos bell = new BlockPos(bx, by, bz);
        level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
        int added = CaravanSpawner.scanVillages(level, bell);
        CaravanData.Stop v = data.stop(bell);
        log("village scan added " + added + " at " + bell);
        check(v != null && v.village, "village bell registered as a stop");
        check(CaravanSpawner.scanVillages(level, bell) == 0, "village not registered twice");
        BlockPos road = CaravanSpawner.findGround(level, stop.offset(ROAD / 2, 0, 0), 0, 3, level.random);
        check(road != null && level.getBlockState(road.below()).is(Blocks.DIRT_PATH), "arrival point prefers the road");
        data.removeStop(bell);
        level.setBlockAndUpdate(bell, Blocks.AIR.defaultBlockState());
    }

    private static void spawn(ServerLevel level, Region region) {
        master = CaravanSpawner.spawnCaravanAt(level, stop.offset(ROAD, 0, 0), stop, region);
    }

    private static void afterTravel(ServerLevel level) {
        double dist = Math.sqrt(master.distanceToSqr(stop.getX() + 0.5, stop.getY(), stop.getZ() + 0.5));
        log("state=" + master.state() + " distance=" + String.format("%.1f", dist) + " pos=" + master.blockPosition());
        check(master.isAlive(), "master alive");
        check(master.state() == CaravanMaster.State.HALTED, "caravan reached the milestone and halted");
        int llamas = 0;
        int following = 0;
        for (java.util.UUID id : master.llamaIds()) {
            if (level.getEntity(id) instanceof TraderLlama l && l.isAlive()) {
                llamas++;
                if (l.distanceToSqr(master) < 24 * 24) following++;
            }
        }
        log("llamas=" + llamas + " following=" + following);
        check(llamas >= 2, "draft llama and at least one pack llama");
        check(following == llamas, "llamas followed the master");
        // The wagon: present, hitched behind the draft llama, on the road, in the colours of the region
        Entity w = master.wagonId() == null ? null : level.getEntity(master.wagonId());
        check(w instanceof CaravanWagon, "wagon exists");
        if (w instanceof CaravanWagon wagon) {
            double gap = Math.sqrt(wagon.distanceToSqr(master));
            log("wagon gap=" + String.format("%.2f", gap) + " yaw=" + String.format("%.0f", wagon.getYRot()) + " y=" + String.format("%.2f", wagon.getY()) + " master y=" + String.format("%.2f", master.getY()));
            check(gap > 3.8 && gap < 7.5, "wagon hitched at the right distance");
            check(Math.abs(wagon.getZ() - (stop.getZ() + 0.5)) < 1.6, "wagon stayed on the road");
            check(wagon.region() == Region.DUNES, "wagon has the caravan's region");
            if (level.getEntity(master.llamaIds().get(0)) instanceof TraderLlama draft) {
                check(draft.getLeashHolder() == master, "draft llama led by the master");
                check(draft.isNoAi(), "train members are moved by the master");
            }
            if (master.llamaIds().size() > 1 && level.getEntity(master.llamaIds().get(1)) instanceof TraderLlama pack) {
                check(pack.getLeashHolder() == wagon, "pack llama tied to the wagon");
            }
        }
        check(master.getOffers().size() >= 9, "stock has offers (" + master.getOffers().size() + ")");
    }

    private static void clearBandits(ServerLevel level) {
        int n = 0;
        for (Entity en : level.getAllEntities()) {
            if (en.getPersistentData().hasUUID(CaravanMaster.AMBUSH_TAG)) {
                en.discard();
                n++;
            }
        }
        log("bandits=" + n);
        check(n >= 2, "bandits spawned");
    }

    /** What the test client sees: a new caravan walks down the road, halts, then gets ambushed. */
    private static void scene(ServerLevel level, int t) {
        if (viewer == null || viewer.hasDisconnected()) return;
        if (t == 60) {
            for (CaravanMaster m : CaravanSpawner.caravans(level)) m.vanish(level);
            level.setDayTime(1000);
            spawn(level, Region.DUNES);
        }
        if (t == 760 && master != null && master.isAlive()) master.startAmbush(level);
        if (t % 5 != 0) return;
        double x, y, z, lx, ly, lz;
        if (t < 60 || master == null || !master.isAlive()) {
            x = stop.getX() + 8; y = stop.getY() + 4; z = stop.getZ() + 9;
            lx = stop.getX() + 14; ly = stop.getY() + 1; lz = stop.getZ();
        } else if (master.state() == CaravanMaster.State.TRAVELING) {
            // Side view of the whole train (it stretches about 10 blocks behind the master)
            x = master.getX() + 1; y = stop.getY() + 4.0; z = stop.getZ() + 11;
            lx = master.getX() + 5; ly = stop.getY() + 1.0; lz = stop.getZ() + 0.5;
        } else if (t < 760) {
            // Three-quarter close-up of the wagon
            Entity w = master.wagonId() == null ? null : level.getEntity(master.wagonId());
            double wx = w != null ? w.getX() : master.getX() + 5;
            x = wx - 4.2; y = stop.getY() + 3.0; z = stop.getZ() + 5.2;
            lx = wx + 0.3; ly = stop.getY() + 1.1; lz = stop.getZ() + 0.5;
        } else {
            x = master.getX() + 2; y = stop.getY() + 8; z = stop.getZ() + 14;
            lx = master.getX() + 4; ly = stop.getY() + 1; lz = stop.getZ() - 1;
        }
        double dx = lx - x, dy = ly - y, dz = lz - z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        viewer.teleportTo(level, x, y, z, yaw, pitch);
    }
}
