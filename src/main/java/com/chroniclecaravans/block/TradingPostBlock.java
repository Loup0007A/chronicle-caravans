package com.chroniclecaravans.block;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//#if !mc26
import net.minecraft.world.level.block.state.properties.DirectionProperty;
//#endif
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The trading post: a market stall that trades on its own with the caravans halting nearby. */
public class TradingPostBlock extends Block implements EntityBlock {
    //#if mc26
    //$$ public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#else
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    //#endif
    /** The counter, and the sign board at its back (the front faces FACING). */
    private static final VoxelShape COUNTER = Block.box(0, 0, 0, 16, 11, 16);
    private static final VoxelShape NORTH = Shapes.or(COUNTER, Block.box(0, 11, 12, 16, 16, 16));
    private static final VoxelShape SOUTH = Shapes.or(COUNTER, Block.box(0, 11, 0, 16, 16, 4));
    private static final VoxelShape WEST = Shapes.or(COUNTER, Block.box(12, 11, 0, 16, 16, 16));
    private static final VoxelShape EAST = Shapes.or(COUNTER, Block.box(0, 11, 0, 4, 16, 16));

    public TradingPostBlock(Properties properties) {
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
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradingPostBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ChronicleCaravans.POST_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TradingPostBlockEntity>) TradingPostBlockEntity::serverTick;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof TradingPostBlockEntity post) post.setOwner(placer.getUUID(), placer.getName().getString());
    }

    //#if !mc26
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TradingPostBlockEntity post) {
            net.minecraft.world.Containers.dropContents(level, pos, post);
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
        return List.of(new ItemStack(ChronicleCaravans.POST_ITEM.get()));
    }

    //#if mc120
    //$$ @Override
    //$$ public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    //$$     if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TradingPostBlockEntity post) sp.openMenu(post);
    //$$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean hasAnalogOutputSignal(BlockState state) {
    //$$     return true;
    //$$ }
    //$$
    //$$ @Override
    //$$ public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    //$$     return level.getBlockEntity(pos) instanceof TradingPostBlockEntity post ? post.signal() : 0;
    //$$ }
    //#else
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TradingPostBlockEntity post) sp.openMenu(post);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    //#if mc26
    //$$ @Override
    //$$ protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
    //#else
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    //#endif
        return level.getBlockEntity(pos) instanceof TradingPostBlockEntity post ? post.signal() : 0;
    }
    //#endif
}
