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
        super(menu, inv, title, TradingPostMenu.WIDTH, TradingPostMenu.HEIGHT);
    }

    private static final String[] ROWS = {"orders", "input", "output"};
    private static final int[] ROW_Y = {TradingPostMenu.ORDERS_Y, TradingPostMenu.INPUT_Y, TradingPostMenu.OUTPUT_Y};
    private static final int[] ROW_ICON = {UiKit.I_SCALES, UiKit.I_DOWN, UiKit.I_UP};

    @Override
    protected void background(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.icon(g, UiKit.I_SCALES, x + 14, y + 12);
        Component head = title.copy().withStyle(ChatFormatting.BOLD);
        UiKit.text(g, font, head, x + 27, y + 13, UiKit.HEAD);
        // The caravan in reach, if any, at the right of the title
        ItemStack caravan = menu.caravanShown();
        if (!caravan.isEmpty()) {
            int room = imageWidth - 14 - (27 + font.width(head) + 8);
            String name = UiKit.fit(font, caravan.getHoverName().getString(), room);
            UiKit.text(g, font, name, x + imageWidth - 14 - font.width(name), y + 13, UiKit.INK_SOFT);
        }
        for (int r = 0; r < 3; r++) {
            UiKit.icon(g, ROW_ICON[r], x + 15, y + ROW_Y[r] - 12);
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.post." + ROWS[r]), x + 28, y + ROW_Y[r] - 11, UiKit.INK);
        }
        slotFrames(g);
        // Status
        int sy = y + TradingPostMenu.OUTPUT_Y + 24;
        Object[] st = status();
        UiKit.icon(g, (Integer) st[0], x + 15, sy - 1);
        UiKit.text(g, font, UiKit.fit(font, ((Component) st[1]).getString(), imageWidth - 44), x + 28, sy, (Integer) st[2]);
        UiKit.separator(g, x + 14, y + TradingPostMenu.INV_Y - 6, imageWidth - 28);
    }

    private Object[] status() {
        return switch (menu.get(TradingPostMenu.D_STATUS)) {
            case TradingPostBlockEntity.TRADING -> new Object[] {UiKit.I_CHECK, Component.translatable("gui.chroniclecaravans.post.trading", menu.get(TradingPostMenu.D_DEALS)), UiKit.GOOD};
            case TradingPostBlockEntity.WAITING -> new Object[] {UiKit.I_CLOCK, Component.translatable("gui.chroniclecaravans.post.nothing"), UiKit.INK_SOFT};
            case TradingPostBlockEntity.PAUSED -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.post.paused"), UiKit.BAD};
            case TradingPostBlockEntity.FULL -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.post.full"), UiKit.BAD};
            case TradingPostBlockEntity.OFF -> new Object[] {UiKit.I_CROSS, Component.translatable("gui.chroniclecaravans.post.off"), UiKit.BAD};
            default -> new Object[] {UiKit.I_CLOCK, Component.translatable("gui.chroniclecaravans.post.idle", menu.get(TradingPostMenu.D_TOTAL)), UiKit.INK_SOFT};
        };
    }

    @Override
    protected void foreground(GuiGraphics g, int mx, int my) {
        for (int r = 0; r < 3; r++) {
            if (in(mx, my, leftPos + 14, topPos + ROW_Y[r] - 13, imageWidth - 28, 11)) {
                tooltip(g, List.of(Component.translatable("gui.chroniclecaravans.post." + ROWS[r]).withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.chroniclecaravans.post." + ROWS[r] + ".help").withStyle(ChatFormatting.GRAY)), mx, my);
            }
        }
    }
}
