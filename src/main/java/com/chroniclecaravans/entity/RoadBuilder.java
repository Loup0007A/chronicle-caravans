package com.chroniclecaravans.entity;

import java.util.UUID;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.RoadWorks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A road crew hired from a caravan: it walks from one stop to another and lays the road as it goes.
 * It cannot be hurt and waits when the land ahead is not loaded.
 */
public class RoadBuilder extends PathfinderMob {
    private BlockPos from = BlockPos.ZERO, to = BlockPos.ZERO;
    private double progress = 2.0;
    private UUID owner;
    private boolean paved, fast;

    public RoadBuilder(EntityType<? extends RoadBuilder> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.chroniclecaravans.road_builder");
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public double progress() {
        return progress;
    }

    public BlockPos from() {
        return from;
    }

    public BlockPos to() {
        return to;
    }

    /** Sends a crew from {@code from} to {@code to}. */
    public static RoadBuilder start(ServerLevel level, BlockPos from, BlockPos to, UUID owner, boolean paved, boolean fast) {
        RoadBuilder b = Compat.create(ChronicleCaravans.BUILDER.get(), level);
        if (b == null) return null;
        b.from = from.immutable();
        b.to = to.immutable();
        b.owner = owner;
        b.paved = paved;
        b.fast = fast;
        double[] p = RoadWorks.at(from, to, b.progress);
        float yaw = (float) (Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX())) - 90.0);
        b.moveTo(p[0], RoadWorks.ground(level, (int) Math.floor(p[0]), (int) Math.floor(p[1])).getY() + 1, p[1], yaw, 0F);
        b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(paved ? Items.IRON_PICKAXE : Items.IRON_SHOVEL));
        b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        for (EquipmentSlot s : EquipmentSlot.values()) b.setDropChance(s, 0F);
        level.addFreshEntity(b);
        level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 1, b.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        return b;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) return;
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % (fast ? 4 : 8) != 0) return;
        double len = RoadWorks.length(from, to);
        if (len < 1 || progress >= len - 2) {
            finish(level);
            return;
        }
        if (!RoadWorks.loaded(level, from, to, progress + 2)) return;
        int y = RoadWorks.buildSlice(level, from, to, progress, paved);
        double[] p = RoadWorks.at(from, to, progress);
        float yaw = (float) (Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX())) - 90.0);
        setPos(p[0], y, p[1]);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        //#if mc26
        //$$ swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        //#else
        swing(InteractionHand.MAIN_HAND, true);
        //#endif
        if (tickCount % 16 == 0) {
            level.playSound(null, blockPosition(), paved ? SoundEvents.STONE_PLACE : SoundEvents.GRAVEL_PLACE, SoundSource.NEUTRAL, 0.7F, 1.0F);
        }
        progress += 1.0;
    }

    private void finish(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        CaravanData.Route r = data.routeOrCreate(from, to);
        r.built = true;
        data.setDirty();
        if (owner != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            CaravanData.Stop a = data.stop(from), b = data.stop(to);
            if (p != null) {
                p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.road_done",
                        a == null ? Component.literal("?") : a.displayName(), b == null ? Component.literal("?") : b.displayName()).withStyle(ChatFormatting.GREEN));
            }
            data.addXp(level.getServer(), owner, 5, "reason.chroniclecaravans.road");
        }
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.VILLAGER_WORK_MASON, SoundSource.NEUTRAL, 1.0F, 1.0F);
        discard();
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //$$     return source.getEntity() instanceof Player p && p.isCreative() && super.hurtServer(level, source, amount);
    //$$ }
    //#else
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.getEntity() instanceof Player p && p.isCreative() && super.hurt(source, amount);
    }
    //#endif

    //#if mc26
    //$$ @Override
    //$$ protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     super.addAdditionalSaveData(out);
    //$$     CompoundTag tag = new CompoundTag();
    //$$     writeJob(tag);
    //$$     out.store("Road", CompoundTag.CODEC, tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    //$$     super.readAdditionalSaveData(in);
    //$$     in.read("Road", CompoundTag.CODEC).ifPresent(this::readJob);
    //$$ }
    //#else
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeJob(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readJob(tag);
    }
    //#endif

    private void writeJob(CompoundTag tag) {
        tag.putLong("From", from.asLong());
        tag.putLong("To", to.asLong());
        tag.putDouble("Progress", progress);
        if (owner != null) Nbt.putUUID(tag, "Owner", owner);
        tag.putBoolean("Paved", paved);
        tag.putBoolean("Fast", fast);
    }

    private void readJob(CompoundTag tag) {
        from = BlockPos.of(Nbt.getLong(tag, "From"));
        to = BlockPos.of(Nbt.getLong(tag, "To"));
        progress = Nbt.getDouble(tag, "Progress");
        owner = Nbt.hasUUID(tag, "Owner") ? Nbt.getUUID(tag, "Owner") : null;
        paved = Nbt.getBoolean(tag, "Paved");
        fast = Nbt.getBoolean(tag, "Fast");
    }
}
