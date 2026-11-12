package com.chroniclecaravans;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Every feature has its own switch or dial (config/chroniclecaravans-common.toml). */
public final class CCConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue VISIT_INTERVAL = B
            .comment("Average time between two caravan visits at the same milestone, in seconds.")
            .defineInRange("visitIntervalSeconds", 1200, 60, 86400);
    public static final ModConfigSpec.IntValue FIRST_VISIT_DELAY = B
            .comment("Delay before the very first caravan reaches a freshly placed milestone, in seconds.")
            .defineInRange("firstVisitDelaySeconds", 150, 10, 86400);
    public static final ModConfigSpec.IntValue HALT_TIME = B
            .comment("How long a caravan stays at a milestone before moving on, in seconds.")
            .defineInRange("haltSeconds", 180, 20, 3600);
    public static final ModConfigSpec.IntValue MAX_CARAVANS = B
            .comment("Maximum number of caravans on the roads of the world (loaded or not). Caravans travel from stop to stop:",
                    "a stop that waits gets a caravan that is free nearby before a new one is ever created.")
            .defineInRange("maxActiveCaravans", 3, 1, 32);
    public static final ModConfigSpec.IntValue NEW_CARAVAN_COOLDOWN = B
            .comment("Shortest time between the arrivals of two brand new caravans in the world, in seconds.")
            .defineInRange("newCaravanCooldownSeconds", 600, 0, 86400);
    public static final ModConfigSpec.BooleanValue WAGON_ENABLED = B
            .comment("Caravans travel with a covered wagon pulled by a draft llama.")
            .define("wagonEnabled", true);
    public static final ModConfigSpec.IntValue MIN_LLAMAS = B
            .comment("Smallest number of pack llamas walking behind the wagon (the draft llama is not counted).")
            .defineInRange("minLlamas", 1, 0, 6);
    public static final ModConfigSpec.IntValue MAX_LLAMAS = B
            .comment("Largest number of pack llamas walking behind the wagon (the draft llama is not counted).")
            .defineInRange("maxLlamas", 2, 0, 6);
    public static final ModConfigSpec.IntValue LINK_RANGE = B
            .comment("Two milestones closer than this (in blocks) are linked: caravans travel from one to the other.")
            .defineInRange("milestoneLinkRange", 320, 32, 2048);
    public static final ModConfigSpec.IntValue CALL_COOLDOWN = B
            .comment("Cooldown after hiring a caravan with an emerald block, in seconds.")
            .defineInRange("hireCooldownSeconds", 600, 0, 86400);

    public static final ModConfigSpec.BooleanValue VILLAGE_STOPS = B
            .comment("Village bells are caravan stops too: caravans travel between villages without any milestone.")
            .define("villageStops", true);

    public static final ModConfigSpec.BooleanValue ROADS_ENABLED = B
            .comment("Caravans prefer walking on road blocks (block tag chroniclecaravans:roads) and are faster on them.")
            .define("roadsEnabled", true);
    public static final ModConfigSpec.DoubleValue OFF_ROAD_PENALTY = B
            .comment("Extra pathfinding cost of every block that is not a road. Higher = caravans make longer detours to stay on roads.")
            .defineInRange("offRoadPenalty", 2.0, 0.0, 16.0);
    public static final ModConfigSpec.DoubleValue ROAD_SPEED_BONUS = B
            .comment("Speed bonus on road blocks (0.3 = +30%).")
            .defineInRange("roadSpeedBonus", 0.3, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue AMBUSH_ENABLED = B
            .comment("Bandits may ambush a caravan while a player is nearby (never in Peaceful).")
            .define("ambushEnabled", true);
    public static final ModConfigSpec.DoubleValue AMBUSH_CHANCE = B
            .comment("Chance of an ambush, checked every 10 seconds while a player is within 48 blocks (0.015 = 1.5%). At most one ambush per caravan.")
            .defineInRange("ambushChance", 0.015, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue REPUTATION_ENABLED = B
            .comment("If false, every player sees the full stock of every caravan and guild experience is not tracked.")
            .define("reputationEnabled", true);
    public static final ModConfigSpec.BooleanValue ANNOUNCEMENTS = B
            .comment("Chat messages when a caravan arrives, leaves or is attacked.")
            .define("announcements", true);

    public static final ModConfigSpec.BooleanValue WEATHER = B
            .comment("Weather matters: rain slows caravans down by a quarter, thunderstorms make them camp until the storm passes.")
            .define("weatherEffects", true);
    public static final ModConfigSpec.BooleanValue NIGHT_CAMPS = B
            .comment("At night, a travelling caravan with no inn within reach makes camp until morning (you can still trade with it).")
            .define("nightCamps", true);
    public static final ModConfigSpec.BooleanValue CAMPFIRES = B
            .comment("Camping caravans light a campfire beside the road (removed when they leave).")
            .define("campfires", true);
    public static final ModConfigSpec.BooleanValue MARKET_PRICES = B
            .comment("Supply and demand: goods sold a lot are paid less, goods bought a lot cost more (it fades by half every day),",
                    "plus a daily shortage and surplus per region.")
            .define("marketPrices", true);
    public static final ModConfigSpec.BooleanValue FREIGHT = B
            .comment("Freight: parcels and loading docks, carried by the caravans from stop to stop.")
            .define("freightEnabled", true);
    public static final ModConfigSpec.BooleanValue TRADING_POSTS = B
            .comment("Trading posts trade on their own with the caravans halting nearby.")
            .define("tradingPostsEnabled", true);
    public static final ModConfigSpec.BooleanValue REDSTONE = B
            .comment("Milestones give a comparator signal (caravan coming / halted) and a redstone pulse calls a caravan,",
                    "paid with an emerald block from a container next to the milestone.")
            .define("redstoneMilestones", true);

    public static final ModConfigSpec SPEC = B.build();

    private CCConfig() {}
}
