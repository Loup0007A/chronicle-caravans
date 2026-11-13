package com.chroniclecaravans.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//#if !mc120 && !mc26
import net.minecraft.world.ItemInteractionResult;
//#endif
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The caravan milestone: a lit stone marker. Placing one registers a stop that caravans visit.
 * Broken, it gives back its lantern only; with Silk Touch it comes back whole and keeps its schedule
 * (so breaking and placing it again never brings a caravan sooner).
 */
public class MilestoneBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    /** Schedules of milestones being broken by a player, until their drops are made. */
    private static final Map<BlockPos, CompoundTag> BREAKING = new HashMap<>();

    /** Powered by redstone (a rising edge calls a caravan). */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Relay tier, for the looks (gold collar, pennants): the stop keeps the real value. */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 4);

    public MilestoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, TIER);
    }

    // ---------------------------------------------------------------- relay tiers

    public static final int UPGRADED = 0, MAX_TIER = 1, NEEDS_POSTS = 2, NOT_A_STOP = 3, DISABLED = 4;

    /**
     * A relay charter raises the milestone by one tier: one more caravan at once, if there is a hitching post
     * nearby for each caravan beyond the first. Returns {@link #UPGRADED} or why not.
     */
    public static int upgrade(ServerLevel level, BlockPos pos) {
        if (!CCConfig.RELAYS.get()) return DISABLED;
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop stop = data.stop(pos);
        if (stop == null || stop.village) return NOT_A_STOP;
        if (stop.tier >= CCConfig.MAX_TIER.get()) return MAX_TIER;
        int posts = data.postsNear(level, pos, HitchingPostBlock.RANGE).size();
        if (posts < stop.tier) return NEEDS_POSTS;
        stop.tier++;
        data.setDirty();
        showTier(level, pos, stop.tier);
        return UPGRADED;
    }

    /** The milestone shows its tier. */
    public static void showTier(Level level, BlockPos pos, int tier) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(TIER)) level.setBlock(pos, state.setValue(TIER, Math.max(1, Math.min(4, tier))), Block.UPDATE_CLIENTS);
    }

    private static void charter(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p)) return;
        int result = upgrade(server, pos);
        CaravanData.Stop stop = CaravanData.get(server).stop(pos);
        switch (result) {
            case UPGRADED -> {
                if (!p.getAbilities().instabuild) stack.shrink(1);
                server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.2F);
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.5, 0.6, 0.5, 0.0);
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.relay_upgraded", stop.displayName(), stop.tier,
                        CaravanSpawner.capacity(server, stop)).withStyle(ChatFormatting.GREEN));
            }
            case MAX_TIER -> p.displayClientMessage(Component.translatable("msg.chroniclecaravans.relay_max").withStyle(ChatFormatting.GOLD), true);
            case NEEDS_POSTS -> {
                Component msg = Component.translatable("msg.chroniclecaravans.relay_posts", stop.tier, HitchingPostBlock.RANGE).withStyle(ChatFormatting.RED);
                p.displayClientMessage(msg, true);
            }
            case DISABLED -> p.displayClientMessage(Component.translatable("msg.chroniclecaravans.relay_disabled").withStyle(ChatFormatting.RED), true);
            default -> p.displayClientMessage(Component.translatable("msg.chroniclecaravans.overworld_only").withStyle(ChatFormatting.RED), true);
        }
    }

    // ---------------------------------------------------------------- redstone

    //#if mc120
    //$$ @Override
    //$$ public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
    //$$     super.neighborChanged(state, level, pos, block, from, moving);
    //$$     redstone(state, level, pos);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean hasAnalogOutputSignal(BlockState state) {
    //$$     return true;
    //$$ }
    //$$
    //$$ @Override
    //$$ public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    //$$     return level instanceof ServerLevel s ? CaravanSpawner.signal(s, pos) : 0;
    //$$ }
    //#elif mc26
    //$$ @Override
    //$$ protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.Orientation orientation, boolean moving) {
    //$$     super.neighborChanged(state, level, pos, block, orientation, moving);
    //$$     redstone(state, level, pos);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected boolean hasAnalogOutputSignal(BlockState state) {
    //$$     return true;
    //$$ }
    //$$
    //$$ @Override
    //$$ protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
    //$$     return level instanceof ServerLevel s ? CaravanSpawner.signal(s, pos) : 0;
    //$$ }
    //#else
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        redstone(state, level, pos);
    }

    /** Comparator: 0 nothing expected, 1 to 7 a caravan on its way (the closer, the stronger), 15 a caravan halted here. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level instanceof ServerLevel s ? CaravanSpawner.signal(s, pos) : 0;
    }
    //#endif

    private static void redstone(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide() || !state.hasProperty(POWERED)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level instanceof ServerLevel server) redstoneCall(server, pos);
    }

    /** A redstone pulse calls a caravan, paid with an emerald block taken from a container next to the milestone. */
    public static boolean redstoneCall(ServerLevel level, BlockPos pos) {
        if (!CCConfig.REDSTONE.get()) return false;
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop stop = data.stop(pos);
        if (stop == null || CaravanSpawner.full(level, stop)) return false;
        long now = level.getGameTime();
        if (stop.lastCall + CCConfig.CALL_COOLDOWN.get() * 20L > now) return false;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (!(level.getBlockEntity(pos.relative(d)) instanceof net.minecraft.world.Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (!c.getItem(i).is(Items.EMERALD_BLOCK)) continue;
                c.removeItem(i, 1);
                c.setChanged();
                stop.lastCall = now;
                stop.nextVisit = now;
                stop.hired = true;
                data.setDirty();
                level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.8F);
                return true;
            }
        }
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) return;
        if (server.dimension() != Level.OVERWORLD) {
            if (placer instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.overworld_only").withStyle(ChatFormatting.RED));
            return;
        }
        CaravanData data = CaravanData.get(server);
        Component custom = Compat.customName(stack);
        CompoundTag saved = Compat.data(stack);
        long now = server.getGameTime();
        long first = now + CCConfig.FIRST_VISIT_DELAY.get() * 20L;
        if (saved != null && saved.contains("Remaining")) first = now + Nbt.getLong(saved, "Remaining");
        String name = custom != null ? custom.getString() : saved != null ? Nbt.getString(saved, "Name") : "";
        CaravanData.Stop stop = data.addStop(pos, placer == null ? null : placer.getUUID(),
                placer == null ? "" : placer.getName().getString(), name, first);
        if (saved != null && saved.contains("SinceCall")) stop.lastCall = now - Nbt.getLong(saved, "SinceCall");
        if (saved != null && saved.contains("Tier")) {
            stop.tier = Math.max(1, Math.min(CCConfig.MAX_TIER.get(), Nbt.getInt(saved, "Tier")));
            showTier(server, pos, stop.tier);
        }
        if (placer instanceof ServerPlayer p) {
            List<CaravanData.Stop> linked = data.linked(pos);
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_placed", stop.displayName()).withStyle(ChatFormatting.GREEN));
            if (!linked.isEmpty()) {
                CaravanData.Stop l = linked.get(0);
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_linked", l.displayName(), (int) Math.sqrt(l.pos.distSqr(pos))).withStyle(ChatFormatting.GRAY));
            } else {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_alone", CCConfig.LINK_RANGE.get()).withStyle(ChatFormatting.GRAY));
            }
            if (saved != null) {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_restored", Math.max(1, (int) Math.round((first - now) / 1200.0))).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ---------------------------------------------------------------- breaking

    /** What the milestone remembers when it is picked up with Silk Touch. */
    public static CompoundTag snapshot(ServerLevel level, BlockPos pos) {
        CaravanData.Stop stop = CaravanData.get(level).stop(pos);
        CompoundTag t = new CompoundTag();
        long now = level.getGameTime();
        t.putLong("Remaining", stop == null ? CCConfig.FIRST_VISIT_DELAY.get() * 20L : Math.max(0, stop.nextVisit - now));
        t.putLong("SinceCall", stop == null ? Long.MAX_VALUE / 4 : Math.min(Long.MAX_VALUE / 4, now - stop.lastCall));
        t.putString("Name", stop == null ? "" : stop.name);
        t.putInt("Tier", stop == null ? 1 : stop.tier);
        // A unique id: two picked-up milestones never stack (each keeps its own schedule)
        t.putLong("Id", level.getRandom().nextLong());
        return t;
    }

    /** The item a milestone gives back: its lantern, or (Silk Touch) itself with its schedule. */
    public static ItemStack drop(ServerLevel level, CompoundTag snapshot, boolean silkTouch) {
        if (!silkTouch) return new ItemStack(Items.LANTERN);
        ItemStack st = new ItemStack(ChronicleCaravans.MILESTONE_ITEM.get());
        Compat.setData(st, snapshot);
        String name = Nbt.getString(snapshot, "Name");
        if (!name.isEmpty()) Compat.setName(st, Component.literal(name));
        long minutes = Math.max(1, Math.round(Nbt.getLong(snapshot, "Remaining") / 1200.0));
        int tier = Nbt.getInt(snapshot, "Tier");
        Compat.setLore(st, tier > 1
                ? List.of(Component.translatable("item.chroniclecaravans.milestone.kept", minutes).withStyle(ChatFormatting.GRAY),
                        Component.translatable("item.chroniclecaravans.milestone.tier", tier).withStyle(ChatFormatting.GOLD))
                : List.of(Component.translatable("item.chroniclecaravans.milestone.kept", minutes).withStyle(ChatFormatting.GRAY)));
        return st;
    }

    @Override
    //#if mc120
    //$$ public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    //$$     remember(level, pos);
    //$$     super.playerWillDestroy(level, pos, state, player);
    //$$ }
    //#else
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        remember(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }
    //#endif

    private static void remember(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            if (BREAKING.size() > 64) BREAKING.clear();
            BREAKING.put(pos.immutable(), snapshot(server, pos));
        }
    }

    @Override
    //#if mc120
    //$$ public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#else
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#endif
        ServerLevel level = builder.getLevel();
        Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
        BlockPos pos = origin == null ? BlockPos.ZERO : BlockPos.containing(origin);
        var tool = builder.getOptionalParameter(LootContextParams.TOOL);
        boolean silk = tool != null && Compat.enchantmentLevel(level, "minecraft:silk_touch", tool) > 0;
        CompoundTag snap = BREAKING.remove(pos);
        if (snap == null) snap = snapshot(level, pos);
        return List.of(drop(level, snap, silk));
    }

    //#if mc26
    //$$ @Override
    //$$ protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //$$     super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    //$$     if (!level.getBlockState(pos).is(this)) CaravanData.get(level).removeStopAndRoutes(pos);
    //$$ }
    //#else
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            CaravanData.get(server).removeStopAndRoutes(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endif

    // ---------------------------------------------------------------- use

    //#if mc120
    //$$ @Override
    //$$ public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    //$$     ItemStack stack = player.getItemInHand(hand);
    //$$     if (stack.is(Items.EMERALD_BLOCK)) hire(stack, level, pos, player);
    //$$     else if (stack.is(ChronicleCaravans.CHARTER.get())) charter(stack, level, pos, player);
    //$$     else info(level, pos, player);
    //$$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$$ }
    //#elif mc26
    //$$ @Override
    //$$ protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    //$$     info(level, pos, player);
    //$$     return InteractionResult.SUCCESS;
    //$$ }
    //$$
    //$$ @Override
    //$$ protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    //$$     if (stack.is(ChronicleCaravans.CHARTER.get())) {
    //$$         charter(stack, level, pos, player);
    //$$         return InteractionResult.SUCCESS;
    //$$     }
    //$$     if (!stack.is(Items.EMERALD_BLOCK)) return InteractionResult.TRY_WITH_EMPTY_HAND;
    //$$     hire(stack, level, pos, player);
    //$$     return InteractionResult.SUCCESS;
    //$$ }
    //#else
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        info(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ChronicleCaravans.CHARTER.get())) {
            charter(stack, level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!stack.is(Items.EMERALD_BLOCK)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        hire(stack, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    //#endif

    /** Right-click: when is the next caravan due, and where the player stands with the guilds. */
    private static void info(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
            CaravanData data = CaravanData.get(server);
            CaravanData.Stop stop = data.stop(pos);
            if (stop == null) {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.overworld_only").withStyle(ChatFormatting.RED));
                return;
            }
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_header", stop.displayName()).withStyle(ChatFormatting.GOLD));
            if (CCConfig.RELAYS.get()) {
                int posts = data.postsNear(server, pos, HitchingPostBlock.RANGE).size();
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_relay", stop.tier, CaravanSpawner.capacity(server, stop), posts)
                        .withStyle(ChatFormatting.GRAY));
            }
            if (CaravanSpawner.caravanHeadingTo(server, stop.pos) != null) {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_incoming").withStyle(ChatFormatting.GREEN));
            } else {
                long ticks = Math.max(0, stop.nextVisit - server.getGameTime());
                int minutes = (int) Math.max(1, Math.round(ticks / 1200.0));
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_next", minutes).withStyle(ChatFormatting.GRAY));
            }
            if (CCConfig.REPUTATION_ENABLED.get()) {
                int xp = data.xp(p.getUUID());
                int lvl = Guild.levelOf(xp);
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep", Guild.describe(xp, data.skills(p.getUUID())),
                        Guild.progress(xp), Guild.needed(lvl)).withStyle(ChatFormatting.GRAY));
            }
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** An emerald block hires a caravan: the first free one comes (with a cooldown). */
    private static void hire(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
            CaravanData data = CaravanData.get(server);
            CaravanData.Stop stop = data.stop(pos);
            if (stop == null) return;
            long now = server.getGameTime();
            long wait = stop.lastCall + CCConfig.CALL_COOLDOWN.get() * 20L - now;
            if (CaravanSpawner.full(server, stop)) {
                p.displayClientMessage(Component.translatable("msg.chroniclecaravans.stop_incoming").withStyle(ChatFormatting.GREEN), true);
            } else if (wait > 0) {
                p.displayClientMessage(Component.translatable("msg.chroniclecaravans.hire_cooldown", (int) Math.ceil(wait / 1200.0)).withStyle(ChatFormatting.RED), true);
            } else {
                if (!p.getAbilities().instabuild) stack.shrink(1);
                stop.lastCall = now;
                stop.nextVisit = now;
                stop.hired = true;
                data.setDirty();
                server.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.8F);
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.hired", stop.displayName()).withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
