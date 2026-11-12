package com.chroniclecaravans.world;

import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.Region;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Supply and demand. Every deal leaves a mark on the market of the goods it involves: what players sell a lot
 * of is paid less, what they buy a lot of costs more. The marks fade by half every day. On top of that, each
 * region has its news of the day: a shortage (one requested good is paid 50% more) and a surplus (one of its
 * goods is sold 25% cheaper).
 */
public final class Market {
    private Market() {}

    public static final long DAY = 24000L;
    /** Payout lost per unit of "heat" on a good players sell, and price gained per unit on a good they buy. */
    private static final double SELL_DROP = 0.06, BUY_RISE = 0.05;
    public static final float SHORTAGE = 1.5F, SURPLUS = 0.75F;

    public static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** How much a good has been traded lately (decays by half every day). */
    public static double heat(CaravanData data, Item item, long now) {
        double[] h = data.market().get(key(item));
        if (h == null) return 0;
        double days = Math.max(0, now - h[1]) / (double) DAY;
        return h[0] * Math.pow(0.5, days);
    }

    public static void record(CaravanData data, Item item, double amount, long now) {
        if (!CCConfig.MARKET_PRICES.get()) return;
        double current = heat(data, item, now);
        data.market().put(key(item), new double[] {Math.min(40, current + amount), now});
        data.setDirty();
    }

    private static int mix(int region, long day, int salt) {
        long x = day * 0x9E3779B97F4A7C15L + region * 0xC2B2AE3D27D4EB4FL + salt;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return (int) (x & 0x7FFFFFFF);
    }

    /** Today's shortage in a region: one of the goods its caravans ask for. */
    public static Item shortage(Region r, long now) {
        List<Item> wants = r.wantedItems();
        return wants.isEmpty() ? Items.AIR : wants.get(mix(r.ordinal(), now / DAY, 1) % wants.size());
    }

    /** Today's surplus in a region: one of its goods, sold cheaper. */
    public static Item surplus(Region r, long now) {
        List<Item> goods = r.soldItems();
        return goods.isEmpty() ? Items.AIR : goods.get(mix(r.ordinal(), now / DAY, 2) % goods.size());
    }

    /**
     * Price factor of an offer for a caravan of {@code region}: for goods sold to the player, the factor of
     * their price (1 = normal); for requests, the factor of the emeralds paid.
     */
    public static float factor(CaravanData data, Region region, MerchantOffer o, long now) {
        if (!CCConfig.MARKET_PRICES.get()) return 1F;
        int kind = Region.kindOf(o);
        if (kind == Region.KIND_SELL) {
            Item wanted = o.getBaseCostA().getItem();
            double f = Math.max(0.4, 1.0 - SELL_DROP * heat(data, wanted, now));
            if (wanted == shortage(region, now)) f *= SHORTAGE;
            return (float) f;
        }
        Item good = o.getResult().getItem();
        double f = Math.min(1.8, 1.0 + BUY_RISE * heat(data, good, now));
        if (good == surplus(region, now)) f *= SURPLUS;
        return (float) f;
    }

    /** A deal happened: the market of its good warms up. */
    public static void traded(CaravanData data, MerchantOffer o, int times, long now) {
        if (Region.kindOf(o) == Region.KIND_SELL) record(data, o.getBaseCostA().getItem(), times, now);
        else {
            ItemStack r = o.getResult();
            if (!r.is(Items.ENCHANTED_BOOK)) record(data, r.getItem(), times, now);
        }
    }
}
