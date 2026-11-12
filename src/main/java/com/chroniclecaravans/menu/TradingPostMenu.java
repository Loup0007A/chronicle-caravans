package com.chroniclecaravans.menu;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.block.TradingPostBlockEntity;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.platform.Compat;
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
import net.minecraft.world.phys.AABB;

/** The trading post: standing orders, input, output, and what it is doing. */
public class TradingPostMenu extends AbstractContainerMenu {
    public static final int D_STATUS = 0, D_DEALS = 1, D_TOTAL = 2, D_SIGNAL = 3, DATA = 4;
    public static final int ROW_X = 8, ORDERS_Y = 32, INPUT_Y = 66, OUTPUT_Y = 100, INV_Y = 140;

    private final Container post;
    private final TradingPostBlockEntity be;
    /** Hidden slot: the caravan in reach (a chest named after it). */
    private final SimpleContainer info = new SimpleContainer(1);
    private final ContainerData data = new SimpleContainerData(DATA);
    private int refresh;

    public TradingPostMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public TradingPostMenu(int id, Inventory inv, TradingPostBlockEntity be) {
        super(ChronicleCaravans.POST_MENU.get(), id);
        this.be = be;
        this.post = be != null ? be : new SimpleContainer(TradingPostBlockEntity.SIZE);
        for (int c = 0; c < 9; c++) addSlot(new Slot(post, TradingPostBlockEntity.ORDERS + c, ROW_X + c * 18, ORDERS_Y));
        for (int c = 0; c < 9; c++) addSlot(new Slot(post, TradingPostBlockEntity.INPUT + c, ROW_X + c * 18, INPUT_Y));
        for (int c = 0; c < 9; c++) addSlot(new MenuSlots.Output(post, TradingPostBlockEntity.OUTPUT + c, ROW_X + c * 18, OUTPUT_Y));
        addSlot(new MenuSlots.Hidden(info, 0));
        MenuSlots.player(inv, 8, INV_Y, this::addSlot);
        addDataSlots(data);
        if (be != null) update();
    }

    public int get(int i) {
        return data.get(i);
    }

    /** The caravan in reach, as an item carrying its name (empty if none). */
    public ItemStack caravanShown() {
        return slots.get(27).getItem();
    }

    private void update() {
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        data.set(D_STATUS, be.status());
        data.set(D_DEALS, Math.min(30000, be.deals()));
        data.set(D_TOTAL, Math.min(30000, be.totalDeals()));
        data.set(D_SIGNAL, be.signal());
        ItemStack shown = ItemStack.EMPTY;
        for (CaravanMaster m : level.getEntitiesOfClass(CaravanMaster.class, new AABB(be.getBlockPos()).inflate(TradingPostBlockEntity.REACH),
                m -> m.isAlive() && m.stationary())) {
            shown = new ItemStack(Items.CHEST);
            Compat.setName(shown, m.getDisplayName());
            break;
        }
        ItemStack old = info.getItem(0);
        if (old.isEmpty() != shown.isEmpty() || !old.getHoverName().getString().equals(shown.getHoverName().getString())) info.setItem(0, shown);
    }

    @Override
    public void broadcastChanges() {
        if (be != null && ++refresh >= 10) {
            refresh = 0;
            update();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        // Slots: 0-8 orders, 9-17 input, 18-26 output, 27 hidden, 28-63 player
        if (index == 27) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 28, 64, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 9, 18, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return post.stillValid(p);
    }
}
