package com.chroniclecaravans.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.entity.TownMayor;
import com.chroniclecaravans.menu.TownMenu;
import com.chroniclecaravans.world.Buildings;
import com.chroniclecaravans.world.Town;
import com.chroniclecaravans.world.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The town's ledger: the mayor, the town's level and prosperity, its treasury; the works under way and what they
 * still need; the buildings; what the town brings.
 */
public class TownScreen extends CCScreen implements MenuAccess<TownMenu> {
    private static final int W = 352, H = 236, LW = 190;
    private static final String[] NUMERALS = {"", "I", "II", "III", "IV"};

    private final TownMenu menu;
    private int x0, y0;

    public TownScreen(TownMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public TownMenu getMenu() {
        return menu;
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

    private int flags() {
        return menu.get(TownMenu.D_FLAGS);
    }

    private void cardTitle(GuiGraphics g, int icon, Component title, int x, int y) {
        UiKit.icon(g, icon, x, y);
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x + 13, y + 1, UiKit.HEAD);
    }

    private static Component levelName(int level) {
        return Component.translatable("town.chroniclecaravans.level." + Math.max(1, Math.min(Towns.MAX_LEVEL, level)));
    }

    private static Component building(Buildings.Type t) {
        return Component.translatable("building.chroniclecaravans." + t.key());
    }

    /** What the town brings, as it stands. */
    private List<Component> benefits() {
        List<Component> out = new ArrayList<>();
        int level = menu.get(TownMenu.D_LEVEL), stalls = menu.get(TownMenu.D_STALLS);
        out.add(Component.translatable("gui.chroniclecaravans.town.benefit.visits", 25 * level));
        out.add(Component.translatable("gui.chroniclecaravans.town.benefit.room", level));
        out.add(Component.translatable("gui.chroniclecaravans.town.benefit.dues", level));
        if (stalls > 0) out.add(Component.translatable("gui.chroniclecaravans.town.benefit.market", 4 * stalls));
        if ((flags() & TownMenu.FLAG_WAREHOUSE) != 0) out.add(Component.translatable("gui.chroniclecaravans.town.benefit.warehouse"));
        if ((flags() & TownMenu.FLAG_INN) != 0) out.add(Component.translatable("gui.chroniclecaravans.town.benefit.inn"));
        if ((flags() & TownMenu.FLAG_STABLE) != 0) out.add(Component.translatable("gui.chroniclecaravans.town.benefit.stable"));
        if ((flags() & TownMenu.FLAG_WATCH) != 0) out.add(Component.translatable("gui.chroniclecaravans.town.benefit.watch", Towns.WATCH_RANGE));
        return out;
    }

    // ================================================================ drawing

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        UiKit.panel(g, x0, y0, W, H);
        List<Component> tip = header(g, mx, my);
        UiKit.separator(g, x0 + 14, y0 + 63, W - 28);
        int cy = y0 + 68;
        List<Component> t2 = works(g, cy, mx, my);
        if (t2 != null) tip = t2;
        List<Component> t3 = buildings(g, cy, mx, my);
        if (t3 != null) tip = t3;
        footer(g);
        if (tip != null && !tip.isEmpty()) tooltip(g, tip, mx, my);
    }

