package com.chroniclecaravans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import com.chroniclecaravans.platform.Compat;

/** Where a caravan comes from. Each region carries its own goods. */
public enum Region {
    DUNES(ChatFormatting.YELLOW,
            good(0, 1, Items.CACTUS, 3), good(0, 2, Items.SAND, 16), good(0, 3, Items.TERRACOTTA, 8),
            good(1, 2, Items.RED_SAND, 16), good(1, 1, Items.DEAD_BUSH, 2),
            good(2, 5, Items.RABBIT_FOOT, 1), good(2, 6, Items.ARMADILLO_SCUTE, 2),
            good(3, 24, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            good(4, 40, Items.ENCHANTED_GOLDEN_APPLE, 1)),
    FROST(ChatFormatting.AQUA,
            good(0, 1, Items.SNOW_BLOCK, 8), good(0, 2, Items.PACKED_ICE, 6), good(0, 2, Items.SPRUCE_SAPLING, 2),
            good(1, 2, Items.SWEET_BERRIES, 6), good(1, 2, Items.ICE, 8),
            good(2, 4, Items.BLUE_ICE, 2), good(2, 6, Items.POWDER_SNOW_BUCKET, 1),
            good(3, 24, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            good(4, 48, Items.NETHERITE_SCRAP, 1)),
    JUNGLE(ChatFormatting.GREEN,
            good(0, 2, Items.BAMBOO, 8), good(0, 2, Items.COCOA_BEANS, 4), good(0, 2, Items.JUNGLE_SAPLING, 2),
            good(1, 2, Items.MELON_SLICE, 8), good(1, 3, Items.VINE, 6),
            good(2, 6, Items.MOSS_BLOCK, 8), good(2, 5, Items.GLOW_BERRIES, 6),
            good(3, 24, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            good(4, 32, Items.AXOLOTL_BUCKET, 1)),
    HIGHLANDS(ChatFormatting.GRAY,
            good(0, 2, Items.CALCITE, 8), good(0, 3, Items.TUFF, 16), good(0, 2, Items.COAL, 8),
            good(1, 2, Items.DRIPSTONE_BLOCK, 8), good(1, 3, Items.COPPER_INGOT, 6),
            good(2, 6, Items.AMETHYST_SHARD, 4), good(2, 6, Items.LAPIS_LAZULI, 6),
            good(3, 22, Items.DIAMOND, 1),
            good(4, 30, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 1)),
    COAST(ChatFormatting.BLUE,
            good(0, 2, Items.PRISMARINE_SHARD, 6), good(0, 2, Items.KELP, 8), good(0, 2, Items.COD, 6),
            good(1, 3, Items.SEA_PICKLE, 3), good(1, 4, Items.TURTLE_SCUTE, 1),
            good(2, 6, Items.NAUTILUS_SHELL, 1), good(2, 5, Items.SPONGE, 1),
            good(3, 24, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            good(4, 48, Items.HEART_OF_THE_SEA, 1)),
    MARSH(ChatFormatting.DARK_GREEN,
            good(0, 1, Items.LILY_PAD, 3), good(0, 2, Items.MUD, 12), good(0, 2, Items.CLAY_BALL, 12),
            good(1, 2, Items.MANGROVE_PROPAGULE, 2), good(1, 1, Items.BROWN_MUSHROOM, 3),
            good(2, 4, Items.SLIME_BALL, 3), good(2, 6, Items.OCHRE_FROGLIGHT, 2),
            good(3, 16, Items.SLIME_BLOCK, 2),
            good(4, 30, Items.PEARLESCENT_FROGLIGHT, 6)),
    WOODS(ChatFormatting.DARK_AQUA,
            good(0, 2, Items.SPRUCE_LOG, 12), good(0, 2, Items.DARK_OAK_LOG, 12), good(0, 1, Items.FERN, 4),
            good(1, 2, Items.PODZOL, 6), good(1, 2, Items.HONEYCOMB, 2),
            good(2, 4, Items.HONEY_BOTTLE, 2), good(2, 6, Items.CHERRY_SAPLING, 1),
            good(3, 18, Items.NAME_TAG, 1),
            good(4, 30, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 1)),
    STEPPE(ChatFormatting.GOLD,
            good(0, 2, Items.HAY_BLOCK, 4), good(0, 2, Items.ACACIA_SAPLING, 2), good(0, 2, Items.PUMPKIN, 4),
            good(1, 2, Items.LEATHER, 3), good(1, 1, Items.SUNFLOWER, 2),
            good(2, 6, Items.SADDLE, 1), good(2, 5, Items.LEAD, 2),
            good(3, 22, Items.DIAMOND_HORSE_ARMOR, 1),
            good(4, 40, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 1));

    /** One line of a caravan's stock: {@code tier} is the reputation rank needed to see it. */
    public record Good(int tier, int price, Item item, int count) {}

    /** A stocked offer and the rank it needs. */
    public record Stocked(MerchantOffer offer, int tier) {}

    /** Things every caravan may buy from the player, whatever its region. */
    private static final Object[][] PURCHASES = {
            {Items.WHEAT, 20}, {Items.COAL, 15}, {Items.IRON_INGOT, 4}, {Items.PAPER, 24},
            {Items.LEATHER, 6}, {Items.STRING, 20}, {Items.GOLD_INGOT, 3}, {Items.BREAD, 6},
    };

    public final ChatFormatting color;
    private final Good[] goods;

    Region(ChatFormatting color, Good... goods) {
        this.color = color;
        this.goods = goods;
    }

    private static Good good(int tier, int price, Item item, int count) {
        return new Good(tier, price, item, count);
    }

    public String key() {
        return name().toLowerCase();
    }

    public MutableComponent label() {
        return Component.translatable("region.chroniclecaravans." + key()).withStyle(color);
    }

    /** Name of a caravan from this region ("Dune Caravan"). */
    public MutableComponent caravanName() {
        return Component.translatable("caravan.chroniclecaravans." + key());
    }

    public static Region byName(String name) {
        for (Region r : values()) if (r.name().equalsIgnoreCase(name)) return r;
        return STEPPE;
    }

    /** The region a position belongs to, from its biome. */
    public static Region at(ServerLevel level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_RIVER)) return COAST;
        // Vanilla checks only: they read the same on every loader and version
        if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS)) return DUNES;
        if (b.value().getBaseTemperature() <= 0.05F) return FROST;
        if (b.is(BiomeTags.IS_JUNGLE)) return JUNGLE;
        if (b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP)) return MARSH;
        if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL)) return HIGHLANDS;
        if (b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_FOREST)) return WOODS;
        return STEPPE;
    }

    /** A region other than {@code local}: caravans bring goods the place does not have. */
    public static Region foreignTo(Region local, RandomSource random) {
        Region[] all = values();
        Region r = all[random.nextInt(all.length)];
        if (r == local) r = all[(r.ordinal() + 1 + random.nextInt(all.length - 1)) % all.length];
        return r;
    }

    /** Full stock of a new caravan: two purchases, then the regional goods sorted by rank. */
    public List<Stocked> stock(RandomSource random) {
        List<Stocked> out = new ArrayList<>();
        List<Object[]> buys = new ArrayList<>(List.of(PURCHASES));
        Collections.shuffle(buys, new java.util.Random(random.nextLong()));
        for (int i = 0; i < 2; i++) {
            Object[] p = buys.get(i);
            out.add(new Stocked(Compat.offer((Item) p[0], (Integer) p[1], new ItemStack(Items.EMERALD), 8, 2), 0));
        }
        for (Good g : goods) {
            int uses = g.tier >= 3 ? 1 : g.tier == 2 ? 3 : 6;
            out.add(new Stocked(Compat.offer(Items.EMERALD, g.price, new ItemStack(g.item, g.count), uses, 1 + g.tier * 2), g.tier));
        }
        return out;
    }

    /** A thank-you present from this region (after a rescue). */
    public ItemStack gift(RandomSource random) {
        List<Good> pool = new ArrayList<>();
        for (Good g : goods) if (g.tier <= 2) pool.add(g);
        Good g = pool.get(random.nextInt(pool.size()));
        return new ItemStack(g.item, g.count);
    }
}
