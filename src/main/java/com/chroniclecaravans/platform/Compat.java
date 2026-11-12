package com.chroniclecaravans.platform;

import com.chroniclecaravans.ChronicleCaravans;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
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

    /** {@code a} (and {@code b}, if not empty) for {@code result}. */
    public static MerchantOffer offer2(ItemStack a, ItemStack b, ItemStack result, int maxUses, int xp) {
        return new MerchantOffer(new ItemCost(a.getItem(), a.getCount()),
                b.isEmpty() ? Optional.empty() : Optional.of(new ItemCost(b.getItem(), b.getCount())), result, maxUses, xp, 0.05F);
    }

    private static Optional<Holder.Reference<Enchantment>> enchantment(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id)));
    }

    /** An enchanted book (empty if the enchantment does not exist). */
    public static ItemStack enchantedBook(ServerLevel level, String id, int lvl) {
        return enchantment(level, id).map(e -> net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, lvl)))
                .orElse(ItemStack.EMPTY);
    }

    public static int enchantmentLevel(ServerLevel level, String id, ItemStack stack) {
        return enchantment(level, id).map(e -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(e, stack)).orElse(0);
    }

    /** The custom data of an item (a copy), or null. */
    public static CompoundTag data(ItemStack stack) {
        net.minecraft.world.item.component.CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    public static void setData(ItemStack stack, CompoundTag tag) {
        net.minecraft.world.item.component.CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    public static void setLore(ItemStack stack, List<Component> lines) {
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
    }

    public static void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    public static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    public static boolean isNight(Level level) {
        return level.isNight();
    }

    /** An operator (permission level 2). */
    public static boolean isOp(net.minecraft.server.level.ServerPlayer p) {
        return p.hasPermissions(2);
    }
}
