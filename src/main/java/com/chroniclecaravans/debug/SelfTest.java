package com.chroniclecaravans.debug;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.block.InnSignBlock;
import com.chroniclecaravans.block.MilestoneBlock;
import com.chroniclecaravans.entity.CaravanGuard;
import com.chroniclecaravans.entity.RoadBuilder;
import com.chroniclecaravans.item.AtlasItem;
import com.chroniclecaravans.menu.CaravanMenu;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.RoadWorks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.BedBlock;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.platform.Nbt;
import com.chroniclecaravans.world.CaravanSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.TraderLlama;
//#if !mc26
import net.minecraft.world.level.GameRules;
//#endif
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
//#if neoforge
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//#else
//$$ import net.minecraftforge.event.TickEvent;
//#endif

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
    private static CaravanMaster second;
    private static BlockPos stopB, innPos, roadA, roadB;
    private static boolean atlasOpened;

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

    //#if neoforge
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        tick(e.getServer());
    }
    //#elif mc120
    //$$ @SubscribeEvent
    //$$ public static void onTick(TickEvent.ServerTickEvent e) {
    //$$     if (e.phase == TickEvent.Phase.END) tick(e.getServer());
    //$$ }
    //#else
    //$$ @SubscribeEvent
    //$$ public static void onTick(TickEvent.ServerTickEvent.Post e) {
    //$$     tick(e.getServer());
    //$$ }
    //#endif

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (!ON || tick < 0) return;
        ServerLevel level = server.overworld();
        tick++;
        try {
            if (tick == 1) prepare(level);
            if (tick == 100) stage(level);
            if (tick == 110) {
                check(master != null && master.isAlive(), "caravan spawned");
                check(master != null && CaravanData.get(level).knows(master.getUUID()), "caravan registered with the guilds");
                check(master != null && master.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "caravan master is not knocked back");
            }
            if (tick == 120) startRoad(level);
            if (tick == 700) checkRoad(level);
            if (tick == 800 && master != null) afterTravel(level);
            if (tick == 805 && master != null) trades(level);
            if (tick == 820 && master != null) check(master.startAmbush(level) >= 2, "ambush started with several bandits");
            if (tick == 830 && master != null) escort(level);
            if (tick == 840) secondCaravan(level);
            if (tick == 845) dispatchTest(level);
            if (tick == 850) innTest(level);
            if (tick == 900) clearBandits(level);
            if (tick == 1000 && master != null) {
                List<CaravanGuard> guards = master.guards(level);
                int close = 0;
                for (CaravanGuard g : guards) if (g.distanceToSqr(master) < 12 * 12) close++;
                log("guards=" + guards.size() + " close=" + close);
                check(guards.size() == 2, "escort guards hired");
                check(close == guards.size(), "guards stay with the caravan");
            }
            if (tick == 1080) afterNight(level);
            if (tick == 1100) {
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
        BlockPos spawn = spawn(level);
        int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4;
        for (int dx = -4; dx <= 5; dx++) for (int dz = -4; dz <= 4; dz++) level.setChunkForced(cx + dx, cz + dz, true);
        // A still, empty stage: no night, no rain, no wandering monsters
        command(level, "time set 1000");
        command(level, "weather clear 100000");
        //#if mc26
        //$$ net.minecraft.world.level.gamerules.GameRules rules = level.getGameRules();
        //$$ rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, level.getServer());
        //$$ rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, level.getServer());
        //$$ rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, level.getServer());
        //#else
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        //#endif
    }

    private static void command(ServerLevel level, String cmd) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static BlockPos spawn(ServerLevel level) {
        //#if mc26
        //$$ return level.getRespawnData().pos();
        //#else
        return level.getSharedSpawnPos();
        //#endif
    }

    /** A milestone at the spawn and a straight dirt path leading east from it. */
    private static void stage(ServerLevel level) {
        BlockPos spawn = spawn(level);
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
        guild(level, data);
        milestoneDrops(level, data);
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
        BlockPos road = CaravanSpawner.findGround(level, stop.offset(ROAD / 2, 0, 0), 0, 3, level.getRandom());
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
            log("wagon pitch=" + String.format("%.1f", wagon.getXRot()) + " ground=" + stop.getY());
            check(Math.abs(wagon.getXRot()) <= 14.5F, "wagon slope stays gentle");
            check(Math.abs(wagon.getY() - stop.getY()) < 0.8, "wagon stands on the ground");
            //#if mc26
            //$$ check(wagon.canBeCollidedWith(viewer), "wagon is solid");
            //#else
            check(wagon.canBeCollidedWith(), "wagon is solid");
            //#endif
            check(!master.canCollideWith(wagon), "the master is not blocked by his own wagon");
            if (level.getEntity(master.llamaIds().get(0)) instanceof TraderLlama draft) {
                check(draft.getLeashHolder() == master, "draft llama led by the master");
                check(draft.isNoAi(), "train members are moved by the master");
            }
            if (master.llamaIds().size() > 1 && level.getEntity(master.llamaIds().get(1)) instanceof TraderLlama pack) {
                check(pack.getLeashHolder() == wagon, "pack llama tied to the wagon");
            }
        }
        check(master.stock().size() >= 12, "stock has offers (" + master.stock().size() + ")");
    }

    /** Levels, skills and titles of the guild. */
    private static void guild(ServerLevel level, CaravanData data) {
        check(Guild.levelOf(0) == 0 && Guild.levelOf(9) == 0 && Guild.levelOf(10) == 1, "guild level thresholds");
        check(Guild.levelOf(Guild.totalFor(6)) == 6 && Guild.levelOf(Guild.totalFor(6) - 1) == 5, "guild levels add up");
        check(Guild.totalFor(6) >= 140, "reputation grows slower (level 6 needs " + Guild.totalFor(6) + " xp)");
        java.util.UUID p = java.util.UUID.randomUUID();
        data.setXp(p, Guild.totalFor(3));
        check(!data.learn(p, Guild.Skill.FAIR_SELLER), "a skill needs its parent");
        check(data.learn(p, Guild.Skill.HAGGLER), "a skill can be learnt");
        check(data.learn(p, Guild.Skill.FAIR_SELLER), "a second skill can be learnt");
        check(!data.learn(p, Guild.Skill.MERCHANT_PRINCE), "a high skill needs a high level");
        check(data.learn(p, Guild.Skill.FOREMAN), "one point per level");
        check(!data.learn(p, Guild.Skill.VETERAN_GUARDS), "no more points than levels");
        String title = Guild.title(data.xp(p), data.skills(p)).getString();
        log("title of a level 3 trader: " + title);
        check(title.equals(Guild.title(Guild.totalFor(3), Guild.Skill.HAGGLER.bit() | Guild.Skill.FAIR_SELLER.bit()).getString())
                && !title.isEmpty(), "title follows the skills");
        check(Guild.discount(data.skills(p)) > 0.07F, "haggler gives a discount");
    }

    /** Broken: a lantern. Silk Touch: the milestone, with its schedule, unstackable. */
    private static void milestoneDrops(ServerLevel level, CaravanData data) {
        BlockPos b = stop.offset(-6, 0, -6);
        level.setBlockAndUpdate(b, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        long now = level.getGameTime();
        CaravanData.Stop s = data.addStop(b, null, "SelfTest", "Oasis", now + 12345);
        CompoundTag snap = MilestoneBlock.snapshot(level, b);
        ItemStack plain = MilestoneBlock.drop(level, snap, false);
        ItemStack kept = MilestoneBlock.drop(level, snap, true);
        ItemStack other = MilestoneBlock.drop(level, MilestoneBlock.snapshot(level, b), true);
        check(plain.is(Items.LANTERN), "a broken milestone gives its lantern");
        check(kept.is(ChronicleCaravans.MILESTONE_ITEM.get()) && Compat.data(kept) != null, "silk touch keeps the milestone and its schedule");
        check(!Compat.sameItem(kept, other), "kept milestones do not stack");
        level.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        data.removeStopAndRoutes(b);
        check(data.stop(b) == null, "breaking forgets the stop");
        level.setBlockAndUpdate(b, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        ChronicleCaravans.MILESTONE.get().setPlacedBy(level, b, level.getBlockState(b), null, kept);
        CaravanData.Stop again = data.stop(b);
        long wait = again == null ? -1 : again.nextVisit - level.getGameTime();
        log("schedule after replacing: " + wait + " (was 12345)");
        check(again != null && Math.abs(wait - 12345) < 5, "the schedule survives breaking and placing");
        check(again != null && "Oasis".equals(again.name), "the name survives too");
        level.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        data.removeStopAndRoutes(b);
    }

    /** A road crew lays a road across untouched land. */
    private static void startRoad(ServerLevel level) {
        int z = stop.getZ() + 34;
        roadA = new BlockPos(stop.getX() - 10, stop.getY(), z);
        roadB = new BlockPos(stop.getX() + 30, stop.getY(), z);
        check(RoadBuilder.start(level, roadA, roadB, null, false, true) != null, "road crew hired");
    }

    private static void checkRoad(ServerLevel level) {
        int built = 0, total = 0;
        for (int t = 4; t <= 36; t++) {
            double[] p = RoadWorks.at(roadA, roadB, t);
            BlockPos g = RoadWorks.ground(level, (int) Math.floor(p[0]), (int) Math.floor(p[1]));
            total++;
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(g);
            if (st.is(ChronicleCaravans.ROADS) || st.is(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)) built++;
        }
        int crews = level.getEntities(ChronicleCaravans.BUILDER.get(), e -> true).size();
        log("road columns built=" + built + "/" + total + " crews left=" + crews);
        check(built >= total * 0.6, "the road crew built the road");
        check(crews == 0, "the road crew went home");
        CaravanData.Route r = CaravanData.get(level).route(roadA, roadB);
        check(r != null && r.built, "the road is recorded as built");
    }

    /** Buying, selling, locks and treasures. */
    private static void trades(ServerLevel level) {
        List<MerchantOffer> offers = master.stock();
        int sells = 0, treasures = 0, locked = 0;
        MerchantOffer buy = null;
        for (MerchantOffer o : offers) {
            int k = Region.kindOf(o);
            if (k == Region.KIND_SELL) sells++;
            if (k == Region.KIND_TREASURE) treasures++;
            if (!Trading.unlocked(o, 0, 0)) locked++;
            if (buy == null && k == Region.KIND_BUY && Region.levelOf(o) == 0 && o.getBaseCostA().is(Items.EMERALD) && o.getCostB().isEmpty()) buy = o;
        }
        log("offers=" + offers.size() + " requests=" + sells + " treasures=" + treasures + " locked=" + locked);
        check(sells >= 2, "the caravan asks for goods");
        check(treasures >= 1, "the caravan carries treasures");
        check(locked >= 1, "some offers need a guild level or a skill");
        check(buy != null, "a basic offer exists");
        if (buy == null) return;
        SimpleContainer inv = new SimpleContainer(36);
        inv.setItem(0, new ItemStack(Items.EMERALD, 64));
        List<ItemStack> got = new ArrayList<>();
        int uses = buy.getUses();
        int value = Trading.execute(buy, 0, inv, got::add, 1);
        check(value > 0 && !got.isEmpty() && got.get(0).is(buy.getResult().getItem()), "buying gives the goods");
        check(inv.getItem(0).getCount() == 64 - buy.getBaseCostA().getCount(), "buying takes the price");
        check(buy.getUses() == uses + 1, "buying uses the stock");
        int haggled = Trading.costA(buy, Guild.Skill.HAGGLER.bit() | Guild.Skill.MERCHANT_PRINCE.bit()).getCount();
        check(haggled <= buy.getBaseCostA().getCount(), "skills lower the price");
        check(Trading.execute(buy, 0, new SimpleContainer(36), got::add, 1) == 0, "no emeralds, no deal");
    }

    private static void escort(ServerLevel level) {
        master.startEscort(level, CaravanMenu.escortGuards(0), 2400, false, null);
    }

    private static void secondCaravan(ServerLevel level) {
        second = CaravanSpawner.spawnCaravanAt(level, stop.offset(24, 0, 12), null, Region.COAST);
        check(second != null, "second caravan spawned");
    }

    /** A stop that waits gets a free caravan nearby, not a new one. */
    private static void dispatchTest(ServerLevel level) {
        if (second == null) return;
        CaravanData data = CaravanData.get(level);
        stopB = stop.offset(-12, 0, 14);
        level.setBlockAndUpdate(stopB, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        CaravanData.Stop b = data.addStop(stopB, null, "SelfTest", "B", level.getGameTime());
        data.nextSpawn = level.getGameTime() + 1_000_000;
        int before = CaravanSpawner.caravans(level).size();
        CaravanMaster sent = CaravanSpawner.dispatch(level, data, b, CaravanSpawner.caravans(level));
        int after = CaravanSpawner.caravans(level).size();
        log("dispatch: sent=" + (sent == null ? "none" : sent.region()) + " caravans " + before + " -> " + after);
        check(sent == second, "a free caravan is sent to the waiting stop");
        check(after == before, "no new caravan appears when one is free");
        check(stopB.equals(second.target()), "the free caravan heads to the stop");
    }

    /** At night, a travelling caravan goes to the inn. */
    private static void innTest(ServerLevel level) {
        innPos = stop.offset(-8, 0, 8);
        level.setBlockAndUpdate(innPos, ChronicleCaravans.INN_SIGN.get().defaultBlockState());
        BlockPos bed = innPos.offset(2, 0, 0);
        level.setBlock(bed, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState(), 3);
        level.setBlock(bed.north(), net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD), 3);
        ChronicleCaravans.INN_SIGN.get().setPlacedBy(level, innPos, level.getBlockState(innPos), null, new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()));
        check(CaravanData.get(level).inn(innPos) != null, "inn registered");
        check(InnSignBlock.open(level, innPos), "an inn with a bed is open");
        command(level, "time set 14000");
    }

    private static void afterNight(ServerLevel level) {
        command(level, "time set 1000");
        boolean inn = second != null && second.isAlive() && (second.headingToInn() || second.state() == CaravanMaster.State.LODGING);
        log("second caravan: " + (second == null ? "none" : second.state() + " target=" + second.target() + " inn=" + second.headingToInn()));
        check(inn, "at night a caravan heads to the inn");
        if (second != null) second.vanish(level);
        CaravanData data = CaravanData.get(level);
        if (stopB != null) {
            data.removeStopAndRoutes(stopB);
            level.setBlockAndUpdate(stopB, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        data.nextSpawn = 0;
    }

    private static void clearBandits(ServerLevel level) {
        int n = 0;
        for (Entity en : level.getAllEntities()) {
            if (Nbt.hasUUID(en.getPersistentData(), CaravanMaster.AMBUSH_TAG)) {
                en.discard();
                n++;
            }
        }
        log("bandits left=" + n);
    }

    /** What the test client sees: a new caravan walks down the road, halts, then gets ambushed. */
    private static void scene(ServerLevel level, int t) {
        if (viewer == null || viewer.hasDisconnected()) return;
        if (t == 60) {
            for (CaravanMaster m : CaravanSpawner.caravans(level)) m.vanish(level);
            command(level, "time set 1000");
            spawn(level, Region.DUNES);
        }
        if (t == 640 && master != null && master.isAlive()) master.startEscort(level, 3, 6000, true, null);
        if (t == 760 && master != null && master.isAlive()) master.startAmbush(level);
        if (t == 1740 && (master == null || !master.isAlive())) spawn(level, Region.DUNES);
        if (t == 1750 && master != null && master.isAlive()) {
            log("opening the caravan interface for the viewer");
            master.openFor(viewer);
        }
        if (t > 1790 && !atlasOpened && !(viewer.containerMenu instanceof CaravanMenu)) {
            atlasOpened = true;
            log("opening the atlas for the viewer");
            AtlasItem.open(viewer);
        }
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
        //#if mc26
        //$$ viewer.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, true);
        //#else
        viewer.teleportTo(level, x, y, z, yaw, pitch);
        //#endif
    }
}
