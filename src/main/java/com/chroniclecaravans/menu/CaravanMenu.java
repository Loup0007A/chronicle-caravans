package com.chroniclecaravans.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.RoadBuilder;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.Logistics;
import com.chroniclecaravans.world.Market;
import com.chroniclecaravans.world.Parcel;
import com.chroniclecaravans.world.RoadWorks;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The caravan's interface: market, requests, freight, services (escort, road crews) and the guild's skill tree.
 * Everything the screen shows travels in display slots and data slots (no custom packets), and every action
 * is a menu button click: it works the same on every loader.
 */
public class CaravanMenu extends AbstractContainerMenu {
    public static final int OFFERS = 20, DESTS = 4, FREIGHT = 5, CARGO = 4;
    // Display slots: three per offer (cost A, cost B, result), then the rest
    public static final int S_ROAD = OFFERS * 3, S_STOP = S_ROAD + DESTS, S_FREIGHT = S_STOP + 1, S_CARGO = S_FREIGHT + FREIGHT,
            S_SHORTAGE = S_CARGO + CARGO, S_SURPLUS = S_SHORTAGE + 1, DISPLAY = S_SURPLUS + 1;

    // Data slots (values are sent as shorts: everything stays within ±32767)
    public static final int D_MARKET = OFFERS, D_OFFER_COUNT = OFFERS * 2, D_PROGRESS = D_OFFER_COUNT + 1, D_NEEDED = D_OFFER_COUNT + 2,
            D_LEVEL = D_OFFER_COUNT + 3, D_SKILLS = D_OFFER_COUNT + 4, D_POINTS = D_OFFER_COUNT + 5, D_REGION = D_OFFER_COUNT + 6,
            D_STATE = D_OFFER_COUNT + 7, D_HALT = D_OFFER_COUNT + 8, D_GUARDS = D_OFFER_COUNT + 9, D_ESCORT_LEFT = D_OFFER_COUNT + 10,
            D_ESCORT_PRICE = D_OFFER_COUNT + 11, D_ESCORT_COUNT = D_OFFER_COUNT + 12, D_ESCORT_MINUTES = D_OFFER_COUNT + 13,
            D_ROAD_PRICE = D_OFFER_COUNT + 14, D_ROAD_DIST = D_ROAD_PRICE + DESTS, D_XP = D_ROAD_DIST + DESTS, D_FLAGS = D_XP + 1,
            D_CAMP = D_FLAGS + 1, D_FREIGHT_FEE = D_CAMP + 1, D_FREIGHT_HOPS = D_FREIGHT_FEE + FREIGHT, D_FREIGHT_DIST = D_FREIGHT_HOPS + FREIGHT,
            D_CARGO = D_FREIGHT_DIST + FREIGHT, D_CAPACITY = D_CARGO + 1, D_WAITING = D_CARGO + 2, D_ENTITY_LO = D_CARGO + 3,
            D_ENTITY_HI = D_CARGO + 4, DATA = D_CARGO + 5;
    public static final int FLAG_AT_STOP = 1, FLAG_REPUTATION = 2, FLAG_TO_INN = 4, FLAG_LINKED = 8, FLAG_MARKET = 16, FLAG_FREIGHT = 32;

    // Buttons (ids stay under 128: 1.20.1 sends them as a byte)
    public static final int B_BUY = 0, B_BUY_ALL = 20, B_ESCORT = 60, B_ROAD = 70, B_SKILL = 80, B_PARCEL = 100;

    private final SimpleContainer display = new SimpleContainer(DISPLAY);
    private final ContainerData data;
    private final CaravanMaster master;
    private final Player player;
    private final BlockPos[] dests = new BlockPos[DESTS];
    private final BlockPos[] freight = new BlockPos[FREIGHT];
    private int refresh;
    private int freightRefresh;
    private Map<BlockPos, Integer> reachable = Map.of();

