package com.chroniclecaravans.menu;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.Logistics;
import com.chroniclecaravans.world.Parcel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Packing a parcel: the player fills the crate, and closing it hands the parcel to the caravans (the freight is
 * paid then, with emeralds from the player's inventory; without them, everything comes back).
 */
public class ParcelMenu extends AbstractContainerMenu {
    public static final int D_FEE = 0, D_HOPS = 1, DATA = 2;
    public static final int GRID_X = 62, GRID_Y = 40, INV_Y = 128;

    private final SimpleContainer crate = new SimpleContainer(9);
    /** Hidden slots: 0 = where it goes, 1 = where it leaves from. */
    private final SimpleContainer info = new SimpleContainer(2);
    private final ContainerData data = new SimpleContainerData(DATA);
    private final BlockPos origin, dest;
    private boolean sealed;

    public ParcelMenu(int id, Inventory inv) {
        this(id, inv, null, null);
    }

    public ParcelMenu(int id, Inventory inv, BlockPos origin, BlockPos dest) {
        super(ChronicleCaravans.PARCEL_MENU.get(), id);
        this.origin = origin;
        this.dest = dest;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) addSlot(new Slot(crate, c + r * 3, GRID_X + c * 18, GRID_Y + r * 18) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    // No parcel in a parcel
                    return !Logistics.isParcel(s);
                }
            });
        }
        addSlot(new MenuSlots.Hidden(info, 0));
        addSlot(new MenuSlots.Hidden(info, 1));
        MenuSlots.player(inv, 8, INV_Y, this::addSlot);
        addDataSlots(data);
        if (origin != null && inv.player.level() instanceof ServerLevel level) {
            CaravanData cd = CaravanData.get(level);
            CaravanData.Stop to = cd.stop(dest), from = cd.stop(origin);
            int hops = Logistics.hops(cd, origin, dest);
            data.set(D_HOPS, hops);
            data.set(D_FEE, hops < 0 ? 0 : Logistics.fee(hops));
            if (to != null) info.setItem(0, named(to));
            if (from != null) info.setItem(1, named(from));
        }
    }

    private static ItemStack named(CaravanData.Stop s) {
        ItemStack st = new ItemStack(s.village ? Items.BELL : ChronicleCaravans.MILESTONE_ITEM.get());
        Compat.setName(st, s.displayName());
        return st;
    }

    public int get(int i) {
        return data.get(i);
    }

    public ItemStack destinationShown() {
        return slots.get(9).getItem();
    }

    public ItemStack originShown() {
        return slots.get(10).getItem();
    }

    /** Closing the crate: the parcel leaves (if paid for), or everything goes back to the player. */
    @Override
    public void removed(Player p) {
        super.removed(p);
        if (sealed || origin == null || !(p instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        sealed = true;
        List<ItemStack> goods = new ArrayList<>();
        for (int i = 0; i < crate.getContainerSize(); i++) {
            ItemStack s = crate.removeItemNoUpdate(i);
            if (!s.isEmpty()) goods.add(s);
        }
        if (goods.isEmpty()) return;
        CaravanData cd = CaravanData.get(level);
        int hops = cd.stop(origin) == null || cd.stop(dest) == null ? -1 : Logistics.hops(cd, origin, dest);
        int fee = hops < 0 ? -1 : Logistics.fee(hops);
        if (fee < 0 || (!sp.isCreative() && !Trading.pay(sp.getInventory(), fee))) {
            for (ItemStack s : goods) if (!sp.getInventory().add(s)) net.minecraft.world.level.block.Block.popResource(level, sp.blockPosition(), s);
            sp.sendSystemMessage(Component.translatable(fee < 0 ? "msg.chroniclecaravans.parcel_no_route" : "msg.chroniclecaravans.parcel_no_fee", fee)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Parcel parcel = Parcel.of(level, goods, origin, dest, sp.getUUID(), sp.getName().getString(), false);
        cd.depot(origin).add(parcel);
        cd.setDirty();
        CaravanData.Stop to = cd.stop(dest);
        level.playSound(null, sp.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
        sp.sendSystemMessage(Component.translatable("msg.chroniclecaravans.parcel_sent", goods.size(), to == null ? Component.literal("?") : to.displayName(), fee, hops)
                .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        // Slots: 0-8 crate, 9-10 hidden, 11-46 player
        if (index == 9 || index == 10) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 9) {
            if (!moveItemStackTo(stack, 11, 47, true)) return ItemStack.EMPTY;
        } else {
            if (Logistics.isParcel(stack) || !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }
}
