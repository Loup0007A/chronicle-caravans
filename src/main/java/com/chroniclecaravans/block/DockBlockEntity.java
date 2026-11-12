package com.chroniclecaravans.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.menu.DockMenu;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//#if mc121
import net.minecraft.core.HolderLookup;
//#endif
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A loading dock: nine slots next to a milestone. Named (its address), it sends its goods to the stop of that
 * name with the next caravan (the freight is paid with the emeralds it holds); unnamed, it receives what
 * caravans deliver here. Hoppers can fill a sending dock and empty a receiving one.
 */
public class DockBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SIZE = 9;
    public static final int IDLE = 0, SHIPPED = 1, NO_ADDRESS = 2, NO_ROUTE = 3, NO_FEE = 4;
    private static final int[] SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private String address = "";
    private UUID owner;
    private String ownerName = "";
    private int status;
    private long statusTime;
    private int shipments;

    public DockBlockEntity(BlockPos pos, BlockState state) {
        super(ChronicleCaravans.DOCK_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- dock

    public String address() {
        return address;
    }

    public boolean outgoing() {
        return !address.isBlank();
    }

    public void setAddress(String address) {
        this.address = address == null ? "" : address.strip();
        if (this.address.length() > 32) this.address = this.address.substring(0, 32);
        status = IDLE;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState s = level.getBlockState(worldPosition);
            if (s.hasProperty(DockBlock.OUTGOING) && s.getValue(DockBlock.OUTGOING) != outgoing()) {
                level.setBlock(worldPosition, s.setValue(DockBlock.OUTGOING, outgoing()), 3);
            }
        }
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public int status() {
        return status;
    }

    public long statusTime() {
        return statusTime;
    }

    public int shipments() {
        return shipments;
    }

    public void setStatus(int status) {
        this.status = status;
        this.statusTime = level == null ? 0 : level.getGameTime();
        if (status == SHIPPED) shipments++;
        setChanged();
    }

    /** Emeralds in the dock (they pay the freight). */
    public int emeralds() {
        int n = 0;
        for (ItemStack s : items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    /** Stacks to ship (everything but the emeralds). */
    public int goodsCount() {
        int n = 0;
        for (ItemStack s : items) if (!s.isEmpty() && !s.is(Items.EMERALD)) n++;
        return n;
    }

    public void takeEmeralds(int n) {
        for (int i = 0; i < SIZE && n > 0; i++) {
            ItemStack s = items.get(i);
            if (s.is(Items.EMERALD)) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
        setChanged();
    }

    public List<ItemStack> takeGoods() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && !s.is(Items.EMERALD)) {
                out.add(s.copy());
                items.set(i, ItemStack.EMPTY);
            }
        }
        setChanged();
        return out;
    }

    // ---------------------------------------------------------------- container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack s = ContainerHelper.removeItem(items, slot, amount);
        if (!s.isEmpty()) setChanged();
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /** Hoppers fill a sending dock and empty a receiving one, never the other way round. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return outgoing();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !outgoing();
    }

    //#if forge
    //$$ /** Pipes and funnels of other mods see the docks like hoppers do (same faces, same rules). */
    //$$ private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandlerModifiable>[] handlers =
    //$$         net.minecraftforge.items.wrapper.SidedInvWrapper.create(this, Direction.values());
    //$$
    //$$ @Override
    //$$ public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, Direction side) {
    //$$     if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && !remove) return handlers[(side == null ? Direction.UP : side).ordinal()].cast();
    //$$     return super.getCapability(cap, side);
    //$$ }
    //$$
    //$$ @Override
    //$$ public void invalidateCaps() {
    //$$     super.invalidateCaps();
    //$$     for (net.minecraftforge.common.util.LazyOptional<?> h : handlers) h.invalidate();
    //$$ }
    //#endif

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chroniclecaravans.loading_dock");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DockMenu(id, inv, this);
    }

    // ---------------------------------------------------------------- storage

    private void writeDock(CompoundTag tag) {
        tag.putString("Address", address);
        if (owner != null) Nbt.putUUID(tag, "Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Status", status);
        tag.putLong("StatusTime", statusTime);
        tag.putInt("Shipments", shipments);
    }

    private void readDock(CompoundTag tag) {
        address = Nbt.getString(tag, "Address");
        owner = Nbt.hasUUID(tag, "Owner") ? Nbt.getUUID(tag, "Owner") : null;
        ownerName = Nbt.getString(tag, "OwnerName");
        status = Nbt.getInt(tag, "Status");
        statusTime = Nbt.getLong(tag, "StatusTime");
        shipments = Nbt.getInt(tag, "Shipments");
    }

    //#if mc26
    //$$ @Override
    //$$ protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     super.saveAdditional(out);
    //$$     ContainerHelper.saveAllItems(out, items);
    //$$     CompoundTag tag = new CompoundTag();
    //$$     writeDock(tag);
    //$$     out.store("Dock", CompoundTag.CODEC, tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
    //$$     super.loadAdditional(in);
    //$$     items.clear();
    //$$     ContainerHelper.loadAllItems(in, items);
    //$$     in.read("Dock", CompoundTag.CODEC).ifPresent(this::readDock);
    //$$ }
    //$$
    //$$ @Override
    //$$ public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    //$$     if (level != null) net.minecraft.world.Containers.dropContents(level, pos, this);
    //$$     super.preRemoveSideEffects(pos, state);
    //$$ }
    //#elif mc121
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        writeDock(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        readDock(tag);
    }
    //#else
    //$$ @Override
    //$$ protected void saveAdditional(CompoundTag tag) {
    //$$     super.saveAdditional(tag);
    //$$     ContainerHelper.saveAllItems(tag, items);
    //$$     writeDock(tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ public void load(CompoundTag tag) {
    //$$     super.load(tag);
    //$$     items.clear();
    //$$     ContainerHelper.loadAllItems(tag, items);
    //$$     readDock(tag);
    //$$ }
    //#endif
}