    /** Client side. */
    public CaravanMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public CaravanMenu(int id, Inventory inv, CaravanMaster master) {
        super(ChronicleCaravans.CARAVAN_MENU.get(), id);
        this.master = master;
        this.player = inv.player;
        this.data = new SimpleContainerData(DATA);
        for (int i = 0; i < DISPLAY; i++) addSlot(new MenuSlots.Hidden(display, i));
        // The player's inventory, never drawn: it keeps the client's copy up to date (prices, "you have")
        for (int i = 0; i < Trading.PLAYER_SLOTS; i++) addSlot(new MenuSlots.Hidden(inv, i));
        addDataSlots(data);
        if (master != null) {
            master.addCustomer(player);
            update();
        }
    }

    // ---------------------------------------------------------------- client reading

    public int get(int index) {
        return data.get(index);
    }

    public ItemStack shown(int slot) {
        return getSlot(slot).getItem();
    }

    public int offerCount() {
        return Math.min(OFFERS, data.get(D_OFFER_COUNT));
    }

    public ItemStack costA(int i) {
        return shown(i * 3);
    }

    public ItemStack costB(int i) {
        return shown(i * 3 + 1);
    }

    public ItemStack result(int i) {
        return shown(i * 3 + 2);
    }

    public int stockLeft(int i) {
        return data.get(i) & 0x7F;
    }

    /** Size of the stock: 1 = rare, 2 = some, 3 = plenty. */
    public int stockClass(int i) {
        return Math.max(1, (data.get(i) >> 13) & 0x3);
    }

    public int kind(int i) {
        return (data.get(i) >> 7) & 3;
    }

    /** 0: unlocked; 1 to 14: the level needed; 15: needs the "Hidden Stock" skill. */
    public int lock(int i) {
        return (data.get(i) >> 9) & 15;
    }

    /** The market's effect on this offer, in percent, as the player feels it (positive = good for the player). */
    public int market(int i) {
        return (short) data.get(D_MARKET + i);
    }

    public ItemStack destination(int k) {
        return shown(S_ROAD + k);
    }

    public ItemStack currentStop() {
        return shown(S_STOP);
    }

    public ItemStack freightDestination(int k) {
        return shown(S_FREIGHT + k);
    }

    public ItemStack cargo(int k) {
        return shown(S_CARGO + k);
    }

    public ItemStack shortage() {
        return shown(S_SHORTAGE);
    }

    public ItemStack surplus() {
        return shown(S_SURPLUS);
    }

    public int entityId() {
        return (data.get(D_ENTITY_LO) & 0x7FFF) | ((data.get(D_ENTITY_HI) & 0xFFFF) << 15);
    }

    // ---------------------------------------------------------------- server

    private ServerLevel level() {
        return (ServerLevel) master.level();
    }

    public static int escortPrice(int skills) {
        return Guild.has(skills, Guild.Skill.WARDEN) ? 10 : 14;
    }

    public static int escortGuards(int skills) {
        return Guild.has(skills, Guild.Skill.VETERAN_GUARDS) ? 3 : 2;
    }

    public static int escortMinutes(int skills) {
        return Guild.has(skills, Guild.Skill.WARDEN) ? 20 : 10;
    }

    public static int roadPrice(double distance, int skills) {
        int p = Math.max(6, (int) Math.round(distance / 8.0));
        return Guild.has(skills, Guild.Skill.FOREMAN) ? Math.max(4, Math.round(p * 0.75F)) : p;
    }

