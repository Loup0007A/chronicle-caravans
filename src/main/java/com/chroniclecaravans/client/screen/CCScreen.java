package com.chroniclecaravans.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Base of the mod's screens: one place for everything the versions do differently (drawing entry points,
 * mouse and keyboard events, tooltips), so the screens themselves are written once.
 */
public abstract class CCScreen extends Screen {
    protected CCScreen(Component title) {
        super(title);
    }

    /** Draws the whole screen (tooltips last). */
    protected abstract void draw(GuiGraphics g, int mx, int my, float pt);

    protected boolean click(double mx, double my, int button, boolean shift) {
        return false;
    }

    protected boolean drag(double mx, double my, double dx, double dy) {
        return false;
    }

    protected void release() {
    }

    protected boolean scroll(double mx, double my, double amount) {
        return false;
    }

    protected boolean key(int key) {
        return false;
    }

    protected boolean typed(char c) {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- drawing helpers

    protected void tooltip(GuiGraphics g, List<Component> lines, int x, int y) {
        //#if mc26
        //$$ g.setComponentTooltipForNextFrame(font, lines, x, y);
        //#else
        g.renderComponentTooltip(font, lines, x, y);
        //#endif
    }

    protected void tooltip(GuiGraphics g, ItemStack stack, int x, int y) {
        //#if mc26
        //$$ g.setTooltipForNextFrame(font, stack, x, y);
        //#else
        g.renderTooltip(font, stack, x, y);
        //#endif
    }

    protected void ding() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** The sound of a ledger's page turning. */
    protected void page() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    protected static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected boolean shiftDown() {
        //#if mc26
        //$$ return minecraft.hasShiftDown();
        //#else
        return Screen.hasShiftDown();
        //#endif
    }

    // ---------------------------------------------------------------- version plumbing

    //#if mc120
    //$$ @Override
    //$$ public void renderBackground(GuiGraphics g) {
    //$$     g.fillGradient(0, 0, width, height, 0x90100804, 0xB0100804);
    //$$ }
    //#elif mc26
    //$$ @Override
    //$$ public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    //$$     g.fillGradient(0, 0, width, height, 0x90100804, 0xB0100804);
    //$$ }
    //#else
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x90100804, 0xB0100804);
    }
    //#endif

    //#if mc26
    //$$ @Override
    //$$ public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    //#else
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    //#endif
        //#if mc120
        //$$ renderBackground(g);
        //#elif !mc26
        renderBackground(g, mouseX, mouseY, partialTick);
        //#endif
        draw(g, mouseX, mouseY, partialTick);
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean doubleClick) {
    //$$     return click(ev.x(), ev.y(), ev.button(), ev.hasShiftDown()) || super.mouseClicked(ev, doubleClick);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent ev, double dx, double dy) {
    //$$     return drag(ev.x(), ev.y(), dx, dy) || super.mouseDragged(ev, dx, dy);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent ev) {
    //$$     release();
    //$$     return super.mouseReleased(ev);
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
    //#else
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return click(mx, my, button, shiftDown()) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return drag(mx, my, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        release();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scan, int modifiers) {
        return key(keyCode) || super.keyPressed(keyCode, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return typed(c) || super.charTyped(c, modifiers);
    }
    //#endif

    //#if mc120
    //$$ @Override
    //$$ public boolean mouseScrolled(double mx, double my, double scrollY) {
    //#else
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
    //#endif
        return scroll(mx, my, scrollY);
    }
}
