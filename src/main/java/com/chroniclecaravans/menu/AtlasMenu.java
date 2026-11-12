package com.chroniclecaravans.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The caravan atlas: the stops, inns and routes around the player (and, for cartographers, the caravans).
 * Each one travels to the client as a named item with a little custom data.
 */
public class AtlasMenu extends AbstractContainerMenu {
    public static final int STOPS = 96, ROUTES = 128, CARAVANS = 12;
    public static final int SIZE = STOPS + ROUTES + CARAVANS;
    public static final int KIND_MILESTONE = 0, KIND_VILLAGE = 1, KIND_INN = 2;
    public static final int D_STOPS = 0, D_ROUTES = 1, D_CARAVANS = 2, D_CARTOGRAPHER = 3, D_RANGE = 4, DATA = 5;

    private final SimpleContainer content = new SimpleContainer(SIZE);
    private final ContainerData data = new SimpleContainerData(DATA);
    private final Player player;

    public AtlasMenu(int id, Inventory inv) {
        super(ChronicleCaravans.ATLAS_MENU.get(), id);
        this.player = inv.player;
        for (int i = 0; i < SIZE; i++) {
            addSlot(new Slot(content, i, -10000, -10000) {
                @Override
                public boolean mayPickup(Player p) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack s) {
                    return false;
                }
            });
        }
        addDataSlots(data);
        if (player instanceof ServerPlayer sp) refresh(sp);
    }

    public int count(int which) {
        return data.get(which);
    }

    public boolean cartographer() {
        return data.get(D_CARTOGRAPHER) != 0;
    }

    public int linkRange() {
        return data.get(D_RANGE);
    }

    public ItemStack stop(int i) {
        return getSlot(i).getItem();
    }

    public ItemStack route(int i) {
        return getSlot(STOPS + i).getItem();
    }

    public ItemStack caravan(int i) {
        return getSlot(STOPS + ROUTES + i).getItem();
    }

    private static boolean canEdit(ServerPlayer p, UUID owner, boolean village) {
        return village || Compat.isOp(p) || p.getUUID().equals(owner);
    }

    /** Rebuilds the content from the world data (on opening and after a rename). */
    public void refresh(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        CaravanData cd = CaravanData.get(level);
        BlockPos me = p.blockPosition();
        for (int i = 0; i < SIZE; i++) content.setItem(i, ItemStack.EMPTY);

        // Stops and inns, nearest first
        List<Object[]> places = new ArrayList<>();
        for (CaravanData.Stop s : cd.stops()) {
            if (s.village && !CCConfig.VILLAGE_STOPS.get()) continue;
            places.add(new Object[] {s.pos, s.village ? KIND_VILLAGE : KIND_MILESTONE, s.displayName(), canEdit(p, s.owner, s.village), s.ownerName});
        }
        for (CaravanData.Inn inn : cd.inns()) places.add(new Object[] {inn.pos, KIND_INN, inn.displayName(), canEdit(p, inn.owner, false), inn.ownerName});
        places.sort((a, b) -> Double.compare(((BlockPos) a[0]).distSqr(me), ((BlockPos) b[0]).distSqr(me)));
        int n = Math.min(STOPS, places.size());
        List<CaravanData.Stop> shown = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Object[] o = places.get(i);
            BlockPos pos = (BlockPos) o[0];
            int kind = (Integer) o[1];
            ItemStack st = new ItemStack(kind == KIND_VILLAGE ? Items.BELL : kind == KIND_INN ? ChronicleCaravans.INN_SIGN_ITEM.get() : ChronicleCaravans.MILESTONE_ITEM.get());
            Compat.setName(st, (Component) o[2]);
            CompoundTag t = new CompoundTag();
            t.putInt("X", pos.getX());
            t.putInt("Y", pos.getY());
            t.putInt("Z", pos.getZ());
            t.putInt("Kind", kind);
            t.putBoolean("Mine", (Boolean) o[3]);
            t.putString("Owner", (String) o[4]);
            Compat.setData(st, t);
            content.setItem(i, st);
            if (kind != KIND_INN) shown.add(cd.stop(pos));
        }
        data.set(D_STOPS, n);

        // Routes: every pair of shown stops within the link range
        double range = CCConfig.LINK_RANGE.get();
        List<CaravanData.Stop[]> pairs = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            for (int j = i + 1; j < shown.size(); j++) {
                CaravanData.Stop a = shown.get(i), b = shown.get(j);
                if (a.pos.distSqr(b.pos) <= range * range) pairs.add(new CaravanData.Stop[] {a, b});
            }
        }
        pairs.sort((x, y) -> Double.compare(mid(x).distSqr(me), mid(y).distSqr(me)));
        int r = 0;
        for (CaravanData.Stop[] pr : pairs) {
            if (r >= ROUTES) break;
            CaravanData.Route route = cd.route(pr[0].pos, pr[1].pos);
            ItemStack st = new ItemStack(route != null && route.built ? Items.DIRT_PATH : Items.PAPER);
            String name = route == null ? "" : route.name;
            if (!name.isEmpty()) Compat.setName(st, Component.literal(name));
            CompoundTag t = new CompoundTag();
            t.putLong("A", pr[0].pos.asLong());
            t.putLong("B", pr[1].pos.asLong());
            t.putString("Name", name);
            t.putBoolean("Built", route != null && route.built);
            t.putBoolean("Mine", Compat.isOp(p) || p.getUUID().equals(pr[0].owner) || p.getUUID().equals(pr[1].owner)
                    || (pr[0].village && pr[1].village));
            Compat.setData(st, t);
            content.setItem(STOPS + r, st);
            r++;
        }
        data.set(D_ROUTES, r);

        // Caravans (cartographers only)
        boolean carto = Guild.has(cd.skills(p.getUUID()), Guild.Skill.CARTOGRAPHER) || p.isCreative() || p.isSpectator();
        data.set(D_CARTOGRAPHER, carto ? 1 : 0);
        data.set(D_RANGE, (int) range);
        int c = 0;
        if (carto) {
            for (Map.Entry<UUID, CaravanData.Caravan> e : cd.caravans().entrySet()) {
                if (c >= CARAVANS) break;
                CaravanData.Caravan cv = e.getValue();
                ItemStack st = new ItemStack(Items.CHEST);
                Compat.setName(st, cv.region.caravanName().withStyle(cv.region.color));
                CompoundTag t = new CompoundTag();
                t.putInt("X", cv.pos.getX());
                t.putInt("Z", cv.pos.getZ());
                if (cv.target != null) {
                    t.putInt("TX", cv.target.getX());
                    t.putInt("TZ", cv.target.getZ());
                    t.putBoolean("HasTarget", true);
                }
                net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromLegacyFormat(cv.region.color);
                t.putInt("Color", tc == null ? 0xFFFFFF : tc.getValue());
                Compat.setData(st, t);
                content.setItem(STOPS + ROUTES + c, st);
                c++;
            }
        }
        data.set(D_CARAVANS, c);
    }

    private static BlockPos mid(CaravanData.Stop[] pr) {
        return new BlockPos((pr[0].pos.getX() + pr[1].pos.getX()) / 2, 0, (pr[0].pos.getZ() + pr[1].pos.getZ()) / 2);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }
}
