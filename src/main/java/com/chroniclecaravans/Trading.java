package com.chroniclecaravans;

import java.util.function.Consumer;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The rules of a deal with a caravan: prices after skills and market, what is unlocked, paying and getting paid.
 * A {@link Quote} is what one player pays and gets for an offer right now.
 */
public final class Trading {
    private Trading() {}

    /** Slots of a player inventory that hold goods (hotbar and main inventory). */
    public static final int PLAYER_SLOTS = 36;

    /** What a deal costs and gives, for one player, at today's prices. {@code market}: factor applied by the market. */
    public record Quote(ItemStack a, ItemStack b, ItemStack result, float market) {
        /** Market effect in percent, as the player feels it: positive = good for the player. */
        public int percent(int kind) {
            int p = Math.round((market - 1F) * 100F);
            return kind == Region.KIND_SELL ? p : -p;
        }
    }

    public static boolean unlocked(MerchantOffer o, int level, int skills) {
        if (Region.kindOf(o) == Region.KIND_TREASURE) return Guild.has(skills, Guild.Skill.HIDDEN_STOCK);
        return level >= Region.levelOf(o);
    }

    private static int scale(int count, float f) {
        return Math.max(1, Math.min(64, Math.round(count * f)));
    }

    /** The price and the goods of an offer for a player with these skills, with the market factor applied. */
    public static Quote quote(MerchantOffer o, int skills, float market) {
        ItemStack a = o.getBaseCostA().copy(), b = o.getCostB().copy(), r = o.getResult().copy();
        if (Region.kindOf(o) == Region.KIND_SELL) {
            // A request: the market and the "Fair Seller" skill change the emeralds paid
            if (r.is(Items.EMERALD)) r.setCount(scale(r.getCount(), market * (1F + Guild.sellBonus(skills))));
        } else {
            float f = market * (1F - Guild.discount(skills));
            if (a.is(Items.EMERALD)) a.setCount(scale(a.getCount(), f));
            else if (b.is(Items.EMERALD)) b.setCount(scale(b.getCount(), f));
        }
        return new Quote(a, b, r, market);
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

    /** How many times the inventory can pay for the quote (stock included). */
    public static int affordable(MerchantOffer o, Quote q, Container inv) {
        if (o.isOutOfStock()) return 0;
        int times = o.getMaxUses() - o.getUses();
        if (!q.b.isEmpty() && q.a.is(q.b.getItem())) {
            times = Math.min(times, count(inv, q.a.getItem()) / (q.a.getCount() + q.b.getCount()));
        } else {
            times = Math.min(times, count(inv, q.a.getItem()) / q.a.getCount());
            if (!q.b.isEmpty()) times = Math.min(times, count(inv, q.b.getItem()) / q.b.getCount());
        }
        return Math.max(0, times);
    }

    /** Emeralds that changed hands in one deal (what the guild counts as trade). */
    public static int value(Quote q) {
        int v = 0;
        for (ItemStack s : new ItemStack[] {q.a, q.b, q.result}) {
            if (s.is(Items.EMERALD)) v += s.getCount();
            else if (s.is(Items.EMERALD_BLOCK)) v += s.getCount() * 9;
        }
        return v == 0 ? 4 : v;
    }

    /**
     * Does the deal up to {@code times} times. Returns how many times it was done.
     * {@code give} receives every item the player gets.
     */
    public static int execute(MerchantOffer o, Quote q, Container inv, Consumer<ItemStack> give, int times) {
        int n = Math.min(times, affordable(o, q, inv));
        for (int i = 0; i < n; i++) {
            take(inv, q.a.getItem(), q.a.getCount());
            if (!q.b.isEmpty()) take(inv, q.b.getItem(), q.b.getCount());
            give.accept(q.result.copy());
            o.increaseUses();
        }
        return Math.max(0, n);
    }
}
