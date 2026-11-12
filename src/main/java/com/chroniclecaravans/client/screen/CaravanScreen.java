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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The caravan's interface, in four tabs: the market (click to buy, shift-click to buy as much as you can),
 * the requests (goods the caravan pays for), the services (escort, road crews) and the guild (skill tree).
 */
public class CaravanScreen extends CCScreen implements MenuAccess<CaravanMenu> {
    private static final int W = 340, H = 226, ROW = 22, ROWS = 7;
    public static final int MARKET = 0, REQUESTS = 1, SERVICES = 2, GUILD = 3;

    private final CaravanMenu menu;
    private int tab;
    private int scroll;
    private int x0, y0;

    public CaravanScreen(CaravanMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public CaravanMenu getMenu() {
        return menu;
    }

    public void setTab(int tab) {
        this.tab = Mth.clamp(tab, 0, 3);
        this.scroll = 0;
    }

    @Override
    protected void init() {
        super.init();
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
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

    // ================================================================ drawing

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        frame(g, x0 - 2, y0 - 2, W + 4, H + 4, DARK, BROWN);
        frame(g, x0, y0, W, H, PANEL, GOLD);
        // Header: name, region, what the caravan is doing
        Region region = Region.values()[Mth.clamp(menu.get(CaravanMenu.D_REGION), 0, Region.values().length - 1)];
        text(g, title.copy().withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 7, GOLD);
        Component from = Component.translatable("gui.chroniclecaravans.from", region.label());
        text(g, from, x0 + W - 8 - font.width(from), y0 + 7, TEXT);
        text(g, status(), x0 + 8, y0 + 19, MUTED);
        // Tabs
        String[] names = {"market", "requests", "services", "guild"};
        int tw = (W - 16) / 4;
        for (int i = 0; i < 4; i++) {
            int tx = x0 + 8 + i * tw, ty = y0 + 31;
            boolean hover = in(mx, my, tx, ty, tw - 2, 15);
            frame(g, tx, ty, tw - 2, 15, i == tab ? 0xFF5A3F1E : hover ? 0xFF3A2C1C : 0xFF2E2318, i == tab ? GOLD : BROWN);
            Component label = Component.translatable("gui.chroniclecaravans.tab." + names[i]);
            if (i == GUILD && menu.get(CaravanMenu.D_POINTS) > 0) label = label.copy().append(Component.literal(" ●").withStyle(ChatFormatting.GREEN));
            text(g, label, tx + (tw - 2 - font.width(label)) / 2, ty + 4, i == tab ? GOLD : TEXT);
        }
        int cy = y0 + 52;
        List<Component> tip = null;
        ItemStack tipStack = ItemStack.EMPTY;
        switch (tab) {
            case MARKET, REQUESTS -> {
                Object[] t = drawOffers(g, cy, mx, my);
                tip = (List<Component>) t[0];
                tipStack = (ItemStack) t[1];
            }
            case SERVICES -> tip = drawServices(g, cy, mx, my);
            default -> tip = drawGuild(g, cy, mx, my);
        }
        if (!tipStack.isEmpty()) tooltip(g, tipStack, mx, my);
        else if (tip != null) tooltip(g, tip, mx, my);
    }

    private Component status() {
        int state = menu.get(CaravanMenu.D_STATE);
        if ((menu.get(CaravanMenu.D_FLAGS) & CaravanMenu.FLAG_TO_INN) != 0) return Component.translatable("gui.chroniclecaravans.state.to_inn");
        return switch (state) {
            case 1 -> {
                ItemStack stop = menu.currentStop();
                int s = menu.get(CaravanMenu.D_HALT);
                yield Component.translatable("gui.chroniclecaravans.state.halted", stop.isEmpty() ? Component.literal("?") : stop.getHoverName(),
                        String.format("%d:%02d", s / 60, s % 60));
            }
            case 2 -> Component.translatable("gui.chroniclecaravans.state.lodging");
            case 3 -> Component.translatable("gui.chroniclecaravans.state.leaving");
            default -> Component.translatable("gui.chroniclecaravans.state.traveling");
        };
    }

    private Object[] drawOffers(GuiGraphics g, int cy, int mx, int my) {
        List<Integer> rows = rows();
        List<Component> tip = null;
        ItemStack tipStack = ItemStack.EMPTY;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
        if (rows.isEmpty()) {
            text(g, Component.translatable("gui.chroniclecaravans.empty"), x0 + 12, cy + 6, MUTED);
        }
        int rx = x0 + 8, rw = W - 16 - 6;
        for (int r = 0; r < ROWS && r + scroll < rows.size(); r++) {
            int i = rows.get(r + scroll);
            int ry = cy + r * ROW;
            int lock = menu.lock(i);
            int left = menu.stockLeft(i);
            boolean hover = in(mx, my, rx, ry, rw, ROW - 2);
            frame(g, rx, ry, rw, ROW - 2, lock != 0 ? 0xFF1E1812 : hover ? 0xFF4A3822 : 0xFF33271A, hover ? GOLD : 0xFF4A3A26);
            ItemStack a = menu.costA(i), b = menu.costB(i), res = menu.result(i);
            int can = affordable(i);
            if (tab == MARKET) {
                item(g, res, rx + 3, ry + 2);
                int nameColor = menu.kind(i) == Region.KIND_TREASURE ? 0xFFD49CFF : TEXT;
                text(g, fit(res.getHoverName().getString(), 132), rx + 23, ry + 6, lock != 0 ? MUTED : nameColor);
                int px = rx + 162;
                text(g, "←", px - 10, ry + 6, MUTED);
                item(g, a, px, ry + 2);
                if (!b.isEmpty()) {
                    text(g, "+", px + 18, ry + 6, MUTED);
                    item(g, b, px + 26, ry + 2);
                }
                if (in(mx, my, rx + 3, ry + 2, 16, 16)) tipStack = res;
                else if (in(mx, my, px, ry + 2, 16, 16)) tipStack = a;
                else if (!b.isEmpty() && in(mx, my, px + 26, ry + 2, 16, 16)) tipStack = b;
            } else {
                item(g, a, rx + 3, ry + 2);
                text(g, fit(a.getHoverName().getString(), 110), rx + 23, ry + 2, lock != 0 ? MUTED : TEXT);
                int have = Trading.count(inv(), a.getItem());
                text(g, Component.translatable("gui.chroniclecaravans.have", have), rx + 23, ry + 11, have >= a.getCount() ? GREEN : MUTED);
                int px = rx + 162;
                text(g, "→", px - 10, ry + 6, MUTED);
                item(g, res, px, ry + 2);
                if (in(mx, my, rx + 3, ry + 2, 16, 16)) tipStack = a;
                else if (in(mx, my, px, ry + 2, 16, 16)) tipStack = res;
            }
            // Right side: stock, or what is missing
            Component right;
            int color;
            if (lock == 15) {
                right = Component.translatable("gui.chroniclecaravans.needs_skill", Guild.Skill.HIDDEN_STOCK.label());
                color = 0xFFD49CFF;
            } else if (lock != 0) {
                right = Component.translatable("gui.chroniclecaravans.needs_level", lock);
                color = RED;
            } else if (left <= 0) {
                right = Component.translatable("gui.chroniclecaravans.sold_out");
                color = MUTED;
            } else {
                right = Component.translatable("gui.chroniclecaravans.left", left);
                color = can > 0 ? GREEN : RED;
            }
            text(g, right, rx + rw - 6 - font.width(right), ry + 6, color);
            if (hover && tipStack.isEmpty()) {
                tip = new ArrayList<>();
                if (lock == 0 && left > 0) {
                    tip.add(Component.translatable(can > 0 ? "gui.chroniclecaravans.click_buy" : "gui.chroniclecaravans.cannot_afford").withStyle(can > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (can > 1) tip.add(Component.translatable("gui.chroniclecaravans.shift_buy", can).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        // Scroll bar
        if (rows.size() > ROWS) {
            int bx = x0 + W - 12, bh = ROWS * ROW - 2;
            g.fill(bx, cy, bx + 3, cy + bh, 0xFF1A140E);
            int kh = Math.max(12, bh * ROWS / rows.size());
            int ky = cy + (bh - kh) * scroll / Math.max(1, rows.size() - ROWS);
            g.fill(bx, ky, bx + 3, ky + kh, GOLD);
        }
        Component hint = Component.translatable(tab == MARKET ? "gui.chroniclecaravans.hint_market" : "gui.chroniclecaravans.hint_requests");
        text(g, hint, x0 + 8, y0 + H - 13, MUTED);
        return new Object[] {tip, tipStack};
    }

    private void button(GuiGraphics g, int x, int y, int w, Component label, boolean enabled, int mx, int my) {
        boolean hover = enabled && in(mx, my, x, y, w, 16);
        frame(g, x, y, w, 16, !enabled ? 0xFF2A2420 : hover ? 0xFF6B4A22 : 0xFF4E361A, enabled ? GOLD : 0xFF4A3A26);
        text(g, label, x + (w - font.width(label)) / 2, y + 4, enabled ? TEXT : MUTED);
    }

    private void wrap(GuiGraphics g, Component c, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(c, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
    }

    private int emeralds() {
        return Trading.count(inv(), Items.EMERALD);
    }

    private List<Component> drawServices(GuiGraphics g, int cy, int mx, int my) {
        List<Component> tip = null;
        // Escort
        int bx = x0 + 8, bw = 158, bh = H - 64;
        frame(g, bx, cy, bw, bh, 0xFF33271A, BROWN);
        item(g, new ItemStack(Items.IRON_SWORD), bx + 6, cy + 5);
        text(g, Component.translatable("gui.chroniclecaravans.escort").withStyle(ChatFormatting.BOLD), bx + 26, cy + 9, GOLD);
        int guards = menu.get(CaravanMenu.D_ESCORT_COUNT), minutes = menu.get(CaravanMenu.D_ESCORT_MINUTES);
        int price = menu.get(CaravanMenu.D_ESCORT_PRICE);
        wrap(g, Component.translatable("gui.chroniclecaravans.escort_desc", guards, minutes), bx + 6, cy + 26, bw - 12, TEXT);
        int left = menu.get(CaravanMenu.D_ESCORT_LEFT);
        if (left > 0) {
            text(g, Component.translatable("gui.chroniclecaravans.escort_active", menu.get(CaravanMenu.D_GUARDS), String.format("%d:%02d", left / 60, left % 60)),
                    bx + 6, cy + bh - 46, GREEN);
        }
        item(g, new ItemStack(Items.EMERALD, price), bx + 6, cy + bh - 22);
        boolean canEscort = emeralds() >= price && menu.get(CaravanMenu.D_STATE) != 3;
        button(g, bx + 28, cy + bh - 22, bw - 34, Component.translatable(left > 0 ? "gui.chroniclecaravans.extend" : "gui.chroniclecaravans.hire"), canEscort, mx, my);
        // Road crews
        int rx = x0 + 174, rw = W - 182;
        frame(g, rx, cy, rw, bh, 0xFF33271A, BROWN);
        item(g, new ItemStack(Items.IRON_SHOVEL), rx + 6, cy + 5);
        text(g, Component.translatable("gui.chroniclecaravans.roads").withStyle(ChatFormatting.BOLD), rx + 26, cy + 9, GOLD);
        if ((menu.get(CaravanMenu.D_FLAGS) & CaravanMenu.FLAG_AT_STOP) == 0) {
            wrap(g, Component.translatable("gui.chroniclecaravans.roads_not_here"), rx + 6, cy + 28, rw - 12, MUTED);
        } else {
            wrap(g, Component.translatable("gui.chroniclecaravans.roads_from", menu.currentStop().getHoverName()), rx + 6, cy + 26, rw - 12, TEXT);
            int any = 0;
            for (int k = 0; k < CaravanMenu.DESTS; k++) {
                ItemStack d = menu.destination(k);
                if (d.isEmpty()) continue;
                int ry = cy + 46 + any * 26;
                any++;
                int p = menu.get(CaravanMenu.D_ROAD_PRICE + k), dist = menu.get(CaravanMenu.D_ROAD_DIST + k);
                text(g, fit(d.getHoverName().getString(), rw - 60), rx + 6, ry, TEXT);
                text(g, Component.translatable("gui.chroniclecaravans.blocks", dist), rx + 6, ry + 10, MUTED);
                item(g, new ItemStack(Items.EMERALD, p), rx + rw - 64, ry);
                button(g, rx + rw - 44, ry, 38, Component.translatable("gui.chroniclecaravans.build"), emeralds() >= p, mx, my);
            }
            if (any == 0) wrap(g, Component.translatable("gui.chroniclecaravans.roads_none"), rx + 6, cy + 48, rw - 12, MUTED);
        }
        text(g, Component.translatable("gui.chroniclecaravans.hint_services"), x0 + 8, y0 + H - 13, MUTED);
        return tip;
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
        int tx = x0 + 132, tw = W - 140;
        int colW = tw / 3;
        int cx = tx + s.branch.ordinal() * colW + colW / 2 + s.col * 20;
        int cy = y0 + 52 + 26 + s.row * 44;
        return new int[] {cx - 11, cy - 11};
    }

    private List<Component> drawGuild(GuiGraphics g, int cy, int mx, int my) {
        List<Component> tip = null;
        int xp = menu.get(CaravanMenu.D_XP), skills = menu.get(CaravanMenu.D_SKILLS);
        int level = menu.get(CaravanMenu.D_LEVEL), points = menu.get(CaravanMenu.D_POINTS);
        int lx = x0 + 8, lw = 118;
        frame(g, lx, cy, lw, H - 64, 0xFF33271A, BROWN);
        wrap(g, Guild.title(xp, skills).withStyle(ChatFormatting.BOLD), lx + 6, cy + 6, lw - 12, GOLD);
        text(g, Component.translatable("gui.chroniclecaravans.level", level), lx + 6, cy + 30, TEXT);
        int need = Math.max(1, menu.get(CaravanMenu.D_NEEDED)), prog = menu.get(CaravanMenu.D_PROGRESS);
        g.fill(lx + 6, cy + 42, lx + lw - 6, cy + 47, 0xFF1A140E);
        if (level < Guild.MAX_LEVEL) g.fill(lx + 6, cy + 42, lx + 6 + (lw - 12) * Mth.clamp(prog, 0, need) / need, cy + 47, GREEN);
        else g.fill(lx + 6, cy + 42, lx + lw - 6, cy + 47, GOLD);
        text(g, level < Guild.MAX_LEVEL ? prog + " / " + need + " xp" : "max", lx + 6, cy + 50, MUTED);
        text(g, Component.translatable("gui.chroniclecaravans.points", points), lx + 6, cy + 64, points > 0 ? GREEN : MUTED);
        if ((menu.get(CaravanMenu.D_FLAGS) & CaravanMenu.FLAG_REPUTATION) == 0) {
            wrap(g, Component.translatable("msg.chroniclecaravans.rep_disabled"), lx + 6, cy + 80, lw - 12, MUTED);
        } else {
            wrap(g, Component.translatable("gui.chroniclecaravans.guild_help"), lx + 6, cy + 80, lw - 12, MUTED);
        }
        // Tree
        int tx = x0 + 132, tw = W - 140;
        frame(g, tx, cy, tw, H - 64, 0xFF2E2318, BROWN);
        int colW = tw / 3;
        for (Guild.Branch b : Guild.Branch.values()) {
            Component label = b.label();
            text(g, label, tx + b.ordinal() * colW + (colW - font.width(label)) / 2, cy + 4, 0xFFFFFFFF);
        }
        for (Guild.Skill s : Guild.Skill.values()) {
            if (s.parent == null) continue;
            int[] a = nodePos(s.parent), b = nodePos(s);
            int ax = a[0] + 11, ay = a[1] + 11, bxx = b[0] + 11, by = b[1] + 11;
            int col = Guild.has(skills, s) ? GOLD : 0xFF5A4A36;
            int midY = (ay + by) / 2;
            g.fill(ax - 1, ay, ax + 1, midY + 1, col);
            g.fill(Math.min(ax, bxx) - 1, midY - 1, Math.max(ax, bxx) + 1, midY + 1, col);
            g.fill(bxx - 1, midY, bxx + 1, by, col);
        }
        for (Guild.Skill s : Guild.Skill.values()) {
            int[] p = nodePos(s);
            boolean have = Guild.has(skills, s);
            String block = blocker(s, level, skills, points);
            boolean can = block == null;
            boolean hover = in(mx, my, p[0], p[1], 22, 22);
            int fill = have ? 0xFF3E5A2A : can ? (hover ? 0xFF6B4A22 : 0xFF4E361A) : 0xFF231B13;
            frame(g, p[0], p[1], 22, 22, fill, have ? GOLD : can ? 0xFF9AE07A : 0xFF4A3A26);
            item(g, icon(s), p[0] + 3, p[1] + 3);
            if (!have && !can) g.fill(p[0] + 1, p[1] + 1, p[0] + 21, p[1] + 21, 0x90000000);
            if (hover) {
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
        int tw = (W - 16) / 4;
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, x0 + 8 + i * tw, y0 + 31, tw - 2, 15)) {
                if (tab != i) {
                    setTab(i);
                    ding();
                }
                return true;
            }
        }
        int cy = y0 + 52;
        switch (tab) {
            case MARKET, REQUESTS -> {
                List<Integer> rows = rows();
                for (int r = 0; r < ROWS && r + scroll < rows.size(); r++) {
                    if (in(mx, my, x0 + 8, cy + r * ROW, W - 22, ROW - 2)) {
                        int i = rows.get(r + scroll);
                        if (menu.lock(i) != 0 || affordable(i) <= 0) return true;
                        send((shift ? CaravanMenu.B_BUY_ALL : CaravanMenu.B_BUY) + i);
                        return true;
                    }
                }
            }
            case SERVICES -> {
                int bh = H - 64;
                if (in(mx, my, x0 + 8 + 28, cy + bh - 22, 158 - 34, 16)) {
                    if (emeralds() >= menu.get(CaravanMenu.D_ESCORT_PRICE)) send(CaravanMenu.B_ESCORT);
                    return true;
                }
                int rx = x0 + 174, rw = W - 182, any = 0;
                for (int k = 0; k < CaravanMenu.DESTS; k++) {
                    if (menu.destination(k).isEmpty()) continue;
                    int ry = cy + 46 + any * 26;
                    any++;
                    if (in(mx, my, rx + rw - 44, ry, 38, 16)) {
                        if (emeralds() >= menu.get(CaravanMenu.D_ROAD_PRICE + k)) send(CaravanMenu.B_ROAD + k);
                        return true;
                    }
                }
            }
            default -> {
                int level = menu.get(CaravanMenu.D_LEVEL), skills = menu.get(CaravanMenu.D_SKILLS), points = menu.get(CaravanMenu.D_POINTS);
                for (Guild.Skill s : Guild.Skill.values()) {
                    int[] p = nodePos(s);
                    if (in(mx, my, p[0], p[1], 22, 22)) {
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
        if (key >= 49 && key <= 52) { // 1 to 4
            setTab(key - 49);
            return true;
        }
        return false;
    }
}
