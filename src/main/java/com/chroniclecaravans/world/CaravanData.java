package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
//#if mc121
import net.minecraft.core.HolderLookup;
//#endif
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import com.chroniclecaravans.platform.Nbt;

/**
 * World data, stored with the Overworld: the stops (milestones and village bells), the inns, the named and
 * built routes, the caravans on the roads, and each player's standing with the guilds.
 */
public class CaravanData extends SavedData {
    private static final String NAME = "chroniclecaravans";
    //#if mc26
    //$$ private static final net.minecraft.world.level.saveddata.SavedDataType<CaravanData> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
    //$$         com.chroniclecaravans.ChronicleCaravans.id(NAME), CaravanData::new,
    //$$         CompoundTag.CODEC.xmap(CaravanData::load, d -> d.save(new CompoundTag())));
    //#elif mc121
    private static final Factory<CaravanData> FACTORY = new Factory<>(CaravanData::new, CaravanData::load, null);
    //#endif

    /** A milestone or a village bell: a place caravans visit. */
    public static final class Stop {
        public final BlockPos pos;
        public UUID owner;
        public String ownerName = "";
        /** Custom name (renamed in an anvil or on the atlas), or empty. */
        public String name = "";
        /** Game time from which the stop wants a caravan again. */
        public long nextVisit;
        /** A village bell rather than a milestone. */
        public boolean village;
        /** Game time of the last hired caravan. */
        public long lastCall = Long.MIN_VALUE / 2;
        /** Game time of the last caravan that halted here. */
        public long lastServed;
        /** Someone paid for a caravan: the first free one comes. */
        public boolean hired;
        /** Relay tier (relay charters): how many caravans the milestone takes at once, with enough hitching posts. */
        public int tier = 1;

        Stop(BlockPos pos) {
            this.pos = pos;
        }

        public Component displayName() {
            if (!name.isEmpty()) return Component.literal(name).withStyle(ChatFormatting.YELLOW);
            if (village) return Component.translatable("stop.chroniclecaravans.village", pos.getX(), pos.getZ()).withStyle(ChatFormatting.YELLOW);
            return Component.translatable("stop.chroniclecaravans.default", ownerName.isEmpty() ? "?" : ownerName).withStyle(ChatFormatting.YELLOW);
        }
    }

    /** An inn sign: caravans nearby spend the night there. */
    public static final class Inn {
        public final BlockPos pos;
        public UUID owner;
        public String ownerName = "";
        public String name = "";

        Inn(BlockPos pos) {
            this.pos = pos;
        }

        public Component displayName() {
            if (!name.isEmpty()) return Component.literal(name).withStyle(ChatFormatting.GOLD);
            if (ownerName.isEmpty()) return Component.translatable("gui.chroniclecaravans.atlas.kind.inn").withStyle(ChatFormatting.GOLD);
            return Component.translatable("inn.chroniclecaravans.default", ownerName).withStyle(ChatFormatting.GOLD);
        }
    }

    /** A road between two stops: named on the atlas, or built by a road crew. */
    public static final class Route {
        public final BlockPos a, b;
        public String name = "";
        public boolean built;

        Route(BlockPos a, BlockPos b) {
            this.a = a;
            this.b = b;
        }
    }

    /** A player's standing with the guilds. */
    public static final class Profile {
        public int xp;
        public int skills;
        /** Emeralds traded since the last experience point. */
        public int traded;
    }

    /** What the world knows of a caravan, loaded or not. */
    public static final class Caravan {
        public long lastSeen;
        public BlockPos pos = BlockPos.ZERO;
        public Region region = Region.STEPPE;
        public BlockPos target;
        /** It carries parcels: never moved elsewhere while asleep. */
        public boolean cargo;
    }

    private final Map<BlockPos, Stop> stops = new LinkedHashMap<>();
    private final Map<BlockPos, Inn> inns = new LinkedHashMap<>();
    private final Map<String, Route> routes = new LinkedHashMap<>();
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<UUID, Caravan> caravans = new LinkedHashMap<>();
    /** Supply and demand: per item id, {heat, game time of the last change}. */
    private final Map<String, double[]> market = new HashMap<>();
    /** Parcels waiting at a stop for a caravan going their way. */
    private final Map<BlockPos, List<Parcel>> depots = new LinkedHashMap<>();
    /** Loading docks (checked against the world when used). */
    private final Set<BlockPos> docks = new LinkedHashSet<>();
    /** Hitching posts: where caravans park their trains (checked against the world when used). */
    private final Set<BlockPos> posts = new LinkedHashSet<>();
    /** Game time before which no new caravan appears. */
    public long nextSpawn;

