package com.chroniclecaravans;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Reputation ranks with the caravan guilds. */
public enum Tier {
    STRANGER(0, ChatFormatting.GRAY),
    ACQUAINTANCE(10, ChatFormatting.WHITE),
    PARTNER(30, ChatFormatting.GREEN),
    PATRON(75, ChatFormatting.AQUA),
    LEGEND(150, ChatFormatting.GOLD);

    public final int threshold;
    public final ChatFormatting color;

    Tier(int threshold, ChatFormatting color) {
        this.threshold = threshold;
        this.color = color;
    }

    public static Tier of(int reputation) {
        Tier best = STRANGER;
        for (Tier t : values()) if (reputation >= t.threshold) best = t;
        return best;
    }

    public Tier next() {
        return this == LEGEND ? null : values()[ordinal() + 1];
    }

    /** Price cut on everything a caravan sells. */
    public float discount() {
        return switch (this) {
            case PATRON -> 0.15F;
            case LEGEND -> 0.25F;
            default -> 0F;
        };
    }

    public MutableComponent label() {
        return Component.translatable("tier.chroniclecaravans." + name().toLowerCase()).withStyle(color);
    }
}