    /** Recomputes everything the screen shows for this player. */
    private void update() {
        ServerLevel level = level();
        CaravanData cd = CaravanData.get(level);
        long now = level.getGameTime();
        int xp = cd.xp(player.getUUID());
        int skills = cd.skills(player.getUUID());
        int lvl = cd.level(player.getUUID());
        List<MerchantOffer> offers = master.stock();
        int n = Math.min(OFFERS, offers.size());
        for (int i = 0; i < OFFERS; i++) {
            if (i >= n) {
                for (int k = 0; k < 3; k++) display.setItem(i * 3 + k, ItemStack.EMPTY);
                data.set(i, 0);
                data.set(D_MARKET + i, 0);
                continue;
            }
            MerchantOffer o = offers.get(i);
            int kind = Region.kindOf(o);
            Trading.Quote q = Trading.quote(o, skills, Market.factor(cd, master.region(), o, now));
            setIfChanged(i * 3, q.a());
            setIfChanged(i * 3 + 1, q.b());
            setIfChanged(i * 3 + 2, q.result());
            int lock = Trading.unlocked(o, lvl, skills) ? 0 : kind == Region.KIND_TREASURE ? 15 : Math.min(14, Region.levelOf(o));
            int left = Math.max(0, Math.min(127, o.getMaxUses() - o.getUses()));
            int size = o.getMaxUses() <= 2 ? 1 : o.getMaxUses() <= 5 ? 2 : 3;
            data.set(i, left | kind << 7 | lock << 9 | size << 13);
            data.set(D_MARKET + i, Math.max(-999, Math.min(999, q.percent(kind))));
        }
        data.set(D_OFFER_COUNT, n);
        data.set(D_PROGRESS, Guild.progress(xp));
        data.set(D_NEEDED, Guild.needed(Guild.levelOf(xp)));
        data.set(D_LEVEL, Guild.levelOf(xp));
        data.set(D_SKILLS, skills);
        data.set(D_POINTS, Guild.points(xp, skills));
        data.set(D_XP, Math.max(-30000, Math.min(30000, xp)));
        data.set(D_REGION, master.region().ordinal());
        data.set(D_STATE, master.state().ordinal());
        data.set(D_CAMP, master.campReason());
        data.set(D_HALT, Math.max(0, master.haltTicks() / 20));
        data.set(D_GUARDS, master.guards(level).size());
        data.set(D_ESCORT_LEFT, Math.min(30000, Math.max(0, master.escortTicks() / 20)));
        data.set(D_ESCORT_PRICE, escortPrice(skills));
        data.set(D_ESCORT_COUNT, escortGuards(skills));
        data.set(D_ESCORT_MINUTES, escortMinutes(skills));
        data.set(D_ENTITY_LO, master.getId() & 0x7FFF);
        data.set(D_ENTITY_HI, master.getId() >> 15);
        BlockPos stop = master.currentStop();
        CaravanData.Stop here = stop == null ? null : cd.stop(stop);
        List<CaravanData.Stop> linked = here == null ? List.of() : cd.linked(here.pos);
        int flags = (stop != null ? FLAG_AT_STOP : 0) | (CCConfig.REPUTATION_ENABLED.get() ? FLAG_REPUTATION : 0)
                | (master.headingToInn() ? FLAG_TO_INN : 0) | (!linked.isEmpty() ? FLAG_LINKED : 0)
                | (CCConfig.MARKET_PRICES.get() ? FLAG_MARKET : 0) | (CCConfig.FREIGHT.get() ? FLAG_FREIGHT : 0);
        data.set(D_FLAGS, flags);
        setIfChanged(S_STOP, here == null ? ItemStack.EMPTY : named(here));
        // Market news
        boolean market = CCConfig.MARKET_PRICES.get();
        setIfChanged(S_SHORTAGE, market ? new ItemStack(Market.shortage(master.region(), now)) : ItemStack.EMPTY);
        setIfChanged(S_SURPLUS, market ? new ItemStack(Market.surplus(master.region(), now)) : ItemStack.EMPTY);
        // Road crews
        int k = 0;
        for (CaravanData.Stop s : linked) {
            if (k >= DESTS) break;
            CaravanData.Route r = cd.route(here.pos, s.pos);
            if (r != null && r.built) continue;
            dests[k] = s.pos;
            setIfChanged(S_ROAD + k, named(s));
            double dist = RoadWorks.length(here.pos, s.pos);
            data.set(D_ROAD_PRICE + k, roadPrice(dist, skills));
            data.set(D_ROAD_DIST + k, (int) Math.round(dist));
            k++;
        }
        for (; k < DESTS; k++) {
            dests[k] = null;
            display.setItem(S_ROAD + k, ItemStack.EMPTY);
            data.set(D_ROAD_PRICE + k, 0);
            data.set(D_ROAD_DIST + k, 0);
        }
        // Freight: the closest stops parcels can be sent to from here, then the cargo on board
        k = 0;
        if (here != null && CCConfig.FREIGHT.get()) {
            if (freightRefresh++ % 4 == 0) reachable = Logistics.reachable(cd, here.pos);
            List<Map.Entry<BlockPos, Integer>> list = new ArrayList<>(reachable.entrySet());
            list.sort((a, b) -> a.getValue().equals(b.getValue()) ? Double.compare(a.getKey().distSqr(here.pos), b.getKey().distSqr(here.pos))
                    : Integer.compare(a.getValue(), b.getValue()));
            for (Map.Entry<BlockPos, Integer> e : list) {
                if (k >= FREIGHT) break;
                CaravanData.Stop s = cd.stop(e.getKey());
                if (s == null) continue;
                freight[k] = s.pos;
                setIfChanged(S_FREIGHT + k, named(s));
                data.set(D_FREIGHT_FEE + k, Logistics.fee(e.getValue()));
                data.set(D_FREIGHT_HOPS + k, e.getValue());
                data.set(D_FREIGHT_DIST + k, (int) Math.min(30000, Math.round(Math.sqrt(s.pos.distSqr(here.pos)))));
                k++;
            }
        }
        for (; k < FREIGHT; k++) {
            freight[k] = null;
            display.setItem(S_FREIGHT + k, ItemStack.EMPTY);
            data.set(D_FREIGHT_FEE + k, 0);
            data.set(D_FREIGHT_HOPS + k, 0);
            data.set(D_FREIGHT_DIST + k, 0);
        }
        List<Parcel> cargo = master.cargo();
        for (k = 0; k < CARGO; k++) {
            if (k >= cargo.size()) {
                display.setItem(S_CARGO + k, ItemStack.EMPTY);
                continue;
            }
            Parcel p = cargo.get(k);
            CaravanData.Stop to = cd.stop(p.dest);
            ItemStack box = new ItemStack(ChronicleCaravans.PARCEL.get());
            Compat.setName(box, Component.translatable("gui.chroniclecaravans.cargo_line", to == null ? Component.literal("?") : to.displayName(), p.stacks));
            setIfChanged(S_CARGO + k, box);
        }
        data.set(D_CARGO, cargo.size());
        data.set(D_CAPACITY, master.cargoCapacity());
        List<Parcel> waiting = here == null ? null : cd.depots().get(here.pos);
        data.set(D_WAITING, waiting == null ? 0 : waiting.size());
    }

