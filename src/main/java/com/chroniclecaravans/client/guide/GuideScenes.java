package com.chroniclecaravans.client.guide;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.block.DockBlock;
import com.chroniclecaravans.block.TradingPostBlock;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.client.guide.GuideScene.Actor;
import com.chroniclecaravans.entity.CaravanWagon;
import com.chroniclecaravans.platform.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The scenes of the Caravan Guide. */
public final class GuideScenes {
    private GuideScenes() {}

    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();

    public static List<GuideScene> all() {
        return List.of(milestone(), route(), caravan(), villages(), ambush(), hire(), inn(), services(), weather(), parcel(), freight(), automation());
    }

    private static BlockState milestoneBlock() {
        return ChronicleCaravans.MILESTONE.get().defaultBlockState();
    }

    private static ItemStack milestoneItem() {
        return new ItemStack(ChronicleCaravans.MILESTONE_ITEM.get());
    }

    // ---------------------------------------------------------------- entities

    static Entity master(Level level) {
        return Compat.create(ChronicleCaravans.CARAVAN_MASTER.get(), level);
    }

    static Entity llama(Level level, boolean chest, Llama.Variant variant) {
        TraderLlama llama = Compat.create(EntityType.TRADER_LLAMA, level);
        if (llama != null) {
            llama.setChest(chest);
            //#if !mc26
            llama.setVariant(variant);
            //#endif
        }
        return llama;
    }

    static Entity wagon(Level level, Region region) {
        CaravanWagon wagon = Compat.create(ChronicleCaravans.WAGON.get(), level);
        if (wagon != null) wagon.setRegion(region);
        return wagon;
    }

    static Entity pillager(Level level) {
        Pillager p = Compat.create(EntityType.PILLAGER, level);
        if (p != null) p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        return p;
    }

    /**
     * A whole caravan walking along +x at depth {@code z}. {@code moves} = tick, x of the master, tick, x...
     * Returns master, draft llama, wagon, then the pack llamas.
     */
    static Actor[] train(GuideScene s, double z, Region region, int packs, double... moves) {
        double[] offsets = {0, -2.2, -5.25, -8.6, -10.8, -13.0};
        Llama.Variant[] coats = {Llama.Variant.BROWN, Llama.Variant.CREAMY, Llama.Variant.WHITE, Llama.Variant.GRAY};
        Actor[] out = new Actor[3 + packs];
        for (int i = 0; i < out.length; i++) {
            final int k = i;
            Actor a = switch (i) {
                case 0 -> s.actor(GuideScenes::master);
                case 1 -> s.actor(l -> llama(l, false, Llama.Variant.BROWN));
                case 2 -> s.actor(l -> wagon(l, region));
                default -> s.actor(l -> llama(l, true, coats[(k - 2) % coats.length]));
            };
            for (int m = 0; m + 1 < moves.length; m += 2) a.key((int) moves[m], moves[m + 1] + offsets[i], 1, z);
            a.facing(-90F);
            out[i] = a;
        }
        out[1].leash(out[0]);
        if (out.length > 3) out[3].leash(out[2]);
        for (int i = 4; i < out.length; i++) out[i].leash(out[i - 1]);
        return out;
    }

    // ---------------------------------------------------------------- 1. the milestone

    static GuideScene milestone() {
        GuideScene s = new GuideScene("milestone", 250, 7, 7, milestoneItem());
        s.plate(GRASS);
        s.block(3, 1, 3, milestoneBlock(), 22);
        s.text(30, 128, "place", GuideScene.at(3.5, 1.95, 3.5))
                .icons(new ItemStack(Items.LANTERN), new ItemStack(Items.EMERALD), new ItemStack(Items.STONE_BRICKS, 3), GuideScene.ARROW, milestoneItem())
                .highlight(3, 1, 3);
        s.text(134, 245, "info", GuideScene.at(3.5, 1.95, 3.5)).highlight(3, 1, 3);
        return s;
    }

    // ---------------------------------------------------------------- 2. trade routes

