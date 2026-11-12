package com.chroniclecaravans.menu;

import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.RoadBuilder;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.RoadWorks;
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
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The caravan's interface: market, requests, services (escort, road crews) and the guild's skill tree.
 * Everything the screen shows travels in display slots and data slots (no custom packets), and every action
 * is a menu button click: it works the same on every loader.
 */
public class CaravanMenu extends AbstractContainerMenu {
    public static final int OFFERS = 24, DESTS = 4;
    /** Display slots: three per offer (cost A, cost B, result), the road destinations, the current stop. */
    public static final int DISPLAY = OFFERS * 3 + DESTS + 1;
    private static final int STOP_SLOT = OFFERS * 3 + DESTS;

    // Data slots (values are sent as shorts: everything stays under 32767)
    public static final int D_OFFER_COUNT = OFFERS, D_PROGRESS = OFFERS + 1, D_NEEDED = OFFERS + 2, D_LEVEL = OFFERS + 3,
            D_SKILLS = OFFERS + 4, D_POINTS = OFFERS + 5, D_REGION = OFFERS + 6, D_STATE = OFFERS + 7, D_HALT = OFFERS + 8,
            D_GUARDS = OFFERS + 9, D_ESCORT_LEFT = OFFERS + 10, D_ESCORT_PRICE = OFFERS + 11, D_ESCORT_COUNT = OFFERS + 12,
            D_ESCORT_MINUTES = OFFERS + 13, D_ROAD_PRICE = OFFERS + 14, D_ROAD_DIST = D_ROAD_PRICE + DESTS,
            D_XP = D_ROAD_DIST + DESTS, D_FLAGS = D_XP + 1, DATA = D_FLAGS + 1;
    public static final int FLAG_AT_STOP = 1, FLAG_REPUTATION = 2, FLAG_TO_INN = 4;

    // Buttons (ids stay under 128: 1.20.1 sends them as a byte)
    public static final int B_BUY = 0, B_BUY_ALL = 24, B_ESCORT = 60, B_ROAD = 70, B_SKILL = 80;

    private final SimpleContainer display = new SimpleContainer(DISPLAY);
    private final ContainerData data;
    private final CaravanMaster master;
    private final Player player;
    private final BlockPos[] dests = new BlockPos[DESTS];
    private int refresh;

    /** Client side. */
    public CaravanMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public CaravanMenu(int id, Inventory inv, CaravanMaster master) {
        super(ChronicleCaravans.CARAVAN_MENU.get(), id);
        this.master = master;
        this.player = inv.player;
        this.data = new SimpleContainerData(DATA);
        for (int i = 0; i < DISPLAY; i++) addSlot(new DisplaySlot(display, i));
        // The player's inventory, never drawn: it keeps the client's copy up to date (prices, "you have")
        for (int i = 0; i < Trading.PLAYER_SLOTS; i++) addSlot(new Slot(inv, i, -10000, -10000));
        addDataSlots(data);
        if (master != null) {
            master.addCustomer(player);
            update();
        }
    }

    /** A slot that only shows an item: nothing can be taken or put. */
    private static class DisplaySlot extends Slot {
        DisplaySlot(SimpleContainer c, int i) {
            super(c, i, -10000, -10000);
        }

        @Override
        public boolean mayPickup(Player p) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack s) {
            return false;
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

    public int kind(int i) {
        return (data.get(i) >> 7) & 3;
    }

    /** 0: unlocked; 1 to 14: the level needed; 15: needs the "Hidden Stock" skill. */
    public int lock(int i) {
        return (data.get(i) >> 9) & 15;
    }

    public ItemStack destination(int k) {
        return shown(OFFERS * 3 + k);
    }

    public ItemStack currentStop() {
        return shown(STOP_SLOT);
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
        int xp = cd.xp(player.getUUID());
        int skills = cd.skills(player.getUUID());
        int lvl = cd.level(player.getUUID());
        List<MerchantOffer> offers = master.stock();
        int n = Math.min(OFFERS, offers.size());
        for (int i = 0; i < OFFERS; i++) {
            if (i >= n) {
                display.setItem(i * 3, ItemStack.EMPTY);
                display.setItem(i * 3 + 1, ItemStack.EMPTY);
                display.setItem(i * 3 + 2, ItemStack.EMPTY);
                data.set(i, 0);
                continue;
            }
            MerchantOffer o = offers.get(i);
            setIfChanged(i * 3, Trading.costA(o, skills));
            setIfChanged(i * 3 + 1, Trading.costB(o));
            setIfChanged(i * 3 + 2, Trading.result(o, skills));
            int kind = Region.kindOf(o);
            int lock = Trading.unlocked(o, lvl, skills) ? 0 : kind == Region.KIND_TREASURE ? 15 : Math.min(14, Region.levelOf(o));
            int left = Math.max(0, Math.min(127, o.getMaxUses() - o.getUses()));
            data.set(i, left | kind << 7 | lock << 9);
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
        data.set(D_HALT, Math.max(0, master.haltTicks() / 20));
        data.set(D_GUARDS, master.guards(level).size());
        data.set(D_ESCORT_LEFT, Math.min(30000, Math.max(0, master.escortTicks() / 20)));
        data.set(D_ESCORT_PRICE, escortPrice(skills));
        data.set(D_ESCORT_COUNT, escortGuards(skills));
        data.set(D_ESCORT_MINUTES, escortMinutes(skills));
        BlockPos stop = master.currentStop();
        int flags = (stop != null ? FLAG_AT_STOP : 0) | (CCConfig.REPUTATION_ENABLED.get() ? FLAG_REPUTATION : 0) | (master.headingToInn() ? FLAG_TO_INN : 0);
        data.set(D_FLAGS, flags);
        CaravanData.Stop here = stop == null ? null : cd.stop(stop);
        setIfChanged(STOP_SLOT, here == null ? ItemStack.EMPTY : named(here));
        List<CaravanData.Stop> linked = here == null ? List.of() : cd.linked(here.pos);
        int k = 0;
        for (CaravanData.Stop s : linked) {
            if (k >= DESTS) break;
            CaravanData.Route r = cd.route(here.pos, s.pos);
            if (r != null && r.built) continue;
            dests[k] = s.pos;
            setIfChanged(OFFERS * 3 + k, named(s));
            double dist = RoadWorks.length(here.pos, s.pos);
            data.set(D_ROAD_PRICE + k, roadPrice(dist, skills));
            data.set(D_ROAD_DIST + k, (int) Math.round(dist));
            k++;
        }
        for (; k < DESTS; k++) {
            dests[k] = null;
            display.setItem(OFFERS * 3 + k, ItemStack.EMPTY);
            data.set(D_ROAD_PRICE + k, 0);
            data.set(D_ROAD_DIST + k, 0);
        }
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
        int value = Trading.execute(o, skills, sp.getInventory(), stack -> {
            if (!sp.getInventory().add(stack)) net.minecraft.world.level.block.Block.popResource(sp.level(), sp.blockPosition(), stack);
        }, times);
        if (value <= 0) return false;
        cd.traded(sp, value);
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
