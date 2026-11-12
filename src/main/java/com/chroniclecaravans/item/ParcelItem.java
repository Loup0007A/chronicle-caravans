package com.chroniclecaravans.item;

import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.Parcel;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
//#if !mc26
import net.minecraft.world.InteractionResultHolder;
//#endif
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A parcel delivered by a caravan: right-click to open it. */
public class ParcelItem extends Item {
    public ParcelItem(Properties properties) {
        super(properties);
    }

    /** Gives the content to the player and uses up the parcel. */
    public static void open(ServerPlayer player, ItemStack stack) {
        CompoundTag data = Compat.data(stack);
        if (data == null) return;
        Parcel p = Parcel.load(data);
        int n = 0;
        for (ItemStack s : p.unpack(player.level())) {
            n++;
            if (!player.getInventory().add(s)) net.minecraft.world.level.block.Block.popResource(player.level(), player.blockPosition(), s);
        }
        stack.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 1.0F);
        Component msg = Component.translatable("msg.chroniclecaravans.parcel_opened", n, p.senderName.isEmpty() ? "?" : p.senderName).withStyle(ChatFormatting.GREEN);
        player.displayClientMessage(msg, true);
    }

    @Override
    //#if mc26
    //$$ public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //$$     if (player instanceof ServerPlayer sp) open(sp, player.getItemInHand(hand));
    //$$     return InteractionResult.SUCCESS;
    //$$ }
    //#else
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) open(sp, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    //#endif
}
