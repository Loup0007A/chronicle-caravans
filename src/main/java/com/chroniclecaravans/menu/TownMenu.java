package com.chroniclecaravans.menu;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.Buildings;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import com.chroniclecaravans.world.Town;
import com.chroniclecaravans.world.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The town's ledger: level and prosperity, treasury, the works under way (what they still need, hand it over or
 * let the caravans bring it), the buildings and what they bring the town.
 */
public class TownMenu extends AbstractContainerMenu {
    public static final int MATERIALS = Buildings.Material.values().length, BUILDINGS = 14;
    public static final int D_LEVEL = 0, D_PROSPERITY = 1, D_NEXT = 2, D_TREASURY = 3, D_RESIDENTS = 4, D_FLAGS = 5, D_SITE = 6,
            D_GOT = 7, D_NEED = D_GOT + MATERIALS, D_COUNT = D_NEED + MATERIALS, D_BUILDINGS = D_COUNT + 1, D_STALLS = D_BUILDINGS + BUILDINGS,
            D_TIER = D_STALLS + 1, D_CAPACITY = D_TIER + 1, DATA = D_CAPACITY + 1;
    public static final int FLAG_OWNER = 1, FLAG_SUPPLY = 2, FLAG_WATCH = 4, FLAG_WAREHOUSE = 8, FLAG_INN = 16, FLAG_STABLE = 32;
    public static final int S_NAME = 0, S_FOUNDER = 1, DISPLAY = 2;
    public static final int B_DELIVER = 0, B_SUPPLY = 1, B_WITHDRAW = 2;

    private final SimpleContainer display = new SimpleContainer(DISPLAY);
    private final ContainerData data = new SimpleContainerData(DATA);
    private final BlockPos town;
    private final Player player;
    private int refresh;

    /** Client side. */
    public TownMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public TownMenu(int id, Inventory inv, BlockPos town) {
        super(ChronicleCaravans.TOWN_MENU.get(), id);
        this.town = town;
        this.player = inv.player;
        for (int i = 0; i < DISPLAY; i++) addSlot(new MenuSlots.Hidden(display, i));
        addDataSlots(data);
        if (town != null) update();
    }

