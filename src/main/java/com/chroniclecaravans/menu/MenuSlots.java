package com.chroniclecaravans.menu;

import java.util.function.Consumer;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Slot helpers shared by the mod's container menus. */
public final class MenuSlots {
    private MenuSlots() {}

    /** The player's inventory (three rows, then the hotbar 58 pixels below the first row). */
    public static void player(Inventory inv, int x, int y, Consumer<Slot> add) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) add.accept(new Slot(inv, c + r * 9 + 9, x + c * 18, y + r * 18));
        }
        for (int c = 0; c < 9; c++) add.accept(new Slot(inv, c, x + c * 18, y + 58));
    }

    /** A slot only shown to carry an item to the client (never drawn, never clicked). */
    public static class Hidden extends Slot {
        public Hidden(Container c, int i) {
            super(c, i, -10000, -10000);
        }

        @Override
        public boolean mayPickup(Player p) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack s) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    /** A slot the player can take from but not put into (results). */
    public static class Output extends Slot {
        public Output(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack s) {
            return false;
        }
    }

}
