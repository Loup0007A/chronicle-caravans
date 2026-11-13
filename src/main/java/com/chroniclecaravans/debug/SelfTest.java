package com.chroniclecaravans.debug;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.block.DockBlock;
import com.chroniclecaravans.block.DockBlockEntity;
import com.chroniclecaravans.block.InnSignBlock;
import com.chroniclecaravans.block.MilestoneBlock;
import com.chroniclecaravans.block.TradingPostBlock;
import com.chroniclecaravans.block.TradingPostBlockEntity;
import com.chroniclecaravans.entity.CaravanGuard;
import com.chroniclecaravans.entity.RoadBuilder;
import com.chroniclecaravans.item.AtlasItem;
import com.chroniclecaravans.menu.AtlasMenu;
import com.chroniclecaravans.menu.CaravanMenu;
import com.chroniclecaravans.menu.DockMenu;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.Logistics;
import com.chroniclecaravans.world.Market;
import com.chroniclecaravans.world.Parcel;
import com.chroniclecaravans.world.RoadWorks;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.state.BlockState;
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
    // Market, freight, automation and weather
    private static BlockPos hamlet, outDock, inDock, hopper, postPos, thirdFire;
    private static Item postBought;
    private static CaravanMaster third;
    /** Inn bed (head part), hitching posts of the relay, the caravan that parks there, the one shown at the inn to the viewer. */
    private static BlockPos bedHead, postA, postB;
    private static CaravanMaster visitor, guest, relayGuest;
    // The viewer's tour of the new screens: 0 none, 1 going to the dock, 2 dock open, 3 going to the post, 4 post open
    private static int menuStep, menuTick, atlasTick;
    private static BlockPos menuFocus;

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
            if (tick == 806 && master != null) market(level);
            if (tick == 810 && master != null) freight(level);
            if (tick == 815 && master != null) tradingPost(level);
            if (tick == 820 && master != null) check(master.startAmbush(level) >= 2, "ambush started with several bandits");
            if (tick == 830 && master != null) escort(level);
            if (tick == 840) secondCaravan(level);
            if (tick == 845) dispatchTest(level);
            if (tick == 850) innTest(level);
            if (tick == 870 && hopper != null) hopperCheck(level);
            if (tick == 900) {
                clearBandits(level);
                postCheck(level);
            }
            if (tick == 940) postPaused(level);
            if (tick == 1000 && master != null) {
                List<CaravanGuard> guards = master.guards(level);
                int close = 0;
                for (CaravanGuard g : guards) if (g.distanceToSqr(master) < 12 * 12) close++;
                log("guards=" + guards.size() + " close=" + close);
                check(guards.size() == 2, "escort guards hired");
                check(close == guards.size(), "guards stay with the caravan");
            }
            if (tick == 1080) afterNight(level);
            if (tick == 1085) nightDispatch(level);
            if (tick == 1090) redstoneCall(level);
            if (tick == 1100) command(level, "weather thunder 100000");
            if (tick == 1220) stormCaravan(level);
            if (tick == 1340) stormCheck(level);
            if (tick == 1460) afterStorm(level);
            if (tick == 1560) innSleep(level);
            if (tick == 1570) relay(level);
            if (tick == 1900) innMorning(level);
            if (tick == 1930) relayCheck(level);
            if (tick == 2110) relayLeft(level);
            if (tick == 2130) {
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
        Trading.Quote q = Trading.quote(buy, 0, 1F);
        int done = Trading.execute(buy, q, inv, got::add, 1);
        check(done == 1 && !got.isEmpty() && got.get(0).is(buy.getResult().getItem()), "buying gives the goods");
        check(inv.getItem(0).getCount() == 64 - buy.getBaseCostA().getCount(), "buying takes the price");
        check(buy.getUses() == uses + 1, "buying uses the stock");
        int haggled = Trading.quote(buy, Guild.Skill.HAGGLER.bit() | Guild.Skill.MERCHANT_PRINCE.bit(), 1F).a().getCount();
        check(haggled <= buy.getBaseCostA().getCount(), "skills lower the price");
        check(Trading.execute(buy, q, new SimpleContainer(36), got::add, 1) == 0, "no emeralds, no deal");
        // Redstone: a comparator next to the milestone reads 15 while a caravan halts there
        check(CaravanSpawner.signal(level, stop) == 15, "a comparator reads 15 while a caravan halts");
    }

    // ---------------------------------------------------------------- market, freight, automation, weather

    /** Supply and demand: deals move the prices, the marks fade with the days, each region has its news. */
    private static void market(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        long now = level.getGameTime();
        MerchantOffer request = null, good = null;
        for (MerchantOffer o : master.stock()) {
            int k = Region.kindOf(o);
            if (request == null && k == Region.KIND_SELL) request = o;
            if (good == null && k == Region.KIND_BUY && !o.getResult().is(Items.ENCHANTED_BOOK)) good = o;
        }
        check(request != null && good != null, "the market has a request and a good to price");
        if (request == null || good == null) return;
        Region r = master.region();
        float sell0 = Market.factor(data, r, request, now), buy0 = Market.factor(data, r, good, now);
        Market.traded(data, request, 6, now);
        Market.traded(data, good, 6, now);
        float sell1 = Market.factor(data, r, request, now), buy1 = Market.factor(data, r, good, now);
        log(String.format("market: request %.2f -> %.2f, good %.2f -> %.2f", sell0, sell1, buy0, buy1));
        check(sell1 < sell0, "selling a lot lowers what the caravans pay");
        check(buy1 > buy0, "buying a lot raises the prices");
        Item wanted = request.getBaseCostA().getItem();
        double h0 = Market.heat(data, wanted, now), h2 = Market.heat(data, wanted, now + 2 * Market.DAY);
        check(h0 > 5 && h2 < h0 * 0.3, "the market calms down over the days");
        check(r.wantedItems().contains(Market.shortage(r, now)), "the shortage of the day is a requested good");
        check(r.soldItems().contains(Market.surplus(r, now)), "the surplus of the day is a good of the region");
        Trading.Quote normal = Trading.quote(request, 0, 1F), dear = Trading.quote(request, 0, Market.SHORTAGE);
        check(dear.result().getCount() >= normal.result().getCount() && dear.percent(Region.KIND_SELL) == 50, "a shortage pays more");
        data.market().clear();
        data.setDirty();
    }

    /** Freight: a dock ships goods, a caravan carries them over the legs of the network, a dock receives them. */
    private static void freight(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        long far = Long.MAX_VALUE / 4;
        // Routing over several legs: E is within reach of the spawn milestone, D only of E
        int reach = CCConfig.LINK_RANGE.get();
        BlockPos e = stop.offset(reach * 3 / 4, 0, 0), d = stop.offset(reach * 3 / 2, 0, 0);
        data.addStop(e, null, "SelfTest", "", far);
        data.addStop(d, null, "SelfTest", "", far);
        int hops = Logistics.hops(data, stop, d);
        log("freight route to a far stop: " + hops + " legs, first stop " + Logistics.nextHop(data, stop, d));
        check(hops == 2 && e.equals(Logistics.nextHop(data, stop, d)), "parcels travel over several legs");
        check(Logistics.feeBetween(data, stop, d) == Logistics.fee(2) && Logistics.fee(2) > Logistics.fee(1), "the freight fee grows with the legs");
        data.removeStop(e);
        data.removeStop(d);
        // A dock named "Hamlet" next to the spawn milestone sends there; an unnamed dock at the hamlet receives
        hamlet = stop.offset(-12, 0, -9);
        level.setBlockAndUpdate(hamlet, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        CaravanData.Stop h = data.addStop(hamlet, null, "SelfTest", "Hamlet", far);
        outDock = stop.offset(-2, 0, -3);
        inDock = hamlet.offset(1, 0, 0);
        ItemStack named = new ItemStack(ChronicleCaravans.DOCK_ITEM.get());
        Compat.setName(named, Component.literal("Hamlet"));
        placeDock(level, outDock, named);
        placeDock(level, inDock, new ItemStack(ChronicleCaravans.DOCK_ITEM.get()));
        if (!(level.getBlockEntity(outDock) instanceof DockBlockEntity out) || !(level.getBlockEntity(inDock) instanceof DockBlockEntity in)) {
            check(false, "loading docks placed");
            return;
        }
        check(out.outgoing() && level.getBlockState(outDock).getValue(DockBlock.OUTGOING), "a dock named after a stop sends");
        check(!in.outgoing() && !level.getBlockState(inDock).getValue(DockBlock.OUTGOING), "an unnamed dock receives");
        CaravanData.Stop owner = Logistics.stopOf(level, outDock);
        check(owner != null && owner.pos.equals(stop), "a dock belongs to the closest milestone");
        out.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        out.setItem(1, new ItemStack(Items.OAK_LOG, 8));
        out.setItem(2, new ItemStack(Items.EMERALD, 3));
        int fee = Logistics.feeBetween(data, stop, hamlet);
        int shipped = Logistics.collectDocks(level, data.stop(stop));
        List<Parcel> depot = data.depots().get(stop);
        log("dock shipped=" + shipped + " fee=" + fee + " emeralds left=" + out.emeralds() + " waiting parcels=" + (depot == null ? 0 : depot.size()));
        check(shipped == 1 && out.goodsCount() == 0, "the dock hands its goods to the guild");
        check(out.emeralds() == 3 - fee && out.status() == DockBlockEntity.SHIPPED, "the dock pays the freight");
        check(hamlet.equals(Logistics.pickNext(level, stop)), "waiting parcels choose the caravan's next stop");
        int loaded = Logistics.load(level, master, stop, hamlet);
        check(loaded == 1 && master.cargo().size() == 1, "the caravan loads the parcel");
        int delivered = Logistics.unload(level, master, h);
        check(delivered == 1 && master.cargo().isEmpty(), "the parcel is delivered at its stop");
        int cobble = 0, logs = 0;
        for (int i = 0; i < in.getContainerSize(); i++) {
            ItemStack s = in.getItem(i);
            if (s.is(Items.COBBLESTONE)) cobble += s.getCount();
            if (s.is(Items.OAK_LOG)) logs += s.getCount();
        }
        check(cobble == 16 && logs == 8, "the goods land in the receiving dock");
        // A parcel sent by a player arrives boxed, and the box holds what was sent
        Logistics.deliver(level, h, Parcel.of(level, List.of(new ItemStack(Items.APPLE, 5)), stop, hamlet, null, "Tester", false));
        ItemStack box = ItemStack.EMPTY;
        for (int i = 0; i < in.getContainerSize(); i++) if (Logistics.isParcel(in.getItem(i))) box = in.getItem(i);
        check(!box.isEmpty(), "a player's parcel arrives as a parcel");
        CompoundTag tag = box.isEmpty() ? null : Compat.data(box);
        List<ItemStack> inside = tag == null ? List.of() : Parcel.load(tag).unpack(level);
        check(inside.size() == 1 && inside.get(0).is(Items.APPLE) && inside.get(0).getCount() == 5, "the parcel holds what was sent");
        // A hopper under the receiving dock empties it
        hopper = inDock.below();
        level.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState());
    }

    private static void placeDock(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.setBlockAndUpdate(pos, ChronicleCaravans.DOCK.get().defaultBlockState());
        ChronicleCaravans.DOCK.get().setPlacedBy(level, pos, level.getBlockState(pos), null, stack);
    }

    private static void hopperCheck(ServerLevel level) {
        int n = 0;
        if (level.getBlockEntity(hopper) instanceof Container c) for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
        log("hopper under the receiving dock holds " + n + " items");
        check(n > 0, "a hopper empties the receiving dock");
    }

    /** A trading post next to the stop trades on its own with the halted caravan. */
    private static void tradingPost(ServerLevel level) {
        postPos = stop.offset(3, 0, -3);
        level.setBlockAndUpdate(postPos, ChronicleCaravans.POST.get().defaultBlockState().setValue(TradingPostBlock.FACING, Direction.SOUTH));
        if (!(level.getBlockEntity(postPos) instanceof TradingPostBlockEntity post)) {
            check(false, "trading post placed");
            return;
        }
        MerchantOffer request = null, good = null;
        for (MerchantOffer o : master.stock()) {
            int k = Region.kindOf(o);
            if (o.isOutOfStock() || !Trading.unlocked(o, 0, 0) || !o.getCostB().isEmpty()) continue;
            if (request == null && k == Region.KIND_SELL) request = o;
            if (good == null && k == Region.KIND_BUY && o.getBaseCostA().is(Items.EMERALD) && !o.getResult().is(Items.ENCHANTED_BOOK)) good = o;
        }
        check(request != null && good != null, "the caravan has a request and a good for the trading post");
        if (request == null || good == null) return;
        if (good.getResult().is(request.getBaseCostA().getItem())) return;
        postBought = good.getResult().getItem();
        post.setItem(TradingPostBlockEntity.INPUT, request.getBaseCostA().copy());
        post.setItem(TradingPostBlockEntity.INPUT + 1, new ItemStack(Items.EMERALD, 64));
        post.setItem(TradingPostBlockEntity.ORDERS, new ItemStack(postBought, 1));
        log("trading post: sells " + request.getBaseCostA().getCount() + " " + Market.key(request.getBaseCostA().getItem()) + ", orders " + Market.key(postBought));
    }

    private static void postCheck(ServerLevel level) {
        if (postBought == null || !(level.getBlockEntity(postPos) instanceof TradingPostBlockEntity post)) return;
        int emeralds = 0, goods = 0;
        for (int i = TradingPostBlockEntity.OUTPUT; i < TradingPostBlockEntity.SIZE; i++) {
            ItemStack s = post.getItem(i);
            if (s.is(Items.EMERALD)) emeralds += s.getCount();
            if (s.is(postBought)) goods += s.getCount();
        }
        log("trading post: deals=" + post.totalDeals() + " status=" + post.status() + " output " + emeralds + " emeralds, " + goods + " goods, comparator " + post.signal());
        check(emeralds > 0, "the trading post sells what the caravan asks for");
        check(goods > 0, "the trading post buys what its orders ask for");
        check(post.signal() > 0, "a comparator reads the trading post");
        level.setBlockAndUpdate(postPos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private static void postPaused(ServerLevel level) {
        if (postBought == null || !(level.getBlockEntity(postPos) instanceof TradingPostBlockEntity post)) return;
        check(post.status() == TradingPostBlockEntity.PAUSED, "a redstone signal pauses the trading post");
        level.setBlockAndUpdate(postPos.east(), Blocks.AIR.defaultBlockState());
    }

    /** A redstone pulse on a milestone calls a caravan, paid with an emerald block from the chest beside it. */
    private static void redstoneCall(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        BlockPos call = stop.offset(10, 0, -8), chest = call.east(), power = call.west();
        level.setBlockAndUpdate(call, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        CaravanData.Stop c = data.addStop(call, null, "SelfTest", "Relay", Long.MAX_VALUE / 4);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof Container box) box.setItem(0, new ItemStack(Items.EMERALD_BLOCK, 2));
        check(CaravanSpawner.signal(level, call) == 0, "no caravan expected: the comparator reads 0");
        level.setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
        boolean powered = level.getBlockState(call).getValue(MilestoneBlock.POWERED);
        int left = level.getBlockEntity(chest) instanceof Container box ? box.getItem(0).getCount() : -1;
        log("redstone call: powered=" + powered + " emerald blocks left=" + left + " hired=" + c.hired);
        check(powered, "the milestone notices a redstone signal");
        check(left == 1 && c.hired && c.nextVisit <= level.getGameTime(), "a redstone pulse calls a caravan, paid from the chest");
        level.setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof Container box) box.clearContent();
        level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(call, Blocks.AIR.defaultBlockState());
        data.removeStopAndRoutes(call);
    }

    /** A caravan on the road when a thunderstorm breaks makes camp, then goes on when it is over. */
    private static void stormCaravan(ServerLevel level) {
        third = CaravanSpawner.spawnCaravanAt(level, stop.offset(44, 0, 6), stop, Region.WOODS);
        log("storm caravan spawned=" + (third != null) + " thundering=" + level.isThundering());
    }

    private static void stormCheck(ServerLevel level) {
        thirdFire = third == null ? null : third.campfire();
        log("storm: thundering=" + level.isThundering() + " caravan " + (third == null ? "none" : third.state() + " reason=" + third.campReason() + " campfire=" + thirdFire
                + " speed=" + String.format("%.3f", third.getAttributeValue(Attributes.MOVEMENT_SPEED))));
        check(third != null && third.isAlive() && third.state() == CaravanMaster.State.CAMPING && third.campReason() == CaravanMaster.CAMP_STORM,
                "a caravan caught in a thunderstorm makes camp");
        check(thirdFire != null && level.getBlockState(thirdFire).is(Blocks.CAMPFIRE), "the camp has a campfire");
        command(level, "weather clear 100000");
    }

    private static void afterStorm(ServerLevel level) {
        log("after the storm: " + (third == null ? "none" : third.state() + " thundering=" + level.isThundering()));
        check(third != null && third.isAlive() && third.state() != CaravanMaster.State.CAMPING, "the caravan breaks camp after the storm");
        check(thirdFire == null || !level.getBlockState(thirdFire).is(Blocks.CAMPFIRE), "the campfire is put out");
        if (third != null) third.vanish(level);
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
        int signal = CaravanSpawner.signal(level, stopB);
        log("comparator at the waiting stop: " + signal);
        check(signal >= 1 && signal <= 7, "a comparator tells that a caravan is on its way");
    }

    /** At night, a travelling caravan goes to the inn. */
    private static void innTest(ServerLevel level) {
        innPos = stop.offset(-8, 0, 8);
        level.setBlockAndUpdate(innPos, ChronicleCaravans.INN_SIGN.get().defaultBlockState());
        BlockPos bed = innPos.offset(2, 0, 0);
        level.setBlock(bed, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState(), 3);
        level.setBlock(bed.north(), net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD), 3);
        bedHead = bed.north();
        ChronicleCaravans.INN_SIGN.get().setPlacedBy(level, innPos, level.getBlockState(innPos), null, new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()));
        check(CaravanData.get(level).inn(innPos) != null, "inn registered");
        check(InnSignBlock.open(level, innPos), "an inn with a bed is open");
        command(level, "time set 14000");
    }

    private static void afterNight(ServerLevel level) {
        boolean inn = second != null && second.isAlive() && (second.headingToInn() || second.state() == CaravanMaster.State.LODGING);
        log("second caravan: " + (second == null ? "none" : second.state() + " target=" + second.target() + " inn=" + second.headingToInn()
                + " serves=" + second.destination()));
        check(inn, "at night a caravan heads to the inn");
    }

    /** At night: a caravan resting on its way still counts for its stop, and no new caravan sets out from afar. */
    private static void nightDispatch(ServerLevel level) {
        if (second == null || stopB == null) return;
        CaravanData data = CaravanData.get(level);
        int before = CaravanSpawner.caravans(level).size();
        CaravanData.Stop b = data.stop(stopB);
        b.nextVisit = level.getGameTime();
        CaravanMaster sent = CaravanSpawner.dispatch(level, data, b, CaravanSpawner.caravans(level));
        log("night dispatch to B: " + (sent == null ? "none" : sent.region()) + " caravans " + before + " -> " + CaravanSpawner.caravans(level).size());
        check(sent == second && CaravanSpawner.caravans(level).size() == before, "a caravan resting at the inn still serves its stop: no other one comes");
        BlockPos c = stop.offset(-12, 0, -10);
        level.setBlockAndUpdate(c, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        CaravanData.Stop sc = data.addStop(c, null, "SelfTest", "C", level.getGameTime());
        long spawnWas = data.nextSpawn;
        data.nextSpawn = 0;
        CaravanMaster fresh = CaravanSpawner.dispatch(level, data, sc, CaravanSpawner.caravans(level));
        log("night dispatch to C: " + (fresh == null ? "none" : fresh.region()) + " caravans " + before + " -> " + CaravanSpawner.caravans(level).size());
        check(fresh == null && CaravanSpawner.caravans(level).size() == before, "no new caravan sets out at night");
        data.nextSpawn = spawnWas;
        data.removeStopAndRoutes(c);
        level.setBlockAndUpdate(c, Blocks.AIR.defaultBlockState());
    }

    /** Deep in the night: the train is parked off the road near the inn and the master sleeps in the bed. */
    private static void innSleep(ServerLevel level) {
        if (second == null) return;
        CaravanWagon w = second.wagon(level);
        BlockPos under = w == null ? null : BlockPos.containing(w.getX(), w.getY() - 0.3, w.getZ());
        log("inn: state=" + second.state() + " parked=" + second.parked() + " sleeping=" + second.isSleeping() + " bed=" + second.footSpot()
                + " post=" + second.parkPost() + " head=" + second.parkHead() + " master=" + second.blockPosition()
                + " wagon=" + (w == null ? "none" : w.blockPosition() + " on " + level.getBlockState(under).getBlock()));
        check(second.isAlive() && second.state() == CaravanMaster.State.LODGING, "the caravan lodges at the inn");
        check(second.parked(), "its train is parked for the night");
        check(w != null && !level.getBlockState(under).is(ChronicleCaravans.ROADS) && w.distanceToSqr(innPos.getX() + 0.5, innPos.getY(), innPos.getZ() + 0.5) < 22 * 22,
                "the wagon is parked off the road, near the inn");
        check(second.isSleeping() && bedHead.equals(second.getSleepingPos().orElse(null)), "the caravan master sleeps in the inn's bed");
        BlockState bed = level.getBlockState(bedHead);
        check(bed.getBlock() instanceof BedBlock && bed.getValue(BedBlock.OCCUPIED), "the bed is taken");
        check(w != null && w.distanceTo(second) > 3, "the train waits where it is parked");
        command(level, "time set 1000");
    }

    /** Morning: he wakes up, walks back to his train and goes on. */
    private static void innMorning(ServerLevel level) {
        if (second != null) {
            CaravanWagon w = second.wagon(level);
            log("morning: state=" + second.state() + " parked=" + second.parked() + " sleeping=" + second.isSleeping() + " master=" + second.blockPosition()
                    + " wagon=" + (w == null ? "none" : w.blockPosition()));
            check(second.isAlive() && !second.isSleeping(), "the master wakes up in the morning");
            check(!second.parked() && second.state() != CaravanMaster.State.LODGING, "he goes back to his train and leaves the inn");
            check(w != null && w.distanceTo(second) < 9, "the train follows him again");
            BlockState bed = level.getBlockState(bedHead);
            check(bed.getBlock() instanceof BedBlock && !bed.getValue(BedBlock.OCCUPIED), "the bed is free again");
            second.vanish(level);
        }
        CaravanData data = CaravanData.get(level);
        if (stopB != null) {
            data.removeStopAndRoutes(stopB);
            level.setBlockAndUpdate(stopB, Blocks.AIR.defaultBlockState());
        }
        data.nextSpawn = 0;
    }

    private static BlockPos placePost(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ChronicleCaravans.HITCHING_POST.get().defaultBlockState());
        ChronicleCaravans.HITCHING_POST.get().setPlacedBy(level, pos, level.getBlockState(pos), null, new ItemStack(ChronicleCaravans.HITCHING_POST_ITEM.get()));
        return pos;
    }

    /** Relay charters raise the milestone (with enough hitching posts); a second caravan comes while the first is halted. */
    private static void relay(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop s = data.stop(stop);
        check(MilestoneBlock.upgrade(level, stop) == MilestoneBlock.NEEDS_POSTS, "a relay charter needs a hitching post first");
        postA = placePost(level, stop.offset(6, 0, -6));
        check(data.posts().contains(postA), "hitching post registered");
        check(MilestoneBlock.upgrade(level, stop) == MilestoneBlock.UPGRADED && s.tier == 2, "a relay charter raises the milestone to tier 2");
        check(CaravanSpawner.capacity(level, s) == 2, "a tier 2 relay takes two caravans at once");
        check(MilestoneBlock.upgrade(level, stop) == MilestoneBlock.NEEDS_POSTS, "tier 3 needs a second hitching post");
        postB = placePost(level, stop.offset(-6, 0, -6));
        check(MilestoneBlock.upgrade(level, stop) == MilestoneBlock.UPGRADED && CaravanSpawner.capacity(level, s) == 3, "with two posts, tier 3: three caravans at once");
        check(level.getBlockState(stop).getValue(MilestoneBlock.TIER) == 3, "the milestone shows its tier");
        check(Nbt.getInt(MilestoneBlock.snapshot(level, stop), "Tier") == 3, "Silk Touch keeps the tier");
        check(master != null && master.isAlive() && stop.equals(master.currentStop()) && !CaravanSpawner.full(level, s), "the relay has room beside the halted caravan");
        visitor = CaravanSpawner.spawnCaravanAt(level, stop.offset(26, 0, 0), stop, Region.STEPPE);
    }

    private static void relayCheck(ServerLevel level) {
        if (visitor == null) return;
        CaravanWagon w = visitor.wagon(level);
        Entity draft = visitor.llamaIds().isEmpty() ? null : level.getEntity(visitor.llamaIds().get(0));
        Entity holder = draft instanceof TraderLlama l ? l.getLeashHolder() : null;
        log("relay: visitor state=" + visitor.state() + " parked=" + visitor.parked() + " post=" + visitor.parkPost() + " head=" + visitor.parkHead()
                + " master=" + visitor.blockPosition() + " wagon=" + (w == null ? "none" : w.blockPosition()) + " draft tied to " + (holder == null ? "nothing" : holder.getType()));
        check(visitor.isAlive() && visitor.parked() && postA.equals(visitor.parkPost()), "the second caravan parks at a hitching post (the main spot is taken)");
        check(stop.equals(visitor.currentStop()), "it halts at the relay too");
        check(visitor.distanceToSqr(stop.getX() + 0.5, stop.getY(), stop.getZ() + 0.5) < 6 * 6, "its master walks to the milestone");
        check(w != null && w.distanceToSqr(postA.getX() + 0.5, postA.getY(), postA.getZ() + 0.5) < 9 * 9, "its wagon waits by the post");
        check(holder instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity, "the draft llama is tied to the post");
        check(CaravanSpawner.serving(CaravanSpawner.caravans(level), stop).size() >= 2 && CaravanSpawner.signal(level, stop) == 15, "the relay serves two caravans at once");
        visitor.hurryDeparture();
    }

    private static void relayLeft(ServerLevel level) {
        if (visitor == null) return;
        CaravanWagon w = visitor.wagon(level);
        int knots = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.LeashFenceKnotEntity.class, new net.minecraft.world.phys.AABB(postA).inflate(1)).size();
        log("relay: after the halt state=" + visitor.state() + " parked=" + visitor.parked() + " alive=" + visitor.isAlive() + " knots=" + knots);
        check(!visitor.isAlive() || !visitor.parked(), "the caravan goes back to its train when it leaves");
        check(!visitor.isAlive() || (w != null && w.distanceTo(visitor) < 9), "its train follows it again");
        check(knots == 0, "the lead knot is taken off the post");
        if (visitor.isAlive()) visitor.vanish(level);
    }

    private static void clearBandits(ServerLevel level) {
        int n = 0;
        // Collect first: discarding while walking the live entity list can leave holes in it (26.x)
        List<Entity> bandits = new ArrayList<>();
        for (Entity en : level.getAllEntities()) {
            if (en != null && Nbt.hasUUID(en.getPersistentData(), CaravanMaster.AMBUSH_TAG)) bandits.add(en);
        }
        for (Entity en : bandits) {
            en.discard();
            n++;
        }
        log("bandits left=" + n);
    }

    /**
     * Placing a milestone unlocks the mod's other recipes (advancement recipes/caravan_network).
     * Checked with the real player of the test client: ci_test.sh fails on any "[SELFTEST] FAIL" line.
     */
    private static void recipeUnlock(ServerLevel level) {
        if (!level.getBlockState(stop).is(ChronicleCaravans.MILESTONE.get()))
            level.setBlockAndUpdate(stop, ChronicleCaravans.MILESTONE.get().defaultBlockState());
        //#if mc120
        //$$ var adv = level.getServer().getAdvancements().getAdvancement(ChronicleCaravans.id("recipes/caravan_network"));
        //#else
        var adv = level.getServer().getAdvancements().get(ChronicleCaravans.id("recipes/caravan_network"));
        //#endif
        check(adv != null, "recipe unlock advancement loaded");
        if (adv == null) return;
        check(!viewer.getAdvancements().getOrStartProgress(adv).isDone(), "new player has not unlocked the recipes yet");
        ItemStack placed = new ItemStack(ChronicleCaravans.MILESTONE_ITEM.get());
        //#if mc26
        //$$ net.minecraft.advancements.triggers.CriteriaTriggers.PLACED_BLOCK.trigger(viewer, stop, placed);
        //#else
        net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(viewer, stop, placed);
        //#endif
        check(viewer.getAdvancements().getOrStartProgress(adv).isDone(), "placing a milestone unlocks the recipes");
        for (String r : new String[] {"caravan_atlas", "inn_sign", "loading_dock", "trading_post", "hitching_post", "relay_charter"}) {
            //#if mc26
            //$$ boolean known = viewer.getRecipeBook().contains(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, ChronicleCaravans.id(r)));
            //#else
            boolean known = viewer.getRecipeBook().contains(ChronicleCaravans.id(r));
            //#endif
            check(known, "recipe " + r + " unlocked");
        }
    }

    /** What the test client sees: a new caravan walks down the road, halts, then gets ambushed. */
    private static void scene(ServerLevel level, int t) {
        if (viewer == null || viewer.hasDisconnected()) return;
        if (t == 10) recipeUnlock(level);
        if (t == 60) {
            for (CaravanMaster m : CaravanSpawner.caravans(level)) m.vanish(level);
            command(level, "time set 1000");
            spawn(level, Region.DUNES);
        }
        if (t == 640 && master != null && master.isAlive()) master.startEscort(level, 3, 6000, true, null);
        if (t == 760 && master != null && master.isAlive()) master.startAmbush(level);
        if (t == 1890 && (master == null || !master.isAlive())) spawn(level, Region.DUNES);
        if (t == 1900 && master != null && master.isAlive()) {
            log("opening the caravan interface for the viewer");
            master.openFor(viewer);
        }
        if (t > 1940 && !atlasOpened && !(viewer.containerMenu instanceof CaravanMenu)) {
            atlasOpened = true;
            atlasTick = t;
            log("opening the atlas for the viewer");
            AtlasItem.open(viewer);
        }
        // Then the loading dock and the trading post, seen from close by (a container screen closes when its block is far)
        if (atlasOpened && menuStep == 0 && t > atlasTick + 20 && !(viewer.containerMenu instanceof AtlasMenu) && outDock != null) {
            menuStep = 1;
            menuTick = t;
            menuFocus = outDock;
        }
        if (menuStep == 1 && t == menuTick + 12) {
            menuStep = 2;
            if (level.getBlockEntity(outDock) instanceof DockBlockEntity dock) {
                dock.setItem(3, new ItemStack(Items.OAK_LOG, 32));
                dock.setItem(4, new ItemStack(Items.WHITE_WOOL, 16));
                dock.setItem(5, new ItemStack(Items.EMERALD, 4));
                log("opening the loading dock for the viewer");
                viewer.openMenu(dock);
            }
        }
        if (menuStep == 2 && !(viewer.containerMenu instanceof DockMenu) && postPos != null) {
            menuStep = 3;
            menuTick = t;
            menuFocus = postPos;
        }
        if (menuStep == 3 && t == menuTick + 12) {
            menuStep = 4;
            if (level.getBlockEntity(postPos) instanceof TradingPostBlockEntity post) {
                log("opening the trading post for the viewer");
                viewer.openMenu(post);
            }
        }
        // A second caravan comes to the relay while the first one is halted: it parks at a hitching post
        if (t == 1300 && postA != null) {
            relayGuest = CaravanSpawner.spawnCaravanAt(level, stop.offset(26, 0, 0), stop, Region.STEPPE);
            log("a second caravan comes to the relay");
        }
        // Last: a night at the inn (the train parked, then the master in bed)
        if (menuStep == 4 && t > menuTick + 20 && !(viewer.containerMenu instanceof com.chroniclecaravans.menu.TradingPostMenu) && bedHead != null) {
            menuStep = 5;
            menuTick = t;
            menuFocus = null;
            if (relayGuest != null && relayGuest.isAlive()) relayGuest.vanish(level, false);
            command(level, "time set 14000");
            command(level, "effect give @a minecraft:night_vision 120 0 true");
            guest = CaravanSpawner.spawnCaravanAt(level, stop.offset(6, 0, 13), null, Region.WOODS);
            log("night falls on the inn for the viewer");
        }
        if (menuStep == 5 && guest != null && t % 100 == 0) {
            log("inn guest: state=" + guest.state() + " parked=" + guest.parked() + " sleeping=" + guest.isSleeping() + " at " + guest.blockPosition());
        }
        if (t % 5 != 0) return;
        double x, y, z, lx, ly, lz;
        if (menuStep == 5 && guest != null && guest.isAlive()) {
            Entity w = guest.wagon(level);
            if (guest.isSleeping()) {
                // Close-up of the bed
                x = bedHead.getX() + 3.2; y = bedHead.getY() + 2.4; z = bedHead.getZ() + 3.4;
                lx = bedHead.getX() + 0.5; ly = bedHead.getY() + 0.3; lz = bedHead.getZ() + 0.8;
            } else if (guest.parked() && w != null) {
                // The parked train, with the inn behind it
                x = (w.getX() + innPos.getX()) / 2 + 7; y = w.getY() + 6; z = (w.getZ() + innPos.getZ()) / 2 + 9;
                lx = (w.getX() + innPos.getX()) / 2; ly = w.getY() + 0.5; lz = (w.getZ() + innPos.getZ()) / 2;
            } else {
                x = guest.getX() + 5; y = guest.getY() + 6; z = guest.getZ() + 9;
                lx = guest.getX(); ly = guest.getY() + 0.5; lz = guest.getZ();
            }
        } else if (menuStep > 0 && menuFocus != null) {
            x = menuFocus.getX() + 3.0; y = menuFocus.getY() + 2.0; z = menuFocus.getZ() + 4.0;
            lx = menuFocus.getX() + 0.5; ly = menuFocus.getY() + 0.5; lz = menuFocus.getZ() + 0.5;
        } else if (t >= 1740 && t < 1890 && postA != null) {
            // The relay: the milestone with its pennants, the hitching posts, the second caravan's train parked by a post
            x = stop.getX() + 15; y = stop.getY() + 7; z = stop.getZ() + 7;
            lx = stop.getX() + 7; ly = stop.getY() + 0.5; lz = stop.getZ() - 4;
        } else if (t < 60 || master == null || !master.isAlive()) {
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