    static GuideScene route() {
        GuideScene s = new GuideScene("route", 330, 15, 5, new ItemStack(Items.DIRT_PATH));
        s.plate(GRASS);
        s.block(1, 1, 2, milestoneBlock(), 10);
        s.block(13, 1, 2, milestoneBlock(), 18);
        s.text(26, 112, "link", GuideScene.at(7.5, 1.4, 2.5)).highlight(new AABB(1, 1, 2, 14, 2, 3));
        for (int x = 2; x <= 12; x++) s.block(x, 0, 2, PATH, 116 + (x - 2) * 5);
        s.text(122, 212, "road", GuideScene.at(7.5, 1.0, 2.5)).highlight(new AABB(2, 0, 2, 13, 1, 3));
        s.block(4, 0, 2, Blocks.GRAVEL.defaultBlockState(), 226).block(5, 0, 2, Blocks.GRAVEL.defaultBlockState(), 229);
        s.block(7, 0, 2, Blocks.COBBLESTONE.defaultBlockState(), 234).block(8, 0, 2, Blocks.COBBLESTONE.defaultBlockState(), 237);
        s.block(10, 0, 2, Blocks.STONE_BRICKS.defaultBlockState(), 242).block(11, 0, 2, Blocks.OAK_PLANKS.defaultBlockState(), 245);
        s.text(218, 325, "blocks", GuideScene.at(7.5, 1.0, 2.5))
                .icons(new ItemStack(Items.DIRT_PATH), new ItemStack(Items.GRAVEL), new ItemStack(Items.COBBLESTONE),
                        new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.MUD_BRICKS));
        return s;
    }

    // ---------------------------------------------------------------- 3. the caravan

    static GuideScene caravan() {
        GuideScene s = new GuideScene("caravan", 410, 22, 5, new ItemStack(Items.CHEST));
        s.plate(GRASS);
        for (int x = 0; x <= 19; x++) s.ground(x, 2, PATH);
        s.block(20, 1, 2, milestoneBlock(), 4);
        Actor[] t = train(s, 2.5, Region.DUNES, 2, 0, 10.4, 20, 10.4, 150, 18.7);
        Actor wagon = t[2];
        wagon.each((e, time) -> {
            if (!(e instanceof CaravanWagon w)) return;
            Region[] all = Region.values();
            w.setRegion(time < 258 ? Region.DUNES : all[((int) (time - 258) / 18) % all.length]);
        });
        s.text(12, 104, "arrive", GuideScene.above(wagon, 2.8));
        s.text(158, 250, "trade", GuideScene.above(t[0], 2.1));
        s.text(256, 405, "regions", GuideScene.above(wagon, 2.8))
                .icons(new ItemStack(Items.CACTUS), new ItemStack(Items.BLUE_ICE), new ItemStack(Items.BAMBOO), new ItemStack(Items.AMETHYST_SHARD),
                        new ItemStack(Items.PRISMARINE_SHARD), new ItemStack(Items.SLIME_BALL), new ItemStack(Items.HONEYCOMB), new ItemStack(Items.SADDLE));
        return s;
    }

    // ---------------------------------------------------------------- 4. villages

    static GuideScene villages() {
        GuideScene s = new GuideScene("villages", 330, 18, 9, new ItemStack(Items.BELL));
        s.plate(GRASS);
        for (int x = 0; x < 18; x++) s.ground(x, 4, PATH);
        for (int z = 0; z < 9; z++) if (z != 4) s.ground(13, z, PATH);
        s.block(14, 1, 6, Blocks.BELL.defaultBlockState(), 8);
        s.block(15, 1, 7, Blocks.HAY_BLOCK.defaultBlockState(), 12).block(16, 1, 7, Blocks.HAY_BLOCK.defaultBlockState(), 14)
                .block(15, 2, 7, Blocks.HAY_BLOCK.defaultBlockState(), 16).block(11, 1, 6, Blocks.COMPOSTER.defaultBlockState(), 18)
                .block(16, 1, 2, Blocks.OAK_FENCE.defaultBlockState(), 20).block(16, 2, 2, Blocks.LANTERN.defaultBlockState(), 22)
                .block(11, 1, 1, Blocks.OAK_LOG.defaultBlockState(), 24).block(11, 2, 1, Blocks.OAK_LEAVES.defaultBlockState(), 26);
        s.actor(l -> Compat.create(EntityType.VILLAGER, l)).appear(20).key(20, 15.5, 1, 5.6).facing(160F);
        Actor[] t = train(s, 4.5, Region.WOODS, 1, 0, 9.2, 120, 9.2, 220, 12.6);
        s.text(26, 118, "bells", GuideScene.at(14.5, 2.0, 6.5)).icons(new ItemStack(Items.BELL)).highlight(14, 1, 6);
        s.text(124, 230, "paths", GuideScene.above(t[2], 2.8)).highlight(new AABB(0, 0, 4, 18, 1, 5));
        s.text(236, 326, "network", GuideScene.above(t[0], 2.1)).icons(new ItemStack(Items.BELL), GuideScene.ARROW, milestoneItem());
        return s;
    }

    // ---------------------------------------------------------------- 5. ambushes and reputation

    static GuideScene ambush() {
        GuideScene s = new GuideScene("ambush", 390, 15, 7, new ItemStack(Items.CROSSBOW));
        s.plate(GRASS);
        for (int x = 0; x <= 14; x++) s.ground(x, 4, PATH);
        s.block(14, 1, 3, milestoneBlock(), 0);
        Actor[] t = train(s, 4.5, Region.FROST, 1, 0, 12.9);
        Actor master = t[0];
        double[][] spots = {{4.5, 0.2, 2.0}, {7.5, 0.0, 2.3}, {10.5, 0.3, 2.0}};
        Actor[] bandits = new Actor[3];
        for (int i = 0; i < 3; i++) {
            double[] p = spots[i];
            bandits[i] = s.actor(GuideScenes::pillager).appear(18 + i * 4)
                    .key(18 + i * 4, p[0], 1, p[1]).key(60 + i * 4, p[0], 1, p[2]).facing(0F)
                    .dies(118 + i * 15);
        }
        s.text(26, 108, "bandits", GuideScene.above(bandits[1], 2.2));
        Vec3 from = master.pos(0).add(0, 1.4, 0);
        s.item(new ItemStack(Items.EMERALD), 168, 182, 390, from, new Vec3(10.6, 1.15, 2.4));
        s.item(new ItemStack(Items.EMERALD), 171, 185, 390, from, new Vec3(11.3, 1.15, 1.9));
        s.item(new ItemStack(Items.BLUE_ICE), 175, 190, 390, from, new Vec3(10.0, 1.15, 1.7));
        s.text(172, 258, "reward", GuideScene.at(10.6, 1.5, 2.1)).icons(new ItemStack(Items.EMERALD, 5), new ItemStack(Items.BLUE_ICE, 2));
        MutableComponent ranks = Component.translatable("guide.chroniclecaravans.ambush.reputation").append("\n");
        Guild.Branch[] branches = Guild.Branch.values();
        for (int i = 0; i < branches.length; i++) {
            ranks.append(branches[i].label());
            if (i < branches.length - 1) ranks.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        }
        s.text(264, 386, ranks, GuideScene.above(master, 2.1));
        return s;
    }

    // ---------------------------------------------------------------- 6. hiring a caravan

    static GuideScene hire() {
        GuideScene s = new GuideScene("hire", 270, 7, 7, new ItemStack(Items.EMERALD_BLOCK));
        s.plate(GRASS);
        s.block(3, 1, 3, milestoneBlock(), 0);
        s.item(new ItemStack(Items.EMERALD_BLOCK), 18, 44, 52, new Vec3(3.5, 4.2, 1.2), new Vec3(3.5, 2.25, 3.5));
        s.text(14, 125, "use", GuideScene.at(3.5, 2.0, 3.5)).icons(new ItemStack(Items.EMERALD_BLOCK), GuideScene.ARROW, milestoneItem()).highlight(3, 1, 3);
        s.actor(GuideScenes::master).appear(70).key(70, 1.6, 1, 6.2).key(110, 2.6, 1, 4.4).facing(-135F);
        s.text(132, 265, "config", GuideScene.at(3.5, 1.95, 3.5));
        return s;
    }

    // ---------------------------------------------------------------- 7. inns

    static GuideScene inn() {
        GuideScene s = new GuideScene("inn", 330, 15, 8, new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()));
        s.plate(GRASS);
        for (int x = 0; x < 15; x++) s.ground(x, 5, PATH);
        BlockState bed = Blocks.RED_BED.defaultBlockState();
        s.block(11, 1, 4, ChronicleCaravans.INN_SIGN.get().defaultBlockState(), 14)
                .block(12, 1, 2, bed, 18)
                .block(12, 1, 1, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 18)
                .block(10, 1, 4, Blocks.BARREL.defaultBlockState(), 22)
                .block(14, 1, 1, Blocks.OAK_FENCE.defaultBlockState(), 24).block(14, 2, 1, Blocks.LANTERN.defaultBlockState(), 26);
        Actor[] t = train(s, 5.5, Region.STEPPE, 1, 0, -1.0, 120, 9.6);
        s.text(20, 112, "sign", GuideScene.at(11.5, 2.0, 4.5)).icons(new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()), new ItemStack(Items.RED_BED)).highlight(11, 1, 4);
        s.text(118, 222, "night", GuideScene.above(t[0], 2.1));
        s.item(new ItemStack(Items.EMERALD, 3), 236, 256, 330, t[0].pos(200).add(0, 1.4, 0), new Vec3(10.5, 2.05, 4.5));
        s.text(228, 326, "morning", GuideScene.at(10.5, 2.0, 4.5)).icons(new ItemStack(Items.EMERALD, 3), GuideScene.ARROW, new ItemStack(Items.BARREL));
        return s;
    }

    // ---------------------------------------------------------------- 8. services: road crews and escorts

    static Entity builder(Level level) {
        Entity e = Compat.create(ChronicleCaravans.BUILDER.get(), level);
        if (e instanceof net.minecraft.world.entity.Mob m) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        return e;
    }

    static Entity guard(Level level) {
        Entity e = Compat.create(ChronicleCaravans.GUARD.get(), level);
        if (e instanceof net.minecraft.world.entity.Mob m) {
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            m.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        }
        return e;
    }

    static GuideScene services() {
        GuideScene s = new GuideScene("services", 380, 17, 8, new ItemStack(Items.IRON_SHOVEL));
        s.plate(GRASS);
        s.block(1, 1, 4, milestoneBlock(), 0).block(16, 1, 4, milestoneBlock(), 0);
        for (int x = 2; x <= 15; x++) {
            int tick = 40 + (x - 2) * 11;
            for (int z = 3; z <= 5; z++) s.block(x, 0, z, PATH, tick);
        }
        s.actor(GuideScenes::builder).appear(30).key(30, 2.5, 1, 4.5).key(185, 15.5, 1, 4.5).vanish(200).facing(-90F);
        s.text(34, 196, "crew", GuideScene.at(8.5, 2.6, 4.5)).icons(new ItemStack(Items.EMERALD, 12), GuideScene.ARROW, new ItemStack(Items.DIRT_PATH));
        s.actor(GuideScenes::master).appear(205).key(205, 7.5, 1, 1.6).facing(0F);
        s.actor(GuideScenes::guard).appear(222).key(222, 5.6, 1, 2.4).facing(20F);
        s.actor(GuideScenes::guard).appear(230).key(230, 9.4, 1, 2.4).facing(-20F);
        s.text(236, 376, "escort", GuideScene.at(7.5, 3.3, 1.6)).icons(new ItemStack(Items.EMERALD, 14), GuideScene.ARROW, new ItemStack(Items.IRON_SWORD));
        return s;
    }

    // ---------------------------------------------------------------- 9. weather and camps

    static GuideScene weather() {
        GuideScene s = new GuideScene("weather", 400, 15, 7, new ItemStack(Items.CAMPFIRE));
        s.plate(GRASS);
        for (int x = 0; x < 15; x++) s.ground(x, 3, PATH);
        Actor[] t = train(s, 3.5, Region.HIGHLANDS, 1, 0, -1.0, 92, 7.6, 300, 7.6, 392, 15.2);
        s.block(8, 1, 5, Blocks.CAMPFIRE.defaultBlockState(), 98).block(8, 1, 5, Blocks.AIR.defaultBlockState(), 296);
        s.block(9, 1, 6, Blocks.SPRUCE_LOG.defaultBlockState(), 104).block(9, 1, 6, Blocks.AIR.defaultBlockState(), 296);
        s.text(100, 196, "storm", GuideScene.above(t[0], 2.1)).icons(new ItemStack(Items.CAMPFIRE)).highlight(8, 1, 5);
        s.text(200, 292, "night", GuideScene.at(8.5, 1.8, 5.5)).icons(new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get()));
        s.text(300, 396, "onward", GuideScene.above(t[2], 2.8));
        return s;
    }

    // ---------------------------------------------------------------- 10. freight and loading docks

    // ---------------------------------------------------------------- 10. a parcel sent by hand

    static GuideScene parcel() {
        GuideScene s = new GuideScene("parcel", 460, 19, 8, new ItemStack(ChronicleCaravans.PARCEL.get()));
        s.plate(GRASS);
        for (int x = 0; x < 19; x++) s.ground(x, 5, PATH);
        s.block(3, 1, 4, milestoneBlock(), 4);
        s.block(16, 1, 4, milestoneBlock(), 8).block(16, 1, 3, Blocks.BARREL.defaultBlockState(), 12);
        ItemStack parcel = new ItemStack(ChronicleCaravans.PARCEL.get());
        Actor[] t = train(s, 5.5, Region.COAST, 1, 0, 6.2, 200, 6.2, 330, 19.4);
        Actor wagon = t[2];
        // One package on the roof per parcel on board
        wagon.each((e, time) -> {
            if (e instanceof CaravanWagon w) w.setParcels(time >= 176 && time < 352 ? 2 : time >= 160 && time < 352 ? 1 : 0);
        });
        Vec3 onMilestone = new Vec3(3.5, 2.3, 4.5), barrel = new Vec3(16.5, 2.1, 3.5);
        // Packed at the caravan, the parcels wait at the milestone, then board
        s.item(new ItemStack(Items.EMERALD, 2), 16, 34, 60, new Vec3(3.5, 4.0, 7.0), t[0].pos(20).add(0, 1.2, 0));
        s.item(parcel, 40, 58, 146, new Vec3(3.5, 4.0, 7.0), onMilestone);
        s.item(parcel, 60, 78, 162, new Vec3(3.5, 4.0, 7.0), onMilestone.add(0, 0.35, 0));
        s.text(14, 120, "send", GuideScene.at(3.5, 2.4, 4.5)).icons(new ItemStack(Items.EMERALD), GuideScene.ARROW, parcel)
                .highlight(3, 1, 4);
        s.item(parcel, 146, 160, 160, onMilestone, wagon.pos(160).add(0, 2.7, 0));
        s.item(parcel, 162, 176, 176, onMilestone.add(0, 0.35, 0), wagon.pos(176).add(0, 2.7, 0));
        s.text(130, 250, "roof", GuideScene.above(wagon, 3.2)).icons(parcel, GuideScene.ARROW, milestoneItem());
        // At the destination: into the barrel against the milestone, then unpacked
        s.item(parcel, 352, 368, 460, wagon.pos(352).add(0, 2.7, 0), barrel);
        s.text(340, 410, "arrive", GuideScene.at(16.5, 2.2, 3.5)).icons(parcel, GuideScene.ARROW, new ItemStack(Items.BARREL))
                .highlight(new AABB(16, 1, 3, 17, 2, 5));
        s.item(new ItemStack(Items.APPLE, 12), 410, 424, 460, barrel, barrel.add(0, 1.2, 1.5));
        s.text(404, 458, "open", GuideScene.at(16.5, 2.6, 4.5)).icons(parcel, GuideScene.ARROW, new ItemStack(Items.APPLE));
        return s;
    }

    static GuideScene freight() {
        GuideScene s = new GuideScene("freight", 520, 19, 8, new ItemStack(ChronicleCaravans.PARCEL.get()));
        s.plate(GRASS);
        for (int x = 0; x < 19; x++) s.ground(x, 5, PATH);
        BlockState sending = ChronicleCaravans.DOCK.get().defaultBlockState().setValue(DockBlock.OUTGOING, true);
        BlockState receiving = ChronicleCaravans.DOCK.get().defaultBlockState();
        s.block(3, 1, 4, milestoneBlock(), 4).block(3, 1, 3, sending, 10);
        s.block(16, 1, 4, milestoneBlock(), 8).block(16, 2, 3, receiving, 14)
                .block(16, 1, 3, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST), 14)
                .block(15, 1, 3, Blocks.BARREL.defaultBlockState(), 16);
        ItemStack parcel = new ItemStack(ChronicleCaravans.PARCEL.get());
        Vec3 dockA = new Vec3(3.5, 2.1, 3.5), dockB = new Vec3(16.5, 3.1, 3.5);
        s.item(new ItemStack(Items.COBBLESTONE, 16), 22, 36, 126, new Vec3(3.5, 4.4, 3.5), dockA);
        s.item(new ItemStack(Items.OAK_LOG, 8), 28, 42, 126, new Vec3(3.5, 4.4, 3.5), dockA);
        s.item(new ItemStack(Items.EMERALD, 2), 34, 48, 126, new Vec3(3.5, 4.4, 3.5), dockA);
        s.text(20, 116, "dock", GuideScene.at(3.5, 2.0, 3.5))
                .icons(new ItemStack(Items.ANVIL), GuideScene.ARROW, new ItemStack(ChronicleCaravans.DOCK_ITEM.get()), new ItemStack(Items.EMERALD))
                .highlight(3, 1, 3);
        Actor[] t = train(s, 5.5, Region.COAST, 1, 0, -1.0, 110, 6.2, 196, 6.2, 330, 19.4);
        s.item(parcel, 130, 146, 330, dockA, new Vec3(0.95, 2.2, 5.5));
        s.text(124, 236, "carry", GuideScene.above(t[2], 2.8)).icons(parcel, GuideScene.ARROW, milestoneItem(), GuideScene.ARROW, milestoneItem());
        s.item(parcel, 338, 352, 372, t[2].pos(330).add(0, 1.6, 0), dockB);
        s.item(new ItemStack(Items.COBBLESTONE, 16), 376, 392, 520, new Vec3(16.5, 1.5, 3.5), new Vec3(15.5, 2.1, 3.5));
        s.text(338, 436, "receive", GuideScene.at(16.5, 3.0, 3.5)).icons(new ItemStack(ChronicleCaravans.DOCK_ITEM.get()), GuideScene.ARROW, new ItemStack(Items.HOPPER))
                .highlight(new AABB(16, 1, 3, 17, 3, 4));
        s.text(440, 516, "parcel", GuideScene.above(t[0], 2.1)).icons(parcel);
        return s;
    }

    // ---------------------------------------------------------------- 11. automation: redstone and the trading post

    static GuideScene automation() {
        GuideScene s = new GuideScene("automation", 420, 13, 8, new ItemStack(Items.COMPARATOR));
        s.plate(GRASS);
        for (int x = 0; x < 13; x++) s.ground(x, 5, PATH);
        BlockState comparator = Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH);
        BlockState lamp = Blocks.REDSTONE_LAMP.defaultBlockState();
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
        s.block(6, 1, 4, milestoneBlock(), 4).block(5, 1, 4, Blocks.BARREL.defaultBlockState(), 8).block(5, 2, 4, lever, 10)
                .block(6, 1, 3, comparator, 12).block(6, 1, 2, lamp, 14);
        s.block(5, 2, 4, lever.setValue(LeverBlock.POWERED, true), 52).block(5, 2, 4, lever, 76);
        s.block(6, 1, 3, comparator.setValue(ComparatorBlock.POWERED, true), 160).block(6, 1, 2, lamp.setValue(RedstoneLampBlock.LIT, true), 160);
        s.item(new ItemStack(Items.EMERALD_BLOCK), 20, 36, 56, new Vec3(5.5, 4.2, 4.5), new Vec3(5.5, 1.6, 4.5));
        s.text(18, 128, "call", GuideScene.at(5.5, 2.4, 4.5))
                .icons(new ItemStack(Items.LEVER), new ItemStack(Items.EMERALD_BLOCK), GuideScene.ARROW, milestoneItem())
                .highlight(new AABB(5, 1, 4, 7, 3, 5));
        Actor[] t = train(s, 5.5, Region.JUNGLE, 1, 0, -2.0, 84, -2.0, 158, 8.6);
        s.text(164, 252, "comparator", GuideScene.at(6.5, 1.6, 3.0)).icons(new ItemStack(Items.COMPARATOR), new ItemStack(Items.REDSTONE_LAMP))
                .highlight(new AABB(6, 1, 2, 7, 2, 4));
        s.block(9, 1, 3, ChronicleCaravans.POST.get().defaultBlockState().setValue(TradingPostBlock.FACING, Direction.SOUTH), 18)
                .block(9, 2, 3, Blocks.HOPPER.defaultBlockState(), 22);
        s.item(new ItemStack(Items.IRON_INGOT, 8), 40, 56, 270, new Vec3(9.5, 4.4, 3.5), new Vec3(9.5, 2.6, 3.5));
        Vec3 master = t[0].pos(200).add(0, 1.3, 0);
        s.item(new ItemStack(Items.EMERALD, 4), 272, 288, 420, master, new Vec3(9.3, 1.25, 3.4));
        s.item(new ItemStack(Items.BAMBOO, 8), 280, 296, 420, master, new Vec3(9.75, 1.25, 3.6));
        s.text(258, 416, "post", GuideScene.at(9.5, 1.9, 3.5)).icons(new ItemStack(ChronicleCaravans.POST_ITEM.get()), new ItemStack(Items.HOPPER))
                .highlight(new AABB(9, 1, 3, 10, 3, 4));
        return s;
    }
}
