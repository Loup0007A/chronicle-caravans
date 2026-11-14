package com.chroniclecaravans.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.block.DockBlockEntity;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.platform.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Freight: parcels travel from stop to stop with the caravans until they reach their destination. A parcel
 * waits at a stop (its "depot") until a caravan leaves in its direction; the route is the shortest chain of
 * stops (each within the link range of the next). Loading docks next to a milestone ship their content
 * automatically (to the stop named like the dock) and receive what arrives.
 */
public final class Logistics {
    private Logistics() {}

    /** Docks and containers this close to a milestone belong to its stop. */
    public static final int DOCK_RANGE = 8;

    // ---------------------------------------------------------------- the network of stops

    private static List<CaravanData.Stop> nodes(CaravanData data) {
        List<CaravanData.Stop> out = new ArrayList<>();
        for (CaravanData.Stop s : data.stops()) if (!s.village || CCConfig.VILLAGE_STOPS.get()) out.add(s);
        return out;
    }

    /**
     * Shortest routes towards {@code to}: for every stop that can reach it, the next stop on the way
     * (key: stop, value: {next hop, number of hops}).
     */
    public static Map<BlockPos, Object[]> routesTo(CaravanData data, BlockPos to) {
        Map<BlockPos, Object[]> out = new HashMap<>();
        List<CaravanData.Stop> all = nodes(data);
        double r2 = (double) CCConfig.LINK_RANGE.get() * CCConfig.LINK_RANGE.get();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        out.put(to, new Object[] {null, 0});
        queue.add(to);
        while (!queue.isEmpty()) {
            BlockPos u = queue.poll();
            int d = (Integer) out.get(u)[1];
            for (CaravanData.Stop s : all) {
                if (out.containsKey(s.pos) || s.pos.distSqr(u) > r2) continue;
                out.put(s.pos, new Object[] {u, d + 1});
                queue.add(s.pos);
            }
        }
        return out;
    }

    /** The next stop on the way from {@code from} to {@code to}, or null (unreachable, or already there). */
    public static BlockPos nextHop(CaravanData data, BlockPos from, BlockPos to) {
        Object[] r = routesTo(data, to).get(from);
        return r == null ? null : (BlockPos) r[0];
    }

    /** Number of caravan legs between two stops (-1: no route). */
    public static int hops(CaravanData data, BlockPos from, BlockPos to) {
        Object[] r = routesTo(data, to).get(from);
        return r == null ? -1 : (Integer) r[1];
    }

    /** Every stop reachable from {@code from}, with the number of legs (the stop itself excluded). */
    public static Map<BlockPos, Integer> reachable(CaravanData data, BlockPos from) {
        Map<BlockPos, Integer> out = new HashMap<>();
        for (Map.Entry<BlockPos, Object[]> e : routesTo(data, from).entrySet()) {
            if (!e.getKey().equals(from)) out.put(e.getKey(), (Integer) e.getValue()[1]);
        }
        return out;
    }

    /** Freight price: one emerald, plus one per leg. */
    public static int fee(int hops) {
        return 1 + Math.max(1, hops);
    }

    /** The stop a dock address designates: a stop with that name (case and accents as typed, spaces trimmed). */
    public static CaravanData.Stop byAddress(CaravanData data, String address) {
        String a = address == null ? "" : address.strip();
        if (a.isEmpty()) return null;
        for (CaravanData.Stop s : data.stops()) if (s.name.strip().equalsIgnoreCase(a)) return s;
        return null;
    }

    // ---------------------------------------------------------------- docks

    /** The loading docks of a stop (loaded and still there). */
    public static List<DockBlockEntity> docksOf(ServerLevel level, BlockPos stop) {
        CaravanData data = CaravanData.get(level);
        List<DockBlockEntity> out = new ArrayList<>();
        for (Iterator<BlockPos> it = data.docks().iterator(); it.hasNext(); ) {
            BlockPos d = it.next();
            if (d.distSqr(stop) > DOCK_RANGE * DOCK_RANGE || !level.isLoaded(d)) continue;
            if (level.getBlockEntity(d) instanceof DockBlockEntity dock) out.add(dock);
            else {
                it.remove();
                data.setDirty();
            }
        }
        return out;
    }

