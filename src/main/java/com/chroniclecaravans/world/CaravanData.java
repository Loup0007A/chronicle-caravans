package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

/** World data: the milestones (caravan stops) and each player's reputation. Stored with the Overworld. */
public class CaravanData extends SavedData {
    private static final String NAME = "chroniclecaravans";
    private static final Factory<CaravanData> FACTORY = new Factory<>(CaravanData::new, CaravanData::load);

    /** A milestone: a place caravans visit. */
    public static final class Stop {
        public final BlockPos pos;
        public UUID owner;
        public String ownerName = "";
        /** Custom name (milestone renamed in an anvil), or empty. */
        public String name = "";
        /** Game time of the next scheduled visit. */
        public long nextVisit;
        /** Game time of the last hired caravan. */
        public long lastCall = Long.MIN_VALUE / 2;

        Stop(BlockPos pos) {
            this.pos = pos;
        }

        public Component displayName() {
            if (!name.isEmpty()) return Component.literal(name).withStyle(ChatFormatting.YELLOW);
            return Component.translatable("stop.chroniclecaravans.default", ownerName.isEmpty() ? "?" : ownerName).withStyle(ChatFormatting.YELLOW);
        }
    }

    private final Map<BlockPos, Stop> stops = new LinkedHashMap<>();
    private final Map<UUID, Integer> reputation = new HashMap<>();

    public static CaravanData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
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

    public void removeStop(BlockPos pos) {
        if (stops.remove(pos) != null) setDirty();
    }

    /** Closest stop to {@code from} within {@code range}, skipping the positions in {@code visited}. */
    public Stop nearestStop(BlockPos from, double range, Collection<BlockPos> visited) {
        Stop best = null;
        double bestD = range * range;
        for (Stop s : stops.values()) {
            if (visited != null && visited.contains(s.pos)) continue;
            double d = s.pos.distSqr(from);
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    public List<Stop> stopsOf(UUID owner) {
        List<Stop> out = new ArrayList<>();
        for (Stop s : stops.values()) if (owner.equals(s.owner)) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------- reputation

    public int reputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public Tier tier(UUID player) {
        if (!CCConfig.REPUTATION_ENABLED.get()) return Tier.LEGEND;
        return Tier.of(reputation(player));
    }

    public void setReputation(UUID player, int value) {
        reputation.put(player, Math.max(-100, Math.min(100000, value)));
        setDirty();
    }

    /** Changes a player's reputation and tells them, with a fanfare on a new rank. */
    public void addReputation(ServerPlayer player, int delta, String reasonKey) {
        if (!CCConfig.REPUTATION_ENABLED.get() || delta == 0) return;
        int before = reputation(player.getUUID());
        Tier was = Tier.of(before);
        setReputation(player.getUUID(), before + delta);
        int now = reputation(player.getUUID());
        Tier is = Tier.of(now);
        ChatFormatting c = delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        player.displayClientMessage(Component.translatable("msg.chroniclecaravans.reputation",
                (delta > 0 ? "+" : "") + delta, Component.translatable(reasonKey), now).withStyle(c), true);
        if (is.ordinal() > was.ordinal()) {
            player.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rank_up", is.label()).withStyle(ChatFormatting.GOLD));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        } else if (is.ordinal() < was.ordinal()) {
            player.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rank_down", is.label()).withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- storage

    private static CaravanData load(CompoundTag tag, HolderLookup.Provider registries) {
        CaravanData d = new CaravanData();
        ListTag list = tag.getList("Stops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Stop s = new Stop(new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")));
            if (t.hasUUID("Owner")) s.owner = t.getUUID("Owner");
            s.ownerName = t.getString("OwnerName");
            s.name = t.getString("Name");
            s.nextVisit = t.getLong("NextVisit");
            if (t.contains("LastCall")) s.lastCall = t.getLong("LastCall");
            d.stops.put(s.pos, s);
        }
        CompoundTag rep = tag.getCompound("Reputation");
        for (String k : rep.getAllKeys()) {
            try {
                d.reputation.put(UUID.fromString(k), rep.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Stop s : stops.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("X", s.pos.getX());
            t.putInt("Y", s.pos.getY());
            t.putInt("Z", s.pos.getZ());
            if (s.owner != null) t.putUUID("Owner", s.owner);
            t.putString("OwnerName", s.ownerName);
            t.putString("Name", s.name);
            t.putLong("NextVisit", s.nextVisit);
            t.putLong("LastCall", s.lastCall);
            list.add(t);
        }
        tag.put("Stops", list);
        CompoundTag rep = new CompoundTag();
        reputation.forEach((k, v) -> rep.putInt(k.toString(), v));
        tag.put("Reputation", rep);
        return tag;
    }
}