    /** Opens the ledger of the town at {@code town} (the milestone). */
    public static void open(ServerPlayer sp, BlockPos town) {
        CaravanData data = CaravanData.get(sp.serverLevel());
        CaravanData.Stop stop = data.stop(town);
        Component title = stop == null ? Component.translatable("gui.chroniclecaravans.town") : stop.displayName();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TownMenu(id, inv, town), title));
    }

    /** The town's milestone (server side). */
    public BlockPos town() {
        return town;
    }

    // ---------------------------------------------------------------- client reading

    public int get(int i) {
        return data.get(i);
    }

    public ItemStack shown(int slot) {
        return getSlot(slot).getItem();
    }

    public Buildings.Type buildingType(int k) {
        return Buildings.Type.byIndex(data.get(D_BUILDINGS + k) & 15);
    }

    public int buildingState(int k) {
        return (data.get(D_BUILDINGS + k) >> 4) & 3;
    }

    public int buildingPercent(int k) {
        return (data.get(D_BUILDINGS + k) >> 6) & 127;
    }

    // ---------------------------------------------------------------- server

    private ServerLevel level() {
        return (ServerLevel) player.level();
    }

    private void update() {
        ServerLevel level = level();
        CaravanData cd = CaravanData.get(level);
        Town t = cd.town(town);
        CaravanData.Stop stop = cd.stop(town);
        if (t == null || stop == null) return;
        data.set(D_LEVEL, t.level);
        data.set(D_PROSPERITY, Math.min(32000, t.prosperity));
        data.set(D_NEXT, t.level >= Towns.MAX_LEVEL ? 0 : Math.min(32000, Towns.threshold(t.level + 1)));
        data.set(D_TREASURY, Math.min(32000, t.treasury));
        data.set(D_RESIDENTS, Math.min(32000, Towns.residents(level, t)));
        int flags = 0;
        if (owner(stop)) flags |= FLAG_OWNER;
        if (t.supply) flags |= FLAG_SUPPLY;
        if (t.count(Buildings.Type.WATCHTOWER, true) > 0) flags |= FLAG_WATCH;
        if (t.count(Buildings.Type.WAREHOUSE, true) > 0) flags |= FLAG_WAREHOUSE;
        if (t.count(Buildings.Type.INN, true) > 0) flags |= FLAG_INN;
        if (t.count(Buildings.Type.STABLE, true) > 0) flags |= FLAG_STABLE;
        data.set(D_FLAGS, flags);
        Town.Building site = t.site();
        data.set(D_SITE, site == null ? 0 : t.buildings.indexOf(site) + 1);
        for (Buildings.Material m : Buildings.Material.values()) {
            data.set(D_GOT + m.ordinal(), site == null ? 0 : Math.min(site.got[m.ordinal()], site.type.need(m)));
            data.set(D_NEED + m.ordinal(), site == null ? 0 : site.type.need(m));
        }
        int n = Math.min(BUILDINGS, t.buildings.size());
        data.set(D_COUNT, n);
        for (int k = 0; k < n; k++) {
            Town.Building b = t.buildings.get(k);
            int planned = b.planned() || b.state != Town.WAITING ? 0 : 1;
            data.set(D_BUILDINGS + k, b.type.ordinal() | (b.state << 4) | (Math.min(100, b.percent()) << 6) | (planned << 13));
        }
        data.set(D_STALLS, Towns.stalls(level, town));
        data.set(D_TIER, stop.tier);
        data.set(D_CAPACITY, CaravanSpawner.capacity(level, stop));
        ItemStack name = new ItemStack(ChronicleCaravans.MILESTONE_ITEM.get());
        Compat.setName(name, stop.displayName());
        setIfChanged(S_NAME, name);
        ItemStack founder = new ItemStack(Items.PAPER);
        Compat.setName(founder, Component.literal(stop.ownerName.isEmpty() ? "?" : stop.ownerName));
        setIfChanged(S_FOUNDER, founder);
    }

    public boolean noPlot(int k) {
        return ((data.get(D_BUILDINGS + k) >> 13) & 1) != 0;
    }

    private boolean owner(CaravanData.Stop stop) {
        return stop.owner == null || stop.owner.equals(player.getUUID()) || player.getAbilities().instabuild;
    }

    private void setIfChanged(int slot, ItemStack stack) {
        ItemStack old = display.getItem(slot);
        if (old.getCount() != stack.getCount() || !Compat.sameItem(old, stack)) display.setItem(slot, stack);
    }

    @Override
    public void broadcastChanges() {
        if (town != null && ++refresh >= 10) {
            refresh = 0;
            update();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (town == null || !(p instanceof ServerPlayer sp) || sp.isSpectator() || !CCConfig.TOWNS.get()) return false;
        ServerLevel level = level();
        CaravanData cd = CaravanData.get(level);
        Town t = cd.town(town);
        CaravanData.Stop stop = cd.stop(town);
        if (t == null || stop == null) return false;
        boolean ok = false;
        switch (id) {
            case B_DELIVER -> {
                Town.Building site = t.site();
                int moved = Towns.deliver(site, sp.getInventory());
                if (moved > 0) {
                    ok = true;
                    Component msg = Component.translatable("msg.chroniclecaravans.town_delivered", moved,
                            Component.translatable("building.chroniclecaravans." + site.type.key())).withStyle(ChatFormatting.GREEN);
                    sp.displayClientMessage(msg, true);
                }
            }
            case B_SUPPLY -> {
                if (owner(stop)) {
                    t.supply = !t.supply;
                    ok = true;
                }
            }
            case B_WITHDRAW -> {
                if (owner(stop) && t.treasury > 0) {
                    int left = t.treasury;
                    while (left > 0) {
                        ItemStack e = new ItemStack(Items.EMERALD, Math.min(64, left));
                        left -= e.getCount();
                        if (!sp.getInventory().add(e)) net.minecraft.world.level.block.Block.popResource(level, sp.blockPosition(), e);
                    }
                    t.treasury = 0;
                    ok = true;
                }
            }
            default -> {
                return false;
            }
        }
        if (ok) cd.setDirty();
        level.playSound(null, sp.blockPosition(), ok ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6F, 1.0F);
        update();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        if (town == null) return true;
        return p.isSpectator() || p.distanceToSqr(town.getX() + 0.5, town.getY(), town.getZ() + 0.5) < 48 * 48;
    }
}