    /** The stop a dock belongs to: the closest stop within reach. */
    public static CaravanData.Stop stopOf(ServerLevel level, BlockPos dock) {
        CaravanData.Stop best = null;
        double bestD = DOCK_RANGE * DOCK_RANGE + 1;
        for (CaravanData.Stop s : CaravanData.get(level).stops()) {
            double d = s.pos.distSqr(dock);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** A caravan halts at {@code stop}: the outgoing docks hand over their goods (fee paid with their emeralds). */
    public static int collectDocks(ServerLevel level, CaravanData.Stop stop) {
        if (!CCConfig.FREIGHT.get()) return 0;
        CaravanData data = CaravanData.get(level);
        int shipped = 0;
        for (DockBlockEntity dock : docksOf(level, stop.pos)) {
            if (!dock.outgoing() || dock.goodsCount() == 0) continue;
            CaravanData.Stop dest = byAddress(data, dock.address());
            if (dest == null || dest.pos.equals(stop.pos)) {
                dock.setStatus(DockBlockEntity.NO_ADDRESS);
                continue;
            }
            int hops = hops(data, stop.pos, dest.pos);
            if (hops < 0) {
                dock.setStatus(DockBlockEntity.NO_ROUTE);
                continue;
            }
            int fee = fee(hops);
            if (dock.emeralds() < fee) {
                dock.setStatus(DockBlockEntity.NO_FEE);
                continue;
            }
            dock.takeEmeralds(fee);
            List<ItemStack> goods = dock.takeGoods();
            Parcel p = Parcel.of(level, goods, stop.pos, dest.pos, dock.owner(), dock.ownerName(), true);
            data.depot(stop.pos).add(p);
            data.setDirty();
            dock.setStatus(DockBlockEntity.SHIPPED);
            shipped++;
        }
        return shipped;
    }

    // ---------------------------------------------------------------- caravans

    /**
     * Where the parcels waiting at {@code at} need to go next (the next hop of the oldest one that can travel),
     * or null. Parcels whose destination disappeared go back to their origin.
     */
    public static BlockPos pickNext(ServerLevel level, BlockPos at) {
        CaravanData data = CaravanData.get(level);
        List<Parcel> waiting = data.depots().get(at);
        if (waiting == null || waiting.isEmpty()) return null;
        for (Parcel p : waiting) {
            if (data.stop(p.dest) == null && data.stop(p.origin) != null) {
                p.dest = p.origin;
                data.setDirty();
            }
            if (p.dest.equals(at)) continue;
            BlockPos hop = nextHop(data, at, p.dest);
            if (hop != null) return hop;
        }
        return null;
    }

    /**
     * What a caravan leaving {@code at} now would do with the parcels waiting there: the stop it would head for
     * (null: the parcels do not decide its way), and, in the depot's order, whether each one would board
     * ({@code room}: free places on board). Same rules as {@link #pickNext} and {@link #load}.
     */
    public record Departure(BlockPos next, List<Parcel> waiting, boolean[] boards) {}

    public static Departure departure(ServerLevel level, BlockPos at, int room) {
        CaravanData data = CaravanData.get(level);
        List<Parcel> waiting = data.depots().get(at);
        if (waiting == null || waiting.isEmpty()) return new Departure(null, List.of(), new boolean[0]);
        BlockPos next = pickNext(level, at);
        boolean[] boards = new boolean[waiting.size()];
        for (int i = 0; i < waiting.size(); i++) {
            Parcel p = waiting.get(i);
            if (next != null && room > 0 && next.equals(nextHop(data, at, p.dest))) {
                boards[i] = true;
                room--;
            }
        }
        return new Departure(next, List.copyOf(waiting), boards);
    }

    /** Loads the parcels of {@code at} that go through {@code next}, as long as the caravan has room. */
    public static int load(ServerLevel level, CaravanMaster m, BlockPos at, BlockPos next) {
        CaravanData data = CaravanData.get(level);
        List<Parcel> waiting = data.depots().get(at);
        if (waiting == null || waiting.isEmpty()) return 0;
        Map<BlockPos, BlockPos> hopCache = new HashMap<>();
        int loaded = 0;
        for (Iterator<Parcel> it = waiting.iterator(); it.hasNext() && m.cargo().size() < m.cargoCapacity(); ) {
            Parcel p = it.next();
            BlockPos hop = hopCache.computeIfAbsent(p.dest, d -> {
                BlockPos h = nextHop(data, at, d);
                return h == null ? BlockPos.ZERO : h;
            });
            if (!next.equals(hop)) continue;
            it.remove();
            m.cargo().add(p);
            loaded++;
        }
        if (loaded > 0) data.setDirty();
        return loaded;
    }

    /** A caravan arrives at a stop: parcels for here are delivered, the others wait here for their next leg. */
    public static int unload(ServerLevel level, CaravanMaster m, CaravanData.Stop stop) {
        CaravanData data = CaravanData.get(level);
        int delivered = 0;
        for (Parcel p : new ArrayList<>(m.cargo())) {
            m.cargo().remove(p);
            if (p.dest.equals(stop.pos)) {
                deliver(level, stop, p);
                delivered++;
            } else {
                data.depot(stop.pos).add(p);
            }
        }
        // Parcels sent to this very stop (from a dock here, after its stop got renamed) are delivered too
        List<Parcel> here = data.depots().get(stop.pos);
        if (here != null) {
            for (Iterator<Parcel> it = here.iterator(); it.hasNext(); ) {
                Parcel p = it.next();
                if (p.dest.equals(stop.pos)) {
                    it.remove();
                    deliver(level, stop, p);
                    delivered++;
                }
            }
        }
        data.setDirty();
        return delivered;
    }

    // ---------------------------------------------------------------- delivery

    /** What is left of {@code stack} after putting it in {@code c}. */
    public static ItemStack insert(Container c, ItemStack stack) {
        stack = stack.copy();
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack in = c.getItem(i);
            if (!in.isEmpty() && Compat.sameItem(in, stack) && in.getCount() < in.getMaxStackSize()) {
                int k = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(k);
                stack.shrink(k);
            }
        }
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stack)) {
                c.setItem(i, stack.copy());
                stack = ItemStack.EMPTY;
            }
        }
        c.setChanged();
        return stack;
    }

    /** Where deliveries go at a stop: its receiving docks, then the containers around the milestone. */
    private static List<Container> receivers(ServerLevel level, BlockPos stop) {
        List<Container> out = new ArrayList<>();
        for (DockBlockEntity d : docksOf(level, stop)) if (!d.outgoing()) out.add(d);
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(stop.relative(dir));
            if (be instanceof DockBlockEntity) continue;
            if (be instanceof Container c && !out.contains(c)) out.add(c);
        }
        return out;
    }

    /** The parcel as an item (opened with a right click). */
    public static ItemStack parcelItem(ServerLevel level, Parcel p) {
        CaravanData data = CaravanData.get(level);
        ItemStack box = new ItemStack(ChronicleCaravans.PARCEL.get());
        Compat.setData(box, p.save());
        CaravanData.Stop from = data.stop(p.origin), to = data.stop(p.dest);
        Compat.setLore(box, List.of(
                Component.translatable("item.chroniclecaravans.caravan_parcel.from", p.senderName.isEmpty() ? "?" : p.senderName,
                        from == null ? Component.literal("?") : from.displayName()).withStyle(ChatFormatting.GRAY),
                Component.translatable("item.chroniclecaravans.caravan_parcel.to", to == null ? Component.literal("?") : to.displayName())
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.chroniclecaravans.caravan_parcel.contents", p.stacks).withStyle(ChatFormatting.DARK_GRAY)));
        return box;
    }

    private static void drop(ServerLevel level, BlockPos at, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, stack);
        e.setUnlimitedLifetime();
        e.setDeltaMovement(0, 0.1, 0);
        level.addFreshEntity(e);
    }

    /** A parcel reaches its destination. */
    public static void deliver(ServerLevel level, CaravanData.Stop stop, Parcel p) {
        List<Container> to = receivers(level, stop.pos);
        if (p.bulk) {
            // Dock to dock: the goods go straight into the receiving docks (hoppers can take them from there)
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack s : p.unpack(level)) {
                for (Container c : to) {
                    if (s.isEmpty()) break;
                    s = insert(c, s);
                }
                if (!s.isEmpty()) rest.add(s);
            }
            if (!rest.isEmpty()) {
                Parcel left = Parcel.of(level, rest, p.origin, p.dest, p.sender, p.senderName, false);
                drop(level, stop.pos, parcelItem(level, left));
            }
        } else {
            ItemStack box = parcelItem(level, p);
            for (Container c : to) {
                if (box.isEmpty()) break;
                box = insert(c, box);
            }
            if (!box.isEmpty()) drop(level, stop.pos, box);
        }
        level.playSound(null, stop.pos, net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop from = data.stop(p.origin);
        Component fromName = from == null ? Component.literal("?") : from.displayName();
        if (stop.owner != null && !stop.owner.equals(p.sender)) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(stop.owner);
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("msg.chroniclecaravans.parcel_arrived", fromName, stop.displayName()).withStyle(ChatFormatting.GREEN));
            }
        }
        if (p.sender != null && !p.bulk) {
            ServerPlayer sender = level.getServer().getPlayerList().getPlayer(p.sender);
            if (sender != null) {
                sender.sendSystemMessage(Component.translatable("msg.chroniclecaravans.parcel_delivered", stop.displayName()).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** The caravan was destroyed: its cargo falls on the road. */
    public static void spill(ServerLevel level, BlockPos at, List<Parcel> cargo) {
        for (Parcel p : cargo) drop(level, at, parcelItem(level, p));
        cargo.clear();
    }

    /** A caravan leaves the area with cargo (it should not): the parcels wait at the closest stop. */
    public static void store(ServerLevel level, BlockPos near, List<Parcel> cargo) {
        if (cargo.isEmpty()) return;
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop s = data.nearestStop(near, Double.MAX_VALUE / 4, null);
        if (s == null) {
            spill(level, near, cargo);
            return;
        }
        data.depot(s.pos).addAll(cargo);
        cargo.clear();
        data.setDirty();
    }

    /** Parcels left at a stop that no longer exists are dropped there (once the land is loaded). */
    public static void cleanDepots(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        for (Iterator<Map.Entry<BlockPos, List<Parcel>>> it = data.depots().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, List<Parcel>> e = it.next();
            if (e.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            if (data.stop(e.getKey()) != null || !level.isLoaded(e.getKey())) continue;
            for (Parcel p : e.getValue()) drop(level, e.getKey(), parcelItem(level, p));
            it.remove();
            data.setDirty();
        }
    }

    /** Emeralds a parcel costs from {@code from} to {@code to} (-1: no route). */
    public static int feeBetween(CaravanData data, BlockPos from, BlockPos to) {
        int h = hops(data, from, to);
        return h < 0 ? -1 : fee(h);
    }

    /** Is {@code stack} a parcel item? */
    public static boolean isParcel(ItemStack stack) {
        return stack.is(ChronicleCaravans.PARCEL.get());
    }
}
