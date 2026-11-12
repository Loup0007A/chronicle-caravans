package com.chroniclecaravans.block;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.Tier;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import com.chroniclecaravans.platform.Compat;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The caravan milestone: a lit stone marker. Placing one registers a stop that caravans visit;
 * two milestones within range are linked and caravans travel from one to the other.
 */
public class MilestoneBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

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
        long first = server.getGameTime() + CCConfig.FIRST_VISIT_DELAY.get() * 20L;
        CaravanData.Stop stop = data.addStop(pos, placer == null ? null : placer.getUUID(),
                placer == null ? "" : placer.getName().getString(), custom == null ? "" : custom.getString(), first);
        if (placer instanceof ServerPlayer p) {
            CaravanData.Stop linked = null;
            double best = Double.MAX_VALUE;
            for (CaravanData.Stop s : data.stops()) {
                if (s == stop) continue;
                double d = s.pos.distSqr(pos);
                if (d < best && d <= (double) CCConfig.LINK_RANGE.get() * CCConfig.LINK_RANGE.get()) {
                    best = d;
                    linked = s;
                }
            }
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_placed", stop.displayName()).withStyle(ChatFormatting.GREEN));
            if (linked != null) {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_linked", linked.displayName(), (int) Math.sqrt(best)).withStyle(ChatFormatting.GRAY));
            } else {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_alone", CCConfig.LINK_RANGE.get()).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    //#if !mc26
    // (26.x has no onRemove: a broken milestone is dropped by the spawner when it next checks the stop)
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            CaravanData.get(server).removeStop(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endif

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
            int rep = data.reputation(p.getUUID());
            Tier tier = Tier.of(rep);
            Tier next = tier.next();
            if (CCConfig.REPUTATION_ENABLED.get()) {
                if (next == null) p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep_max", rep, tier.label()).withStyle(ChatFormatting.GRAY));
                else p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep", rep, tier.label(), next.threshold - rep, next.label()).withStyle(ChatFormatting.GRAY));
            }
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.stop_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** An emerald block hires a caravan right away (with a cooldown). */
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
                stop.nextVisit = now + 200 + server.getRandom().nextInt(300);
                data.setDirty();
                server.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.8F);
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.hired", stop.displayName()).withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
