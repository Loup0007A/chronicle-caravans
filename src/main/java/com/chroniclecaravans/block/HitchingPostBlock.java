package com.chroniclecaravans.block;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The hitching post: a parking place for one caravan train. Around an upgraded milestone, every caravan beyond
 * the first parks at a post and walks to the milestone; around an inn, the caravans park here for the night.
 * Leads tie to it like to a fence.
 */
public class HitchingPostBlock extends Block {
    /** Posts this close to a milestone (or an inn sign) belong to it. */
    public static final int RANGE = 16;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    public HitchingPostBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) return;
        CaravanData data = CaravanData.get(server);
        data.addPost(pos);
        if (!(placer instanceof ServerPlayer p)) return;
        CaravanData.Stop stop = data.nearestStop(pos, RANGE, null);
        CaravanData.Inn inn = data.nearestInn(pos, RANGE);
        if (stop != null && !stop.village) {
            int posts = data.postsNear(server, stop.pos, RANGE).size();
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.post_stop", stop.displayName(), posts,
                    stop.tier, CaravanSpawner.capacity(server, stop)).withStyle(ChatFormatting.GREEN));
        } else if (inn != null) {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.post_inn", inn.displayName()).withStyle(ChatFormatting.GREEN));
        } else {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.post_alone", RANGE).withStyle(ChatFormatting.GOLD));
        }
    }

    //#if mc26
    //$$ @Override
    //$$ protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //$$     super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    //$$     if (!level.getBlockState(pos).is(this)) CaravanData.get(level).removePost(pos);
    //$$ }
    //#else
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) CaravanData.get(server).removePost(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endif

    @Override
    //#if mc120
    //$$ public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#else
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    //#endif
        return List.of(new ItemStack(ChronicleCaravans.HITCHING_POST_ITEM.get()));
    }
}
