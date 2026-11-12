package com.chroniclecaravans.client.screen;

import java.util.List;

import com.chroniclecaravans.block.TradingPostBlockEntity;
import com.chroniclecaravans.menu.TradingPostMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The trading post: three labelled rows (orders, input, output) and what it is doing. */
public class TradingPostScreen extends CCContainerScreen<TradingPostMenu> {
    public TradingPostScreen(TradingPostMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 232);
    }

    private static final String[] ROWS = {"orders", "input", "output"};
    private static final int[] ROW_Y = {TradingPostMenu.ORDERS_Y, TradingPostMenu.INPUT_Y, TradingPostMenu.OUTPUT_Y};
    private static final int[] ROW_ICON = {UiKit.I_SCALES, UiKit.I_DOWN, UiKit.I_UP};

    @Override
    protected void background(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x + 14, y + 13, UiKit.HEAD);
        for (int r = 0; r < 3; r++) {
            UiKit.icon(g, ROW_ICON[r], x + 8, y + ROW_Y[r] - 11);
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.post." + ROWS[r]), x + 20, y + ROW_Y[r] - 10, UiKit.INK);
        }
        slotFrames(g);
        // Status
        int sy = y + TradingPostMenu.OUTPUT_Y + 23;
        Object[] st = status();
        UiKit.icon(g, (Integer) st[0], x + 8, sy - 1);
        UiKit.text(g, font, UiKit.fit(font, ((Component) st[1]).getString(), imageWidth - 30), x + 20, sy, (Integer) st[2]);
        UiKit.separator(g, x + 14, y + TradingPostMenu.INV_Y - 6, imageWidth - 28);
    }

    private Object[] status() {
        ItemStack caravan = menu.caravanShown();
        String name = caravan.isEmpty() ? "?" : caravan.getHoverName().getString();
        return switch (menu.get(TradingPostMenu.D_STATUS)) {
            case TradingPostBlockEntity.TRADING -> new Object[] {UiKit.I_CHECK, Component.translatable("gui.chroniclecaravans.post.trading", name, menu.get(TradingPostMenu.D_DEALS)), UiKit.GOOD};
            case TradingPostBlockEntity.WAITING -> new Object[] {UiKit.I_CLOCK, Component.translatable("gui.chroniclecaravans.post.nothing", name), UiKit.INK_SOFT};
            case TradingPostBlockEntity.PAUSED -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.post.paused"), UiKit.BAD};
            case TradingPostBlockEntity.FULL -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.post.full"), UiKit.BAD};
            case TradingPostBlockEntity.OFF -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.post.off"), UiKit.BAD};
            default -> new Object[] {UiKit.I_CLOCK, Component.translatable("gui.chroniclecaravans.post.idle", menu.get(TradingPostMenu.D_TOTAL)), UiKit.INK_SOFT};
        };
    }

    @Override
    protected void foreground(GuiGraphics g, int mx, int my) {
        for (int r = 0; r < 3; r++) {
            if (in(mx, my, leftPos + 8, topPos + ROW_Y[r] - 12, imageWidth - 16, 10)) {
                tooltip(g, List.of(Component.translatable("gui.chroniclecaravans.post." + ROWS[r]).withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.chroniclecaravans.post." + ROWS[r] + ".help").withStyle(ChatFormatting.GRAY)), mx, my);
            }
        }
    }
}
