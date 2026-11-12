package com.chroniclecaravans.client.screen;

import java.util.List;

import com.chroniclecaravans.block.DockBlockEntity;
import com.chroniclecaravans.menu.DockMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The loading dock: nine slots, its address (the stop it sends to; empty = it receives), the freight price
 * and how the last shipment went. Click the address to change it.
 */
public class DockScreen extends CCContainerScreen<DockMenu> {
    private StringBuilder editing;

    public DockScreen(DockMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, DockMenu.WIDTH, DockMenu.HEIGHT);
    }

    private boolean outgoing() {
        return menu.get(DockMenu.D_OUT) != 0;
    }

    private int boxX() {
        return leftPos + 14;
    }

    private int boxY() {
        return topPos + 36;
    }

    /** Where the number of shipments is shown (left of the grid). */
    private int shipX() {
        return leftPos + 22;
    }

    private int shipY() {
        return topPos + DockMenu.GRID_Y + 19;
    }

    @Override
    protected void background(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.icon(g, outgoing() ? UiKit.I_UP : UiKit.I_DOWN, x + 14, y + 12);
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x + 27, y + 13, UiKit.HEAD);
        // The stop it belongs to, on its own line
        ItemStack stop = menu.stopShown();
        String at = stop.isEmpty() ? Component.translatable("gui.chroniclecaravans.dock.no_stop").getString()
                : Component.translatable("gui.chroniclecaravans.dock.at", stop.getHoverName().getString()).getString();
        UiKit.text(g, font, UiKit.fit(font, at, imageWidth - 41), x + 27, y + 24, stop.isEmpty() ? UiKit.BAD : UiKit.INK_SOFT);
        // Address box
        UiKit.inset(g, boxX(), boxY(), imageWidth - 28, 14);
        String shown;
        int color;
        if (editing != null) {
            shown = editing + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
            color = 0xFFFFF4DC;
        } else if (outgoing()) {
            shown = "→ " + menu.address();
            color = 0xFFF6E2A8;
        } else {
            shown = Component.translatable("gui.chroniclecaravans.dock.receiving").getString();
            color = 0xFFB8D8A0;
        }
        while (font.width(shown) > imageWidth - 36 && shown.length() > 1) shown = shown.substring(1);
        UiKit.text(g, font, shown, boxX() + 4, boxY() + 3, color);
        slotFrames(g);
        // Right of the grid: the price and the legs
        int rx = x + DockMenu.GRID_X + 58;
        if (outgoing() && menu.get(DockMenu.D_HOPS) >= 0) {
            g.renderItem(new ItemStack(Items.EMERALD), rx, y + DockMenu.GRID_Y);
            UiKit.text(g, font, "×" + menu.get(DockMenu.D_FEE), rx + 17, y + DockMenu.GRID_Y + 5, UiKit.INK);
            String legs = Component.translatable("gui.chroniclecaravans.dock.legs", menu.get(DockMenu.D_HOPS)).getString();
            UiKit.text(g, font, UiKit.fit(font, legs, x + imageWidth - 13 - rx), rx, y + DockMenu.GRID_Y + 22, UiKit.INK_SOFT);
        }
        // Left of the grid: the shipments so far
        UiKit.icon(g, UiKit.I_PARCEL, shipX(), shipY());
        UiKit.text(g, font, "×" + menu.get(DockMenu.D_SHIPMENTS), shipX() + 12, shipY() + 1, UiKit.INK_SOFT);
        // Status
        int sy = y + DockMenu.GRID_Y + 59;
        Object[] st = status();
        UiKit.icon(g, (Integer) st[0], x + 14, sy - 1);
        UiKit.text(g, font, UiKit.fit(font, ((Component) st[1]).getString(), imageWidth - 42), x + 27, sy, (Integer) st[2]);
        UiKit.separator(g, x + 14, y + DockMenu.INV_Y - 6, imageWidth - 28);
    }

    /** {icon, text, colour} of the dock's state. */
    private Object[] status() {
        if (menu.get(DockMenu.D_HAS_STOP) == 0) return new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.dock.no_stop_long"), UiKit.BAD};
        if (!outgoing()) return new Object[] {UiKit.I_DOWN, Component.translatable("gui.chroniclecaravans.dock.status.receiving"), UiKit.INK_SOFT};
        if (menu.get(DockMenu.D_HOPS) < 0) return new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.dock.status.no_route"), UiKit.BAD};
        int age = menu.get(DockMenu.D_AGE);
        return switch (menu.get(DockMenu.D_STATUS)) {
            case DockBlockEntity.SHIPPED -> new Object[] {UiKit.I_CHECK, Component.translatable("gui.chroniclecaravans.dock.status.shipped", age), UiKit.GOOD};
            case DockBlockEntity.NO_ADDRESS -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.dock.status.no_address"), UiKit.BAD};
            case DockBlockEntity.NO_ROUTE -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.dock.status.no_route"), UiKit.BAD};
            case DockBlockEntity.NO_FEE -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.dock.status.no_fee"), UiKit.BAD};
            default -> new Object[] {UiKit.I_CLOCK, Component.translatable("gui.chroniclecaravans.dock.status.waiting"), UiKit.INK_SOFT};
        };
    }

    @Override
    protected void foreground(GuiGraphics g, int mx, int my) {
        if (in(mx, my, shipX(), shipY() - 2, 34, 12)) {
            tooltip(g, List.of(Component.translatable("gui.chroniclecaravans.dock.shipments", menu.get(DockMenu.D_SHIPMENTS))), mx, my);
        }
        if (editing == null && in(mx, my, boxX(), boxY(), imageWidth - 28, 14)) {
            tooltip(g, List.of(Component.translatable("gui.chroniclecaravans.dock.address").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.chroniclecaravans.dock.address_help").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.chroniclecaravans.dock.fee_help").withStyle(ChatFormatting.GRAY)), mx, my);
        }
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        if (in(mx, my, boxX(), boxY(), imageWidth - 28, 14)) {
            if (editing == null) editing = new StringBuilder(outgoing() ? menu.address() : "");
            return true;
        }
        if (editing != null) commit();
        return false;
    }

    private void commit() {
        if (editing == null || minecraft.player == null) return;
        String a = editing.toString().strip();
        minecraft.player.connection.sendCommand("caravans dock " + (a.isEmpty() ? "-" : a));
        editing = null;
    }

    @Override
    protected boolean key(int key) {
        if (editing == null) return false;
        switch (key) {
            case 256 -> editing = null;
            case 257, 335 -> commit();
            case 259 -> {
                if (editing.length() > 0) editing.setLength(editing.length() - 1);
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    protected boolean typed(char c) {
        if (editing == null) return false;
        if (c >= ' ' && c != '§' && c != 127 && editing.length() < 32) editing.append(c);
        return true;
    }
}
