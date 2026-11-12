package com.chroniclecaravans.item;

import com.chroniclecaravans.menu.AtlasMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
//#if !mc26
import net.minecraft.world.InteractionResultHolder;
//#endif
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The caravan atlas: a map of the stops, inns and routes, where they can be renamed. */
public class AtlasItem extends Item {
    public AtlasItem(Properties properties) {
        super(properties);
    }

    public static void open(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AtlasMenu(id, inv), Component.translatable("gui.chroniclecaravans.atlas")));
    }

    @Override
    //#if mc26
    //$$ public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //$$     if (player instanceof ServerPlayer sp) open(sp);
    //$$     return InteractionResult.SUCCESS;
    //$$ }
    //#else
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) open(sp);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    //#endif
}