    private void setIfChanged(int slot, ItemStack stack) {
        ItemStack old = display.getItem(slot);
        if (old.getCount() != stack.getCount() || !Compat.sameItem(old, stack)) display.setItem(slot, stack);
    }

    /** A stop as an item (milestone or bell) carrying its name. */
    private static ItemStack named(CaravanData.Stop s) {
        ItemStack st = new ItemStack(s.village ? Items.BELL : ChronicleCaravans.MILESTONE_ITEM.get());
        Compat.setName(st, s.displayName());
        return st;
    }

    @Override
    public void broadcastChanges() {
        if (master != null && ++refresh >= 10) {
            refresh = 0;
            update();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (master == null || !(p instanceof ServerPlayer sp) || sp.isSpectator() || !master.isAlive()) return false;
        if (id >= B_PARCEL && id < B_PARCEL + FREIGHT) return parcel(sp, id - B_PARCEL);
        boolean ok;
        if (id >= B_BUY && id < B_BUY + OFFERS) ok = buy(sp, id - B_BUY, 1);
        else if (id >= B_BUY_ALL && id < B_BUY_ALL + OFFERS) ok = buy(sp, id - B_BUY_ALL, 64);
        else if (id == B_ESCORT) ok = escort(sp);
        else if (id >= B_ROAD && id < B_ROAD + DESTS) ok = road(sp, id - B_ROAD);
        else if (id >= B_SKILL && id < B_SKILL + Guild.Skill.values().length) ok = learn(sp, Guild.Skill.byIndex(id - B_SKILL));
        else return false;
        level().playSound(null, master.blockPosition(), ok ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO, SoundSource.NEUTRAL, 0.6F, 1.0F);
        update();
        return true;
    }

    private boolean buy(ServerPlayer sp, int index, int times) {
        List<MerchantOffer> offers = master.stock();
        if (index >= offers.size()) return false;
        MerchantOffer o = offers.get(index);
        CaravanData cd = CaravanData.get(level());
        int skills = cd.skills(sp.getUUID());
        if (!Trading.unlocked(o, cd.level(sp.getUUID()), skills)) return false;
        long now = level().getGameTime();
        Trading.Quote q = Trading.quote(o, skills, Market.factor(cd, master.region(), o, now));
        int done = Trading.execute(o, q, sp.getInventory(), stack -> {
            if (!sp.getInventory().add(stack)) net.minecraft.world.level.block.Block.popResource(sp.level(), sp.blockPosition(), stack);
        }, times);
        if (done <= 0) return false;
        Market.traded(cd, o, done, now);
        cd.traded(sp, Trading.value(q) * done);
        return true;
    }

    private boolean escort(ServerPlayer sp) {
        if (master.state() == CaravanMaster.State.LEAVING) return false;
        CaravanData cd = CaravanData.get(level());
        int skills = cd.skills(sp.getUUID());
        if (!sp.isCreative() && !Trading.pay(sp.getInventory(), escortPrice(skills))) return false;
        master.startEscort(level(), escortGuards(skills), escortMinutes(skills) * 1200, Guild.has(skills, Guild.Skill.VETERAN_GUARDS), sp.getUUID());
        cd.addXp(sp, 1, "reason.chroniclecaravans.escort");
        sp.sendSystemMessage(Component.translatable("msg.chroniclecaravans.escort_hired", escortGuards(skills), escortMinutes(skills)).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private boolean road(ServerPlayer sp, int k) {
        BlockPos stop = master.currentStop();
        BlockPos dest = dests[k];
        if (stop == null || dest == null) return false;
        CaravanData cd = CaravanData.get(level());
        int skills = cd.skills(sp.getUUID());
        if (!sp.isCreative() && !Trading.pay(sp.getInventory(), roadPrice(RoadWorks.length(stop, dest), skills))) return false;
        RoadBuilder b = RoadBuilder.start(level(), stop, dest, sp.getUUID(), Guild.has(skills, Guild.Skill.PAVED_ROADS), Guild.has(skills, Guild.Skill.FOREMAN));
        if (b == null) return false;
        CaravanData.Stop to = cd.stop(dest);
        sp.sendSystemMessage(Component.translatable("msg.chroniclecaravans.road_hired", to == null ? Component.literal("?") : to.displayName(),
                (int) Math.round(RoadWorks.length(stop, dest))).withStyle(ChatFormatting.GREEN));
        // The same road cannot be ordered twice
        cd.routeOrCreate(stop, dest);
        return true;
    }

    private boolean learn(ServerPlayer sp, Guild.Skill skill) {
        if (skill == null) return false;
        CaravanData cd = CaravanData.get(level());
        if (!cd.learn(sp.getUUID(), skill)) return false;
        Component title = Guild.title(cd.xp(sp.getUUID()), cd.skills(sp.getUUID()));
        sp.sendSystemMessage(Component.translatable("msg.chroniclecaravans.learnt", skill.label().withStyle(skill.branch.color), title).withStyle(ChatFormatting.GOLD));
        level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        return true;
    }

    /** Opens the parcel crate for a destination (the caravan's interface closes). */
    private boolean parcel(ServerPlayer sp, int k) {
        BlockPos stop = master.currentStop();
        BlockPos dest = freight[k];
        if (stop == null || dest == null || !CCConfig.FREIGHT.get()) return false;
        CaravanData.Stop to = CaravanData.get(level()).stop(dest);
        Component title = Component.translatable("gui.chroniclecaravans.parcel_to", to == null ? Component.literal("?") : to.displayName());
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ParcelMenu(id, inv, stop, dest), title));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        if (master == null) return true;
        return master.isAlive() && (p.isSpectator() || p.distanceToSqr(master) < 10 * 10);
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (master != null) master.removeCustomer(p);
    }
}
