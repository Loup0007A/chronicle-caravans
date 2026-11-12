package com.chroniclecaravans.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Trading;
import com.chroniclecaravans.menu.CaravanMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The caravan's interface, a ledger in five tabs: the market (click to buy, shift-click to buy all you can),
 * the requests (goods the caravan pays for), freight (parcels), services (escort, road crews) and the guild.
 */
public class CaravanScreen extends CCScreen implements MenuAccess<CaravanMenu> {
    private static final int W = 352, H = 220, ROW = 25, ROWS = 5;
    public static final int MARKET = 0, REQUESTS = 1, FREIGHT = 2, SERVICES = 3, GUILD = 4;
    private static final String[] TABS = {"market", "requests", "freight", "services", "guild"};

    private final CaravanMenu menu;
    private int tab;
    private int scroll;
    private int x0, y0;
    private List<Component> hoverTip;

    public CaravanScreen(CaravanMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public CaravanMenu getMenu() {
        return menu;
    }

    public void setTab(int tab) {
        this.tab = Mth.clamp(tab, 0, TABS.length - 1);
        this.scroll = 0;
    }

    @Override
    protected void init() {
        super.init();
        x0 = (width - W) / 2;
        y0 = (height - H) / 2 + 9;
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) minecraft.player.closeContainer();
        else super.onClose();
    }

    @Override
    public void removed() {
        if (minecraft.player != null) menu.removed(minecraft.player);
        super.removed();
    }