    public static CaravanData get(MinecraftServer server) {
        //#if mc120
        //$$ return server.overworld().getDataStorage().computeIfAbsent(CaravanData::load, CaravanData::new, NAME);
        //#elif mc26
        //$$ return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        //#else
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        //#endif
    }

    public static CaravanData get(ServerLevel level) {
        return get(level.getServer());
    }

    // ---------------------------------------------------------------- stops

    public Collection<Stop> stops() {
        return stops.values();
    }

    public Stop stop(BlockPos pos) {
        return stops.get(pos);
    }

    public Stop addStop(BlockPos pos, UUID owner, String ownerName, String name, long firstVisit) {
        Stop s = new Stop(pos.immutable());
        s.owner = owner;
        s.ownerName = ownerName == null ? "" : ownerName;
        s.name = name == null ? "" : name;
        s.nextVisit = firstVisit;
        stops.put(s.pos, s);
        setDirty();
        return s;
    }

    /** A village bell becomes a stop (no owner). */
    public Stop addVillage(BlockPos pos, long firstVisit) {
        Stop s = addStop(pos, null, "", "", firstVisit);
        s.village = true;
        return s;
    }

    public void removeStop(BlockPos pos) {
        if (stops.remove(pos) != null) setDirty();
    }

    /** Closest stop to {@code from} within {@code range}, skipping the positions in {@code visited}. */
    public Stop nearestStop(BlockPos from, double range, Collection<BlockPos> visited) {
        Stop best = null;
        double bestD = range * range;
        for (Stop s : stops.values()) {
            if (visited != null && visited.contains(s.pos)) continue;
            if (s.village && !CCConfig.VILLAGE_STOPS.get()) continue;
            double d = s.pos.distSqr(from);
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** The stops linked to {@code from} (within the link range), closest first. */
    public List<Stop> linked(BlockPos from) {
        double r = CCConfig.LINK_RANGE.get();
        List<Stop> out = new ArrayList<>();
        for (Stop s : stops.values()) {
            if (s.pos.equals(from) || (s.village && !CCConfig.VILLAGE_STOPS.get())) continue;
            if (s.pos.distSqr(from) <= r * r) out.add(s);
        }
        out.sort((x, y) -> Double.compare(x.pos.distSqr(from), y.pos.distSqr(from)));
        return out;
    }

    public List<Stop> stopsOf(UUID owner) {
        List<Stop> out = new ArrayList<>();
        for (Stop s : stops.values()) if (owner.equals(s.owner)) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------- inns

    public Collection<Inn> inns() {
        return inns.values();
    }

    public Inn inn(BlockPos pos) {
        return inns.get(pos);
    }

    public Inn addInn(BlockPos pos, UUID owner, String ownerName) {
        Inn inn = new Inn(pos.immutable());
        inn.owner = owner;
        inn.ownerName = ownerName == null ? "" : ownerName;
        inns.put(inn.pos, inn);
        setDirty();
        return inn;
    }

    public void removeInn(BlockPos pos) {
        if (inns.remove(pos) != null) setDirty();
    }

    public Inn nearestInn(BlockPos from, double range) {
        Inn best = null;
        double bestD = range * range;
        for (Inn i : inns.values()) {
            double d = i.pos.distSqr(from);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- routes

    private static String key(BlockPos a, BlockPos b) {
        long x = a.asLong(), y = b.asLong();
        return x < y ? x + ":" + y : y + ":" + x;
    }

    public Collection<Route> routes() {
        return routes.values();
    }

    /** The record of the road between two stops, or null (an unnamed, unbuilt link). */
    public Route route(BlockPos a, BlockPos b) {
        return routes.get(key(a, b));
    }

    public Route routeOrCreate(BlockPos a, BlockPos b) {
        return routes.computeIfAbsent(key(a, b), k -> {
            setDirty();
            return a.asLong() < b.asLong() ? new Route(a.immutable(), b.immutable()) : new Route(b.immutable(), a.immutable());
        });
    }

    public String routeName(BlockPos a, BlockPos b) {
        Route r = route(a, b);
        return r == null ? "" : r.name;
    }

    /** Forgets the routes of a stop that no longer exists. */
    private void dropRoutesOf(BlockPos pos) {
        routes.values().removeIf(r -> (r.a.equals(pos) || r.b.equals(pos)) && !r.built);
    }

    // ---------------------------------------------------------------- caravans

    public Map<UUID, Caravan> caravans() {
        return caravans;
    }

    /** Called by every caravan now and then: it is still on the roads. */
    public void seen(UUID id, long now, BlockPos pos, Region region, BlockPos target) {
        seen(id, now, pos, region, target, false);
    }

    public void seen(UUID id, long now, BlockPos pos, Region region, BlockPos target, boolean cargo) {
        Caravan c = caravans.computeIfAbsent(id, k -> new Caravan());
        c.lastSeen = now;
        c.pos = pos.immutable();
        c.region = region;
        c.target = target;
        c.cargo = cargo;
        setDirty();
    }

    // ---------------------------------------------------------------- market, parcels, docks

    public Map<String, double[]> market() {
        return market;
    }

    /** The parcels waiting at a stop (a live list: changes must be followed by {@link #setDirty()}). */
    public List<Parcel> depot(BlockPos stop) {
        return depots.computeIfAbsent(stop.immutable(), k -> new ArrayList<>());
    }

    public Map<BlockPos, List<Parcel>> depots() {
        return depots;
    }

    public Set<BlockPos> docks() {
        return docks;
    }

    public void addDock(BlockPos pos) {
        if (docks.add(pos.immutable())) setDirty();
    }

    public void removeDock(BlockPos pos) {
        if (docks.remove(pos)) setDirty();
    }

    public Set<BlockPos> posts() {
        return posts;
    }

    public void addPost(BlockPos pos) {
        if (posts.add(pos.immutable())) setDirty();
    }

    public void removePost(BlockPos pos) {
        if (posts.remove(pos)) setDirty();
    }

    /** The hitching posts within {@code range} of {@code center} that are still there (loaded ones only), closest first. */
    public List<BlockPos> postsNear(net.minecraft.server.level.ServerLevel level, BlockPos center, int range) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : posts) {
            if (Math.abs(p.getX() - center.getX()) > range || Math.abs(p.getZ() - center.getZ()) > range || Math.abs(p.getY() - center.getY()) > 8) continue;
            if (level.isLoaded(p) && !level.getBlockState(p).is(com.chroniclecaravans.ChronicleCaravans.HITCHING_POST.get())) continue;
            out.add(p);
        }
        out.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return out;
    }

    public boolean knows(UUID id) {
        return caravans.containsKey(id);
    }

    public void forget(UUID id) {
        if (caravans.remove(id) != null) setDirty();
    }

    // ---------------------------------------------------------------- guild

    public Profile profile(UUID player) {
        return profiles.computeIfAbsent(player, k -> new Profile());
    }

    public int xp(UUID player) {
        Profile p = profiles.get(player);
        return p == null ? 0 : p.xp;
    }

    public int skills(UUID player) {
        Profile p = profiles.get(player);
        return p == null ? 0 : p.skills;
    }

    /** The level that unlocks offers (everything when reputation is switched off). */
    public int level(UUID player) {
        return CCConfig.REPUTATION_ENABLED.get() ? Guild.levelOf(xp(player)) : Guild.MAX_LEVEL;
    }

    public void setXp(UUID player, int value) {
        profile(player).xp = Math.max(-200, Math.min(1_000_000, value));
        setDirty();
    }

    /** Learns a skill if the player can. Returns true on success. */
    public boolean learn(UUID player, Guild.Skill skill) {
        Profile p = profile(player);
        if (Guild.blocker(p.xp, p.skills, skill) != null) return false;
        p.skills |= skill.bit();
        setDirty();
        return true;
    }

    /** Same, for a player who may be offline (a trading post trades for its owner). */
    public void traded(MinecraftServer server, UUID player, int emeralds) {
        if (player == null || emeralds <= 0) return;
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            traded(online, emeralds);
            return;
        }
        Profile p = profile(player);
        p.traded += emeralds;
        int points = p.traded / 10;
        p.traded %= 10;
        if (points > 0 && CCConfig.REPUTATION_ENABLED.get()) p.xp += points;
        setDirty();
    }

    /** Emeralds traded with a caravan: one experience point every ten. */
    public void traded(ServerPlayer player, int emeralds) {
        if (emeralds <= 0) return;
        Profile p = profile(player.getUUID());
        p.traded += emeralds;
        int points = p.traded / 10;
        p.traded %= 10;
        setDirty();
        if (points > 0) addXp(player, points, "reason.chroniclecaravans.trade");
    }

    /** Changes a player's standing and tells them, with a fanfare on a new level. */
    public void addXp(ServerPlayer player, int delta, String reasonKey) {
        if (!CCConfig.REPUTATION_ENABLED.get() || delta == 0) return;
        Profile p = profile(player.getUUID());
        int before = Guild.levelOf(p.xp);
        setXp(player.getUUID(), p.xp + delta);
        int now = Guild.levelOf(p.xp);
        ChatFormatting c = delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        Component line = Component.translatable("msg.chroniclecaravans.reputation", (delta > 0 ? "+" : "") + delta, Component.translatable(reasonKey),
                Guild.progress(p.xp), Guild.needed(now)).withStyle(c);
        player.displayClientMessage(line, true);
        if (now > before) {
            player.sendSystemMessage(Component.translatable("msg.chroniclecaravans.level_up", now, Guild.title(p.xp, p.skills)).withStyle(ChatFormatting.GOLD));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        } else if (now < before) {
            player.sendSystemMessage(Component.translatable("msg.chroniclecaravans.level_down", now).withStyle(ChatFormatting.RED));
        }
    }

    /** Same, for a player who may be offline. */
    public void addXp(MinecraftServer server, UUID player, int delta, String reasonKey) {
        if (player == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) addXp(online, delta, reasonKey);
        else if (CCConfig.REPUTATION_ENABLED.get()) setXp(player, xp(player) + delta);
    }

    // ---------------------------------------------------------------- storage

    //#if mc120 || mc26
    //$$ private static CaravanData load(CompoundTag tag) {
    //#else
    private static CaravanData load(CompoundTag tag, HolderLookup.Provider registries) {
    //#endif
        CaravanData d = new CaravanData();
        ListTag list = Nbt.getList(tag, "Stops");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = Nbt.at(list, i);
            Stop s = new Stop(pos(t));
            if (Nbt.hasUUID(t, "Owner")) s.owner = Nbt.getUUID(t, "Owner");
            s.ownerName = Nbt.getString(t, "OwnerName");
            s.name = Nbt.getString(t, "Name");
            s.nextVisit = Nbt.getLong(t, "NextVisit");
            if (t.contains("LastCall")) s.lastCall = Nbt.getLong(t, "LastCall");
            s.lastServed = Nbt.getLong(t, "LastServed");
            s.village = Nbt.getBoolean(t, "Village");
            s.hired = Nbt.getBoolean(t, "Hired");
            s.tier = Math.max(1, Nbt.getInt(t, "Tier"));
            d.stops.put(s.pos, s);
        }
        list = Nbt.getList(tag, "Inns");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = Nbt.at(list, i);
            Inn inn = new Inn(pos(t));
            if (Nbt.hasUUID(t, "Owner")) inn.owner = Nbt.getUUID(t, "Owner");
            inn.ownerName = Nbt.getString(t, "OwnerName");
            inn.name = Nbt.getString(t, "Name");
            d.inns.put(inn.pos, inn);
        }
        list = Nbt.getList(tag, "Routes");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = Nbt.at(list, i);
            Route r = new Route(BlockPos.of(Nbt.getLong(t, "A")), BlockPos.of(Nbt.getLong(t, "B")));
            r.name = Nbt.getString(t, "Name");
            r.built = Nbt.getBoolean(t, "Built");
            d.routes.put(key(r.a, r.b), r);
        }
        list = Nbt.getList(tag, "Caravans");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = Nbt.at(list, i);
            if (!Nbt.hasUUID(t, "Id")) continue;
            Caravan c = new Caravan();
            c.lastSeen = Nbt.getLong(t, "LastSeen");
            c.pos = BlockPos.of(Nbt.getLong(t, "Pos"));
            c.region = Region.byName(Nbt.getString(t, "Region"));
            if (t.contains("Target")) c.target = BlockPos.of(Nbt.getLong(t, "Target"));
            c.cargo = Nbt.getBoolean(t, "Cargo");
            d.caravans.put(Nbt.getUUID(t, "Id"), c);
        }
        list = Nbt.getList(tag, "Market");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = Nbt.at(list, i);
            d.market.put(Nbt.getString(t, "K"), new double[] {Nbt.getDouble(t, "H"), Nbt.getLong(t, "T")});
        }
        list = Nbt.getList(tag, "Depots");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = Nbt.at(list, i);
            List<Parcel> parcels = new ArrayList<>();
            Parcel.loadAll(Nbt.getList(t, "Parcels"), parcels);
            if (!parcels.isEmpty()) d.depots.put(BlockPos.of(Nbt.getLong(t, "Stop")), parcels);
        }
        for (long l : Nbt.getLongArray(tag, "Docks")) d.docks.add(BlockPos.of(l));
        for (long l : Nbt.getLongArray(tag, "Posts")) d.posts.add(BlockPos.of(l));
        CompoundTag prof = Nbt.getCompound(tag, "Guild");
        for (String k : Nbt.keys(prof)) {
            try {
                CompoundTag t = Nbt.getCompound(prof, k);
                Profile p = new Profile();
                p.xp = Nbt.getInt(t, "Xp");
                p.skills = Nbt.getInt(t, "Skills");
                p.traded = Nbt.getInt(t, "Traded");
                d.profiles.put(UUID.fromString(k), p);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // 1.0: plain reputation points become guild experience
        CompoundTag rep = Nbt.getCompound(tag, "Reputation");
        for (String k : Nbt.keys(rep)) {
            try {
                UUID id = UUID.fromString(k);
                if (!d.profiles.containsKey(id)) d.profile(id).xp = Nbt.getInt(rep, k);
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.nextSpawn = Nbt.getLong(tag, "NextSpawn");
        return d;
    }

    private static BlockPos pos(CompoundTag t) {
        return new BlockPos(Nbt.getInt(t, "X"), Nbt.getInt(t, "Y"), Nbt.getInt(t, "Z"));
    }

    private static CompoundTag posTag(BlockPos p) {
        CompoundTag t = new CompoundTag();
        t.putInt("X", p.getX());
        t.putInt("Y", p.getY());
        t.putInt("Z", p.getZ());
        return t;
    }

    //#if mc120
    //$$ @Override
    //$$ public CompoundTag save(CompoundTag tag) {
    //#elif mc26
    //$$ public CompoundTag save(CompoundTag tag) {
    //#else
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    //#endif
        ListTag list = new ListTag();
        for (Stop s : stops.values()) {
            CompoundTag t = posTag(s.pos);
            if (s.owner != null) Nbt.putUUID(t, "Owner", s.owner);
            t.putString("OwnerName", s.ownerName);
            t.putString("Name", s.name);
            t.putLong("NextVisit", s.nextVisit);
            t.putLong("LastCall", s.lastCall);
            t.putLong("LastServed", s.lastServed);
            if (s.village) t.putBoolean("Village", true);
            if (s.hired) t.putBoolean("Hired", true);
            if (s.tier > 1) t.putInt("Tier", s.tier);
            list.add(t);
        }
        tag.put("Stops", list);
        list = new ListTag();
        for (Inn inn : inns.values()) {
            CompoundTag t = posTag(inn.pos);
            if (inn.owner != null) Nbt.putUUID(t, "Owner", inn.owner);
            t.putString("OwnerName", inn.ownerName);
            t.putString("Name", inn.name);
            list.add(t);
        }
        tag.put("Inns", list);
        list = new ListTag();
        for (Route r : routes.values()) {
            if (r.name.isEmpty() && !r.built) continue;
            CompoundTag t = new CompoundTag();
            t.putLong("A", r.a.asLong());
            t.putLong("B", r.b.asLong());
            t.putString("Name", r.name);
            if (r.built) t.putBoolean("Built", true);
            list.add(t);
        }
        tag.put("Routes", list);
        list = new ListTag();
        for (Map.Entry<UUID, Caravan> e : caravans.entrySet()) {
            CompoundTag t = new CompoundTag();
            Nbt.putUUID(t, "Id", e.getKey());
            Caravan c = e.getValue();
            t.putLong("LastSeen", c.lastSeen);
            t.putLong("Pos", c.pos.asLong());
            t.putString("Region", c.region.name());
            if (c.target != null) t.putLong("Target", c.target.asLong());
            if (c.cargo) t.putBoolean("Cargo", true);
            list.add(t);
        }
        tag.put("Caravans", list);
        list = new ListTag();
        for (Map.Entry<String, double[]> e : market.entrySet()) {
            if (e.getValue()[0] < 0.01) continue;
            CompoundTag t = new CompoundTag();
            t.putString("K", e.getKey());
            t.putDouble("H", e.getValue()[0]);
            t.putLong("T", (long) e.getValue()[1]);
            list.add(t);
        }
        tag.put("Market", list);
        list = new ListTag();
        for (Map.Entry<BlockPos, List<Parcel>> e : depots.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putLong("Stop", e.getKey().asLong());
            t.put("Parcels", Parcel.saveAll(e.getValue()));
            list.add(t);
        }
        tag.put("Depots", list);
        tag.putLongArray("Docks", docks.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Posts", posts.stream().mapToLong(BlockPos::asLong).toArray());
        CompoundTag prof = new CompoundTag();
        profiles.forEach((k, p) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("Xp", p.xp);
            t.putInt("Skills", p.skills);
            t.putInt("Traded", p.traded);
            prof.put(k.toString(), t);
        });
        tag.put("Guild", prof);
        tag.putLong("NextSpawn", nextSpawn);
        return tag;
    }

    /** Forgets a stop and its unbuilt routes. */
    public void removeStopAndRoutes(BlockPos pos) {
        removeStop(pos);
        dropRoutesOf(pos);
    }
}
