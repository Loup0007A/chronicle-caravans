package com.chroniclecaravans.entity;

import java.util.UUID;

import com.chroniclecaravans.Region;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The covered wagon of a caravan. It does not move by itself: its {@link CaravanMaster} places it on the
 * trail he leaves behind, hitched to the draft llama. Right-clicking it trades with the master.
 */
public class CaravanWagon extends Entity {
    private static final EntityDataAccessor<Integer> REGION = SynchedEntityData.defineId(CaravanWagon.class, EntityDataSerializers.INT);
    /** Distance from the wagon's centre to its rear hitch (where the first pack llama is tied), in blocks. */
    public static final double REAR_HITCH = 1.65;

    private UUID masterId;
    private int orphanTicks;

    // Client: smooth movement and rolling wheels
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    public float roll, rollO;

    public CaravanWagon(EntityType<? extends CaravanWagon> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    //#if mc120
    //$$ @Override
    //$$ protected void defineSynchedData() {
    //$$     entityData.define(REGION, Region.STEPPE.ordinal());
    //$$ }
    //#else
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(REGION, Region.STEPPE.ordinal());
    }
    //#endif

    public Region region() {
        int i = entityData.get(REGION);
        Region[] all = Region.values();
        return all[Mth.clamp(i, 0, all.length - 1)];
    }

    public void setRegion(Region region) {
        entityData.set(REGION, region.ordinal());
    }

    public void setMaster(UUID id) {
        this.masterId = id;
    }

    public UUID masterId() {
        return masterId;
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.chroniclecaravans.wagon");
    }

    /** Called by the master every tick. */
    public void place(Vec3 pos, float yaw, float pitch) {
        setPos(pos.x, pos.y, pos.z);
        setYRot(yaw);
        setXRot(pitch);
        orphanTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            //#if !mc26
            if (lerpSteps > 0) {
                double k = 1.0 / lerpSteps;
                setPos(getX() + (lerpX - getX()) * k, getY() + (lerpY - getY()) * k, getZ() + (lerpZ - getZ()) * k);
                setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) * (float) k);
                setXRot(getXRot() + (lerpXRot - getXRot()) * (float) k);
                lerpSteps--;
            }
            //#endif
            rollO = roll;
            double dx = getX() - xo, dz = getZ() - zo;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            roll += (float) (-dx * Mth.sin(yaw) + dz * Mth.cos(yaw));
        } else if (level() instanceof ServerLevel server && tickCount % 20 == 0) {
            // The master places the wagon every tick: if nobody did for a minute, the caravan is gone
            orphanTicks += 20;
            if (orphanTicks > 1200 && (masterId == null || !(server.getEntity(masterId) instanceof CaravanMaster))) discard();
        }
    }

    //#if mc26
    //$$ @Override
    //$$ protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
    //$$     return net.minecraft.world.entity.LinearInterpolationHandler.create(this, 3);
    //$$ }
    //$$
    //$$ public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    //#elif mc120
    //$$ @Override
    //$$ public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
    //#else
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    //#endif
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = Math.max(1, steps);
    }

    //#if mc121
    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return lerpSteps > 0 ? lerpXRot : getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? lerpYRot : getYRot();
    }

    //#endif

    //#if mc26
    //$$ @Override
    //$$ public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
    //#else
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
    //#endif
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (masterId != null && ((ServerLevel) level()).getEntity(masterId) instanceof CaravanMaster master && master.isAlive()) {
            return master.mobInteract(player, hand);
        }
        return InteractionResult.PASS;
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //$$     return false;
    //$$ }
    //#else
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
    //#endif

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean canBeCollidedWith(Entity other) {
    //#else
    @Override
    public boolean canBeCollidedWith() {
    //#endif
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return ItemStack.EMPTY;
    }

    //#if !mc26
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0, 0.5, 2.0);
    }
    //#endif

    /** The leash of the first pack llama is tied to the back of the wagon. */
    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        float yaw = Mth.lerp(partialTick, yRotO, getYRot()) * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(REAR_HITCH);
        return getPosition(partialTick).add(back).add(0, 0.8, 0);
    }

    //#if mc26
    //$$ @Override
    //$$ protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    //$$     in.read("Wagon", CompoundTag.CODEC).ifPresent(this::readWagon);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     CompoundTag tag = new CompoundTag();
    //$$     writeWagon(tag);
    //$$     out.store("Wagon", CompoundTag.CODEC, tag);
    //$$ }
    //#else
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        readWagon(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        writeWagon(tag);
    }
    //#endif

    private void readWagon(CompoundTag tag) {
        if (Nbt.hasUUID(tag, "Master")) masterId = Nbt.getUUID(tag, "Master");
        setRegion(Region.byName(Nbt.getString(tag, "Region")));
    }

    private void writeWagon(CompoundTag tag) {
        if (masterId != null) Nbt.putUUID(tag, "Master", masterId);
        tag.putString("Region", region().name());
    }

    public static EntityType.Builder<CaravanWagon> builder() {
        return EntityType.Builder.<CaravanWagon>of(CaravanWagon::new, net.minecraft.world.entity.MobCategory.MISC)
                .sized(2.0F, 2.7F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .fireImmune();
    }
}
