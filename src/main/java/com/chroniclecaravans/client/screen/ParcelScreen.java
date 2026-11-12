package com.chroniclecaravans.client.screen;

import com.chroniclecaravans.menu.ParcelMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Packing a parcel: the crate, where it goes, what it costs. Closing the screen hands it over. */
public class ParcelScreen extends CCContainerScreen<ParcelMenu> {
    public ParcelScreen(ParcelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 214);
    }

    @Override
    protected void background(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.icon(g, UiKit.I_PARCEL, x + 14, y + 13);
        UiKit.text(g, font, UiKit.fit(font, title.getString(), imageWidth - 44), x + 27, y + 14, UiKit.HEAD);
        ItemStack from = menu.originShown();
        UiKit.text(g, font, UiKit.fit(font, Component.translatable("gui.chroniclecaravans.parcel.from",
                from.isEmpty() ? "?" : from.getHoverName().getString(), menu.get(ParcelMenu.D_HOPS)).getString(), imageWidth - 28), x + 14, y + 26, UiKit.INK_SOFT);
        slotFrames(g);
        int rx = x + ParcelMenu.GRID_X + 60;
        g.renderItem(new ItemStack(Items.EMERALD), rx, y + ParcelMenu.GRID_Y);
        UiKit.text(g, font, "×" + menu.get(ParcelMenu.D_FEE), rx + 17, y + ParcelMenu.GRID_Y + 5, UiKit.INK);
        UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.parcel.help").withStyle(ChatFormatting.ITALIC), x + 14, y + ParcelMenu.GRID_Y + 58,
                imageWidth - 28, UiKit.INK_SOFT);
        UiKit.separator(g, x + 14, y + ParcelMenu.INV_Y - 5, imageWidth - 28);
    }
}
