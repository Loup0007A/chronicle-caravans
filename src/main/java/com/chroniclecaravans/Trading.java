package com.chroniclecaravans;

import java.util.function.Consumer;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/** The rules of a deal with a caravan: prices after skills, what is unlocked, paying and getting paid. */
public final class Trading {
    private Trading() {}

    /** Slots of a player inventory that hold goods (hotbar and main inventory). */
    public static final int PLAYER_SLOTS = 36;

    public static boolean unlocked(MerchantOffer o, int level, int skills) {
        if (Region.kindOf(o) == Region.KIND_TREASURE) return Guild.has(skills, Guild.Skill.HIDDEN_STOCK);
        return level >= Region.levelOf(o);
    }

    /** First cost, after the player's discount (on emeralds only). */
    public static ItemStack costA(MerchantOffer o, int skills) {
        ItemStack a = o.getBaseCostA().copy();
        if (a.is(Items.EMERALD) && Region.kindOf(o) != Region.KIND_SELL) {
            a.setCount(Math.max(1, Math.round(a.getCount() * (1F - Guild.discount(skills)))));
        }
        return a;
    }

    public static ItemStack costB(MerchantOffer o) {
        return o.getCostB().copy();
    }

    /** What the player gets, with the "Fair Seller" bonus on the emeralds paid for requested goods. */
    public static ItemStack result(MerchantOffer o, int skills) {
        ItemStack r = o.getResult().copy();
        if (Region.kindOf(o) == Region.KIND_SELL && r.is(Items.EMERALD)) {
            r.setCount(Math.min(64, Math.round(r.getCount() * (1F + Guild.sellBonus(skills)))));
        }
        return r;
    }

    public static int count(Container inv, Item item) {
        int n = 0;
        for (int i = 0; i < Math.min(PLAYER_SLOTS, inv.getContainerSize()); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.isDamaged()) n += s.getCount();
        }
        return n;
    }

    /** Takes {@code emeralds} emeralds from the inventory, if it holds that many. */
    public static boolean pay(Container inv, int emeralds) {
        if (count(inv, Items.EMERALD) < emeralds) return false;
        take(inv, Items.EMERALD, emeralds);
        return true;
    }

    private static void take(Container inv, Item item, int n) {
        for (int i = 0; i < Math.min(PLAYER_SLOTS, inv.getContainerSize()) && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.isDamaged()) {
                int k = Math.min(n, s.getCount());
                inv.removeItem(i, k);
                n -= k;
            }
        }
        inv.setChanged();
    }

    /** How many times the player can afford the offer right now (stock included). */
    public static int affordable(MerchantOffer o, int skills, Container inv) {
        if (o.isOutOfStock()) return 0;
        ItemStack a = costA(o, skills), b = costB(o);
        int times = o.getMaxUses() - o.getUses();
        if (a.is(b.getItem()) && !b.isEmpty()) {
            times = Math.min(times, count(inv, a.getItem()) / (a.getCount() + b.getCount()));
        } else {
            times = Math.min(times, count(inv, a.getItem()) / a.getCount());
            if (!b.isEmpty()) times = Math.min(times, count(inv, b.getItem()) / b.getCount());
        }
        return Math.max(0, times);
    }

    /** Emeralds that changed hands in one deal (what the guild counts as trade). */
    public static int value(MerchantOffer o, int skills) {
        int v = 0;
        for (ItemStack s : new ItemStack[] {costA(o, skills), costB(o), result(o, skills)}) {
            if (s.is(Items.EMERALD)) v += s.getCount();
            else if (s.is(Items.EMERALD_BLOCK)) v += s.getCount() * 9;
        }
        return v == 0 ? 4 : v;
    }

    /**
     * Does the deal up to {@code times} times. Returns the emerald value traded (0 if nothing happened).
     * {@code give} receives every item the player gets.
     */
    public static int execute(MerchantOffer o, int skills, Container inv, Consumer<ItemStack> give, int times) {
        int n = Math.min(times, affordable(o, skills, inv));
        if (n <= 0) return 0;
        ItemStack a = costA(o, skills), b = costB(o);
        for (int i = 0; i < n; i++) {
            take(inv, a.getItem(), a.getCount());
            if (!b.isEmpty()) take(inv, b.getItem(), b.getCount());
            give.accept(result(o, skills));
            o.increaseUses();
        }
        return value(o, skills) * n;
    }
}
