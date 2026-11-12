package com.chroniclecaravans.menu;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.block.DockBlockEntity;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.Logistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The loading dock: its nine slots, where it sends to, and how the last shipment went. */
public class DockMenu extends AbstractContainerMenu {
    public static final int D_STATUS = 0, D_AGE = 1, D_FEE = 2, D_HOPS = 3, D_OUT = 4, D_SHIPMENTS = 5, D_HAS_STOP = 6, DATA = 7;
    public static final int WIDTH = 192, HEIGHT = 218, GRID_X = 70, GRID_Y = 56, INV_Y = 130;

    private final Container dock;
    private final DockBlockEntity be;
    /** Hidden slots: 0 = the address (a name tag), 1 = the dock's stop, 2 = the destination stop. */
    private final SimpleContainer info = new SimpleContainer(3);
    private final ContainerData data = new SimpleContainerData(DATA);
    private int refresh;

    public DockMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public DockMenu(int id, Inventory inv, DockBlockEntity be) {
        super(ChronicleCaravans.DOCK_MENU.get(), id);
        this.be = be;
        this.dock = be != null ? be : new SimpleContainer(DockBlockEntity.SIZE);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) addSlot(new Slot(dock, c + r * 3, GRID_X + c * 18, GRID_Y + r * 18));
        }
        for (int i = 0; i < 3; i++) addSlot(new MenuSlots.Hidden(info, i));
        MenuSlots.player(inv, 16, INV_Y, this::addSlot);
        addDataSlots(data);
        if (be != null) update();
    }

    public DockBlockEntity dock() {
        return be;
    }

    public int get(int i) {
        return data.get(i);
    }

    /** The address ("" for a receiving dock). */
    public String address() {
        ItemStack tag = slots.get(9).getItem();
        return tag.isEmpty() ? "" : tag.getHoverName().getString();
    }

    public ItemStack stopShown() {
        return slots.get(10).getItem();
    }

    public ItemStack destinationShown() {
        return slots.get(11).getItem();
    }

    private void update() {
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        CaravanData cd = CaravanData.get(level);
        ItemStack tag = ItemStack.EMPTY;
        if (be.outgoing()) {
            tag = new ItemStack(Items.NAME_TAG);
            Compat.setName(tag, Component.literal(be.address()));
        }
        info.setItem(0, tag);
        CaravanData.Stop here = Logistics.stopOf(level, be.getBlockPos());
        info.setItem(1, here == null ? ItemStack.EMPTY : named(here));
        CaravanData.Stop dest = be.outgoing() ? Logistics.byAddress(cd, be.address()) : null;
        info.setItem(2, dest == null ? ItemStack.EMPTY : named(dest));
        int hops = here == null || dest == null ? -1 : Logistics.hops(cd, here.pos, dest.pos);
        data.set(D_STATUS, be.status());
        data.set(D_AGE, (int) Math.min(30000, Math.max(0, (level.getGameTime() - be.statusTime()) / 1200)));
        data.set(D_FEE, hops < 0 ? 0 : Logistics.fee(hops));
        data.set(D_HOPS, hops);
        data.set(D_OUT, be.outgoing() ? 1 : 0);
        data.set(D_SHIPMENTS, Math.min(30000, be.shipments()));
        data.set(D_HAS_STOP, here == null ? 0 : 1);
    }

    private static ItemStack named(CaravanData.Stop s) {
        ItemStack st = new ItemStack(s.village ? Items.BELL : ChronicleCaravans.MILESTONE_ITEM.get());
        Compat.setName(st, s.displayName());
        return st;
    }

    @Override
    public void broadcastChanges() {
        if (be != null && ++refresh >= 10) {
            refresh = 0;
            update();
        }
        super.broadcastChanges();
    }

    /** After the address changed (the command), refresh at once. */
    public void refreshNow() {
        if (be != null) update();
    }

    public BlockPos pos() {
        return be == null ? BlockPos.ZERO : be.getBlockPos();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index >= 9 && index < 12) return ItemStack.EMPTY;
        // Slots: 0-8 dock, 9-11 hidden, 12-47 player
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 9) {
            if (!moveItemStackTo(stack, 12, 48, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 9, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return dock.stillValid(p);
    }
}
