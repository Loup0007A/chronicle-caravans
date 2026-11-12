package com.chroniclecaravans.client.screen;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The look of the mod's screens: a ledger in a wooden frame with brass corners, parchment pages, paper cards,
 * leather tabs, wooden buttons. Everything comes from one texture sheet drawn by {@code tools/gui.py}.
 */
public final class UiKit {
    private UiKit() {}

    public static final ResourceLocation TEX = ChronicleCaravans.id("textures/gui/ledger.png");
    private static final int TW = 256, TH = 256;

    // Ink on parchment, and a few accents
    public static final int INK = 0xFF3B2A1A, INK_SOFT = 0xFF75603F, HEAD = 0xFF7A2418, GOOD = 0xFF2E7A30, BAD = 0xFFB0382A,
            GOLD = 0xFF9A6A10, TREASURE = 0xFF7A3FA0, CREAM = 0xFFF6E8C8, CREAM_SOFT = 0xFFD8C49C;

    public static final int CARD = 0, CARD_HOVER = 1, CARD_DIM = 2;
    public static final int BUTTON = 0, BUTTON_HOVER = 1, BUTTON_OFF = 2;
    public static final int MEDAL_LOCKED = 0, MEDAL_OPEN = 1, MEDAL_DONE = 2;
    public static final int I_LOCK = 0, I_ARROW = 1, I_UP = 2, I_DOWN = 3, I_STAR = 4, I_CLOCK = 5, I_PARCEL = 6, I_MOON = 7,
            I_STORM = 8, I_FIRE = 9, I_CHECK = 10, I_CROSS = 11, I_COIN = 12, I_ROAD = 13, I_SHIELD = 14, I_SCALES = 15;
    public static final int PIN_RED = 0, PIN_GOLD = 1, PIN_BLUE = 2, PIN_GREEN = 3;

    // ---------------------------------------------------------------- blitting

