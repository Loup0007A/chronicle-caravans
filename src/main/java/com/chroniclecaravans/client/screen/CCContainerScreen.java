package com.chroniclecaravans.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Base of the mod's container screens (real slots): the ledger look, and what the versions do differently
 * (background, labels, input), so the screens themselves are written once. Coordinates are absolute.
 */
public abstract class CCContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected CCContainerScreen(M menu, Inventory inv, Component title, int w, int h) {
        //#if mc26
        //$$ super(menu, inv, title, w, h);
        //#else
        super(menu, inv, title);
        this.imageWidth = w;
        this.imageHeight = h;
        //#endif
    }

    /** The panel, slot frames and texts (under the items). */
    protected abstract void background(GuiGraphics g, int mx, int my);

    /** Tooltips of the screen's own parts (over everything). */
    protected void foreground(GuiGraphics g, int mx, int my) {
    }

    /** Text input; true if the key was used. */
    protected boolean key(int key) {
        return false;
    }

    protected boolean typed(char c) {
        return false;
    }

    protected boolean click(double mx, double my, int button) {
        return false;
    }

    /** Frames under every visible slot of the menu. */
    protected void slotFrames(GuiGraphics g) {
        for (Slot s : menu.slots) {
            if (s.isActive() && s.x > -1000) UiKit.slot(g, leftPos + s.x - 1, topPos + s.y - 1, false);
        }
    }

    protected static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void tooltip(GuiGraphics g, List<Component> lines, int x, int y) {
        //#if mc26
        //$$ g.setComponentTooltipForNextFrame(font, lines, x, y);
        //#else
        g.renderComponentTooltip(font, lines, x, y);
        //#endif
    }

    // ---------------------------------------------------------------- version plumbing

    //#if mc26
    //$$ @Override
    //$$ public void extractBackground(GuiGraphics g, int mx, int my, float pt) {
    //$$     super.extractBackground(g, mx, my, pt);
    //$$     background(g, mx, my);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void extractLabels(GuiGraphics g, int mx, int my) {
    //$$ }
    //$$
    //$$ @Override
    //$$ public void extractRenderState(GuiGraphics g, int mx, int my, float pt) {
    //$$     super.extractRenderState(g, mx, my, pt);
    //$$     foreground(g, mx, my);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean keyPressed(net.minecraft.client.input.KeyEvent ev) {
    //$$     return key(ev.key()) || super.keyPressed(ev);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean charTyped(net.minecraft.client.input.CharacterEvent ev) {
    //$$     String s = ev.codepointAsString();
    //$$     return (s.length() == 1 && typed(s.charAt(0))) || super.charTyped(ev);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean doubleClick) {
    //$$     return click(ev.x(), ev.y(), ev.button()) || super.mouseClicked(ev, doubleClick);
    //$$ }
    //#else
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        background(g, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        //#if mc120
        //$$ renderBackground(g);
        //#endif
        super.render(g, mx, my, pt);
        foreground(g, mx, my);
        renderTooltip(g, mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scan, int modifiers) {
        return key(keyCode) || super.keyPressed(keyCode, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return typed(c) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return click(mx, my, button) || super.mouseClicked(mx, my, button);
    }
    //#endif
}
