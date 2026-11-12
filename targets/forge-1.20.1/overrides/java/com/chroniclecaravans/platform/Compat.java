package com.chroniclecaravans.platform;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

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
        boolean none = Math.abs(bonus) <= 1e-6;
        if (current != null && (none || current.getAmount() != bonus)) speed.removeModifier(ROAD_SPEED);
        if (!none && speed.getModifier(ROAD_SPEED) == null) {
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

    public static MerchantOffer offer2(ItemStack a, ItemStack b, ItemStack result, int maxUses, int xp) {
        return new MerchantOffer(a.copy(), b.copy(), result, maxUses, xp, 0.05F);
    }

    public static ItemStack enchantedBook(ServerLevel level, String id, int lvl) {
        Enchantment e = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(new net.minecraft.resources.ResourceLocation(id));
        return e == null ? ItemStack.EMPTY : net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, lvl));
    }

    public static int enchantmentLevel(ServerLevel level, String id, ItemStack stack) {
        Enchantment e = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(new net.minecraft.resources.ResourceLocation(id));
        return e == null ? 0 : net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(e, stack);
    }

    public static CompoundTag data(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null || !t.contains("ChronicleCaravans") ? null : t.getCompound("ChronicleCaravans").copy();
    }

    public static void setData(ItemStack stack, CompoundTag tag) {
        stack.getOrCreateTag().put("ChronicleCaravans", tag);
    }

    public static void setLore(ItemStack stack, List<Component> lines) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Component c : lines) list.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
        stack.getOrCreateTagElement("display").put("Lore", list);
    }

    public static void setName(ItemStack stack, Component name) {
        stack.setHoverName(name);
    }

    public static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    public static boolean isNight(Level level) {
        return level.isNight();
    }

    /** An operator (permission level 2). */
    public static boolean isOp(net.minecraft.server.level.ServerPlayer p) {
        return p.hasPermissions(2);
    }

    public static CompoundTag saveStack(Level level, ItemStack stack) {
        return stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag());
    }

    public static ItemStack loadStack(Level level, CompoundTag tag) {
        return tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag);
    }
}
