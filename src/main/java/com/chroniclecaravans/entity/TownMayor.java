package com.chroniclecaravans.entity;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.menu.TownMenu;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** The mayor of a merchant town: he strolls by the town hall and opens the town's ledger to whoever asks. */
public class TownMayor extends PathfinderMob {
    private BlockPos town = BlockPos.ZERO, home = BlockPos.ZERO;

    public TownMayor(EntityType<? extends TownMayor> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    public static TownMayor spawn(ServerLevel level, BlockPos door, BlockPos town) {
        TownMayor m = Compat.create(ChronicleCaravans.MAYOR.get(), level);
        if (m == null) return null;
        m.town = town.immutable();
        m.home = door.immutable();
        m.stayHome();
        m.moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 0F, 0F);
        m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WRITABLE_BOOK));
        for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0F);
        level.addFreshEntity(m);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1.5, m.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
        return m;
    }

    public BlockPos town() {
        return town;
    }

    private void stayHome() {
        //#if mc26
        //$$ setHomeTo(home, 8);
        //#else
        restrictTo(home, 8);
        //#endif
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.chroniclecaravans.town_mayor");
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer sp) TownMenu.open(sp, town);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (tickCount % 200 == 0 && level() instanceof ServerLevel level) {
            // No town any more (its milestone broken): he goes
            Town t = CaravanData.get(level).town(town);
            if (t == null || t.level <= 0 || (t.mayor != null && !t.mayor.equals(getUUID()))) {
                level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
        }
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
    //$$     writeTown(tag);
    //$$     out.store("Town", CompoundTag.CODEC, tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    //$$     super.readAdditionalSaveData(in);
    //$$     in.read("Town", CompoundTag.CODEC).ifPresent(this::readTown);
    //$$ }
    //#else
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeTown(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readTown(tag);
    }
    //#endif

    private void writeTown(CompoundTag tag) {
        tag.putLong("TownPos", town.asLong());
        tag.putLong("Home", home.asLong());
    }

    private void readTown(CompoundTag tag) {
        town = BlockPos.of(Nbt.getLong(tag, "TownPos"));
        home = BlockPos.of(Nbt.getLong(tag, "Home"));
        stayHome();
    }
}
