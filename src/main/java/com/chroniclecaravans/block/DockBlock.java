package com.chroniclecaravans.block;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.Logistics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The loading dock block. Name it in an anvil (or in its screen) with the name of a stop: it becomes a sending
 * dock; without a name it receives deliveries. It must stand within 8 blocks of a milestone.
 */
public class DockBlock extends Block implements EntityBlock {
    public static final BooleanProperty OUTGOING = BooleanProperty.create("outgoing");

    public DockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OUTGOING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTGOING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DockBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof DockBlockEntity dock)) return;
        CaravanData.get(server).addDock(pos);
        if (placer != null) dock.setOwner(placer.getUUID(), placer.getName().getString());
        Component custom = Compat.customName(stack);
        dock.setAddress(custom == null ? "" : custom.getString());
        if (placer instanceof ServerPlayer p) {
            CaravanData.Stop stop = Logistics.stopOf(server, pos);
            if (stop == null) {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.dock_no_stop", Logistics.DOCK_RANGE).withStyle(ChatFormatting.GOLD));
            } else {
                p.sendSystemMessage(Component.translatable(dock.outgoing() ? "msg.chroniclecaravans.dock_sending" : "msg.chroniclecaravans.dock_receiving",
                        stop.displayName(), dock.address()).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    //#if mc26
    //$$ @Override
    //$$ protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //$$     super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    //$$     if (!level.getBlockState(pos).is(this)) CaravanData.get(level).removeDock(pos);
    //$$ }
    //#else
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DockBlockEntity dock) net.minecraft.world.Containers.dropContents(level, pos, dock);
            if (level instanceof ServerLevel server) CaravanData.get(server).removeDock(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endif

    @Override
    //#if mc120
    //$$ public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#else
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#endif
        return List.of(new ItemStack(ChronicleCaravans.DOCK_ITEM.get()));
    }

    //#if mc120
    //$$ @Override
    //$$ public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    //$$     open(level, pos, player);
    //$$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$$ }
    //#else
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        open(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    //#endif

    private static void open(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DockBlockEntity dock) sp.openMenu(dock);
    }

    //#if mc120
    //$$ @Override
    //$$ public boolean hasAnalogOutputSignal(BlockState state) {
    //$$     return true;
    //$$ }
    //$$
    //$$ @Override
    //$$ public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    //$$     return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    //$$ }
    //#elif mc26
    //$$ @Override
    //$$ protected boolean hasAnalogOutputSignal(BlockState state) {
    //$$     return true;
    //$$ }
    //$$
    //$$ @Override
    //$$ protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
    //$$     return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    //$$ }
    //#else
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
    //#endif
}
