package com.chroniclecaravans.block;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.world.CaravanData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//#if !mc26
import net.minecraft.world.level.block.state.properties.DirectionProperty;
//#endif
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The inn sign. Put it up next to beds: at nightfall, caravans within reach come and spend the night here.
 * In the morning they restock, and pay the innkeeper (in the chest or barrel next to the sign).
 */
public class InnSignBlock extends Block {
    //#if mc26
    //$$ public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#else
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#endif
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    /** Beds this close to the sign make it an inn. */
    public static final int BED_RANGE = 8;

    public InnSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Number of beds around the sign. */
    public static int beds(Level level, BlockPos pos) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-BED_RANGE, -3, -BED_RANGE), pos.offset(BED_RANGE, 3, BED_RANGE))) {
            if (level.getBlockState(p).is(BlockTags.BEDS)) n++;
        }
        // Every bed is two blocks
        return n / 2;
    }

    /** A real inn: the sign is still there and has a bed nearby. */
    public static boolean open(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ChronicleCaravans.INN_SIGN.get()) && beds(level, pos) > 0;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) return;
        CaravanData data = CaravanData.get(server);
        CaravanData.Inn inn = data.addInn(pos, placer == null ? null : placer.getUUID(), placer == null ? "" : placer.getName().getString());
        Component custom = Compat.customName(stack);
        if (custom != null) inn.name = custom.getString();
        if (placer instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.inn_placed", inn.displayName()).withStyle(ChatFormatting.GREEN));
            if (beds(level, pos) == 0) p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.inn_no_bed", BED_RANGE).withStyle(ChatFormatting.GOLD));
        }
    }

    //#if mc26
    //$$ @Override
    //$$ protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //$$     super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    //$$     if (!level.getBlockState(pos).is(this)) CaravanData.get(level).removeInn(pos);
    //$$ }
    //#else
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) CaravanData.get(server).removeInn(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endif

    @Override
    //#if mc120
    //$$ public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#else
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#endif
        return List.of(new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()));
    }

    //#if mc120
    //$$ @Override
    //$$ public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    //$$     info(level, pos, player);
    //$$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$$ }
    //#else
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        info(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    //#endif

    private static void info(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p)) return;
        CaravanData.Inn inn = CaravanData.get(server).inn(pos);
        if (inn == null) {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.overworld_only").withStyle(ChatFormatting.RED));
            return;
        }
        p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.inn_header", inn.displayName()).withStyle(ChatFormatting.GOLD));
        int beds = beds(level, pos);
        p.sendSystemMessage(Component.translatable(beds > 0 ? "msg.chroniclecaravans.inn_beds" : "msg.chroniclecaravans.inn_no_bed", beds > 0 ? beds : BED_RANGE)
                .withStyle(beds > 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.inn_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The innkeeper's fee goes into a container next to the sign (or on the ground). */
    public static void pay(ServerLevel level, BlockPos pos, ItemStack fee) {
        if (!level.isLoaded(pos)) return;
        for (Direction d : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(d));
            if (be instanceof Container c) {
                fee = insert(c, fee);
                if (fee.isEmpty()) return;
            }
        }
        Block.popResource(level, pos.above(), fee);
    }

    private static ItemStack insert(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack in = c.getItem(i);
            if (in.isEmpty()) {
                c.setItem(i, stack.copy());
                stack = ItemStack.EMPTY;
            } else if (Compat.sameItem(in, stack) && in.getCount() < in.getMaxStackSize()) {
                int k = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(k);
                stack = stack.copyWithCount(stack.getCount() - k);
            }
        }
        c.setChanged();
        return stack;
    }
}
