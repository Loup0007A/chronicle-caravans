package com.chroniclecaravans.block;

import java.util.List;
import java.util.UUID;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.menu.TradingPostMenu;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.Logistics;
import com.chroniclecaravans.world.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//#if mc121
import net.minecraft.core.HolderLookup;
//#endif
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A trading post: trades for its owner with every caravan that halts nearby. Three rows: standing orders
 * (samples of what to buy: the stack size is how many deals per caravan), the input (goods to sell, emeralds to
 * pay) and the output (what it earned). Hoppers fill the input from above or the sides, and empty the output
 * from below; a redstone signal pauses it.
 */
public class TradingPostBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int ROW = 9, SIZE = 27, ORDERS = 0, INPUT = 9, OUTPUT = 18;
    public static final int IDLE = 0, TRADING = 1, PAUSED = 2, FULL = 3, OFF = 4, WAITING = 5;
    /** How close a caravan must halt. */
    public static final int REACH = 12;
    private static final int[] INPUT_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] OUTPUT_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private UUID owner;
    private String ownerName = "";
    private int status;
    private UUID visit;
    private final int[] bought = new int[ROW];
    private int deals;
    private int totalDeals;
    private int cooldown;

    public TradingPostBlockEntity(BlockPos pos, BlockState state) {
        super(ChronicleCaravans.POST_BE.get(), pos, state);
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public UUID owner() {
        return owner;
    }

    public int status() {
        return status;
    }

    public int deals() {
        return deals;
    }

    public int totalDeals() {
        return totalDeals;
    }

    // ---------------------------------------------------------------- trading

    /** A view on one row, for the trading rules (which read a container). */
    private Container row(int from) {
        TradingPostBlockEntity be = this;
        return new net.minecraft.world.SimpleContainer(ROW) {
            @Override
            public ItemStack getItem(int i) {
                return be.items.get(from + i);
            }

            @Override
            public void setItem(int i, ItemStack s) {
                be.items.set(from + i, s);
            }

            @Override
            public ItemStack removeItem(int i, int n) {
                return ContainerHelper.removeItem(be.items, from + i, n);
            }

            @Override
            public void setChanged() {
                be.setChanged();
            }
        };
    }

    private boolean fits(ItemStack stack) {
        int room = 0;
        for (int i = OUTPUT; i < SIZE; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) room += stack.getMaxStackSize();
            else if (Compat.sameItem(s, stack)) room += s.getMaxStackSize() - s.getCount();
        }
        return room >= stack.getCount();
    }

    private static boolean matches(ItemStack sample, ItemStack result) {
        if (sample.is(Items.ENCHANTED_BOOK)) return Compat.sameItem(sample.copyWithCount(1), result.copyWithCount(1));
        return result.is(sample.getItem());
    }

    /** The caravan halted within reach, if any. */
    private CaravanMaster caravanInReach(ServerLevel level) {
        List<CaravanMaster> list = level.getEntitiesOfClass(CaravanMaster.class, new AABB(worldPosition).inflate(REACH),
                m -> m.isAlive() && m.stationary());
        return list.isEmpty() ? null : list.get(0);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TradingPostBlockEntity be) {
        if (level instanceof ServerLevel server && ++be.cooldown >= 20) {
            be.cooldown = 0;
            be.tick(server);
        }
    }

    private void tick(ServerLevel level) {
        int before = status;
        if (!CCConfig.TRADING_POSTS.get()) status = OFF;
        else if (level.hasNeighborSignal(worldPosition)) status = PAUSED;
        else {
            CaravanMaster m = caravanInReach(level);
            if (m == null) {
                status = IDLE;
            } else {
                if (!m.getUUID().equals(visit)) {
                    visit = m.getUUID();
                    deals = 0;
                    java.util.Arrays.fill(bought, 0);
                }
                status = WAITING;
                if (trade(level, m)) status = TRADING;
            }
        }
        if (status != before) setChanged();
    }

    /** One deal per second: a request it can fill first, then a standing order. Returns true if a deal was done. */
    private boolean trade(ServerLevel level, CaravanMaster m) {
        CaravanData data = CaravanData.get(level);
        int skills = owner == null ? 0 : data.skills(owner);
        int lvl = owner == null ? 0 : data.level(owner);
        long now = level.getGameTime();
        List<MerchantOffer> offers = m.stock();
        Container in = row(INPUT);
        boolean full = false;
        // Sell what the caravan asks for
        for (MerchantOffer o : offers) {
            if (Region.kindOf(o) != Region.KIND_SELL || o.isOutOfStock() || !Trading.unlocked(o, lvl, skills)) continue;
            Trading.Quote q = Trading.quote(o, skills, Market.factor(data, m.region(), o, now));
            if (Trading.affordable(o, q, in) <= 0) continue;
            if (!fits(q.result())) {
                full = true;
                continue;
            }
            if (deal(level, data, o, q, in)) return true;
        }
        // Buy what the orders ask for
        for (int j = 0; j < ROW; j++) {
            ItemStack sample = items.get(ORDERS + j);
            if (sample.isEmpty() || bought[j] >= sample.getCount()) continue;
            for (MerchantOffer o : offers) {
                if (Region.kindOf(o) == Region.KIND_SELL || o.isOutOfStock() || !Trading.unlocked(o, lvl, skills)) continue;
                if (!matches(sample, o.getResult())) continue;
                Trading.Quote q = Trading.quote(o, skills, Market.factor(data, m.region(), o, now));
                if (Trading.affordable(o, q, in) <= 0) continue;
                if (!fits(q.result())) {
                    full = true;
                    continue;
                }
                if (deal(level, data, o, q, in)) {
                    bought[j]++;
                    return true;
                }
            }
        }
        if (full) status = FULL;
        return false;
    }

    private boolean deal(ServerLevel level, CaravanData data, MerchantOffer o, Trading.Quote q, Container in) {
        Container out = row(OUTPUT);
        int done = Trading.execute(o, q, in, stack -> {
            ItemStack rest = Logistics.insert(out, stack);
            if (!rest.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, worldPosition.above(), rest);
        }, 1);
        if (done <= 0) return false;
        Market.traded(data, o, done, level.getGameTime());
        if (owner != null) data.traded(level.getServer(), owner, Trading.value(q));
        deals++;
        totalDeals++;
        level.playSound(null, worldPosition, SoundEvents.VILLAGER_TRADE, SoundSource.BLOCKS, 0.5F, 1.0F);
        setChanged();
        return true;
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
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN && slot >= INPUT && slot < OUTPUT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot >= OUTPUT;
    }

    /** The comparator reads how full the output row is. */
    public int signal() {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(row(OUTPUT));
    }

    //#if forge
    //$$ /** Pipes and funnels of other mods see the trading posts like hoppers do (same faces, same rules). */
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
        return Component.translatable("block.chroniclecaravans.trading_post");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TradingPostMenu(id, inv, this);
    }

    // ---------------------------------------------------------------- storage

    private void writePost(CompoundTag tag) {
        if (owner != null) Nbt.putUUID(tag, "Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("TotalDeals", totalDeals);
    }

    private void readPost(CompoundTag tag) {
        owner = Nbt.hasUUID(tag, "Owner") ? Nbt.getUUID(tag, "Owner") : null;
        ownerName = Nbt.getString(tag, "OwnerName");
        totalDeals = Nbt.getInt(tag, "TotalDeals");
    }

    //#if mc26
    //$$ @Override
    //$$ protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     super.saveAdditional(out);
    //$$     ContainerHelper.saveAllItems(out, items);
    //$$     CompoundTag tag = new CompoundTag();
    //$$     writePost(tag);
    //$$     out.store("Post", CompoundTag.CODEC, tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
    //$$     super.loadAdditional(in);
    //$$     items.clear();
    //$$     ContainerHelper.loadAllItems(in, items);
    //$$     in.read("Post", CompoundTag.CODEC).ifPresent(this::readPost);
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
        writePost(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        readPost(tag);
    }
    //#else
    //$$ @Override
    //$$ protected void saveAdditional(CompoundTag tag) {
    //$$     super.saveAdditional(tag);
    //$$     ContainerHelper.saveAllItems(tag, items);
    //$$     writePost(tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ public void load(CompoundTag tag) {
    //$$     super.load(tag);
    //$$     items.clear();
    //$$     ContainerHelper.loadAllItems(tag, items);
    //$$     readPost(tag);
    //$$ }
    //#endif
}
