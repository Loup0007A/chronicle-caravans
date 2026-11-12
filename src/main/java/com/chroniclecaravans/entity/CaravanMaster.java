package com.chroniclecaravans.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Tier;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The head of a caravan. It walks from milestone to milestone (preferring roads), halts at each one to
 * trade the goods of its home region, and leads a line of pack llamas.
 */
public class CaravanMaster extends WanderingTrader {
    public enum State { TRAVELING, HALTED, LEAVING }

    /** Tag (persistent data) on the bandits of an ambush: the UUID of the caravan they attack. */
    public static final String AMBUSH_TAG = "ChronicleCaravansAmbush";
    /** Speed modifier of the walk goal: about 2.4 blocks per second, slow enough for the llamas. */
    private static final double WALK_SPEED = 0.46;

    private Region region = Region.STEPPE;
    private State state = State.TRAVELING;
    private BlockPos target;
    private int haltTicks;
    private int leaveTicks;
    private int lifeTicks;
    private final List<BlockPos> visited = new ArrayList<>();
    /** Llamas of the train, in walking order. With a wagon, the first one is the draft llama. */
    private final List<UUID> llamas = new ArrayList<>();
    private UUID wagonId;
    /** Where the master walked, newest first: every member of the train walks in his footsteps. */
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();

    private int[] offerTiers = new int[0];
    private MerchantOffers visibleOffers;
    private boolean savingOffers;

    private boolean ambushDone;
    private boolean ambushActive;
    private int ambushTicks;
    private final List<UUID> raiders = new ArrayList<>();
    private final Set<UUID> defenders = new HashSet<>();
    private int scoldCooldown;

