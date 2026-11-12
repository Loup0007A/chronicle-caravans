package com.chroniclecaravans.platform;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/** Version-specific code: Forge 1.20.1 variant (see src/main for the reference). */
public final class Compat {
    private Compat() {}

    private static final UUID ROAD_SPEED = UUID.fromString("6b1f8a4e-2c37-4f0a-9d1e-c4a7a0c3a1b2");

    public static <T extends Entity> T create(EntityType<T> type, Level level) {
        return type.create(level);
    }

    public static void finalizeSpawn(Mob mob, ServerLevel level, BlockPos at) {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
    }

    public static void unleash(Mob mob) {
        mob.dropLeash(true, false);
    }

    public static void speedBonus(LivingEntity e, double bonus) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier current = speed.getModifier(ROAD_SPEED);
        if (current != null && (bonus <= 0 || current.getAmount() != bonus)) speed.removeModifier(ROAD_SPEED);
        if (bonus > 0 && speed.getModifier(ROAD_SPEED) == null) {
            speed.addTransientModifier(new AttributeModifier(ROAD_SPEED, "Road speed", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public static MerchantOffer offer(Item cost, int count, ItemStack result, int maxUses, int xp) {
        return new MerchantOffer(new ItemStack(cost, count), result, maxUses, xp, 0.05F);
    }

    public static Component customName(ItemStack stack) {
        return stack.hasCustomHoverName() ? stack.getHoverName() : null;
    }

    public static SoundEvent raidHorn() {
        return SoundEvents.RAID_HORN.value();
    }
}