    private void send(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private Inventory inv() {
        return minecraft.player.getInventory();
    }

    private int flags() {
        return menu.get(CaravanMenu.D_FLAGS);
    }

    /** The offers shown in a tab: market (goods and treasures) or requests. */
    private List<Integer> rows() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < menu.offerCount(); i++) {
            boolean request = menu.kind(i) == Region.KIND_SELL;
            if ((tab == REQUESTS) == request) out.add(i);
        }
        return out;
    }

    private int affordable(int i) {
        ItemStack a = menu.costA(i), b = menu.costB(i);
        if (a.isEmpty()) return 0;
        int times = menu.stockLeft(i);
        if (!b.isEmpty() && a.is(b.getItem())) return Math.min(times, Trading.count(inv(), a.getItem()) / (a.getCount() + b.getCount()));
        times = Math.min(times, Trading.count(inv(), a.getItem()) / a.getCount());
        if (!b.isEmpty()) times = Math.min(times, Trading.count(inv(), b.getItem()) / b.getCount());
        return times;
    }

    private int emeralds() {
        return Trading.count(inv(), Items.EMERALD);
    }

    private Region region() {
        return Region.values()[Mth.clamp(menu.get(CaravanMenu.D_REGION), 0, Region.values().length - 1)];
    }

    private static int colorOf(ChatFormatting f) {
        TextColor c = TextColor.fromLegacyFormat(f);
        return 0xFF000000 | (c == null ? 0xFFFFFF : c.getValue());
    }

    // ================================================================ drawing

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        int tw = (W - 28) / TABS.length;
        // Inactive tabs behind the frame, the active one over it
        for (int i = 0; i < TABS.length; i++) {
            if (i == tab) continue;
            int tx = x0 + 14 + i * tw;
            UiKit.tab(g, font, tx, y0 - 18, tw - 2, tabLabel(i), false, in(mx, my, tx, y0 - 18, tw - 2, 18));
        }
        UiKit.panel(g, x0, y0, W, H);
        UiKit.tab(g, font, x0 + 14 + tab * tw, y0 - 18, tw - 2, tabLabel(tab), true, false);

        hoverTip = null;
        header(g, mx, my);
        UiKit.separator(g, x0 + 14, y0 + 63, W - 28);

        List<Component> tip;
        ItemStack[] tipStack = {ItemStack.EMPTY};
        int cy = y0 + 68;
        switch (tab) {
            case MARKET, REQUESTS -> tip = drawOffers(g, cy, mx, my, tipStack);
            case FREIGHT -> tip = drawFreight(g, cy, mx, my);
            case SERVICES -> tip = drawServices(g, cy, mx, my);
            default -> tip = drawGuild(g, cy, mx, my);
        }
        if (tip == null) tip = hoverTip;
        footer(g);
        if (!tipStack[0].isEmpty()) tooltip(g, tipStack[0], mx, my);
        else if (tip != null && !tip.isEmpty()) tooltip(g, tip, mx, my);
    }

    private Component tabLabel(int i) {
        Component label = Component.translatable("gui.chroniclecaravans.tab." + TABS[i]);
        if (i == GUILD && menu.get(CaravanMenu.D_POINTS) > 0) label = label.copy().append(Component.literal(" ✦").withStyle(ChatFormatting.GREEN));
        return label;
    }

    private void header(GuiGraphics g, int mx, int my) {
        // Portrait of the master, looking at the mouse
        UiKit.inset(g, x0 + 14, y0 + 13, 40, 46);
        Entity e = minecraft.level == null ? null : minecraft.level.getEntity(menu.entityId());
        if (e instanceof LivingEntity le) UiKit.portrait(g, x0 + 16, y0 + 15, x0 + 52, y0 + 57, 19, mx, my, le);
        Region r = region();
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x0 + 60, y0 + 15, UiKit.HEAD);
        g.fill(x0 + 60, y0 + 27, x0 + 67, y0 + 34, 0xFF2A1C10);
        g.fill(x0 + 61, y0 + 28, x0 + 66, y0 + 33, colorOf(r.color));
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.from", Component.translatable("region.chroniclecaravans." + r.key())),
                x0 + 71, y0 + 27, UiKit.INK);
        UiKit.icon(g, statusIcon(), x0 + 59, y0 + 39);
        UiKit.text(g, font, status(), x0 + 71, y0 + 40, UiKit.INK_SOFT);
        // The player: level seal, title, emeralds
        int level = menu.get(CaravanMenu.D_LEVEL), xp = menu.get(CaravanMenu.D_XP), skills = menu.get(CaravanMenu.D_SKILLS);
        UiKit.seal(g, font, x0 + W - 46, y0 + 14, String.valueOf(level));
        String t = Guild.title(xp, skills).getString();
        UiKit.text(g, font, Component.literal(t).withStyle(ChatFormatting.ITALIC), x0 + W - 52 - font.width(Component.literal(t).withStyle(ChatFormatting.ITALIC)), y0 + 18, UiKit.GOLD);
        String coins = String.valueOf(emeralds());
        g.renderItem(new ItemStack(Items.EMERALD), x0 + W - 70 - font.width(coins), y0 + 28);
        UiKit.text(g, font, coins, x0 + W - 52 - font.width(coins), y0 + 33, UiKit.INK);
        if (in(mx, my, x0 + W - 46, y0 + 14, 28, 28)) {
            hoverTip = List.of(Component.translatable("gui.chroniclecaravans.level", level), Guild.title(xp, skills));
        }
    }

    private int statusIcon() {
        if ((flags() & CaravanMenu.FLAG_TO_INN) != 0) return UiKit.I_MOON;
        return switch (menu.get(CaravanMenu.D_STATE)) {
            case 1 -> UiKit.I_CLOCK;
            case 2 -> UiKit.I_MOON;
            case 3 -> UiKit.I_ARROW;
            case 4 -> menu.get(CaravanMenu.D_CAMP) == 1 ? UiKit.I_STORM : UiKit.I_FIRE;
            default -> UiKit.I_ROAD;
        };
    }

    private Component status() {
        if ((flags() & CaravanMenu.FLAG_TO_INN) != 0) return Component.translatable("gui.chroniclecaravans.state.to_inn");
        return switch (menu.get(CaravanMenu.D_STATE)) {
            case 1 -> {
                ItemStack stop = menu.currentStop();
                int s = menu.get(CaravanMenu.D_HALT);
                yield Component.translatable("gui.chroniclecaravans.state.halted", stop.isEmpty() ? "?" : stop.getHoverName().getString(),
                        String.format("%d:%02d", s / 60, s % 60));
            }
            case 2 -> Component.translatable("gui.chroniclecaravans.state.lodging");
            case 3 -> Component.translatable("gui.chroniclecaravans.state.leaving");
            case 4 -> Component.translatable(menu.get(CaravanMenu.D_CAMP) == 1 ? "gui.chroniclecaravans.state.camp_storm" : "gui.chroniclecaravans.state.camp_night");
            default -> Component.translatable("gui.chroniclecaravans.state.traveling");
        };
    }

    private void footer(GuiGraphics g) {
        int fy = y0 + H - 22;
        if ((tab == MARKET || tab == REQUESTS) && (flags() & CaravanMenu.FLAG_MARKET) != 0) {
            ItemStack item = tab == REQUESTS ? menu.shortage() : menu.surplus();
            if (!item.isEmpty()) {
                UiKit.icon(g, UiKit.I_STAR, x0 + 14, fy - 1);
                Component news = Component.translatable(tab == REQUESTS ? "gui.chroniclecaravans.news.shortage" : "gui.chroniclecaravans.news.surplus",
                        item.getHoverName().getString(), Component.translatable("region.chroniclecaravans." + region().key()));
                UiKit.text(g, font, UiKit.fit(font, news.getString(), W - 44), x0 + 27, fy, UiKit.GOLD);
                return;
            }
        }
        String key = switch (tab) {
            case MARKET -> "gui.chroniclecaravans.hint_market";
            case REQUESTS -> "gui.chroniclecaravans.hint_requests";
            case FREIGHT -> "gui.chroniclecaravans.hint_freight";
            case SERVICES -> "gui.chroniclecaravans.hint_services";
            default -> "gui.chroniclecaravans.hint_guild";
        };
        UiKit.text(g, font, UiKit.fit(font, Component.translatable(key).getString(), W - 32), x0 + 14, fy, UiKit.INK_SOFT);
    }

    private List<Component> drawOffers(GuiGraphics g, int cy, int mx, int my, ItemStack[] tipStack) {
        List<Integer> rows = rows();
        List<Component> tip = null;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
        if (rows.isEmpty()) UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.empty"), x0 + 18, cy + 8, UiKit.INK_SOFT);
        int rx = x0 + 14, rw = W - 28 - 11;
        boolean requests = tab == REQUESTS;
        for (int r = 0; r < ROWS && r + scroll < rows.size(); r++) {
            int i = rows.get(r + scroll);
            int ry = cy + r * ROW;
            int lock = menu.lock(i), left = menu.stockLeft(i), can = affordable(i);
            boolean hover = in(mx, my, rx, ry, rw, ROW - 1);
            UiKit.card(g, rx, ry, rw, ROW - 1, lock != 0 || left <= 0 ? UiKit.CARD_DIM : hover ? UiKit.CARD_HOVER : UiKit.CARD);
            ItemStack a = menu.costA(i), b = menu.costB(i), res = menu.result(i);
            ItemStack main = requests ? a : res;
            boolean treasure = menu.kind(i) == Region.KIND_TREASURE;
            UiKit.slot(g, rx + 3, ry + 3, treasure);
            UiKit.item(g, font, main, rx + 4, ry + 4);
            int nameColor = lock != 0 ? UiKit.INK_SOFT : treasure ? UiKit.TREASURE : UiKit.INK;
            UiKit.text(g, font, UiKit.fit(font, main.getHoverName().getString(), 128), rx + 26, ry + 4, nameColor);
            // Second line: stock (or what you have), or what is missing
            if (lock == 15) {
                UiKit.icon(g, UiKit.I_LOCK, rx + 26, ry + 13);
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.needs_skill", Guild.Skill.HIDDEN_STOCK.label()), rx + 38, ry + 14, UiKit.TREASURE);
            } else if (lock != 0) {
                UiKit.icon(g, UiKit.I_LOCK, rx + 26, ry + 13);
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.needs_level", lock), rx + 38, ry + 14, UiKit.BAD);
            } else if (left <= 0) {
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.sold_out"), rx + 26, ry + 14, UiKit.INK_SOFT);
            } else if (requests) {
                int have = Trading.count(inv(), a.getItem());
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.have", have), rx + 26, ry + 14, have >= a.getCount() ? UiKit.GOOD : UiKit.INK_SOFT);
            } else {
                // Stock pips: one per remaining deal (up to eight), then the number
                int shown = Math.min(8, left);
                for (int k = 0; k < shown; k++) g.fill(rx + 27 + k * 4, ry + 16, rx + 30 + k * 4, ry + 19, can > 0 ? 0xFF4C8A3A : 0xFF9A8A70);
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.left", left), rx + 30 + shown * 4, ry + 14, UiKit.INK_SOFT);
            }
            // Price (or payout) on the right
            int px = rx + rw - 96;
            if (requests) {
                UiKit.icon(g, UiKit.I_ARROW, px + 8, ry + 8);
                UiKit.slot(g, px + 26, ry + 3, can > 0 && lock == 0);
                UiKit.item(g, font, res, px + 27, ry + 4);
                if (in(mx, my, px + 26, ry + 3, 18, 18)) tipStack[0] = res;
            } else if (!b.isEmpty()) {
                UiKit.slot(g, px, ry + 3, false);
                UiKit.item(g, font, a, px + 1, ry + 4);
                UiKit.text(g, font, "+", px + 20, ry + 8, UiKit.INK_SOFT);
                UiKit.slot(g, px + 26, ry + 3, can > 0 && lock == 0);
                UiKit.item(g, font, b, px + 27, ry + 4);
                if (in(mx, my, px, ry + 3, 18, 18)) tipStack[0] = a;
                if (in(mx, my, px + 26, ry + 3, 18, 18)) tipStack[0] = b;
            } else {
                UiKit.slot(g, px + 26, ry + 3, can > 0 && lock == 0);
                UiKit.item(g, font, a, px + 27, ry + 4);
                if (in(mx, my, px + 26, ry + 3, 18, 18)) tipStack[0] = a;
            }
            if (in(mx, my, rx + 3, ry + 3, 18, 18)) tipStack[0] = main;
            // Market trend: ▲/▼ is where the price goes, green when it is good for you
            int m = menu.market(i);
            if (Math.abs(m) >= 3 && lock == 0) {
                boolean up = requests ? m > 0 : m < 0;
                String s = (up ? "▲" : "▼") + Math.abs(m) + "%";
                UiKit.text(g, font, s, rx + rw - 6 - font.width(s), ry + 8, m > 0 ? UiKit.GOOD : UiKit.BAD);
            }
            if (hover && tipStack[0].isEmpty()) {
                tip = new ArrayList<>();
                tip.add(main.getHoverName().copy().withStyle(treasure ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
                if (lock == 0 && left > 0) {
                    tip.add(Component.translatable(can > 0 ? (requests ? "gui.chroniclecaravans.click_sell" : "gui.chroniclecaravans.click_buy")
                            : "gui.chroniclecaravans.cannot_afford").withStyle(can > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (can > 1) tip.add(Component.translatable("gui.chroniclecaravans.shift_buy", can).withStyle(ChatFormatting.GRAY));
                }
                if (Math.abs(m) >= 3) {
                    tip.add(Component.translatable(m > 0 ? "gui.chroniclecaravans.market_good" : "gui.chroniclecaravans.market_bad", Math.abs(m) + "%")
                            .withStyle(m > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
            }
        }
        UiKit.scrollbar(g, x0 + W - 22, cy, ROWS * ROW - 1, scroll, rows.size(), ROWS);
        return tip;
    }

    private void cardTitle(GuiGraphics g, int icon, Component title, int x, int y) {
        UiKit.icon(g, icon, x, y);
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x + 13, y + 1, UiKit.HEAD);
    }

    private static final int FREIGHT_W = 206;

    private List<Component> drawFreight(GuiGraphics g, int cy, int mx, int my) {
        int lx = x0 + 14, lw = FREIGHT_W, bh = H - 96;
        UiKit.card(g, lx, cy, lw, bh, UiKit.CARD);
        cardTitle(g, UiKit.I_PARCEL, Component.translatable("gui.chroniclecaravans.freight.send"), lx + 6, cy + 6);
        if ((flags() & CaravanMenu.FLAG_FREIGHT) == 0) {
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.freight.off"), lx + 6, cy + 22, lw - 12, UiKit.INK_SOFT);
        } else if ((flags() & CaravanMenu.FLAG_AT_STOP) == 0) {
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.freight.not_here"), lx + 6, cy + 22, lw - 12, UiKit.INK_SOFT);
        } else {
            int any = 0;
            for (int k = 0; k < CaravanMenu.FREIGHT; k++) {
                ItemStack d = menu.freightDestination(k);
                if (d.isEmpty()) continue;
                int ry = cy + 20 + any * 20;
                any++;
                int fee = menu.get(CaravanMenu.D_FREIGHT_FEE + k), hops = menu.get(CaravanMenu.D_FREIGHT_HOPS + k), dist = menu.get(CaravanMenu.D_FREIGHT_DIST + k);
                g.renderItem(d, lx + 5, ry);
                UiKit.text(g, font, UiKit.fit(font, d.getHoverName().getString(), 98), lx + 24, ry, UiKit.INK);
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.freight.legs", hops, dist), lx + 24, ry + 9, UiKit.INK_SOFT);
                g.renderItem(new ItemStack(Items.EMERALD), lx + lw - 68, ry);
                UiKit.text(g, font, "×" + fee, lx + lw - 51, ry + 5, UiKit.INK);
                boolean ok = emeralds() >= fee;
                boolean hover = ok && in(mx, my, lx + lw - 34, ry, 30, 16);
                UiKit.button(g, font, lx + lw - 34, ry, 30, 16, Component.translatable("gui.chroniclecaravans.freight.go"),
                        ok ? (hover ? UiKit.BUTTON_HOVER : UiKit.BUTTON) : UiKit.BUTTON_OFF);
            }
            if (any == 0) UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.freight.none"), lx + 6, cy + 22, lw - 12, UiKit.INK_SOFT);
        }
        // The cargo on board
        int rx = x0 + 14 + FREIGHT_W + 6, rw = W - 28 - FREIGHT_W - 6;
        UiKit.card(g, rx, cy, rw, bh, UiKit.CARD);
        cardTitle(g, UiKit.I_SCALES, Component.translatable("gui.chroniclecaravans.freight.cargo"), rx + 6, cy + 6);
        int cargo = menu.get(CaravanMenu.D_CARGO), cap = menu.get(CaravanMenu.D_CAPACITY);
        UiKit.bar(g, rx + 6, cy + 20, rw - 12, cargo, Math.max(1, cap), true);
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.freight.load", cargo, cap), rx + 6, cy + 31, UiKit.INK);
        for (int k = 0; k < CaravanMenu.CARGO; k++) {
            ItemStack c = menu.cargo(k);
            if (c.isEmpty()) continue;
            int ry = cy + 44 + k * 12;
            UiKit.icon(g, UiKit.I_PARCEL, rx + 6, ry - 1);
            UiKit.text(g, font, UiKit.fit(font, c.getHoverName().getString(), rw - 24), rx + 18, ry, UiKit.INK_SOFT);
        }
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.freight.waiting", menu.get(CaravanMenu.D_WAITING)), rx + 6, cy + bh - 13, UiKit.INK_SOFT);
        return null;
    }

    private int roadsTop(int cy, int rw) {
        int lines = font.split(Component.translatable("gui.chroniclecaravans.roads_from", menu.currentStop().getHoverName().getString()), rw - 12).size();
        return cy + 20 + lines * 10;
    }

    private List<Component> drawServices(GuiGraphics g, int cy, int mx, int my) {
        int bh = H - 96;
        // Escort
        int bx = x0 + 14, bw = 160;
        UiKit.card(g, bx, cy, bw, bh, UiKit.CARD);
        cardTitle(g, UiKit.I_SHIELD, Component.translatable("gui.chroniclecaravans.escort"), bx + 6, cy + 6);
        int guards = menu.get(CaravanMenu.D_ESCORT_COUNT), minutes = menu.get(CaravanMenu.D_ESCORT_MINUTES), price = menu.get(CaravanMenu.D_ESCORT_PRICE);
        UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.escort_desc", guards, minutes), bx + 6, cy + 21, bw - 12, UiKit.INK);
        int left = menu.get(CaravanMenu.D_ESCORT_LEFT);
        if (left > 0) {
            UiKit.icon(g, UiKit.I_CHECK, bx + 6, cy + bh - 40);
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.escort_active", menu.get(CaravanMenu.D_GUARDS),
                    String.format("%d:%02d", left / 60, left % 60)), bx + 19, cy + bh - 39, UiKit.GOOD);
        }
        g.renderItem(new ItemStack(Items.EMERALD), bx + 6, cy + bh - 22);
        UiKit.text(g, font, "×" + price, bx + 23, cy + bh - 17, UiKit.INK);
        boolean canEscort = emeralds() >= price && menu.get(CaravanMenu.D_STATE) != 3;
        boolean hover = canEscort && in(mx, my, bx + 48, cy + bh - 22, bw - 54, 18);
        UiKit.button(g, font, bx + 48, cy + bh - 22, bw - 54, 18, Component.translatable(left > 0 ? "gui.chroniclecaravans.extend" : "gui.chroniclecaravans.hire"),
                canEscort ? (hover ? UiKit.BUTTON_HOVER : UiKit.BUTTON) : UiKit.BUTTON_OFF);
        // Road crews
        int rx = x0 + 180, rw = W - 194;
        UiKit.card(g, rx, cy, rw, bh, UiKit.CARD);
        cardTitle(g, UiKit.I_ROAD, Component.translatable("gui.chroniclecaravans.roads"), rx + 6, cy + 6);
        if ((flags() & CaravanMenu.FLAG_AT_STOP) == 0) {
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.roads_not_here"), rx + 6, cy + 22, rw - 12, UiKit.INK_SOFT);
        } else {
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.roads_from", menu.currentStop().getHoverName().getString()), rx + 6, cy + 20, rw - 12, UiKit.INK);
            int y = roadsTop(cy, rw);
            int any = 0;
            for (int k = 0; k < CaravanMenu.DESTS; k++) {
                ItemStack d = menu.destination(k);
                if (d.isEmpty()) continue;
                int ry = y + 2 + any * 21;
                any++;
                int p = menu.get(CaravanMenu.D_ROAD_PRICE + k), dist = menu.get(CaravanMenu.D_ROAD_DIST + k);
                UiKit.text(g, font, UiKit.fit(font, d.getHoverName().getString(), rw - 76), rx + 6, ry, UiKit.INK);
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.blocks", dist), rx + 6, ry + 9, UiKit.INK_SOFT);
                g.renderItem(new ItemStack(Items.EMERALD), rx + rw - 68, ry);
                UiKit.text(g, font, "×" + p, rx + rw - 51, ry + 5, UiKit.INK);
                boolean ok = emeralds() >= p;
                boolean h = ok && in(mx, my, rx + rw - 34, ry, 30, 16);
                UiKit.button(g, font, rx + rw - 34, ry, 30, 16, Component.translatable("gui.chroniclecaravans.build"), ok ? (h ? UiKit.BUTTON_HOVER : UiKit.BUTTON) : UiKit.BUTTON_OFF);
            }
            if (any == 0) {
                boolean linked = (flags() & CaravanMenu.FLAG_LINKED) != 0;
                UiKit.wrap(g, font, Component.translatable(linked ? "gui.chroniclecaravans.roads_none" : "gui.chroniclecaravans.roads_alone"), rx + 6, y + 4, rw - 12, UiKit.INK_SOFT);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- guild

    private static ItemStack icon(Guild.Skill s) {
        return new ItemStack(switch (s) {
            case HAGGLER -> Items.EMERALD;
            case FAIR_SELLER -> Items.WHEAT;
            case HIDDEN_STOCK -> Items.ENCHANTED_BOOK;
            case MERCHANT_PRINCE -> Items.GOLDEN_HELMET;
            case VETERAN_GUARDS -> Items.IRON_CHESTPLATE;
            case BOUNTY_HUNTER -> Items.CROSSBOW;
            case WARDEN -> Items.SHIELD;
            case SWORN_SHIELD -> Items.TOTEM_OF_UNDYING;
            case FOREMAN -> Items.IRON_SHOVEL;
            case PAVED_ROADS -> Items.STONE_BRICKS;
            case INNKEEPER -> Items.RED_BED;
            case CARTOGRAPHER -> Items.FILLED_MAP;
        });
    }

    private int[] nodePos(Guild.Skill s) {
        int tx = x0 + 136, tw = W - 150;
        int colW = tw / 3;
        int cx = tx + s.branch.ordinal() * colW + colW / 2 + s.col * 18;
        int cy = y0 + 68 + 32 + s.row * 36;
        return new int[] {cx - 13, cy - 13};
    }

    private List<Component> drawGuild(GuiGraphics g, int cy, int mx, int my) {
        List<Component> tip = null;
        int xp = menu.get(CaravanMenu.D_XP), skills = menu.get(CaravanMenu.D_SKILLS);
        int level = menu.get(CaravanMenu.D_LEVEL), points = menu.get(CaravanMenu.D_POINTS);
        int lx = x0 + 14, lw = 116, bh = H - 96;
        UiKit.card(g, lx, cy, lw, bh, UiKit.CARD);
        int y = UiKit.wrap(g, font, Guild.title(xp, skills).withStyle(ChatFormatting.BOLD), lx + 6, cy + 6, lw - 12, UiKit.HEAD);
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.level", level), lx + 6, y + 2, UiKit.INK);
        int need = Math.max(1, menu.get(CaravanMenu.D_NEEDED)), prog = menu.get(CaravanMenu.D_PROGRESS);
        boolean max = level >= Guild.MAX_LEVEL;
        UiKit.bar(g, lx + 6, y + 13, lw - 12, max ? 1 : prog, max ? 1 : need, max);
        UiKit.text(g, font, max ? "max" : prog + " / " + need + " xp", lx + 6, y + 24, UiKit.INK_SOFT);
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.points", points), lx + 6, y + 37, points > 0 ? UiKit.GOOD : UiKit.INK_SOFT);
        Component help = Component.translatable((flags() & CaravanMenu.FLAG_REPUTATION) == 0 ? "msg.chroniclecaravans.rep_disabled" : "gui.chroniclecaravans.guild_short");
        UiKit.wrap(g, font, help, lx + 6, y + 50, lw - 12, UiKit.INK_SOFT);
        // Tree
        int tx = x0 + 136, tw = W - 150;
        UiKit.card(g, tx, cy, tw, bh, UiKit.CARD);
        int colW = tw / 3;
        for (Guild.Branch b : Guild.Branch.values()) {
            Component label = Component.translatable("branch.chroniclecaravans." + b.key()).withStyle(ChatFormatting.BOLD);
            int lxx = tx + b.ordinal() * colW + (colW - font.width(label)) / 2;
            UiKit.text(g, font, label, lxx, cy + 5, UiKit.HEAD);
            g.fill(lxx, cy + 14, lxx + font.width(label), cy + 15, colorOf(b.color));
        }
        for (Guild.Skill s : Guild.Skill.values()) {
            if (s.parent == null) continue;
            int[] a = nodePos(s.parent), b = nodePos(s);
            int ax = a[0] + 13, ay = a[1] + 26, bx = b[0] + 13, by = b[1];
            int col = Guild.has(skills, s) ? 0xFFB07A20 : 0x90593A1E;
            int midY = (ay + by) / 2;
            g.fill(ax, ay, ax + 1, midY + 1, col);
            g.fill(Math.min(ax, bx), midY, Math.max(ax, bx) + 1, midY + 1, col);
            g.fill(bx, midY, bx + 1, by, col);
        }
        for (Guild.Skill s : Guild.Skill.values()) {
            int[] p = nodePos(s);
            boolean have = Guild.has(skills, s);
            String block = blocker(s, level, skills, points);
            boolean can = block == null;
            boolean hover = in(mx, my, p[0], p[1], 26, 26);
            UiKit.medal(g, p[0], p[1], have ? UiKit.MEDAL_DONE : can ? UiKit.MEDAL_OPEN : UiKit.MEDAL_LOCKED);
            g.renderItem(icon(s), p[0] + 5, p[1] + 5);
            if (!have && !can) g.fill(p[0] + 5, p[1] + 5, p[0] + 21, p[1] + 21, 0x70302820);
            if (hover) {
                UiKit.medalHover(g, p[0], p[1]);
                tip = new ArrayList<>();
                tip.add(s.label().withStyle(s.branch.color));
                for (String line : s.description().getString().split("\n")) tip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                if (have) tip.add(Component.translatable("gui.chroniclecaravans.skill.learnt").withStyle(ChatFormatting.GOLD));
                else if (can) tip.add(Component.translatable("gui.chroniclecaravans.skill.click").withStyle(ChatFormatting.GREEN));
                else tip.add(Component.translatable(block, s.minLevel, s.parent == null ? "" : s.parent.label().getString()).withStyle(ChatFormatting.RED));
            }
        }
        return tip;
    }

    private static String blocker(Guild.Skill s, int level, int skills, int points) {
        if (Guild.has(skills, s)) return "gui.chroniclecaravans.skill.learnt";
        if (level < s.minLevel) return "gui.chroniclecaravans.skill.need_level";
        if (s.parent != null && !Guild.has(skills, s.parent)) return "gui.chroniclecaravans.skill.need_parent";
        if (points <= 0) return "gui.chroniclecaravans.skill.no_points";
        return null;
    }

    // ================================================================ input

    @Override
    protected boolean click(double mx, double my, int button, boolean shift) {
        int tw = (W - 28) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, x0 + 14 + i * tw, y0 - 18, tw - 2, 18)) {
                if (tab != i) {
                    setTab(i);
                    page();
                }
                return true;
            }
        }
        int cy = y0 + 68, bh = H - 96;
        switch (tab) {
            case MARKET, REQUESTS -> {
                List<Integer> rows = rows();
                for (int r = 0; r < ROWS && r + scroll < rows.size(); r++) {
                    if (in(mx, my, x0 + 14, cy + r * ROW, W - 39, ROW - 1)) {
                        int i = rows.get(r + scroll);
                        if (menu.lock(i) != 0 || affordable(i) <= 0) return true;
                        send((shift ? CaravanMenu.B_BUY_ALL : CaravanMenu.B_BUY) + i);
                        return true;
                    }
                }
            }
            case FREIGHT -> {
                int lx = x0 + 14, lw = FREIGHT_W, any = 0;
                for (int k = 0; k < CaravanMenu.FREIGHT; k++) {
                    if (menu.freightDestination(k).isEmpty()) continue;
                    int ry = cy + 20 + any * 20;
                    any++;
                    if (in(mx, my, lx + lw - 34, ry, 30, 16)) {
                        if (emeralds() >= menu.get(CaravanMenu.D_FREIGHT_FEE + k)) send(CaravanMenu.B_PARCEL + k);
                        return true;
                    }
                }
            }
            case SERVICES -> {
                if (in(mx, my, x0 + 14 + 48, cy + bh - 22, 160 - 54, 18)) {
                    if (emeralds() >= menu.get(CaravanMenu.D_ESCORT_PRICE)) send(CaravanMenu.B_ESCORT);
                    return true;
                }
                if ((flags() & CaravanMenu.FLAG_AT_STOP) != 0) {
                    int rx = x0 + 180, rw = W - 194;
                    int y = roadsTop(cy, rw), any = 0;
                    for (int k = 0; k < CaravanMenu.DESTS; k++) {
                        if (menu.destination(k).isEmpty()) continue;
                        int ry = y + 2 + any * 21;
                        any++;
                        if (in(mx, my, rx + rw - 34, ry, 30, 16)) {
                            if (emeralds() >= menu.get(CaravanMenu.D_ROAD_PRICE + k)) send(CaravanMenu.B_ROAD + k);
                            return true;
                        }
                    }
                }
            }
            default -> {
                int level = menu.get(CaravanMenu.D_LEVEL), skills = menu.get(CaravanMenu.D_SKILLS), points = menu.get(CaravanMenu.D_POINTS);
                for (Guild.Skill s : Guild.Skill.values()) {
                    int[] p = nodePos(s);
                    if (in(mx, my, p[0], p[1], 26, 26)) {
                        if (blocker(s, level, skills, points) == null) send(CaravanMenu.B_SKILL + s.ordinal());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected boolean scroll(double mx, double my, double amount) {
        if (tab == MARKET || tab == REQUESTS) {
            scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, rows().size() - ROWS));
            return true;
        }
        return false;
    }

    @Override
    protected boolean key(int key) {
        if (key >= 49 && key < 49 + TABS.length) { // 1 to 5
            setTab(key - 49);
            page();
            return true;
        }
        return false;
    }
}
