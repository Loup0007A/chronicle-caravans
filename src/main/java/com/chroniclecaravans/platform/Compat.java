package com.chroniclecaravans.platform;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/**
 * Everything that changes from one version of Minecraft to another, behind one small API.
 * This is the NeoForge 1.21.1 variant; every other target has its own copy in targets/&lt;target&gt;/overrides/.
 */
public final class Compat {
    private Compat() {}

    private static final ResourceLocation ROAD_SPEED = ChronicleCaravans.id("road_speed");

    // ---------------------------------------------------------------- entities

    public static <T extends Entity> T create(EntityType<T> type, Level level) {
        return type.create(level);
    }

    /** Equipment, variants...: what a mob gets when it spawns naturally. */
    public static void finalizeSpawn(Mob mob, ServerLevel level, BlockPos at) {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
    }

    public static void unleash(Mob mob) {
        mob.dropLeash(true, false);
    }

    /** Extra walking speed (a fraction: 0.3 = +30%), or none. */
    public static void speedBonus(LivingEntity e, double bonus) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (bonus > 0) speed.addOrUpdateTransientModifier(new AttributeModifier(ROAD_SPEED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(ROAD_SPEED);
    }

    // ---------------------------------------------------------------- items and trades

    /** {@code count} × {@code cost} for {@code result}. */
    public static MerchantOffer offer(Item cost, int count, ItemStack result, int maxUses, int xp) {
        return new MerchantOffer(new ItemCost(cost, count), result, maxUses, xp, 0.05F);
    }

    /** The name given to an item in an anvil, or null. */
    public static Component customName(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_NAME);
    }

    public static SoundEvent raidHorn() {
        return SoundEvents.RAID_HORN.value();
    }
}
