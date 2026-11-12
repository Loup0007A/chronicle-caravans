package com.chroniclecaravans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

/**
 * Where a caravan comes from. Each region has its own goods (unlocked by guild level), the goods it is looking
 * for (the player sells them for emeralds), and its treasures (enchanted books and rarities, for the players
 * who learnt the "Hidden Stock" skill).
 */
public enum Region {
    DUNES(ChatFormatting.YELLOW, new Good[] {
            sell(0, 3, Items.SANDSTONE, 32), sell(0, 3, Items.ORANGE_TERRACOTTA, 24), sell(0, 2, Items.CACTUS, 12),
            sell(0, 4, Items.RED_SAND, 48), sell(2, 5, Items.RABBIT_FOOT, 2), sell(2, 6, Items.ARMADILLO_SCUTE, 4),
            sell(2, 6, Items.GOLD_INGOT, 6), sell(4, 8, Items.GOLDEN_CARROT, 16), sell(4, 7, Items.ARCHER_POTTERY_SHERD, 1),
            sell(6, 20, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 1), sell(9, 36, Items.ENCHANTED_GOLDEN_APPLE, 1),
            swap(6, Items.GOLD_INGOT, 16, 20, Items.TOTEM_OF_UNDYING, 1)
    }, new Good[] {
            want(Items.OAK_LOG, 16, 4), want(Items.WHITE_WOOL, 8, 3), want(Items.MELON_SLICE, 16, 3),
            want(Items.BUCKET, 2, 3), want(Items.SWEET_BERRIES, 16, 3)
    }, new Good[] {
            book(18, "minecraft:fire_protection", 4), book(22, "minecraft:power", 5), rare(26, Items.SNIFFER_EGG, 1)
    }),
    FROST(ChatFormatting.AQUA, new Good[] {
            sell(0, 3, Items.PACKED_ICE, 16), sell(0, 2, Items.SNOW_BLOCK, 32), sell(0, 3, Items.SPRUCE_LOG, 32),
            sell(0, 3, Items.SWEET_BERRIES, 24), sell(2, 6, Items.BLUE_ICE, 8), sell(2, 5, Items.POWDER_SNOW_BUCKET, 1),
            sell(2, 6, Items.GOAT_HORN, 1), sell(4, 9, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            sell(4, 7, Items.CHAINMAIL_CHESTPLATE, 1), sell(6, 16, Items.SPYGLASS, 1), sell(9, 40, Items.NETHERITE_SCRAP, 2),
            swap(2, Items.RAW_GOLD, 12, 2, Items.GOLD_INGOT, 14)
    }, new Good[] {
            want(Items.COAL, 16, 3), want(Items.COOKED_BEEF, 8, 4), want(Items.TORCH, 32, 3),
            want(Items.WHITE_WOOL, 8, 3), want(Items.BAKED_POTATO, 16, 3)
    }, new Good[] {
            book(16, "minecraft:frost_walker", 2), book(20, "minecraft:feather_falling", 4), rare(30, Items.SKELETON_SKULL, 1)
    }),
    JUNGLE(ChatFormatting.GREEN, new Good[] {
            sell(0, 2, Items.BAMBOO, 32), sell(0, 3, Items.COCOA_BEANS, 16), sell(0, 3, Items.JUNGLE_LOG, 32),
            sell(0, 3, Items.MELON, 8), sell(2, 5, Items.GLOW_BERRIES, 16), sell(2, 6, Items.MOSS_BLOCK, 24),
            sell(2, 5, Items.BAMBOO_BLOCK, 16), sell(4, 8, Items.TORCHFLOWER_SEEDS, 2),
            sell(4, 12, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 1), sell(6, 18, Items.AXOLOTL_BUCKET, 1),
            sell(9, 30, Items.TURTLE_EGG, 2), swap(4, Items.GOLD_INGOT, 8, 4, Items.GOLDEN_APPLE, 2)
    }, new Good[] {
            want(Items.IRON_INGOT, 4, 3), want(Items.BREAD, 12, 3), want(Items.GLASS_BOTTLE, 8, 2),
            want(Items.STRING, 16, 3), want(Items.COPPER_INGOT, 12, 3)
    }, new Good[] {
            book(18, "minecraft:looting", 3), book(16, "minecraft:respiration", 3), rare(24, Items.PIGLIN_BANNER_PATTERN, 1)
    }),
    HIGHLANDS(ChatFormatting.GRAY, new Good[] {
            sell(0, 3, Items.CALCITE, 32), sell(0, 3, Items.TUFF, 32), sell(0, 3, Items.COAL, 24),
            sell(0, 4, Items.COPPER_INGOT, 16), sell(2, 6, Items.AMETHYST_SHARD, 12), sell(2, 6, Items.LAPIS_LAZULI, 16),
            sell(2, 7, Items.IRON_INGOT, 10), sell(4, 9, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            sell(4, 12, Items.DIAMOND, 2), sell(6, 16, Items.DIAMOND_PICKAXE, 1), sell(9, 34, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1),
            swap(2, Items.RAW_IRON, 16, 2, Items.IRON_BLOCK, 2)
    }, new Good[] {
            want(Items.BREAD, 16, 4), want(Items.OAK_PLANKS, 32, 3), want(Items.TORCH, 32, 3),
            want(Items.LEATHER, 6, 3), want(Items.PUMPKIN_PIE, 6, 3)
    }, new Good[] {
            book(20, "minecraft:efficiency", 5), book(24, "minecraft:fortune", 3), book(18, "minecraft:silk_touch", 1)
    }),
    COAST(ChatFormatting.BLUE, new Good[] {
            sell(0, 3, Items.PRISMARINE_SHARD, 16), sell(0, 2, Items.KELP, 32), sell(0, 3, Items.COOKED_COD, 16),
            sell(0, 3, Items.SEA_PICKLE, 8), sell(2, 6, Items.TURTLE_SCUTE, 3), sell(2, 6, Items.NAUTILUS_SHELL, 2),
            sell(2, 6, Items.SPONGE, 2), sell(4, 10, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            sell(4, 8, Items.PRISMARINE_CRYSTALS, 24), sell(6, 20, Items.HEART_OF_THE_SEA, 1),
            sell(9, 36, Items.TRIDENT, 1), swap(4, Items.PRISMARINE_SHARD, 16, 4, Items.SEA_LANTERN, 6)
    }, new Good[] {
            want(Items.WHEAT, 24, 3), want(Items.IRON_INGOT, 4, 3), want(Items.OAK_LOG, 16, 4),
            want(Items.APPLE, 12, 3), want(Items.PAPER, 24, 3)
    }, new Good[] {
            book(16, "minecraft:depth_strider", 3), book(18, "minecraft:luck_of_the_sea", 3), book(22, "minecraft:riptide", 3)
    }),
    MARSH(ChatFormatting.DARK_GREEN, new Good[] {
            sell(0, 2, Items.LILY_PAD, 12), sell(0, 3, Items.MUD_BRICKS, 32), sell(0, 2, Items.CLAY_BALL, 32),
            sell(0, 3, Items.MANGROVE_LOG, 32), sell(2, 5, Items.SLIME_BALL, 8), sell(2, 6, Items.OCHRE_FROGLIGHT, 6),
            sell(2, 6, Items.FERMENTED_SPIDER_EYE, 4), sell(4, 9, Items.SLIME_BLOCK, 4), sell(4, 9, Items.PEARLESCENT_FROGLIGHT, 8),
            sell(6, 14, Items.BREWING_STAND, 1), sell(9, 28, Items.WITHER_ROSE, 2), swap(2, Items.SUGAR_CANE, 16, 1, Items.GLISTERING_MELON_SLICE, 2)
    }, new Good[] {
            want(Items.GLASS_BOTTLE, 8, 2), want(Items.COAL, 16, 3), want(Items.COBBLESTONE, 64, 3),
            want(Items.BREAD, 12, 3), want(Items.SUGAR, 16, 2)
    }, new Good[] {
            book(16, "minecraft:aqua_affinity", 1), book(20, "minecraft:protection", 4), rare(24, Items.ECHO_SHARD, 2)
    }),
    WOODS(ChatFormatting.DARK_AQUA, new Good[] {
            sell(0, 3, Items.DARK_OAK_LOG, 32), sell(0, 3, Items.SPRUCE_LOG, 32), sell(0, 2, Items.PODZOL, 24),
            sell(0, 3, Items.APPLE, 16), sell(2, 5, Items.HONEY_BOTTLE, 4), sell(2, 5, Items.HONEYCOMB, 8),
            sell(2, 6, Items.CHERRY_SAPLING, 2), sell(4, 8, Items.NAME_TAG, 1), sell(4, 9, Items.BEEHIVE, 1),
            sell(6, 18, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 1), sell(9, 26, Items.GOLDEN_APPLE, 4),
            swap(2, Items.WHEAT, 20, 1, Items.CAKE, 1)
    }, new Good[] {
            want(Items.IRON_INGOT, 4, 3), want(Items.FLINT, 12, 2), want(Items.GLASS, 16, 3),
            want(Items.COOKED_PORKCHOP, 8, 4), want(Items.STRING, 16, 3)
    }, new Good[] {
            book(22, "minecraft:mending", 1), book(16, "minecraft:unbreaking", 3), book(18, "minecraft:sharpness", 5)
    }),
    STEPPE(ChatFormatting.GOLD, new Good[] {
            sell(0, 3, Items.HAY_BLOCK, 12), sell(0, 2, Items.ACACIA_LOG, 32), sell(0, 3, Items.PUMPKIN, 12),
            sell(0, 3, Items.LEATHER, 12), sell(2, 5, Items.SADDLE, 1), sell(2, 4, Items.LEAD, 4),
            sell(2, 6, Items.IRON_HORSE_ARMOR, 1), sell(4, 9, Items.GOLDEN_HORSE_ARMOR, 1), sell(4, 7, Items.SPECTRAL_ARROW, 16),
            sell(6, 18, Items.DIAMOND_HORSE_ARMOR, 1), sell(9, 28, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
            swap(2, Items.LEATHER, 8, 3, Items.SADDLE, 1)
    }, new Good[] {
            want(Items.WHEAT, 24, 3), want(Items.CARROT, 24, 3), want(Items.IRON_INGOT, 4, 3),
            want(Items.COBBLESTONE, 64, 3), want(Items.FEATHER, 12, 2)
    }, new Good[] {
            book(20, "minecraft:infinity", 1), book(16, "minecraft:punch", 2), book(18, "minecraft:thorns", 3)
    });

    // ---------------------------------------------------------------- offers

    public static final int KIND_BUY = 0, KIND_SELL = 1, KIND_TREASURE = 2;

    /**
     * One line of stock. {@code level}: guild level that unlocks it; {@code costItem}/{@code costCount}: what
     * the player pays (besides {@code emeralds}); {@code ench}: an enchanted book instead of {@code item}.
     */
    public record Good(int kind, int level, Item costItem, int costCount, int emeralds, Item item, int count, String ench, int enchLevel) {}

    private static Good sell(int level, int price, Item item, int count) {
        return new Good(KIND_BUY, level, Items.EMERALD, price, 0, item, count, null, 0);
    }

    private static Good swap(int level, Item cost, int costCount, int emeralds, Item item, int count) {
        return new Good(KIND_BUY, level, cost, costCount, emeralds, item, count, null, 0);
    }

    private static Good want(Item item, int count, int emeralds) {
        return new Good(KIND_SELL, 0, item, count, 0, Items.EMERALD, emeralds, null, 0);
    }

    private static Good book(int price, String ench, int level) {
        return new Good(KIND_TREASURE, 0, Items.EMERALD, price, 0, Items.ENCHANTED_BOOK, 1, ench, level);
    }

    private static Good rare(int price, Item item, int count) {
        return new Good(KIND_TREASURE, 0, Items.EMERALD, price, 0, item, count, null, 0);
    }

    /** Things every caravan carries, whatever its region. */
    private static final Good[] SUPPLIES = {
            sell(0, 2, Items.BREAD, 16), sell(0, 2, Items.TORCH, 32), sell(0, 3, Items.ARROW, 32),
            sell(0, 3, Items.LANTERN, 4), sell(2, 6, Items.EXPERIENCE_BOTTLE, 8), sell(2, 5, Items.ENDER_PEARL, 2),
            sell(4, 7, Items.GOLDEN_APPLE, 1), sell(4, 6, Items.NAME_TAG, 1), sell(2, 4, Items.FIREWORK_ROCKET, 16)
    };

    /** Treasures any caravan may carry. */
    private static final Good[] TREASURES = {
            book(18, "minecraft:unbreaking", 3), book(22, "minecraft:mending", 1), book(18, "minecraft:protection", 4),
            book(16, "minecraft:feather_falling", 4), book(18, "minecraft:sharpness", 5), book(20, "minecraft:efficiency", 5)
    };

    /** Encodes the kind and the level of an offer in its xp (the only free number a vanilla offer saves). */
    public static int meta(int kind, int level) {
        return 1000 + kind * 100 + level;
    }

    public static int kindOf(MerchantOffer o) {
        int x = o.getXp();
        return x >= 1000 ? (x - 1000) / 100 : KIND_BUY;
    }

    public static int levelOf(MerchantOffer o) {
        int x = o.getXp();
        return x >= 1000 ? (x - 1000) % 100 : 0;
    }

    public final ChatFormatting color;
    private final Good[] goods, wants, treasures;

    Region(ChatFormatting color, Good[] goods, Good[] wants, Good[] treasures) {
        this.color = color;
        this.goods = goods;
        this.wants = wants;
        this.treasures = treasures;
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

    private static List<Good> pick(Good[] from, int n, Random random) {
        List<Good> list = new ArrayList<>();
        for (Good g : from) if (g.count > 0) list.add(g);
        Collections.shuffle(list, random);
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }

    private static MerchantOffer make(ServerLevel level, Good g) {
        ItemStack result = g.ench != null ? Compat.enchantedBook(level, g.ench, g.enchLevel) : new ItemStack(g.item, g.count);
        if (result.isEmpty()) return null;
        int uses = switch (g.kind) {
            case KIND_SELL -> 6;
            case KIND_TREASURE -> 1;
            default -> g.level >= 9 ? 1 : g.level >= 6 ? 2 : g.level >= 4 ? 3 : g.level >= 2 ? 5 : 8;
        };
        ItemStack costB = g.emeralds > 0 ? new ItemStack(Items.EMERALD, g.emeralds) : ItemStack.EMPTY;
        return Compat.offer2(new ItemStack(g.costItem, g.costCount), costB, result, uses, meta(g.kind, g.level));
    }

    /**
     * The stock of a new caravan: eight regional goods, two supplies and the atlas, three requests and two
     * treasures, sorted by kind then level.
     */
    public List<MerchantOffer> stock(ServerLevel level, RandomSource random) {
        Random r = new Random(random.nextLong());
        List<Good> chosen = new ArrayList<>();
        List<Good> regional = pick(goods, goods.length, r);
        // At least three goods anyone can buy, then the rest
        List<Good> basic = new ArrayList<>(), rest = new ArrayList<>();
        for (Good g : regional) (g.level == 0 ? basic : rest).add(g);
        chosen.addAll(basic.subList(0, Math.min(3, basic.size())));
        chosen.addAll(rest.subList(0, Math.min(5, rest.size())));
        chosen.addAll(pick(SUPPLIES, 2, r));
        chosen.add(sell(0, 5, ChronicleCaravans.ATLAS.get(), 1));
        chosen.addAll(pick(wants, 3, r));
        List<Good> rare = pick(treasures, 1, r);
        Good extra = pick(TREASURES, 2, r).stream().filter(g -> rare.stream().noneMatch(x -> x.item == g.item && java.util.Objects.equals(x.ench, g.ench))).findFirst().orElse(null);
        if (extra != null) rare.add(extra);
        chosen.addAll(rare);
        chosen.sort((a, b) -> a.kind != b.kind ? Integer.compare(a.kind, b.kind) : Integer.compare(a.level, b.level));
        List<MerchantOffer> out = new ArrayList<>();
        for (Good g : chosen) {
            MerchantOffer o = make(level, g);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** The goods this region's caravans ask for. */
    public List<Item> wantedItems() {
        List<Item> out = new ArrayList<>();
        for (Good g : wants) out.add(g.costItem);
        return out;
    }

    /** The ordinary goods this region sells (no barter, no treasure). */
    public List<Item> soldItems() {
        List<Item> out = new ArrayList<>();
        for (Good g : goods) if (g.kind == KIND_BUY && g.emeralds == 0 && g.count > 0) out.add(g.item);
        return out;
    }

    /** A thank-you present from this region (after a rescue). */
    public ItemStack gift(RandomSource random) {
        List<Good> pool = new ArrayList<>();
        for (Good g : goods) if (g.level <= 2 && g.count > 0 && g.kind == KIND_BUY && g.emeralds == 0) pool.add(g);
        Good g = pool.get(random.nextInt(pool.size()));
        return new ItemStack(g.item, g.count);
    }
}
