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
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.block.InnSignBlock;
import com.chroniclecaravans.menu.CaravanMenu;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import com.chroniclecaravans.world.Logistics;
import com.chroniclecaravans.world.Parcel;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The head of a caravan. It travels from stop to stop (preferring roads and villages), halts at each one to
 * trade, spends the night at an inn when there is one, and leads its train: draft llama, wagon, pack llamas.
 */
public class CaravanMaster extends WanderingTrader {
    public enum State { TRAVELING, HALTED, LODGING, LEAVING, CAMPING }

    /** Why a caravan camps: a thunderstorm, or a night with no inn within reach. */
    public static final int CAMP_STORM = 1, CAMP_NIGHT = 2;

    /** Tag (persistent data) on the bandits of an ambush: the UUID of the caravan they attack. */
    public static final String AMBUSH_TAG = "ChronicleCaravansAmbush";
    /** Speed modifier of the walk goal: about 2.4 blocks per second, slow enough for the llamas. */
    private static final double WALK_SPEED = 0.46;
    /** How long a caravan stays on the roads before it heads home. */
    private static final int LIFETIME = 72000;

    private Region region = Region.STEPPE;
    private State state = State.TRAVELING;
    private BlockPos target;
    /** While going to (or staying at) an inn: where the caravan was going before. */
    private BlockPos resumeTarget;
    private boolean toInn;
    private boolean lodgedTonight;
    private int haltTicks;
    private int leaveTicks;
    private int lifeTicks;
    private boolean registered;
    private final List<BlockPos> visited = new ArrayList<>();
    /** Llamas of the train, in walking order. With a wagon, the first one is the draft llama. */
    private final List<UUID> llamas = new ArrayList<>();
    private UUID wagonId;
    /** Where the master walked, newest first: every member of the train walks in his footsteps. */
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private float wagonPitch;
    private double wagonY = Double.NaN;

    /** Players looking at the caravan's interface: the caravan waits for them. */
    private final Set<UUID> customers = new HashSet<>();

    private boolean ambushDone;
    private boolean ambushActive;
    private int ambushTicks;
    private final List<UUID> raiders = new ArrayList<>();
    private final Set<UUID> defenders = new HashSet<>();
    private int scoldCooldown;

    /** Parcels carried to another stop. */
    private final List<Parcel> cargo = new ArrayList<>();
    private int campReason;
    private BlockPos campfire;

    // Parking: the train waits on a strip of ground (off the road, or at a hitching post) while the master goes on
    // foot to a bed at the inn, or to an upgraded milestone that already has a caravan in its main spot.
    private static final int PARK_NONE = 0, PARK_ENTRY = 1, PARK_LINE = 2, PARK_OUT = 3, PARK_STAY = 4, PARK_BACK = 5;
    private int park;
    /** Head of the parking strip (the master's last step before the train stops) and its far end (where he enters it). */
    private Vec3 parkHead, parkEntry;
    /** The hitching post the draft llama is tied to, if any. */
    private BlockPos parkPost;
    /** Where the master walks once the train is parked: a bed, or a spot by the milestone. */
    private BlockPos footSpot;
    private int parkTicks;
    /** No parking strip was found on the way (not saved: tried again after a reload). */
    private boolean parkFailed;

    private final List<UUID> guards = new ArrayList<>();
    private int escortTicks;
    private UUID escortBuyer;

