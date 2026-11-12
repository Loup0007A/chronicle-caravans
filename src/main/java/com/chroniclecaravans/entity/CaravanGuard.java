package com.chroniclecaravans.entity;

import java.util.EnumSet;
import java.util.UUID;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import com.chroniclecaravans.world.CaravanSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A guard hired to escort a caravan: follows its master, fights the monsters and bandits that come close,
 * and leaves when the contract is over.
 */
public class CaravanGuard extends PathfinderMob {
    private UUID masterId;
    /** Who paid for the escort: the bandits the guard kills count for them. */
    private UUID buyer;
    private int lostTicks;

    public CaravanGuard(EntityType<? extends CaravanGuard> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(2, new FollowMasterGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new DefendMasterGoal());
        targetSelector.addGoal(2, new HurtByTargetGoal(this, CaravanGuard.class, CaravanMaster.class));
        //#if mc26
        //$$ targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, (target, level) -> threatens(target)));
        //#else
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, this::threatens));
        //#endif
    }

    /** A monster close to the caravan (creepers are left alone: no fighting next to the wagon). */
    private boolean threatens(LivingEntity target) {
        if (target instanceof Creeper) return false;
        CaravanMaster m = master();
        return m != null && target.distanceToSqr(m) < 18 * 18;
    }

    private CaravanMaster master() {
        return masterId != null && level() instanceof ServerLevel s && s.getEntity(masterId) instanceof CaravanMaster m && m.isAlive() ? m : null;
    }

    public UUID buyer() {
        return buyer;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return !(other instanceof CaravanWagon) && !(other instanceof CaravanMaster) && super.canCollideWith(other);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.chroniclecaravans.caravan_guard");
    }

    /** A new guard next to the master, armed and armoured. */
    public static CaravanGuard spawnNear(ServerLevel level, CaravanMaster master, boolean veteran, UUID buyer) {
        CaravanGuard g = Compat.create(ChronicleCaravans.GUARD.get(), level);
        if (g == null) return null;
        BlockPos at = CaravanSpawner.findGround(level, master.blockPosition(), 2, 5, level.getRandom());
        if (at == null) at = master.blockPosition();
        g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, master.getYRot(), 0F);
        g.masterId = master.getUUID();
        g.buyer = buyer;
        g.equip(veteran);
        level.addFreshEntity(g);
        level.sendParticles(ParticleTypes.POOF, g.getX(), g.getY() + 1, g.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        return g;
    }

    private void equip(boolean veteran) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(veteran ? Items.IRON_SWORD : Items.STONE_SWORD));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(veteran ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(veteran ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0F);
        if (veteran) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7.0);
            setHealth(40F);
        }
    }

    /** The contract is over: the guard walks off (in a puff of smoke). */
    public void leave(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        discard();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || tickCount % 20 != 0) return;
        CaravanMaster m = master();
        if (m == null) {
            lostTicks += 20;
            if (lostTicks > 600) leave(level);
            return;
        }
        lostTicks = 0;
        if (getHealth() < getMaxHealth() && getTarget() == null) heal(1.0F);
        if (distanceToSqr(m) > 32 * 32) {
            BlockPos at = CaravanSpawner.findGround(level, m.blockPosition(), 2, 5, getRandom());
            if (at != null) teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //$$     if (source.getEntity() instanceof CaravanGuard || source.getEntity() instanceof CaravanMaster) return false;
    //$$     return super.hurtServer(level, source, amount);
    //$$ }
    //#else
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof CaravanGuard || source.getEntity() instanceof CaravanMaster) return false;
        return super.hurt(source, amount);
    }
    //#endif

    /** Stays a few blocks from the master. */
    private class FollowMasterGoal extends Goal {
        private int repath;

        FollowMasterGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            CaravanMaster m = master();
            return m != null && getTarget() == null && distanceToSqr(m) > 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            CaravanMaster m = master();
            return m != null && getTarget() == null && distanceToSqr(m) > 3 * 3;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            CaravanMaster m = master();
            if (m == null) return;
            getLookControl().setLookAt(m, 10F, getMaxHeadXRot());
            if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(m, distanceToSqr(m) > 12 * 12 ? 1.35 : 1.1);
            }
        }
    }

    /** Whoever hurts the master becomes the guard's target. */
    private class DefendMasterGoal extends TargetGoal {
        private LivingEntity attacker;

        DefendMasterGoal() {
            super(CaravanGuard.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            CaravanMaster m = master();
            if (m == null) return false;
            LivingEntity a = m.getLastHurtByMob();
            if (a == null || !a.isAlive() || a instanceof CaravanGuard || a instanceof CaravanMaster) return false;
            if (a instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
            attacker = a;
            return true;
        }

        @Override
        public void start() {
            setTarget(attacker);
            super.start();
        }
    }

    //#if mc26
    //$$ @Override
    //$$ protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     super.addAdditionalSaveData(out);
    //$$     CompoundTag tag = new CompoundTag();
    //$$     writeGuard(tag);
    //$$     out.store("Guard", CompoundTag.CODEC, tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    //$$     super.readAdditionalSaveData(in);
    //$$     in.read("Guard", CompoundTag.CODEC).ifPresent(this::readGuard);
    //$$ }
    //#else
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeGuard(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readGuard(tag);
    }
    //#endif

    private void writeGuard(CompoundTag tag) {
        if (masterId != null) Nbt.putUUID(tag, "Master", masterId);
        if (buyer != null) Nbt.putUUID(tag, "Buyer", buyer);
    }

    private void readGuard(CompoundTag tag) {
        masterId = Nbt.hasUUID(tag, "Master") ? Nbt.getUUID(tag, "Master") : null;
        buyer = Nbt.hasUUID(tag, "Buyer") ? Nbt.getUUID(tag, "Buyer") : null;
    }
}
