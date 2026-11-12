package com.chroniclecaravans;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Standing with the caravan guilds: experience gives levels, every level gives one skill point, and the skills
 * a player picks (three branches: trader, protector, builder) give their title.
 */
public final class Guild {
    private Guild() {}

    public static final int MAX_LEVEL = 12;
    /** Below this, caravans refuse to trade with the player. */
    public static final int OUTLAW = -30;

    public enum Branch {
        TRADER(ChatFormatting.GOLD), PROTECTOR(ChatFormatting.RED), BUILDER(ChatFormatting.AQUA);

        public final ChatFormatting color;

        Branch(ChatFormatting color) {
            this.color = color;
        }

        public String key() {
            return name().toLowerCase();
        }

        public MutableComponent label() {
            return Component.translatable("branch.chroniclecaravans." + key()).withStyle(color);
        }
    }

    /** The skill tree: four skills per branch. {@code row} is the depth in the tree, {@code col} the side at a fork. */
    public enum Skill {
        HAGGLER(Branch.TRADER, 0, 0, null, 1),
        FAIR_SELLER(Branch.TRADER, 1, -1, HAGGLER, 3),
        HIDDEN_STOCK(Branch.TRADER, 1, 1, HAGGLER, 4),
        MERCHANT_PRINCE(Branch.TRADER, 2, 0, HIDDEN_STOCK, 8),
        VETERAN_GUARDS(Branch.PROTECTOR, 0, 0, null, 1),
        BOUNTY_HUNTER(Branch.PROTECTOR, 1, -1, VETERAN_GUARDS, 3),
        WARDEN(Branch.PROTECTOR, 1, 1, VETERAN_GUARDS, 4),
        SWORN_SHIELD(Branch.PROTECTOR, 2, 0, WARDEN, 8),
        FOREMAN(Branch.BUILDER, 0, 0, null, 1),
        PAVED_ROADS(Branch.BUILDER, 1, -1, FOREMAN, 3),
        INNKEEPER(Branch.BUILDER, 1, 1, FOREMAN, 4),
        CARTOGRAPHER(Branch.BUILDER, 2, 0, PAVED_ROADS, 8);

        public final Branch branch;
        public final int row, col;
        public final Skill parent;
        public final int minLevel;

        Skill(Branch branch, int row, int col, Skill parent, int minLevel) {
            this.branch = branch;
            this.row = row;
            this.col = col;
            this.parent = parent;
            this.minLevel = minLevel;
        }

        public int bit() {
            return 1 << ordinal();
        }

        public String key() {
            return name().toLowerCase();
        }

        public MutableComponent label() {
            return Component.translatable("skill.chroniclecaravans." + key());
        }

        public MutableComponent description() {
            return Component.translatable("skill.chroniclecaravans." + key() + ".desc");
        }

        public static Skill byIndex(int i) {
            Skill[] all = values();
            return i >= 0 && i < all.length ? all[i] : null;
        }
    }

    // ---------------------------------------------------------------- levels

    /** Experience needed to go from {@code level} to the next one. */
    public static int needed(int level) {
        return 10 + 6 * level;
    }

    public static int levelOf(int xp) {
        int level = 0;
        while (level < MAX_LEVEL && xp >= needed(level)) {
            xp -= needed(level);
            level++;
        }
        return level;
    }

    /** Experience gathered towards the next level. */
    public static int progress(int xp) {
        int level = 0;
        xp = Math.max(0, xp);
        while (level < MAX_LEVEL && xp >= needed(level)) {
            xp -= needed(level);
            level++;
        }
        return level >= MAX_LEVEL ? 0 : xp;
    }

    public static int totalFor(int level) {
        int sum = 0;
        for (int i = 0; i < level; i++) sum += needed(i);
        return sum;
    }

    public static boolean has(int skills, Skill s) {
        return (skills & s.bit()) != 0;
    }

    public static int count(int skills) {
        return Integer.bitCount(skills & ((1 << Skill.values().length) - 1));
    }

    public static int points(int xp, int skills) {
        return Math.max(0, levelOf(xp) - count(skills));
    }

    /** Why a skill cannot be learnt yet (a translation key), or null if it can. */
    public static String blocker(int xp, int skills, Skill s) {
        if (has(skills, s)) return "gui.chroniclecaravans.skill.learnt";
        if (levelOf(xp) < s.minLevel) return "gui.chroniclecaravans.skill.need_level";
        if (s.parent != null && !has(skills, s.parent)) return "gui.chroniclecaravans.skill.need_parent";
        if (points(xp, skills) <= 0) return "gui.chroniclecaravans.skill.no_points";
        return null;
    }

    // ---------------------------------------------------------------- effects

    /** Price cut on everything bought from a caravan. */
    public static float discount(int skills) {
        float d = 0F;
        if (has(skills, Skill.HAGGLER)) d += 0.08F;
        if (has(skills, Skill.MERCHANT_PRINCE)) d += 0.10F;
        return d;
    }

    /** Bonus on the emeralds paid for the goods a caravan asks for. */
    public static float sellBonus(int skills) {
        return has(skills, Skill.FAIR_SELLER) ? 0.25F : 0F;
    }

    // ---------------------------------------------------------------- titles

    /** A title that tells what the player does: the branch they invested most in, and how far. */
    public static MutableComponent title(int xp, int skills) {
        int level = levelOf(xp);
        if (xp <= OUTLAW) return Component.translatable("title.chroniclecaravans.outlaw").withStyle(ChatFormatting.DARK_RED);
        int[] per = new int[Branch.values().length];
        for (Skill s : Skill.values()) if (has(skills, s)) per[s.branch.ordinal()]++;
        Branch best = null;
        int bestN = 0;
        boolean tie = false;
        for (Branch b : Branch.values()) {
            int n = per[b.ordinal()];
            if (n > bestN) {
                best = b;
                bestN = n;
                tie = false;
            } else if (n == bestN && n > 0) {
                tie = true;
            }
        }
        int rank = level >= 12 ? 4 : level >= 9 ? 3 : level >= 6 ? 2 : level >= 3 ? 1 : 0;
        if (best == null) {
            return Component.translatable("title.chroniclecaravans.none." + Math.min(rank, 1)).withStyle(level == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE);
        }
        if (tie) return Component.translatable("title.chroniclecaravans.mixed." + rank).withStyle(ChatFormatting.LIGHT_PURPLE);
        return Component.translatable("title.chroniclecaravans." + best.key() + "." + rank).withStyle(best.color);
    }

    /** "Level 4 Merchant" */
    public static MutableComponent describe(int xp, int skills) {
        return Component.translatable("msg.chroniclecaravans.level_title", levelOf(xp), title(xp, skills));
    }
}