    private List<Component> header(GuiGraphics g, int mx, int my) {
        List<Component> tip = null;
        // The mayor's portrait (or the milestone, before there is a town hall)
        UiKit.inset(g, x0 + 14, y0 + 13, 40, 46);
        TownMayor mayor = null;
        if (minecraft.level != null && minecraft.player != null) {
            double best = Double.MAX_VALUE;
            for (TownMayor m : minecraft.level.getEntitiesOfClass(TownMayor.class, minecraft.player.getBoundingBox().inflate(48))) {
                double d = m.distanceToSqr(minecraft.player);
                if (d < best) {
                    best = d;
                    mayor = m;
                }
            }
        }
        if (mayor != null) UiKit.portrait(g, x0 + 16, y0 + 15, x0 + 52, y0 + 57, 19, mx, my, mayor);
        else g.renderItem(menu.shown(TownMenu.S_NAME), x0 + 26, y0 + 28);
        int level = menu.get(TownMenu.D_LEVEL);
        UiKit.text(g, font, UiKit.fit(font, title.getString(), W - 196), x0 + 60, y0 + 15, UiKit.HEAD);
        Component sub = Component.translatable("gui.chroniclecaravans.town.subtitle", levelName(level), menu.shown(TownMenu.S_FOUNDER).getHoverName().getString());
        UiKit.text(g, font, UiKit.fit(font, sub.getString(), W - 196), x0 + 60, y0 + 27, UiKit.INK);
        // Prosperity towards the next level
        int p = menu.get(TownMenu.D_PROSPERITY), next = menu.get(TownMenu.D_NEXT);
        UiKit.bar(g, x0 + 60, y0 + 40, 150, next == 0 ? 1 : p, next == 0 ? 1 : next, true);
        Component prog = next == 0 ? Component.translatable("gui.chroniclecaravans.town.complete")
                : Component.translatable("gui.chroniclecaravans.town.prosperity", p, next, levelName(level + 1));
        UiKit.text(g, font, UiKit.fit(font, prog.getString(), 150), x0 + 60, y0 + 48, UiKit.INK_SOFT);
        if (in(mx, my, x0 + 58, y0 + 38, 154, 20)) {
            tip = List.of(Component.translatable("gui.chroniclecaravans.town.prosperity_tip").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.chroniclecaravans.town.prosperity_how"));
        }
        // The town's level, on a seal: what it brings
        UiKit.seal(g, font, x0 + W - 46, y0 + 14, NUMERALS[Math.max(1, Math.min(4, level))]);
        if (in(mx, my, x0 + W - 46, y0 + 14, 28, 28)) {
            tip = new ArrayList<>();
            tip.add(levelName(level).copy().withStyle(ChatFormatting.GOLD));
            tip.addAll(benefits());
        }
        // Treasury, residents
        int treasury = menu.get(TownMenu.D_TREASURY);
        int tx = x0 + W - 128;
        g.renderItem(new ItemStack(Items.EMERALD), tx, y0 + 12);
        UiKit.text(g, font, "×" + treasury, tx + 17, y0 + 17, UiKit.INK);
        if (in(mx, my, tx, y0 + 12, 50, 16)) tip = List.of(Component.translatable("gui.chroniclecaravans.town.treasury_tip", treasury));
        boolean owner = (flags() & TownMenu.FLAG_OWNER) != 0;
        boolean wHover = owner && treasury > 0 && in(mx, my, tx, y0 + 30, 50, 14);
        UiKit.button(g, font, tx, y0 + 30, 50, 14, Component.translatable("gui.chroniclecaravans.town.withdraw"),
                owner && treasury > 0 ? (wHover ? UiKit.BUTTON_HOVER : UiKit.BUTTON) : UiKit.BUTTON_OFF);
        if (in(mx, my, tx, y0 + 30, 50, 14)) {
            tip = List.of(Component.translatable(owner ? "gui.chroniclecaravans.town.withdraw_tip" : "gui.chroniclecaravans.town.owner_only"));
        }
        UiKit.icon(g, UiKit.I_STAR, tx, y0 + 48);
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.town.residents", menu.get(TownMenu.D_RESIDENTS)), tx + 13, y0 + 48, UiKit.INK);
        return tip;
    }

    /** The works under way: what the building still needs, and how to bring it. */
    private List<Component> works(GuiGraphics g, int cy, int mx, int my) {
        int lx = x0 + 14, bh = H - 96;
        List<Component> tip = null;
        UiKit.card(g, lx, cy, LW, bh, UiKit.CARD);
        cardTitle(g, UiKit.I_ROAD, Component.translatable("gui.chroniclecaravans.town.works"), lx + 6, cy + 6);
        int site = menu.get(TownMenu.D_SITE) - 1;
        if (site < 0) {
            int next = menu.get(TownMenu.D_NEXT);
            Component c = next == 0 ? Component.translatable("gui.chroniclecaravans.town.all_built_max")
                    : Component.translatable("gui.chroniclecaravans.town.all_built", next, levelName(menu.get(TownMenu.D_LEVEL) + 1));
            UiKit.wrap(g, font, c, lx + 6, cy + 22, LW - 12, UiKit.INK_SOFT);
            return null;
        }
        Buildings.Type type = menu.buildingType(site);
        int state = menu.buildingState(site);
        g.renderItem(new ItemStack(type.icon()), lx + 6, cy + 20);
        UiKit.text(g, font, building(type).copy().withStyle(ChatFormatting.BOLD), lx + 26, cy + 21, UiKit.INK);
        if (state == Town.BUILDING) {
            int pc = menu.buildingPercent(site);
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.town.building", pc), lx + 26, cy + 31, UiKit.GOOD);
            UiKit.bar(g, lx + 6, cy + 46, LW - 12, pc, 100, true);
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.town.builders"), lx + 6, cy + 58, LW - 12, UiKit.INK_SOFT);
            return null;
        }
        if (menu.noPlot(site)) {
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.town.no_plot_short"), lx + 26, cy + 31, UiKit.BAD);
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.town.no_plot"), lx + 6, cy + 44, LW - 12, UiKit.INK_SOFT);
            return null;
        }
        UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.town.waiting"), lx + 26, cy + 31, UiKit.INK_SOFT);
        // What it needs: two columns
        int k = 0;
        for (Buildings.Material m : Buildings.Material.values()) {
            int need = menu.get(TownMenu.D_NEED + m.ordinal());
            if (need <= 0) continue;
            int got = menu.get(TownMenu.D_GOT + m.ordinal());
            int mx0 = lx + 6 + (k % 2) * 92, my0 = cy + 44 + (k / 2) * 18;
            g.renderItem(new ItemStack(m.icon), mx0, my0);
            UiKit.text(g, font, got + " / " + need, mx0 + 19, my0 + 5, got >= need ? UiKit.GOOD : UiKit.INK);
            if (in(mx, my, mx0, my0, 88, 16)) tip = List.of(Component.translatable("material.chroniclecaravans." + m.name().toLowerCase(java.util.Locale.ROOT)),
                    Component.translatable("gui.chroniclecaravans.town.material_tip").withStyle(ChatFormatting.GRAY));
            k++;
        }
        // Hand it over, or let the caravans bring it
        int by = cy + bh - 22;
        boolean dHover = in(mx, my, lx + 6, by, 76, 16);
        UiKit.button(g, font, lx + 6, by, 76, 16, Component.translatable("gui.chroniclecaravans.town.deliver"), dHover ? UiKit.BUTTON_HOVER : UiKit.BUTTON);
        if (dHover) tip = List.of(Component.translatable("gui.chroniclecaravans.town.deliver_tip"));
        boolean supply = (flags() & TownMenu.FLAG_SUPPLY) != 0, owner = (flags() & TownMenu.FLAG_OWNER) != 0;
        boolean sHover = owner && in(mx, my, lx + 86, by, LW - 92, 16);
        UiKit.button(g, font, lx + 86, by, LW - 92, 16, Component.translatable(supply ? "gui.chroniclecaravans.town.supply_on" : "gui.chroniclecaravans.town.supply_off"),
                owner ? (sHover ? UiKit.BUTTON_HOVER : UiKit.BUTTON) : UiKit.BUTTON_OFF);
        if (in(mx, my, lx + 86, by, LW - 92, 16)) {
            tip = List.of(Component.translatable("gui.chroniclecaravans.town.supply_tip", Towns.SUPPLY_BATCH, Towns.ITEMS_PER_EMERALD));
        }
        UiKit.text(g, font, UiKit.fit(font, Component.translatable("gui.chroniclecaravans.town.crate").getString(), LW - 12), lx + 6, by - 12, UiKit.INK_SOFT);
        return tip;
    }

    /** Every building of the town, with how far along it is. */
    private List<Component> buildings(GuiGraphics g, int cy, int mx, int my) {
        int rx = x0 + 14 + LW + 6, rw = W - 28 - LW - 6, bh = H - 96;
        List<Component> tip = null;
        UiKit.card(g, rx, cy, rw, bh, UiKit.CARD);
        cardTitle(g, UiKit.I_STAR, Component.translatable("gui.chroniclecaravans.town.buildings"), rx + 6, cy + 6);
        int n = menu.get(TownMenu.D_COUNT);
        int y = cy + 20;
        for (int k = 0; k < n && y <= cy + bh - 10; k++) {
            int state = menu.buildingState(k);
            int icon = state == Town.DONE ? UiKit.I_CHECK : state == Town.BUILDING ? UiKit.I_ROAD : UiKit.I_CLOCK;
            UiKit.icon(g, icon, rx + 6, y - 1);
            String name = building(menu.buildingType(k)).getString();
            if (state != Town.DONE) name += " " + menu.buildingPercent(k) + "%";
            UiKit.text(g, font, UiKit.fit(font, name, rw - 22), rx + 18, y, state == Town.DONE ? UiKit.GOOD : UiKit.INK);
            if (in(mx, my, rx + 4, y - 1, rw - 8, 9)) {
                tip = List.of(building(menu.buildingType(k)).copy().withStyle(ChatFormatting.GOLD),
                        Component.translatable("building.chroniclecaravans." + menu.buildingType(k).key() + ".desc"));
            }
            y += 9;
        }
        return tip;
    }

    private void footer(GuiGraphics g) {
        int fy = y0 + H - 22;
        MutableComponent line = Component.translatable("gui.chroniclecaravans.town.hint");
        UiKit.icon(g, UiKit.I_STAR, x0 + 14, fy - 1);
        UiKit.text(g, font, UiKit.fit(font, line.getString(), W - 44), x0 + 27, fy, UiKit.GOLD);
    }

    // ================================================================ input

    @Override
    protected boolean click(double mx, double my, int button, boolean shift) {
        int cy = y0 + 68, bh = H - 96, lx = x0 + 14;
        int tx = x0 + W - 128;
        if (in(mx, my, tx, y0 + 30, 50, 14)) {
            send(TownMenu.B_WITHDRAW);
            ding();
            return true;
        }
        int site = menu.get(TownMenu.D_SITE) - 1;
        if (site >= 0 && menu.buildingState(site) == Town.WAITING && !menu.noPlot(site)) {
            int by = cy + bh - 22;
            if (in(mx, my, lx + 6, by, 76, 16)) {
                send(TownMenu.B_DELIVER);
                ding();
                return true;
            }
            if (in(mx, my, lx + 86, by, LW - 92, 16)) {
                send(TownMenu.B_SUPPLY);
                ding();
                return true;
            }
        }
        return false;
    }
}