    public static void blit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        if (w <= 0 || h <= 0) return;
        //#if mc26
        //$$ g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEX, x, y, (float) u, (float) v, w, h, TW, TH);
        //#else
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(TEX, x, y, (float) u, (float) v, w, h, TW, TH);
        //#endif
    }

    /** Repeats the region (u, v, sw, sh) over the area (x, y, w, h). */
    public static void tiled(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        if (sw <= 0 || sh <= 0) return;
        for (int yy = 0; yy < h; yy += sh) {
            for (int xx = 0; xx < w; xx += sw) {
                blit(g, x + xx, y + yy, u, v, Math.min(sw, w - xx), Math.min(sh, h - yy));
            }
        }
    }

    /** Nine-slice: corners as they are, edges and middle repeated. */
    public static void nine(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh, int b) {
        int mw = sw - 2 * b, mh = sh - 2 * b;
        blit(g, x, y, u, v, b, b);
        blit(g, x + w - b, y, u + sw - b, v, b, b);
        blit(g, x, y + h - b, u, v + sh - b, b, b);
        blit(g, x + w - b, y + h - b, u + sw - b, v + sh - b, b, b);
        tiled(g, x + b, y, w - 2 * b, b, u + b, v, mw, b);
        tiled(g, x + b, y + h - b, w - 2 * b, b, u + b, v + sh - b, mw, b);
        tiled(g, x, y + b, b, h - 2 * b, u, v + b, b, mh);
        tiled(g, x + w - b, y + b, b, h - 2 * b, u + sw - b, v + b, b, mh);
        tiled(g, x + b, y + b, w - 2 * b, h - 2 * b, u + b, v + b, mw, mh);
    }

    // ---------------------------------------------------------------- pieces

    /** Parchment, as large as needed. */
    public static void paper(GuiGraphics g, int x, int y, int w, int h) {
        tiled(g, x, y, w, h, 64, 0, 64, 64);
    }

    /** The ledger: parchment in a wooden frame (border 12), with a soft shadow along the inner edge. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + h, x + w + 3, y + h + 3, 0x50000000);
        g.fill(x + w, y + 3, x + w + 3, y + h, 0x50000000);
        paper(g, x + 8, y + 8, w - 16, h - 16);
        g.fill(x + 12, y + 12, x + w - 12, y + 14, 0x22000000);
        g.fill(x + 12, y + 14, x + 14, y + h - 12, 0x18000000);
        nine(g, x, y, w, h, 0, 0, 48, 48, 12);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, int style) {
        nine(g, x, y, w, h, 128 + 24 * style, 0, 24, 24, 4);
    }

    /** A dark recessed window (portraits, the map). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, x, y, w, h, 200, 0, 24, 24, 4);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean highlight) {
        blit(g, x, y, 224, highlight ? 18 : 0, 18, 18);
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, int style) {
        nine(g, x, y, w, h, 128 + 32 * style, 24, 32, 18, 4);
        int color = style == BUTTON_OFF ? 0xFFC8C0B4 : CREAM;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, style != BUTTON_OFF);
    }

    /** A leather tab above the ledger; the active one is parchment and joins the page. */
    public static void tab(GuiGraphics g, Font font, int x, int y, int w, Component label, boolean active, boolean hover) {
        if (active) nine(g, x, y - 2, w, 24 + 6, 96, 64, 48, 24, 6);
        else nine(g, x, y, w, 22, hover ? 48 : 0, 64, 48, 22, 6);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + (active ? 5 : 7), active ? HEAD : CREAM, !active);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int value, int max, boolean gold) {
        nine(g, x, y, w, 8, 0, 48, 64, 8, 3);
        int fill = max <= 0 ? 0 : (int) ((w - 2) * Math.max(0, Math.min(1.0, value / (double) max)));
        if (fill > 0) tiled(g, x + 1, y + 1, fill, 6, gold ? 160 : 0, gold ? 65 : 57, 64, 6);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int h, int pos, int total, int visible) {
        if (total <= visible) return;
        nine(g, x, y, 8, h, 144, 64, 8, 32, 3);
        int kh = Math.max(12, h * visible / total);
        int ky = y + (h - kh) * pos / Math.max(1, total - visible);
        nine(g, x, ky, 8, kh, 152, 64, 8, 16, 3);
    }

    public static void medal(GuiGraphics g, int x, int y, int state) {
        blit(g, x, y, 26 * state, 90, 26, 26);
    }

    public static void medalHover(GuiGraphics g, int x, int y) {
        blit(g, x, y, 78, 90, 26, 26);
    }

    /** A red wax seal (28×28) with a number on it. */
    public static void seal(GuiGraphics g, Font font, int x, int y, String text) {
        blit(g, x, y, 104, 90, 28, 28);
        g.drawString(font, text, x + 14 - font.width(text) / 2, y + 10, 0xFFFFE8D0, true);
    }

    public static void icon(GuiGraphics g, int which, int x, int y) {
        blit(g, x, y, 16 * which, 128, 10, 10);
    }

    public static void compass(GuiGraphics g, int x, int y) {
        blit(g, x, y, 0, 144, 48, 48);
    }

    public static void pin(GuiGraphics g, int x, int y, int color) {
        blit(g, x - 5, y - 13, 48 + 10 * color, 144, 10, 14);
    }

    /** An ink rule with a small diamond in the middle. */
    public static void separator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0x60593A1E);
        int c = x + w / 2;
        g.fill(c - 2, y - 1, c + 3, y + 2, 0xA0593A1E);
        g.fill(c - 1, y - 2, c + 2, y + 3, 0xA0593A1E);
    }

    // ---------------------------------------------------------------- text

    public static void text(GuiGraphics g, Font font, Component c, int x, int y, int color) {
        g.drawString(font, c, x, y, color, false);
    }

    public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    /** Wrapped text; returns the y below it. */
    public static int wrap(GuiGraphics g, Font font, Component c, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(c, w);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    public static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        while (!s.isEmpty() && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static void item(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
    }

    // ---------------------------------------------------------------- portraits

    /** A living entity in a window, turning its head towards the mouse. */
    public static void portrait(GuiGraphics g, int x1, int y1, int x2, int y2, int scale, int mx, int my, LivingEntity e) {
        if (e == null) return;
        //#if mc120
        //$$ g.enableScissor(x1, y1, x2, y2);
        //$$ InventoryScreen.renderEntityInInventoryFollowsMouse(g, (x1 + x2) / 2, y2 - 2, scale, (x1 + x2) / 2F - mx, y1 + (y2 - y1) / 3F - my, e);
        //$$ g.disableScissor();
        //#elif mc26
        //$$ InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0.0625F, mx, my, e);
        //#else
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0.0625F, mx, my, e);
        //#endif
    }
}