    public CaravanMaster(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new RoadNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.6));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(2, new TravelGoal());
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** Called once, before the caravan is added to the world. */
    public void setup(Region region, BlockPos target) {
        this.region = region;
        this.target = target;
        this.state = State.TRAVELING;
        setCustomName(region.caravanName());
        setDespawnDelay(6000);
    }

    public Region region() {
        return region;
    }

    public State state() {
        return state;
    }

    public BlockPos target() {
        return target;
    }

    public List<UUID> llamaIds() {
        return llamas;
    }

    public boolean ambushActive() {
        return ambushActive;
    }

    public void addLlama(TraderLlama llama) {
        llamas.add(llama.getUUID());
    }

    public void setWagon(CaravanWagon wagon) {
        this.wagonId = wagon.getUUID();
    }

    public UUID wagonId() {
        return wagonId;
    }

    // ------------------------------------------------------------------ trading

    //#if mc26
    //$$ @Override
    //$$ protected void updateTrades(ServerLevel level) {
    //#else
    @Override
    protected void updateTrades() {
    //#endif
        List<Region.Stocked> stock = region.stock(this.random);
        MerchantOffers all = this.offers;
        offerTiers = new int[stock.size()];
        for (int i = 0; i < stock.size(); i++) {
            all.add(stock.get(i).offer());
            offerTiers[i] = stock.get(i).tier();
        }
    }

    private int tierOf(int index) {
        return index < offerTiers.length ? offerTiers[index] : 0;
    }

    /** While a player trades, only the offers their rank unlocks exist. */
    @Override
    public MerchantOffers getOffers() {
        if (!savingOffers && visibleOffers != null && getTradingPlayer() != null) return visibleOffers;
        return super.getOffers();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.VILLAGER_SPAWN_EGG) || !isAlive() || isTrading() || isBaby()) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            CaravanData data = CaravanData.get(sp.serverLevel());
            Tier tier = data.tier(sp.getUUID());
            MerchantOffers all = super.getOffers();
            MerchantOffers visible = new MerchantOffers();
            int locked = 0;
            for (int i = 0; i < all.size(); i++) {
                MerchantOffer o = all.get(i);
                if (tierOf(i) > tier.ordinal()) {
                    locked++;
                    continue;
                }
                o.resetSpecialPriceDiff();
                if (tier.discount() > 0F && o.getBaseCostA().is(Items.EMERALD)) {
                    o.setSpecialPriceDiff(-(int) Math.floor(o.getBaseCostA().getCount() * tier.discount()));
                }
                visible.add(o);
            }
            if (visible.isEmpty()) return InteractionResult.CONSUME;
            visibleOffers = visible;
            setTradingPlayer(sp);
            openTradingScreen(sp, getDisplayName(), 1);
            Tier next = tier.next();
            if (locked > 0 && next != null) {
                sp.displayClientMessage(Component.translatable("msg.chroniclecaravans.locked", locked, next.label()).withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void stopTrading() {
        super.stopTrading();
        if (visibleOffers != null) for (MerchantOffer o : visibleOffers) o.resetSpecialPriceDiff();
        visibleOffers = null;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        if (getTradingPlayer() instanceof ServerPlayer sp) {
            CaravanData.get(sp.serverLevel()).addReputation(sp, 1, "reason.chroniclecaravans.trade");
        }
    }

    // ------------------------------------------------------------------ life

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && isAlive()) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        lifeTicks++;
        if (scoldCooldown > 0) scoldCooldown--;
        if (tickCount % 10 == 0) updateRoadSpeed(level);
        recordTrail();
        layoutTrain(level);
        if (tickCount % 40 == 0) {
            setDespawnDelay(6000);
            tendTrain(level);
            if (!ambushActive && getHealth() < getMaxHealth()) heal(1.0F);
        }
        if (tickCount % 100 == 0) checkTarget(level);

        if (state == State.HALTED) {
            if (!isTrading() && !ambushActive && --haltTicks <= 0) depart(level);
        } else if (state == State.LEAVING) {
            if (++leaveTicks > 1800) vanish(level);
        } else if (lifeTicks > 72000) {
            startLeaving(level);
        }

        if (tickCount % 200 == 0) maybeAmbush(level);
        if (ambushActive && tickCount % 20 == 0) tickAmbush(level);
    }

    /** A summoned caravan has no destination yet, and a milestone can be broken on the way. */
    private void checkTarget(ServerLevel level) {
        if (state == State.LEAVING) return;
        CaravanData data = CaravanData.get(level);
        if (state == State.TRAVELING && (target == null || data.stop(target) == null)) {
            CaravanData.Stop next = data.nearestStop(blockPosition(), CCConfig.LINK_RANGE.get(), visited);
            if (next != null) {
                target = next.pos;
            } else if (target == null) {
                state = State.HALTED;
                haltTicks = CCConfig.HALT_TIME.get() * 20;
            } else {
                startLeaving(level);
            }
        }
    }

    private void updateRoadSpeed(ServerLevel level) {
        // A mob's real speed grows with the square of the attribute, hence the square root
        boolean onRoad = CCConfig.ROADS_ENABLED.get() && level.getBlockState(getOnPos()).is(ChronicleCaravans.ROADS);
        Compat.speedBonus(this, onRoad ? Math.sqrt(1.0 + CCConfig.ROAD_SPEED_BONUS.get()) - 1.0 : 0);
    }

    // ------------------------------------------------------------------ the train

    /** Gap between the master and the draft llama, then between the llama and the wagon's centre (blocks). */
    private static final double DRAFT_GAP = 2.2, WAGON_GAP = 3.05;
    /** Half the distance between the wagon's axles, and gaps between pack llamas. */
    private static final double AXLE_HALF = 0.95, PACK_GAP = 2.2, AFTER_WAGON = 1.7;
    private static final double TRAIL_LENGTH = 24.0;

    /** Fills the trail with a straight line behind the master (at spawn, after a warp). */
    public void seedTrail(ServerLevel level) {
        trail.clear();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        for (double s = 0.5; s <= TRAIL_LENGTH; s += 0.5) {
            Vec3 p = position().add(back.scale(s));
            trail.addLast(new Vec3(p.x, groundY(level, p), p.z));
        }
    }

    private double groundY(ServerLevel level, Vec3 p) {
        int x = Mth.floor(p.x), z = Mth.floor(p.z);
        if (!level.isLoaded(new BlockPos(x, getBlockY(), z))) return getY();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.abs(y - getY()) <= 3 ? y : getY();
    }

    private void recordTrail() {
        Vec3 p = position();
        if (trail.isEmpty() || trail.peekFirst().distanceToSqr(p) > 0.04) trail.addFirst(p);
        double len = 0;
        Vec3 prev = p;
        int keep = 0;
        for (Vec3 q : trail) {
            keep++;
            len += prev.distanceTo(q);
            prev = q;
            if (len > TRAIL_LENGTH) break;
        }
        while (trail.size() > keep) trail.removeLast();
    }

    /** The point of the trail {@code s} blocks behind the master. */
    private Vec3 sample(double s) {
        Vec3 prev = position();
        Vec3 dir = null;
        double acc = 0;
        for (Vec3 q : trail) {
            double seg = prev.distanceTo(q);
            if (seg > 1e-4) {
                if (acc + seg >= s) return prev.lerp(q, (s - acc) / seg);
                dir = q.subtract(prev).scale(1.0 / seg);
                acc += seg;
            }
            prev = q;
        }
        if (dir == null) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            dir = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        }
        return prev.add(dir.x * (s - acc), 0, dir.z * (s - acc));
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private CaravanWagon wagon(ServerLevel level) {
        return wagonId != null && level.getEntity(wagonId) instanceof CaravanWagon w && w.isAlive() ? w : null;
    }

    /** Places every member of the train on the trail. Called every tick. */
    private void layoutTrain(ServerLevel level) {
        if (llamas.isEmpty() && wagonId == null) return;
        CaravanWagon wagon = wagon(level);
        if (trail.size() < 2) seedTrailFromMembers(level, wagon);
        if (llamas.isEmpty() && wagon != null) {
            // No llama left: the master drags the wagon himself
            Vec3 front = sample(1.6), rear = sample(1.6 + 2 * AXLE_HALF);
            wagon.place(front.add(rear).scale(0.5), yawTowards(rear, front), 0F);
            return;
        }
        double d = 0;
        boolean afterWagon = false;
        for (int i = 0; i < llamas.size(); i++) {
            Entity e = level.getEntity(llamas.get(i));
            d += i == 0 ? DRAFT_GAP : afterWagon ? AFTER_WAGON + CaravanWagon.REAR_HITCH : PACK_GAP;
            afterWagon = false;
            if (e instanceof TraderLlama llama && llama.isAlive() && llama.isNoAi()) {
                Vec3 p = sample(d);
                float yaw = yawTowards(p, sample(d - 0.8));
                llama.setPos(p.x, p.y, p.z);
                llama.setDeltaMovement(Vec3.ZERO);
                llama.setYRot(yaw);
                llama.setYHeadRot(yaw);
                llama.yBodyRot = yaw;
            }
            if (i == 0 && wagon != null) {
                d += WAGON_GAP;
                Vec3 front = sample(d - AXLE_HALF), rear = sample(d + AXLE_HALF);
                double flat = Math.sqrt((front.x - rear.x) * (front.x - rear.x) + (front.z - rear.z) * (front.z - rear.z));
                float pitch = (float) (Mth.atan2(rear.y - front.y, Math.max(0.1, flat)) * Mth.RAD_TO_DEG);
                wagon.place(front.add(rear).scale(0.5), yawTowards(rear, front), Mth.clamp(pitch, -25F, 25F));
                afterWagon = true;
            }
        }
    }

    /** Lays the train out at once and ties it (right after spawning). */
    public void settleTrain(ServerLevel level) {
        layoutTrain(level);
        tendTrain(level);
    }

    /** After a reload the trail is lost: rebuild it through the members, where they stand. */
    private void seedTrailFromMembers(ServerLevel level, CaravanWagon wagon) {
        trail.clear();
        for (int i = 0; i < llamas.size(); i++) {
            if (level.getEntity(llamas.get(i)) instanceof TraderLlama llama && llama.isAlive()) trail.addLast(llama.position());
            if (i == 0 && wagon != null) trail.addLast(wagon.position());
        }
        if (trail.size() < 2) seedTrail(level);
    }

    /** Keeps the train in order: alive, tied, and moved by the master only. */
    private void tendTrain(ServerLevel level) {
        CaravanWagon wagon = wagon(level);
        if (wagon != null) wagon.setMaster(getUUID());
        Entity prev = this;
        for (Iterator<UUID> it = llamas.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Entity e = level.getEntity(id);
            if (e == null) {
                continue;
            }
            if (!(e instanceof TraderLlama llama) || !llama.isAlive()) {
                it.remove();
                continue;
            }
            Entity holder = llama.getLeashHolder();
            if (llama.isTamed() || llama.isVehicle() || holder instanceof Player) {
                // A player took this one: it leaves the caravan
                llama.setNoAi(false);
                it.remove();
                continue;
            }
            llama.setNoAi(true);
            llama.setDespawnDelay(6000);
            Entity expected = prev == this ? this : prev;
            if (holder != expected) llama.setLeashedTo(expected, true);
            prev = (prev == this && wagon != null) ? wagon : llama;
        }
    }

    private List<TraderLlama> loadedLlamas(ServerLevel level) {
        List<TraderLlama> out = new ArrayList<>();
        for (UUID id : llamas) if (level.getEntity(id) instanceof TraderLlama l && l.isAlive()) out.add(l);
        return out;
    }

    /** The master is gone: the llamas are free again, the wagon goes with him. */
    private void breakTrain(ServerLevel level) {
        for (TraderLlama llama : loadedLlamas(level)) {
            Compat.unleash(llama);
            llama.setNoAi(false);
        }
        llamas.clear();
        CaravanWagon wagon = wagon(level);
        if (wagon != null) {
            level.sendParticles(ParticleTypes.POOF, wagon.getX(), wagon.getY() + 1, wagon.getZ(), 30, 1.0, 0.8, 1.0, 0.02);
            wagon.discard();
        }
    }

    // ------------------------------------------------------------------ route

    private void arrive(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop stop = data.stop(target);
        state = State.HALTED;
        haltTicks = CCConfig.HALT_TIME.get() * 20;
        visited.add(target);
        getNavigation().stop();
        level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_REAPPEARED, SoundSource.NEUTRAL, 1.2F, 1.0F);
        level.playSound(null, target, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
        if (stop == null) return;
        long interval = CCConfig.VISIT_INTERVAL.get() * 20L;
        stop.nextVisit = Math.max(stop.nextVisit, level.getGameTime() + (long) (interval * (0.75 + random.nextDouble() * 0.5)));
        data.setDirty();
        Component msg = Component.translatable("msg.chroniclecaravans.arrived", region.caravanName().withStyle(region.color), stop.displayName());
        announce(level, msg, stop.owner);
        ServerPlayer owner = stop.owner == null ? null : level.getServer().getPlayerList().getPlayer(stop.owner);
        if (owner != null) {
            data.addReputation(owner, 3, "reason.chroniclecaravans.host");
        }
    }

    private void depart(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop next = data.nearestStop(blockPosition(), CCConfig.LINK_RANGE.get(), visited);
        if (next == null) {
            startLeaving(level);
            return;
        }
        target = next.pos;
        state = State.TRAVELING;
        announce(level, Component.translatable("msg.chroniclecaravans.departs", region.caravanName().withStyle(region.color), next.displayName()), null);
    }

    public void startLeaving(ServerLevel level) {
        if (state == State.LEAVING) return;
        state = State.LEAVING;
        leaveTicks = 0;
        double a = random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(getX() + Math.cos(a) * 48);
        int z = (int) Math.floor(getZ() + Math.sin(a) * 48);
        BlockPos probe = new BlockPos(x, getBlockY(), z);
        int y = level.isLoaded(probe) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : getBlockY();
        target = new BlockPos(x, y, z);
        announce(level, Component.translatable("msg.chroniclecaravans.leaves", region.caravanName().withStyle(region.color)), null);
    }

    /** The whole caravan disappears in a puff of smoke (it has left the area). */
    public void vanish(ServerLevel level) {
        for (TraderLlama llama : loadedLlamas(level)) {
            if (llama.isTamed() || llama.getLeashHolder() instanceof Player) continue;
            level.sendParticles(ParticleTypes.POOF, llama.getX(), llama.getY() + 1, llama.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
            Compat.unleash(llama);
            llama.discard();
        }
        CaravanWagon wagon = wagon(level);
        if (wagon != null) {
            level.sendParticles(ParticleTypes.POOF, wagon.getX(), wagon.getY() + 1, wagon.getZ(), 30, 1.0, 0.8, 1.0, 0.02);
            wagon.discard();
        }
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 16, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_DISAPPEARED, SoundSource.NEUTRAL, 1.0F, 1.0F);
        discard();
    }

    private void announce(ServerLevel level, Component msg, UUID alsoTell) {
        if (!CCConfig.ANNOUNCEMENTS.get()) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) <= 64 * 64 || p.getUUID().equals(alsoTell)) p.sendSystemMessage(msg);
        }
    }

    /** Next waypoint towards the target: about 24 blocks ahead, on a road when one leads roughly the right way. */
    private Vec3 nextHop(ServerLevel level) {
        Vec3 dest = Vec3.atBottomCenterOf(target);
        double dx = dest.x - getX();
        double dz = dest.z - getZ();
        if (dx * dx + dz * dz <= 28 * 28) return dest;
        double base = Math.atan2(dz, dx);
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = -3; i <= 3; i++) {
            double a = base + Math.toRadians(20) * i;
            int x = (int) Math.floor(getX() + Math.cos(a) * 24);
            int z = (int) Math.floor(getZ() + Math.sin(a) * 24);
            if (!level.isLoaded(p.set(x, getBlockY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            p.set(x, y - 1, z);
            boolean road = CCConfig.ROADS_ENABLED.get() && level.getBlockState(p).is(ChronicleCaravans.ROADS);
            boolean wet = !level.getFluidState(p).isEmpty();
            double score = Math.abs(i) * 4 + (road ? -10 : 0) + (wet ? 30 : 0) + Math.abs(y - getY()) * 0.5;
            if (score < bestScore) {
                bestScore = score;
                best = new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return best;
    }

    /** Last resort when the path is blocked for a long time: the caravan reappears a little further. */
    private void warp(ServerLevel level, Vec3 hop) {
        if (hop == null) return;
        BlockPos ground = CaravanSpawner.findGround(level, BlockPos.containing(hop), 0, 4, random);
        if (ground == null) return;
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        Vec3 dest = new Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        setYRot(yawTowards(position(), dest));
        teleportTo(dest.x, dest.y, dest.z);
        // The whole train reappears in a line behind the master
        seedTrail(level);
        layoutTrain(level);
    }

    private class TravelGoal extends Goal {
        private int cooldown;
        private int repath;
        private int stuckTimer;
        private int stuck;
        private Vec3 lastPos;
        private Vec3 hop;

        TravelGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return state != State.HALTED && target != null && !isTrading();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            cooldown = 0;
            repath = 0;
            stuckTimer = 0;
            stuck = 0;
            lastPos = null;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level) || target == null) return;
            double d2 = distanceToSqr(Vec3.atBottomCenterOf(target));
            if (d2 <= (state == State.LEAVING ? 16.0 : 20.25)) {
                getNavigation().stop();
                if (state == State.LEAVING) vanish(level);
                else arrive(level);
                return;
            }
            if (cooldown > 0) cooldown--;
            repath++;
            if (cooldown == 0 && (getNavigation().isDone() || repath >= 100)) {
                repath = 0;
                cooldown = 20;
                hop = nextHop(level);
                if (hop != null) getNavigation().moveTo(hop.x, hop.y, hop.z, WALK_SPEED);
            }
            if (++stuckTimer >= 200) {
                stuckTimer = 0;
                if (lastPos != null && position().distanceToSqr(lastPos) < 4.0) {
                    if (++stuck >= 3) {
                        stuck = 0;
                        warp(level, hop);
                    }
                } else {
                    stuck = 0;
                }
                lastPos = position();
            }
        }
    }

    // ------------------------------------------------------------------ ambush

    private void maybeAmbush(ServerLevel level) {
        if (ambushDone || ambushActive || state == State.LEAVING || !CCConfig.AMBUSH_ENABLED.get()) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL || lifeTicks < 600) return;
        if (level.getNearestPlayer(this, 48) == null) return;
        if (random.nextDouble() < CCConfig.AMBUSH_CHANCE.get()) startAmbush(level);
    }

    /** Bandits appear nearby and go for the caravan. Returns the number of bandits. */
    public int startAmbush(ServerLevel level) {
        if (ambushActive) return 0;
        BlockPos center = CaravanSpawner.findGround(level, blockPosition(), 16, 24, random);
        if (center == null) return 0;
        int count = 2 + level.getDifficulty().getId();
        raiders.clear();
        defenders.clear();
        for (int i = 0; i < count + (level.getDifficulty() == Difficulty.HARD ? 1 : 0); i++) {
            EntityType<? extends AbstractIllager> type = i >= count ? EntityType.VINDICATOR : EntityType.PILLAGER;
            AbstractIllager bandit = Compat.create(type, level);
            if (bandit == null) continue;
            BlockPos at = i == 0 ? center : CaravanSpawner.findGround(level, center, 1, 4, random);
            if (at == null) at = center;
            bandit.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360F, 0F);
            Compat.finalizeSpawn(bandit, level, at);
            bandit.setCanJoinRaid(false);
            Nbt.putUUID(bandit.getPersistentData(), AMBUSH_TAG, getUUID());
            bandit.setTarget(this);
            level.addFreshEntity(bandit);
            raiders.add(bandit.getUUID());
        }
        if (raiders.isEmpty()) return 0;
        ambushActive = true;
        ambushDone = true;
        ambushTicks = 0;
        level.playSound(null, getX(), getY(), getZ(), Compat.raidHorn(), SoundSource.HOSTILE, 24.0F, 1.0F);
        announce(level, Component.translatable("msg.chroniclecaravans.ambush", region.caravanName().withStyle(region.color)).withStyle(ChatFormatting.RED), null);
        return raiders.size();
    }

    private void tickAmbush(ServerLevel level) {
        ambushTicks += 20;
        int alive = 0;
        for (UUID id : raiders) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) alive++;
        }
        if (alive > 0 && ambushTicks < 3600) return;
        ambushActive = false;
        raiders.clear();
        if (alive > 0) return;
        if (defenders.isEmpty()) {
            announce(level, Component.translatable("msg.chroniclecaravans.repelled", region.caravanName().withStyle(region.color)), null);
            return;
        }
        announce(level, Component.translatable("msg.chroniclecaravans.saved", region.caravanName().withStyle(region.color)).withStyle(ChatFormatting.GREEN), null);
        playCelebrateSound();
        //#if mc26
        //$$ spawnAtLocation(level, new ItemStack(Items.EMERALD, 3 + random.nextInt(4)));
        //$$ spawnAtLocation(level, region.gift(random));
        //#else
        spawnAtLocation(new ItemStack(Items.EMERALD, 3 + random.nextInt(4)));
        spawnAtLocation(region.gift(random));
        //#endif
        CaravanData data = CaravanData.get(level);
        for (UUID id : defenders) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) data.addReputation(p, 5, "reason.chroniclecaravans.rescue");
        }
        defenders.clear();
    }

    /** A player killed one of the bandits of this caravan's ambush. */
    public void onBanditKilled(ServerPlayer player) {
        defenders.add(player.getUUID());
        CaravanData.get(player.serverLevel()).addReputation(player, 2, "reason.chroniclecaravans.bandit");
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //$$     boolean hurt = super.hurtServer(level, source, amount);
    //#else
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
    //#endif
        if (hurt && source.getEntity() instanceof ServerPlayer p && !p.isCreative() && scoldCooldown == 0) {
            scoldCooldown = 40;
            CaravanData.get(p.serverLevel()).addReputation(p, -5, "reason.chroniclecaravans.attack");
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            breakTrain(level);
            announce(level, Component.translatable("msg.chroniclecaravans.lost", region.caravanName().withStyle(region.color)).withStyle(ChatFormatting.DARK_RED), null);
            if (source.getEntity() instanceof ServerPlayer p && !p.isCreative()) {
                CaravanData.get(level).addReputation(p, -25, "reason.chroniclecaravans.murder");
            }
        }
    }

    // ------------------------------------------------------------------ storage

    //#if mc26
    //$$ @Override
    //$$ protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     savingOffers = true;
    //$$     try {
    //$$         super.addAdditionalSaveData(out);
    //$$     } finally {
    //$$         savingOffers = false;
    //$$     }
    //$$     CompoundTag tag = new CompoundTag();
    //$$     writeCaravan(tag);
    //$$     out.store("Caravan", CompoundTag.CODEC, tag);
    //$$ }
    //$$
    //$$ @Override
    //$$ protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    //$$     super.readAdditionalSaveData(in);
    //$$     in.read("Caravan", CompoundTag.CODEC).ifPresent(this::readCaravan);
    //$$ }
    //#else
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        savingOffers = true;
        try {
            super.addAdditionalSaveData(tag);
        } finally {
            savingOffers = false;
        }
        writeCaravan(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readCaravan(tag);
    }
    //#endif

    private void writeCaravan(CompoundTag tag) {
        tag.putString("CaravanRegion", region.name());
        tag.putString("CaravanState", state.name());
        if (target != null) tag.putLong("CaravanTarget", target.asLong());
        tag.putInt("HaltTicks", haltTicks);
        tag.putInt("LeaveTicks", leaveTicks);
        tag.putInt("LifeTicks", lifeTicks);
        tag.putLongArray("Visited", visited.stream().mapToLong(BlockPos::asLong).toArray());
        Nbt.putUUIDs(tag, "Llamas", llamas);
        if (wagonId != null) Nbt.putUUID(tag, "Wagon", wagonId);
        tag.putIntArray("OfferTiers", offerTiers);
        tag.putBoolean("AmbushDone", ambushDone);
        tag.putBoolean("AmbushActive", ambushActive);
        tag.putInt("AmbushTicks", ambushTicks);
        Nbt.putUUIDs(tag, "Raiders", raiders);
        Nbt.putUUIDs(tag, "Defenders", defenders);
    }

    private void readCaravan(CompoundTag tag) {
        region = Region.byName(Nbt.getString(tag, "CaravanRegion"));
        try {
            state = State.valueOf(Nbt.getString(tag, "CaravanState"));
        } catch (IllegalArgumentException e) {
            state = State.TRAVELING;
        }
        target = tag.contains("CaravanTarget") ? BlockPos.of(Nbt.getLong(tag, "CaravanTarget")) : null;
        haltTicks = Nbt.getInt(tag, "HaltTicks");
        leaveTicks = Nbt.getInt(tag, "LeaveTicks");
        lifeTicks = Nbt.getInt(tag, "LifeTicks");
        visited.clear();
        for (long l : Nbt.getLongArray(tag, "Visited")) visited.add(BlockPos.of(l));
        Nbt.getUUIDs(tag, "Llamas", llamas);
        wagonId = Nbt.hasUUID(tag, "Wagon") ? Nbt.getUUID(tag, "Wagon") : null;
        offerTiers = Nbt.getIntArray(tag, "OfferTiers");
        ambushDone = Nbt.getBoolean(tag, "AmbushDone");
        ambushActive = Nbt.getBoolean(tag, "AmbushActive");
        ambushTicks = Nbt.getInt(tag, "AmbushTicks");
        Nbt.getUUIDs(tag, "Raiders", raiders);
        Nbt.getUUIDs(tag, "Defenders", defenders);
    }
}
