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

    public MilestoneBlock(Properties properties) {
        super(properties);
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
        Compat.setLore(st, List.of(Component.translatable("item.chroniclecaravans.milestone.kept", minutes).withStyle(ChatFormatting.GRAY)));
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
            if (CaravanSpawner.caravanHeadingTo(server, stop.pos) != null) {
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