    public CaravanMaster(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                // Blows do not throw him around (and his train with him)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new RoadNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ServeGoal());
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new TravelGoal());
        this.goalSelector.addGoal(2, new FootGoal());
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** The wagon and the train are solid for players, not for the master who pulls them. */
    @Override
    public boolean canCollideWith(Entity other) {
        return !(other instanceof CaravanWagon) && !(other instanceof CaravanGuard) && super.canCollideWith(other);
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

    public boolean headingToInn() {
        return toInn;
    }

    /** The stop the caravan is halted at, or null. */
    public BlockPos currentStop() {
        return state == State.HALTED && !toInn ? target : null;
    }

    /**
     * The stop this caravan serves: where it travels to, where it is halted, or (at an inn, or camping on the way to
     * one) where it goes on in the morning. Null when it leaves the area.
     */
    public BlockPos destination() {
        if (state == State.LEAVING) return null;
        return toInn ? resumeTarget : target;
    }

    /** The train is parked and the master is on foot (or in bed). */
    public boolean parked() {
        return park >= PARK_OUT;
    }

    public BlockPos parkPost() {
        return parkPost;
    }

    public Vec3 parkHead() {
        return parkHead;
    }

    /** The bed (or the spot by the milestone) the master walks to while the train is parked. */
    public BlockPos footSpot() {
        return footSpot;
    }

    public int haltTicks() {
        return haltTicks;
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
        if (!(level() instanceof ServerLevel level)) return;
    //#endif
        this.offers.addAll(region.stock(level, this.random));
    }

    /** Every offer of the caravan (server side), whoever looks. */
    public MerchantOffers stock() {
        return super.getOffers();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.VILLAGER_SPAWN_EGG) || !isAlive() || isBaby()) return InteractionResult.PASS;
        if (isSleeping()) {
            if (!level().isClientSide && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("msg.chroniclecaravans.asleep", region.caravanName().withStyle(region.color)), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && player instanceof ServerPlayer sp) openFor(sp);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Opens the caravan's interface: market, requests, services and guild. */
    public void openFor(ServerPlayer sp) {
        CaravanData data = CaravanData.get(sp.serverLevel());
        if (CCConfig.REPUTATION_ENABLED.get() && data.xp(sp.getUUID()) <= Guild.OUTLAW && !sp.isSpectator()) {
            sp.displayClientMessage(Component.translatable("msg.chroniclecaravans.outlaw").withStyle(ChatFormatting.RED), true);
            playSound(SoundEvents.WANDERING_TRADER_NO, 1.0F, 1.0F);
            return;
        }
        stock();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CaravanMenu(id, inv, this), getDisplayName()));
    }

    public void addCustomer(Player p) {
        customers.add(p.getUUID());
        getNavigation().stop();
    }

    public void removeCustomer(Player p) {
        customers.remove(p.getUUID());
    }

    public boolean serving() {
        if (customers.isEmpty()) return false;
        if (level() instanceof ServerLevel server) {
            // A customer who left (or closed the interface without telling) does not hold the caravan
            customers.removeIf(id -> !(server.getPlayerByUUID(id) instanceof ServerPlayer p) || !(p.containerMenu instanceof CaravanMenu));
        }
        return !customers.isEmpty();
    }

    /** All the offers are back in stock (a night at an inn). */
    public void restock(boolean requestsOnly) {
        for (MerchantOffer o : stock()) {
            if (!requestsOnly || Region.kindOf(o) == Region.KIND_SELL) o.resetUses();
        }
    }

    // ------------------------------------------------------------------ life

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && isAlive()) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        if (lifeTicks == 0 || tickCount % 100 == 1) {
            CaravanData data = CaravanData.get(level);
            if (registered && !data.knows(getUUID())) {
                // The guilds sent this caravan elsewhere while its land was asleep
                vanish(level, false);
                return;
            }
            registered = true;
            data.seen(getUUID(), level.getGameTime(), blockPosition(), region, target, !cargo.isEmpty());
        }
        lifeTicks++;
        if (scoldCooldown > 0) scoldCooldown--;
        if (tickCount % 10 == 0) updateRoadSpeed(level);
        if (park < PARK_OUT) {
            recordTrail();
            layoutTrain(level);
        } else {
            tickParked(level);
        }
        if (tickCount % 40 == 0) {
            setDespawnDelay(6000);
            tendTrain(level);
            if (!ambushActive && getHealth() < getMaxHealth()) heal(1.0F);
        }
        if (tickCount % 100 == 0) {
            checkTarget(level);
            checkNight(level);
        }
        if (tickCount % 20 == 0) tickEscort(level);

        if (state == State.HALTED) {
            if (!serving() && !ambushActive && --haltTicks <= 0) depart(level);
        } else if (state == State.LEAVING) {
            if (++leaveTicks > 1800) vanish(level, true);
        } else if (state == State.TRAVELING && lifeTicks > LIFETIME && !toInn && cargo.isEmpty()) {
            startLeaving(level);
        }

        if (tickCount % 200 == 0) maybeAmbush(level);
        if (ambushActive && tickCount % 20 == 0) tickAmbush(level);
    }

    /** A summoned caravan has no destination yet, and a milestone can be broken on the way. */
    private void checkTarget(ServerLevel level) {
        if (state == State.LEAVING || state == State.LODGING || state == State.CAMPING || toInn) {
            if (toInn && state != State.CAMPING && CaravanData.get(level).inn(target) == null) leaveInn(level);
            return;
        }
        CaravanData data = CaravanData.get(level);
        if (state == State.TRAVELING && (target == null || data.stop(target) == null)) {
            CaravanData.Stop next = chooseNext(level);
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
        // Rain makes the ground heavy: a quarter slower
        boolean wet = CCConfig.WEATHER.get() && level.isRainingAt(blockPosition().above());
        double factor = (onRoad ? 1.0 + CCConfig.ROAD_SPEED_BONUS.get() : 1.0) * (wet ? 0.75 : 1.0);
        Compat.speedBonus(this, Math.sqrt(factor) - 1.0);
    }

    /** Sent to a stop by the guilds (a stop waits for a caravan, or someone hired one). */
    public void redirect(ServerLevel level, BlockPos stop) {
        if (toInn) {
            // At the inn (or on its way, or camping on its way): goes there in the morning
            resumeTarget = stop;
            return;
        }
        if (state == State.CAMPING) {
            // Breaks camp when the weather (or the night) allows, then goes there
            target = stop;
            return;
        }
        target = stop;
        state = State.TRAVELING;
        leaveTicks = 0;
        visited.remove(stop);
        CaravanData.Stop s = CaravanData.get(level).stop(stop);
        if (s != null) announce(level, Component.translatable("msg.chroniclecaravans.departs", region.caravanName().withStyle(region.color), s.displayName()), s.owner);
    }

    /** Free for another job: on the road between two stops, about to leave a stop, or leaving the area. */
    public boolean available() {
        if (ambushActive || !cargo.isEmpty() || park == PARK_ENTRY || park == PARK_LINE || park == PARK_OUT) return false;
        // Resting at an inn (or on the way to one) with nowhere to go yet: it will come in the morning
        if (toInn) return resumeTarget == null;
        return state == State.TRAVELING || state == State.LEAVING || (state == State.HALTED && haltTicks < 600 && !serving());
    }

    // ------------------------------------------------------------------ night and inns

    /** Not moving: halted at a stop, at an inn, or camping (a trading post nearby can trade). */
    public boolean stationary() {
        return state == State.HALTED || state == State.LODGING || state == State.CAMPING;
    }

    public int campReason() {
        return state == State.CAMPING ? campReason : 0;
    }

    private void checkNight(ServerLevel level) {
        boolean night = Compat.isNight(level);
        boolean storm = CCConfig.WEATHER.get() && level.isThundering();
        if (state == State.CAMPING) {
            if (!storm && !(campReason == CAMP_NIGHT && night)) breakCamp(level);
            return;
        }
        if (!night) {
            lodgedTonight = false;
            if (state == State.LODGING) leaveInn(level);
        }
        if (state == State.TRAVELING && storm && !ambushActive && !toInn && park == PARK_NONE) {
            startCamp(level, CAMP_STORM);
            return;
        }
        if (!night) return;
        if (toInn || lodgedTonight || state == State.LEAVING || state == State.LODGING || ambushActive) return;
        if (state == State.HALTED && haltTicks > 200) return;
        CaravanData.Inn inn = null;
        double best = (double) CCConfig.LINK_RANGE.get() * CCConfig.LINK_RANGE.get();
        for (CaravanData.Inn i : CaravanData.get(level).inns()) {
            double d = i.pos.distSqr(blockPosition());
            if (d < best && InnSignBlock.open(level, i.pos)) {
                best = d;
                inn = i;
            }
        }
        if (inn == null) {
            // No inn within reach: the caravan makes camp for the night
            if (state == State.TRAVELING && CCConfig.NIGHT_CAMPS.get() && park == PARK_NONE) startCamp(level, CAMP_NIGHT);
            return;
        }
        resumeTarget = state == State.HALTED || state == State.LEAVING ? null : target;
        if (park == PARK_ENTRY || park == PARK_LINE) {
            park = PARK_NONE;
            parkPost = null;
        }
        target = inn.pos;
        toInn = true;
        state = State.TRAVELING;
        parkFailed = false;
        announce(level, Component.translatable("msg.chroniclecaravans.to_inn", region.caravanName().withStyle(region.color), inn.displayName()), inn.owner);
    }

    private void arriveAtInn(ServerLevel level) {
        state = State.LODGING;
        lodgedTonight = true;
        getNavigation().stop();
        CaravanData data = CaravanData.get(level);
        CaravanData.Inn inn = data.inn(target);
        level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_REAPPEARED, SoundSource.NEUTRAL, 1.0F, 0.9F);
        if (inn != null) announce(level, Component.translatable("msg.chroniclecaravans.lodging", region.caravanName().withStyle(region.color), inn.displayName()), inn.owner);
    }

    /** Morning: the caravan pays the innkeeper, restocks and goes on. */
    private void leaveInn(ServerLevel level) {
        if (isSleeping()) stopSleeping();
        CaravanData data = CaravanData.get(level);
        CaravanData.Inn inn = target == null ? null : data.inn(target);
        if (state == State.LODGING) {
            restock(false);
            if (inn != null) {
                boolean innkeeper = inn.owner != null && Guild.has(data.skills(inn.owner), Guild.Skill.INNKEEPER);
                InnSignBlock.pay(level, inn.pos, new ItemStack(Items.EMERALD, innkeeper ? 6 : 3));
                data.addXp(level.getServer(), inn.owner, innkeeper ? 3 : 2, "reason.chroniclecaravans.inn");
            }
        }
        toInn = false;
        state = State.TRAVELING;
        target = resumeTarget;
        resumeTarget = null;
        if (target == null || data.stop(target) == null) {
            CaravanData.Stop next = chooseNext(level);
            target = next == null ? null : next.pos;
            if (target == null) startLeaving(level);
        }
    }

    // ------------------------------------------------------------------ camps

    private void startCamp(ServerLevel level, int reason) {
        state = State.CAMPING;
        campReason = reason;
        getNavigation().stop();
        campfire = placeCampfire(level);
        announce(level, Component.translatable(reason == CAMP_STORM ? "msg.chroniclecaravans.camp_storm" : "msg.chroniclecaravans.camp_night",
                region.caravanName().withStyle(region.color)), null);
    }

    /** A campfire beside the road (on solid ground, never on top of anything). */
    private BlockPos placeCampfire(ServerLevel level) {
        if (!CCConfig.CAMPFIRES.get()) return null;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double sx = Mth.cos(yaw), sz = Mth.sin(yaw);
        for (int side : new int[] {1, -1}) {
            for (double d = 2.5; d <= 4.5; d += 1.0) {
                BlockPos p = BlockPos.containing(getX() + sx * d * side, getY() + 0.5, getZ() + sz * d * side);
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos q = p.above(dy);
                    BlockPos below = q.below();
                    if (level.getBlockState(q).isAir() && level.getFluidState(q).isEmpty()
                            && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                        level.setBlock(q, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
                        return q;
                    }
                }
            }
        }
        return null;
    }

    private void removeCampfire(ServerLevel level) {
        if (campfire != null && level.isLoaded(campfire) && level.getBlockState(campfire).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) {
            level.removeBlock(campfire, false);
        }
        campfire = null;
    }

    private void breakCamp(ServerLevel level) {
        removeCampfire(level);
        campReason = 0;
        state = State.TRAVELING;
    }

    public BlockPos campfire() {
        return campfire;
    }

    // ------------------------------------------------------------------ parking

    /** Length of the train behind the master (blocks), from his heels to the tail of the last llama, plus a margin. */
    private double trainLength() {
        boolean withWagon = wagonId != null;
        if (llamas.isEmpty()) return withWagon ? 1.6 + 2 * AXLE_HALF + 2.0 : 0;
        double d = 0;
        for (int i = 0; i < llamas.size(); i++) {
            d += i == 0 ? DRAFT_GAP : (i == 1 && withWagon) ? AFTER_WAGON + CaravanWagon.REAR_HITCH : PACK_GAP;
            if (i == 0 && withWagon) d += WAGON_GAP;
        }
        return d + 1.5;
    }

    /** This caravan parks its train before going on foot: always at an inn, at a milestone when another caravan holds the main spot. */
    private boolean wantsParking(ServerLevel level) {
        if (!CCConfig.PARKING.get() || (llamas.isEmpty() && wagonId == null)) return false;
        if (toInn) return true;
        if (target == null || CaravanData.get(level).stop(target) == null) return false;
        for (CaravanMaster m : CaravanSpawner.caravans(level)) {
            if (m != this && target.equals(m.currentStop()) && m.park == PARK_NONE) return true;
        }
        return false;
    }

    /** Hitching posts around {@code center} that no other caravan uses. */
    private List<BlockPos> freePosts(ServerLevel level, BlockPos center) {
        List<BlockPos> out = new ArrayList<>(CaravanData.get(level).postsNear(level, center, com.chroniclecaravans.block.HitchingPostBlock.RANGE));
        for (CaravanMaster m : CaravanSpawner.caravans(level)) if (m != this && m.park != PARK_NONE && m.parkPost != null) out.remove(m.parkPost);
        out.removeIf(p -> !level.isLoaded(p));
        return out;
    }

    /** Finds a strip for the train: at a free hitching post near the stop (or the inn), else (inn only) on open ground off the road. */
    private boolean planParking(ServerLevel level) {
        BlockPos center = target;
        double length = trainLength() + 1.0;
        // Never on top of another train (parked, or halted at the milestone)
        List<Vec3> taken = new ArrayList<>();
        for (CaravanMaster m : CaravanSpawner.caravans(level)) {
            if (m == this || m.distanceToSqr(this) > 64 * 64) continue;
            taken.add(m.position());
            CaravanWagon w = m.wagon(level);
            if (w != null) taken.add(w.position());
            for (TraderLlama l : m.loadedLlamas(level)) taken.add(l.position());
        }
        obstacles = taken;
        for (BlockPos post : freePosts(level, center)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Vec3 head = standOn(level, post.getX() + side.getStepX(), post.getZ() + side.getStepZ(), post.getY());
                if (head == null) continue;
                if (planFrom(level, head, center, length, false, false, side.getStepX(), side.getStepZ())) {
                    parkPost = post;
                    return true;
                }
            }
        }
        if (!toInn) return false;
        // Open ground around the inn: the closest strip that is off the road and under the open sky
        for (int r = 5; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || ((dx + dz) & 1) != 0) continue;
                    Vec3 head = standOn(level, center.getX() + dx, center.getZ() + dz, center.getY());
                    if (head == null) continue;
                    if (planFrom(level, head, center, length, true, true, 0, 0)) {
                        parkPost = null;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Other trains, while a parking strip is being looked for. */
    private List<Vec3> obstacles = List.of();

    /** Tries the eight directions from {@code head} (away from the centre first); {@code sx, sz}: only directions not towards the post. */
    private boolean planFrom(ServerLevel level, Vec3 head, BlockPos center, double length, boolean offRoad, boolean openSky, int sx, int sz) {
        List<double[]> dirs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double bx = Math.round(Math.cos(a) * 1000) / 1000.0, bz = Math.round(Math.sin(a) * 1000) / 1000.0;
            if (bx * sx + bz * sz < -0.01) continue;
            double ax = head.x - (center.getX() + 0.5), az = head.z - (center.getZ() + 0.5);
            dirs.add(new double[] {bx, bz, -(bx * ax + bz * az)});
        }
        dirs.sort((u, v) -> Double.compare(u[2], v[2]));
        for (double[] d : dirs) {
            if (!clearStrip(level, head, d[0], d[1], length + 1.0, offRoad, openSky) || crowded(head, d[0], d[1], length)) continue;
            parkHead = head;
            parkEntry = new Vec3(head.x + d[0] * length, head.y, head.z + d[1] * length);
            return true;
        }
        return false;
    }

    /** Another train stands on (or right next to) the strip. */
    private boolean crowded(Vec3 head, double bx, double bz, double length) {
        for (double s = 0; s <= length; s += 1.0) {
            double x = head.x + bx * s, z = head.z + bz * s;
            for (Vec3 o : obstacles) if ((o.x - x) * (o.x - x) + (o.z - z) * (o.z - z) < 2.5 * 2.5) return true;
        }
        return false;
    }

    /**
     * A strip of ground three blocks wide from {@code head} along (bx, bz): firm, dry, gently sloped, with room for
     * the wagon's height, never on a milestone, a post or a bed; off the roads and under the sky if asked.
     */
    private static boolean clearStrip(ServerLevel level, Vec3 head, double bx, double bz, double length, boolean offRoad, boolean openSky) {
        double y = head.y;
        for (double s = 0.5; s <= length; s += 0.75) {
            double cx = head.x + bx * s, cz = head.z + bz * s;
            Vec3 c = standOn(level, Mth.floor(cx), Mth.floor(cz), Mth.floor(y));
            if (c == null || Math.abs(c.y - y) > 1.01) return false;
            y = c.y;
            BlockPos ground = BlockPos.containing(cx, y - 0.5, cz);
            if (offRoad && level.getBlockState(ground).is(ChronicleCaravans.ROADS)) return false;
            BlockPos top = BlockPos.containing(cx, y + 2.2, cz);
            if (!level.getBlockState(top).getCollisionShape(level, top).isEmpty()) return false;
            if (openSky && !level.canSeeSky(BlockPos.containing(cx, y, cz))) return false;
            for (int side = -1; side <= 1; side += 2) {
                double px = cx - bz * side, pz = cz + bx * side;
                Vec3 q = standOn(level, Mth.floor(px), Mth.floor(pz), Mth.floor(y));
                if (q == null || Math.abs(q.y - y) > 1.01) return false;
            }
        }
        return true;
    }

    /** Where a mob stands in column (x, z) near height {@code refY}: firm ground below, two free blocks above. */
    private static Vec3 standOn(ServerLevel level, int x, int z, int refY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (!level.isLoaded(p.set(x, refY, z))) return null;
        for (int y = refY + 1; y >= refY - 1; y--) {
            p.set(x, y - 1, z);
            BlockState ground = level.getBlockState(p);
            VoxelShape shape = ground.getCollisionShape(level, p);
            if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.5 || !level.getFluidState(p).isEmpty()) continue;
            if (ground.is(BlockTags.BEDS) || ground.is(ChronicleCaravans.MILESTONE.get()) || ground.is(ChronicleCaravans.HITCHING_POST.get())
                    || ground.is(BlockTags.FENCES) || ground.is(BlockTags.WALLS)) return null;
            p.set(x, y, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) continue;
            p.set(x, y + 1, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
            return new Vec3(x + 0.5, y - 1 + shape.max(Direction.Axis.Y), z + 0.5);
        }
        return null;
    }

    /** Walking to the strip, then along it: the train lines up behind the master on its way. */
    private void tickParking(ServerLevel level, TravelGoal goal) {
        if (target == null || parkHead == null || parkEntry == null || parkHead.distanceToSqr(Vec3.atCenterOf(target)) > 32 * 32) {
            // Going elsewhere now (night, a broken milestone): no parking here
            park = PARK_NONE;
            parkPost = null;
            return;
        }
        parkTicks++;
        Vec3 dest = park == PARK_ENTRY ? parkEntry : parkHead;
        double dx = dest.x - getX(), dz = dest.z - getZ();
        double d2 = dx * dx + dz * dz;
        if (park == PARK_ENTRY && d2 <= 2.25) {
            park = PARK_LINE;
            parkTicks = 0;
            getNavigation().moveTo(parkHead.x, parkHead.y, parkHead.z, WALK_SPEED);
            return;
        }
        if (park == PARK_LINE && (d2 <= 0.36 || (getNavigation().isDone() && d2 <= 2.5))) {
            parkTrain(level, true);
            return;
        }
        if (parkTicks > 500) {
            // Could not get there: the train stays where it is
            parkTrain(level, false);
            return;
        }
        if (getNavigation().isDone() || parkTicks % 60 == 0) getNavigation().moveTo(dest.x, dest.y, dest.z, WALK_SPEED);
    }

    /** The train stops; the draft llama is tied to the post (or the wagon); the master goes on foot. */
    private void parkTrain(ServerLevel level, boolean onStrip) {
        getNavigation().stop();
        if (onStrip && parkHead != null && parkEntry != null) {
            // The master walked the strip straight: square the train up on it
            Vec3 back = parkEntry.subtract(parkHead).normalize();
            trail.clear();
            trail.addFirst(position());
            for (double s = 0.5; s <= TRAIL_LENGTH; s += 0.5) {
                Vec3 q = position().add(back.x * s, 0, back.z * s);
                trail.addLast(new Vec3(q.x, groundAt(level, q.x, q.z, getY()), q.z));
            }
            layoutTrain(level);
        } else {
            parkPost = null;
        }
        parkHead = position();
        park = PARK_OUT;
        parkTicks = 0;
        tendTrain(level);
        if (toInn) {
            arriveAtInn(level);
            footSpot = findBed(level, target);
        } else {
            arrive(level);
            footSpot = target == null ? null : CaravanSpawner.findGround(level, target, 2, 4, random);
        }
        if (footSpot == null) park = PARK_STAY;
    }

    /** What the draft llama is tied to while the train is parked: the post (with a lead knot), else the wagon. */
    private Entity parkedHolder(ServerLevel level) {
        if (parkPost != null && level.isLoaded(parkPost) && level.getBlockState(parkPost).is(ChronicleCaravans.HITCHING_POST.get())) {
            return LeashFenceKnotEntity.getOrCreateKnot(level, parkPost);
        }
        return wagon(level);
    }

    /** Takes the lead knot off the post when nothing else is tied there. */
    private void releaseKnot(ServerLevel level) {
        if (parkPost == null || !level.isLoaded(parkPost)) return;
        for (LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(parkPost).inflate(0.5))) {
            boolean used = false;
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(parkPost).inflate(12))) {
                if (mob.getLeashHolder() == knot && !llamas.contains(mob.getUUID())) used = true;
            }
            if (!used) knot.discard();
        }
    }

    /** A free bed at the inn (the head part), closest to the sign. */
    private BlockPos findBed(ServerLevel level, BlockPos inn) {
        if (inn == null) return null;
        List<BlockPos> taken = new ArrayList<>();
        for (CaravanMaster m : CaravanSpawner.caravans(level)) if (m != this && m.footSpot != null) taken.add(m.footSpot);
        BlockPos best = null;
        int r = InnSignBlock.BED_RANGE;
        for (BlockPos p : BlockPos.betweenClosed(inn.offset(-r, -3, -r), inn.offset(r, 3, r))) {
            BlockState b = level.getBlockState(p);
            if (!(b.getBlock() instanceof BedBlock) || b.getValue(BedBlock.PART) != BedPart.HEAD || b.getValue(BedBlock.OCCUPIED)) continue;
            if (taken.contains(p)) continue;
            if (best == null || p.distSqr(inn) < best.distSqr(inn)) best = p.immutable();
        }
        return best;
    }

    /** Every tick while parked: the train is held, and the master goes back to it when it is time to go on. */
    private void tickParked(ServerLevel level) {
        parkTicks++;
        if (tickCount % 20 == 0) {
            CaravanWagon wagon = wagon(level);
            if (wagon != null) wagon.hold();
        }
        if ((state == State.TRAVELING || state == State.LEAVING) && park != PARK_BACK) {
            if (isSleeping()) stopSleeping();
            park = PARK_BACK;
            parkTicks = 0;
            getNavigation().stop();
        }
        if (park == PARK_STAY && state == State.LODGING && tickCount % 100 == 0) goToBed(level);
    }

    /** At night, in his bed at the inn (again, if something woke him up). */
    private void goToBed(ServerLevel level) {
        if (isSleeping() || footSpot == null || !Compat.isNight(level)) return;
        BlockState b = level.getBlockState(footSpot);
        if (!(b.getBlock() instanceof BedBlock) || b.getValue(BedBlock.OCCUPIED)) return;
        if (distanceToSqr(Vec3.atCenterOf(footSpot)) > 9) return;
        startSleeping(footSpot);
    }

    /** The master reached his bed (or his spot by the milestone), or came back to his train. */
    private void footArrived(ServerLevel level) {
        getNavigation().stop();
        if (park == PARK_BACK) {
            unpark(level);
            return;
        }
        park = PARK_STAY;
        parkTicks = 0;
        if (state == State.LODGING) goToBed(level);
    }

    /** Back at the head of the train: it follows him again. */
    private void unpark(ServerLevel level) {
        park = PARK_NONE;
        footSpot = null;
        parkHead = parkEntry = null;
        parkTicks = 0;
        parkFailed = false;
        // Tie the draft llama to the master first: a knot taken away from under a lead would snap it
        tendTrain(level);
        releaseKnot(level);
        parkPost = null;
    }

    /** Test hook: the caravan ends its halt now. */
    public void hurryDeparture() {
        if (state == State.HALTED) haltTicks = Math.min(haltTicks, 1);
    }

    /** Lying in bed: no walking, no looking around. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isSleeping();
    }

    /** On foot while the train is parked: to the bed (or the milestone), then back to the train. Doors open for him. */
    private class FootGoal extends Goal {
        FootGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return (park == PARK_OUT || park == PARK_BACK) && !serving() && !isSleeping();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ((RoadNavigation) getNavigation()).setCanOpenDoors(true);
        }

        @Override
        public void stop() {
            getNavigation().stop();
            ((RoadNavigation) getNavigation()).setCanOpenDoors(false);
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level)) return;
            Vec3 dest = park == PARK_BACK ? parkHead : footSpot == null ? null : Vec3.atBottomCenterOf(footSpot);
            if (dest == null) {
                footArrived(level);
                return;
            }
            double dx = dest.x - getX(), dz = dest.z - getZ(), dy = dest.y - getY();
            double d2 = dx * dx + dz * dz;
            double reach = park == PARK_BACK ? 1.0 : footSpot != null && level.getBlockState(footSpot).getBlock() instanceof BedBlock ? 4.0 : 2.25;
            if (d2 <= reach && Math.abs(dy) < 2.5) {
                footArrived(level);
                return;
            }
            if (parkTicks > 600) {
                // Lost his way: he finds it in the end
                teleportTo(dest.x, dest.y, dest.z);
                footArrived(level);
                return;
            }
            if (getNavigation().isDone() || parkTicks % 40 == 0) getNavigation().moveTo(dest.x, dest.y, dest.z, WALK_SPEED);
        }
    }

    // ------------------------------------------------------------------ cargo

    public List<Parcel> cargo() {
        return cargo;
    }

    /** Parcels the caravan can carry: the wagon holds six, every llama two more. */
    public int cargoCapacity() {
        return 6 + 2 * llamas.size();
    }

    // ------------------------------------------------------------------ escort

    public int escortTicks() {
        return escortTicks;
    }

    public List<CaravanGuard> guards(ServerLevel level) {
        List<CaravanGuard> out = new ArrayList<>();
        for (UUID id : guards) if (level.getEntity(id) instanceof CaravanGuard g && g.isAlive()) out.add(g);
        return out;
    }

    /** Hires (or renews) an escort of {@code count} guards for {@code ticks}. */
    public void startEscort(ServerLevel level, int count, int ticks, boolean veteran, UUID buyer) {
        escortTicks = Math.max(escortTicks, 0) + ticks;
        escortBuyer = buyer;
        guards.removeIf(id -> !(level.getEntity(id) instanceof CaravanGuard g) || !g.isAlive());
        while (guards.size() < count) {
            CaravanGuard g = CaravanGuard.spawnNear(level, this, veteran, buyer);
            if (g == null) break;
            guards.add(g.getUUID());
        }
        level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private void tickEscort(ServerLevel level) {
        if (escortTicks <= 0) {
            if (!guards.isEmpty()) dismissGuards(level);
            return;
        }
        escortTicks -= 20;
        if (escortTicks <= 0) {
            dismissGuards(level);
            announce(level, Component.translatable("msg.chroniclecaravans.escort_over", region.caravanName().withStyle(region.color)), escortBuyer);
        }
    }

    private void dismissGuards(ServerLevel level) {
        for (CaravanGuard g : guards(level)) g.leave(level);
        guards.clear();
        escortTicks = 0;
    }

    // ------------------------------------------------------------------ the train

    /** Gap between the master and the draft llama, then between the llama and the wagon's centre (blocks). */
    private static final double DRAFT_GAP = 2.2, WAGON_GAP = 3.05;
    /** Half the distance between the wagon's axles, and gaps between pack llamas. */
    private static final double AXLE_HALF = 0.95, PACK_GAP = 2.2, AFTER_WAGON = 1.7;
    private static final double TRAIL_LENGTH = 24.0;
    private static final float MAX_PITCH = 14F;

    /** Fills the trail with a straight line behind the master (at spawn, after a warp). */
    public void seedTrail(ServerLevel level) {
        trail.clear();
        wagonY = Double.NaN;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        for (double s = 0.5; s <= TRAIL_LENGTH; s += 0.5) {
            Vec3 p = position().add(back.scale(s));
            trail.addLast(new Vec3(p.x, groundAt(level, p.x, p.z, getY()), p.z));
        }
    }

    /**
     * Height of the ground under (x, z), searched from a little above {@code refY} down: the train always
     * stands on the ground, whatever the master does (jumps, knock-backs, swimming).
     */
    public static double groundAt(ServerLevel level, double x, double z, double refY) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int top = Mth.floor(refY) + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (!level.isLoaded(p.set(bx, top, bz))) return refY;
        for (int y = top; y >= top - 6; y--) {
            p.set(bx, y, bz);
            BlockState s = level.getBlockState(p);
            if (!level.getFluidState(p).isEmpty()) return y + 0.9;
            VoxelShape shape = s.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            double h = y + shape.max(Direction.Axis.Y);
            if (h <= refY + 1.6) return h;
        }
        return refY;
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

    /** The point of the trail {@code s} blocks behind the master (distances measured flat). */
    private Vec3 sample(double s) {
        Vec3 prev = position();
        Vec3 dir = null;
        double acc = 0;
        for (Vec3 q : trail) {
            double seg = Math.sqrt((q.x - prev.x) * (q.x - prev.x) + (q.z - prev.z) * (q.z - prev.z));
            if (seg > 1e-4) {
                if (acc + seg >= s) return prev.lerp(q, (s - acc) / seg);
                dir = new Vec3((q.x - prev.x) / seg, 0, (q.z - prev.z) / seg);
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

    public CaravanWagon wagon(ServerLevel level) {
        return wagonId != null && level.getEntity(wagonId) instanceof CaravanWagon w && w.isAlive() ? w : null;
    }

    /** Places the wagon between two axle points, on the ground, with a gentle (and smoothed) slope. */
    private void placeWagon(ServerLevel level, CaravanWagon wagon, Vec3 front, Vec3 rear) {
        double fy = groundAt(level, front.x, front.z, front.y);
        double ry = groundAt(level, rear.x, rear.z, rear.y);
        double y = (fy + ry) * 0.5;
        if (Double.isNaN(wagonY) || Math.abs(y - wagonY) > 3) wagonY = y;
        else wagonY += (y - wagonY) * 0.35;
        float pitch = Mth.clamp((float) (Mth.atan2(ry - fy, 2 * AXLE_HALF) * Mth.RAD_TO_DEG), -MAX_PITCH, MAX_PITCH);
        wagonPitch += Mth.clamp(pitch - wagonPitch, -2F, 2F);
        Vec3 c = front.add(rear).scale(0.5);
        wagon.place(new Vec3(c.x, wagonY, c.z), yawTowards(rear, front), wagonPitch);
    }

    /** Places every member of the train on the trail. Called every tick. */
    private void layoutTrain(ServerLevel level) {
        if (llamas.isEmpty() && wagonId == null) return;
        CaravanWagon wagon = wagon(level);
        if (trail.size() < 2) seedTrailFromMembers(level, wagon);
        if (llamas.isEmpty() && wagon != null) {
            // No llama left: the master drags the wagon himself
            placeWagon(level, wagon, sample(1.6), sample(1.6 + 2 * AXLE_HALF));
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
                double y = groundAt(level, p.x, p.z, p.y);
                llama.setPos(p.x, y, p.z);
                llama.setDeltaMovement(Vec3.ZERO);
                llama.setYRot(yaw);
                llama.setYHeadRot(yaw);
                llama.yBodyRot = yaw;
            }
            if (i == 0 && wagon != null) {
                d += WAGON_GAP;
                placeWagon(level, wagon, sample(d - AXLE_HALF), sample(d + AXLE_HALF));
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
            Entity expected = prev != this ? prev : park >= PARK_OUT ? parkedHolder(level) : this;
            if (expected == null) {
                // Parked with nothing to tie it to: no lead (it would snap when the master walks away)
                if (holder != null) Compat.unleash(llama);
            } else if (holder != expected) {
                llama.setLeashedTo(expected, true);
            }
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
        dismissGuards(level);
    }

    // ------------------------------------------------------------------ route

    private void arrive(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop stop = data.stop(target);
        state = State.HALTED;
        haltTicks = CCConfig.HALT_TIME.get() * 20;
        visited.remove(target);
        visited.add(target);
        while (visited.size() > 4) visited.remove(0);
        getNavigation().stop();
        level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_REAPPEARED, SoundSource.NEUTRAL, 1.2F, 1.0F);
        level.playSound(null, target, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
        restock(true);
        if (stop == null) return;
        // Freight: parcels for here are delivered, the others wait for their next leg; the docks hand over theirs
        if (CCConfig.FREIGHT.get()) {
            Logistics.unload(level, this, stop);
            Logistics.collectDocks(level, stop);
        }
        long now = level.getGameTime();
        long interval = CCConfig.VISIT_INTERVAL.get() * 20L;
        // A relay takes several caravans: they come 3, 6 or 10 times as often (tiers 2, 3, 4)
        int cap = CaravanSpawner.capacity(level, stop);
        stop.nextVisit = now + (long) (interval * (0.75 + random.nextDouble() * 0.5) / (cap * (cap + 1) / 2.0));
        stop.lastServed = now;
        stop.hired = false;
        data.setDirty();
        Component msg = Component.translatable("msg.chroniclecaravans.arrived", region.caravanName().withStyle(region.color), stop.displayName());
        announce(level, msg, stop.owner);
        if (stop.owner != null) {
            data.addXp(level.getServer(), stop.owner, 2, "reason.chroniclecaravans.host");
            // Sworn Shield: the caravans you host get a guard while they stay
            if (Guild.has(data.skills(stop.owner), Guild.Skill.SWORN_SHIELD) && escortTicks <= 0) {
                startEscort(level, 1, haltTicks + 200, true, stop.owner);
            }
        }
    }

    /** The best next stop: one that waits, a village, close by, not just visited, that no one else serves. */
    private CaravanData.Stop chooseNext(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        List<CaravanData.Stop> options = data.linked(blockPosition());
        if (options.isEmpty()) return null;
        long now = level.getGameTime();
        List<CaravanMaster> others = CaravanSpawner.caravans(level);
        CaravanData.Stop best = null;
        double bestScore = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (CaravanData.Stop s : options) {
                if (pass == 0 && visited.contains(s.pos)) continue;
                if (pass == 1 && !visited.isEmpty() && s.pos.equals(visited.get(visited.size() - 1))) continue;
                int coming = 0;
                for (CaravanMaster m : others) if (m != this && s.pos.equals(m.destination())) coming++;
                boolean taken = coming >= CaravanSpawner.capacity(level, s);
                double score = Math.sqrt(s.pos.distSqr(blockPosition())) / CCConfig.LINK_RANGE.get() * 10.0;
                if (now >= s.nextVisit) score -= 8;
                if (s.hired) score -= 20;
                if (s.village) score -= 4;
                if (taken) score += 25;
                if (level.getNearestPlayer(s.pos.getX(), s.pos.getY(), s.pos.getZ(), 96, false) != null) score -= 5;
                List<Parcel> waiting = data.depots().get(s.pos);
                if (waiting != null && !waiting.isEmpty()) score -= 6;
                score += random.nextDouble() * 3;
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
        }
        return best;
    }

    private void depart(ServerLevel level) {
        CaravanData data = CaravanData.get(level);
        BlockPos here = target;
        // Parcels waiting here decide the way, if any can travel
        BlockPos freight = here == null || !CCConfig.FREIGHT.get() ? null : Logistics.pickNext(level, here);
        CaravanData.Stop next = freight != null ? data.stop(freight) : null;
        if (next == null) next = chooseNext(level);
        if (next == null || (lifeTicks > LIFETIME && cargo.isEmpty() && freight == null)) {
            startLeaving(level);
            return;
        }
        if (here != null && CCConfig.FREIGHT.get()) Logistics.load(level, this, here, next.pos);
        target = next.pos;
        state = State.TRAVELING;
        announce(level, Component.translatable("msg.chroniclecaravans.departs", region.caravanName().withStyle(region.color), next.displayName()), null);
    }

    public void startLeaving(ServerLevel level) {
        if (state == State.LEAVING) return;
        state = State.LEAVING;
        toInn = false;
        leaveTicks = 0;
        double a = random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(getX() + Math.cos(a) * 48);
        int z = (int) Math.floor(getZ() + Math.sin(a) * 48);
        BlockPos probe = new BlockPos(x, getBlockY(), z);
        int y = level.isLoaded(probe) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : getBlockY();
        target = new BlockPos(x, y, z);
        announce(level, Component.translatable("msg.chroniclecaravans.leaves", region.caravanName().withStyle(region.color)), null);
    }

    public void vanish(ServerLevel level) {
        vanish(level, true);
    }

    /** The whole caravan disappears (it has left the area). */
    public void vanish(ServerLevel level, boolean effects) {
        for (TraderLlama llama : loadedLlamas(level)) {
            if (llama.isTamed() || llama.getLeashHolder() instanceof Player) continue;
            if (effects) level.sendParticles(ParticleTypes.POOF, llama.getX(), llama.getY() + 1, llama.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
            Compat.unleash(llama);
            llama.discard();
        }
        CaravanWagon wagon = wagon(level);
        if (wagon != null) {
            if (effects) level.sendParticles(ParticleTypes.POOF, wagon.getX(), wagon.getY() + 1, wagon.getZ(), 30, 1.0, 0.8, 1.0, 0.02);
            wagon.discard();
        }
        dismissGuards(level);
        removeCampfire(level);
        if (isSleeping()) stopSleeping();
        releaseKnot(level);
        Logistics.store(level, blockPosition(), cargo);
        if (effects) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 16, 0.3, 0.6, 0.3, 0.02);
            level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_DISAPPEARED, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        if (registered) CaravanData.get(level).forget(getUUID());
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
        for (CaravanGuard g : guards(level)) g.teleportTo(dest.x, dest.y, dest.z);
    }

    /** While players look at the caravan's interface, it stays put and faces them. */
    private class ServeGoal extends Goal {
        ServeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return serving();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getNavigation().stop();
            Player nearest = level().getNearestPlayer(CaravanMaster.this, 8);
            if (nearest != null) getLookControl().setLookAt(nearest, 30F, 30F);
        }
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
            return (state == State.TRAVELING || state == State.LEAVING) && target != null && !serving() && park < PARK_OUT;
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
            if (park == PARK_ENTRY || park == PARK_LINE) {
                tickParking(level, this);
                return;
            }
            double d2 = distanceToSqr(Vec3.atBottomCenterOf(target));
            if (state != State.LEAVING && park == PARK_NONE && !parkFailed && d2 <= 24 * 24 && wantsParking(level)) {
                if (planParking(level)) {
                    park = PARK_ENTRY;
                    parkTicks = 0;
                    getNavigation().stop();
                    return;
                }
                parkFailed = true;
            }
            if (d2 <= (state == State.LEAVING ? 16.0 : toInn ? 64.0 : 20.25)) {
                getNavigation().stop();
                if (state == State.LEAVING) vanish(level, true);
                else if (toInn && CCConfig.PARKING.get() && (!llamas.isEmpty() || wagonId != null)) parkTrain(level, false);
                else if (toInn) arriveAtInn(level);
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
        if (ambushDone || ambushActive || state == State.LEAVING || state == State.LODGING || park != PARK_NONE || !CCConfig.AMBUSH_ENABLED.get()) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL || lifeTicks < 600) return;
        if (level.getNearestPlayer(this, 48) == null) return;
        // Bandits like the dark, and stay away from an escort
        double chance = CCConfig.AMBUSH_CHANCE.get() * (Compat.isNight(level) ? 2.0 : 1.0) * (escortTicks > 0 ? 0.5 : 1.0);
        if (random.nextDouble() < chance) startAmbush(level);
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
        for (UUID id : defenders) data.addXp(level.getServer(), id, 4, "reason.chroniclecaravans.rescue");
        defenders.clear();
    }

    /** One of the bandits of this caravan's ambush was killed by {@code player} (or by their guards). */
    public void onBanditKilled(ServerLevel level, UUID player) {
        defenders.add(player);
        CaravanData.get(level).addXp(level.getServer(), player, 2, "reason.chroniclecaravans.bandit");
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
            CaravanData.get(p.serverLevel()).addXp(p, -5, "reason.chroniclecaravans.attack");
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            breakTrain(level);
            removeCampfire(level);
            releaseKnot(level);
            // The cargo falls on the road
            Logistics.spill(level, blockPosition(), cargo);
            CaravanData.get(level).forget(getUUID());
            announce(level, Component.translatable("msg.chroniclecaravans.lost", region.caravanName().withStyle(region.color)).withStyle(ChatFormatting.DARK_RED), null);
            if (source.getEntity() instanceof ServerPlayer p && !p.isCreative()) {
                CaravanData.get(level).addXp(p, -40, "reason.chroniclecaravans.murder");
            }
        }
    }

    // ------------------------------------------------------------------ storage

    //#if mc26
    //$$ @Override
    //$$ protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    //$$     super.addAdditionalSaveData(out);
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
        super.addAdditionalSaveData(tag);
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
        if (resumeTarget != null) tag.putLong("ResumeTarget", resumeTarget.asLong());
        tag.putBoolean("ToInn", toInn);
        tag.putBoolean("LodgedTonight", lodgedTonight);
        tag.putBoolean("Registered", registered);
        tag.putInt("HaltTicks", haltTicks);
        tag.putInt("LeaveTicks", leaveTicks);
        tag.putInt("LifeTicks", lifeTicks);
        tag.putLongArray("Visited", visited.stream().mapToLong(BlockPos::asLong).toArray());
        Nbt.putUUIDs(tag, "Llamas", llamas);
        if (wagonId != null) Nbt.putUUID(tag, "Wagon", wagonId);
        tag.putBoolean("AmbushDone", ambushDone);
        tag.putBoolean("AmbushActive", ambushActive);
        tag.putInt("AmbushTicks", ambushTicks);
        Nbt.putUUIDs(tag, "Raiders", raiders);
        Nbt.putUUIDs(tag, "Defenders", defenders);
        Nbt.putUUIDs(tag, "Guards", guards);
        tag.putInt("EscortTicks", escortTicks);
        if (escortBuyer != null) Nbt.putUUID(tag, "EscortBuyer", escortBuyer);
        tag.put("Cargo", Parcel.saveAll(cargo));
        tag.putInt("CampReason", campReason);
        if (campfire != null) tag.putLong("Campfire", campfire.asLong());
        tag.putInt("Park", park);
        tag.putInt("ParkTicks", parkTicks);
        putVec(tag, "ParkHead", parkHead);
        putVec(tag, "ParkEntry", parkEntry);
        if (parkPost != null) tag.putLong("ParkPost", parkPost.asLong());
        if (footSpot != null) tag.putLong("FootSpot", footSpot.asLong());
    }

    private static void putVec(CompoundTag tag, String key, Vec3 v) {
        if (v == null) return;
        CompoundTag t = new CompoundTag();
        t.putDouble("X", v.x);
        t.putDouble("Y", v.y);
        t.putDouble("Z", v.z);
        tag.put(key, t);
    }

    private static Vec3 getVec(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        CompoundTag t = Nbt.getCompound(tag, key);
        return new Vec3(Nbt.getDouble(t, "X"), Nbt.getDouble(t, "Y"), Nbt.getDouble(t, "Z"));
    }

    private void readCaravan(CompoundTag tag) {
        region = Region.byName(Nbt.getString(tag, "CaravanRegion"));
        try {
            state = State.valueOf(Nbt.getString(tag, "CaravanState"));
        } catch (IllegalArgumentException e) {
            state = State.TRAVELING;
        }
        target = tag.contains("CaravanTarget") ? BlockPos.of(Nbt.getLong(tag, "CaravanTarget")) : null;
        resumeTarget = tag.contains("ResumeTarget") ? BlockPos.of(Nbt.getLong(tag, "ResumeTarget")) : null;
        toInn = Nbt.getBoolean(tag, "ToInn");
        lodgedTonight = Nbt.getBoolean(tag, "LodgedTonight");
        registered = Nbt.getBoolean(tag, "Registered");
        haltTicks = Nbt.getInt(tag, "HaltTicks");
        leaveTicks = Nbt.getInt(tag, "LeaveTicks");
        lifeTicks = Nbt.getInt(tag, "LifeTicks");
        visited.clear();
        for (long l : Nbt.getLongArray(tag, "Visited")) visited.add(BlockPos.of(l));
        Nbt.getUUIDs(tag, "Llamas", llamas);
        wagonId = Nbt.hasUUID(tag, "Wagon") ? Nbt.getUUID(tag, "Wagon") : null;
        ambushDone = Nbt.getBoolean(tag, "AmbushDone");
        ambushActive = Nbt.getBoolean(tag, "AmbushActive");
        ambushTicks = Nbt.getInt(tag, "AmbushTicks");
        Nbt.getUUIDs(tag, "Raiders", raiders);
        Nbt.getUUIDs(tag, "Defenders", defenders);
        Nbt.getUUIDs(tag, "Guards", guards);
        escortTicks = Nbt.getInt(tag, "EscortTicks");
        escortBuyer = Nbt.hasUUID(tag, "EscortBuyer") ? Nbt.getUUID(tag, "EscortBuyer") : null;
        Parcel.loadAll(Nbt.getList(tag, "Cargo"), cargo);
        campReason = Nbt.getInt(tag, "CampReason");
        campfire = tag.contains("Campfire") ? BlockPos.of(Nbt.getLong(tag, "Campfire")) : null;
        park = Nbt.getInt(tag, "Park");
        parkTicks = Nbt.getInt(tag, "ParkTicks");
        parkHead = getVec(tag, "ParkHead");
        parkEntry = getVec(tag, "ParkEntry");
        parkPost = tag.contains("ParkPost") ? BlockPos.of(Nbt.getLong(tag, "ParkPost")) : null;
        footSpot = tag.contains("FootSpot") ? BlockPos.of(Nbt.getLong(tag, "FootSpot")) : null;
        // A broken save never leaves a train parked for good
        if (park != PARK_NONE && parkHead == null) park = PARK_NONE;
        if ((park == PARK_ENTRY || park == PARK_LINE) && parkEntry == null) park = PARK_NONE;
    }
}
