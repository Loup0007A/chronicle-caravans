package com.chroniclecaravans.client.guide;

import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Tier;
import com.chroniclecaravans.client.guide.GuideScene.Actor;
import com.chroniclecaravans.entity.CaravanWagon;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The five scenes of the Caravan Guide. */
public final class GuideScenes {
    private GuideScenes() {}

    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();

    public static List<GuideScene> all() {
        return List.of(milestone(), route(), caravan(), ambush(), hire());
    }

    private static BlockState milestoneBlock() {
        return ChronicleCaravans.MILESTONE.get().defaultBlockState();
    }

    private static ItemStack milestoneItem() {
        return new ItemStack(ChronicleCaravans.MILESTONE_ITEM.get());
    }

    // ---------------------------------------------------------------- entities

    static Entity master(Level level) {
        return ChronicleCaravans.CARAVAN_MASTER.get().create(level);
    }

    static Entity llama(Level level, boolean chest, Llama.Variant variant) {
        TraderLlama llama = EntityType.TRADER_LLAMA.create(level);
        if (llama != null) {
            llama.setChest(chest);
            llama.setVariant(variant);
        }
        return llama;
    }

    static Entity wagon(Level level, Region region) {
        CaravanWagon wagon = ChronicleCaravans.WAGON.get().create(level);
        if (wagon != null) wagon.setRegion(region);
        return wagon;
    }

    static Entity pillager(Level level) {
        Pillager p = EntityType.PILLAGER.create(level);
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

    // ---------------------------------------------------------------- 4. ambushes and reputation

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
        s.item(new ItemStack(Items.EMERALD), 168, 182, 390, from, new Vec3(11.6, 1.15, 3.1));
        s.item(new ItemStack(Items.EMERALD), 171, 185, 390, from, new Vec3(12.3, 1.15, 2.6));
        s.item(new ItemStack(Items.BLUE_ICE), 175, 190, 390, from, new Vec3(11.0, 1.15, 2.4));
        s.text(172, 258, "reward", GuideScene.at(11.6, 1.5, 2.8)).icons(new ItemStack(Items.EMERALD, 5), new ItemStack(Items.BLUE_ICE, 2));
        MutableComponent ranks = Component.translatable("guide.chroniclecaravans.ambush.reputation").append("\n");
        Tier[] tiers = Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            ranks.append(tiers[i].label());
            if (i < tiers.length - 1) ranks.append(Component.literal(" › ").withStyle(ChatFormatting.DARK_GRAY));
        }
        s.text(264, 386, ranks, GuideScene.above(master, 2.1));
        return s;
    }

    // ---------------------------------------------------------------- 5. hiring a caravan

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
}
