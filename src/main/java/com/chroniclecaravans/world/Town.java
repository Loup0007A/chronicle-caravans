package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chroniclecaravans.platform.Nbt;
import com.chroniclecaravans.world.Buildings.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Rotation;

/**
 * A merchant town growing around a milestone: its prosperity (trade brings it), its level, its treasury (the
 * caravans' dues) and its buildings, planned, waiting for materials, being built or done.
 */
public final class Town {
    public static final int WAITING = 0, BUILDING = 1, DONE = 2;

    public final BlockPos pos;
    /** 0: not founded yet (prosperity is counted all the same). */
    public int level;
    public int prosperity;
    public int treasury;
    /** The caravans bring the missing materials, paid from the treasury. */
    public boolean supply = true;
    public Buildings.Palette palette = Buildings.Palette.OAK;
    public UUID mayor;
    public long founded;
    /** Villagers who came to live in the town's houses. */
    public int villagers;
    public final List<Building> buildings = new ArrayList<>();

    // Not saved
    float budget;
    long nextPlanTry;
    long mayorSeen;

    public Town(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public static final class Building {
        public final Buildings.Type type;
        public final int variant;
        /** North-west corner of the footprint, at the level one stands on; null until a plot is found. */
        public BlockPos origin;
        public Rotation rot = Rotation.NONE;
        public int state = WAITING;
        public final int[] got = new int[Material.values().length];
        public int progress;
        /** The barrel where materials are left for this building. */
        public BlockPos crate;
        public UUID builder;
        // Not saved: the steps of the work, worked out again when needed
        List<Buildings.Op> ops;

        public Building(Buildings.Type type, int variant) {
            this.type = type;
            this.variant = variant;
        }

        public boolean planned() {
            return origin != null;
        }

        public int missing(Material m) {
            return Math.max(0, type.need(m) - got[m.ordinal()]);
        }

        public boolean supplied() {
            for (Material m : Material.values()) if (missing(m) > 0) return false;
            return true;
        }

        /** How far along it is, in percent. */
        public int percent() {
            if (state == DONE) return 100;
            if (state == WAITING) {
                int need = 0, have = 0;
                for (Material m : Material.values()) {
                    need += type.need(m);
                    have += Math.min(got[m.ordinal()], type.need(m));
                }
                return need == 0 ? 100 : have * 100 / need;
            }
            return ops == null || ops.isEmpty() ? 0 : Math.min(99, progress * 100 / ops.size());
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Type", type.name());
            t.putInt("Variant", variant);
            if (origin != null) t.putLong("Origin", origin.asLong());
            t.putInt("Rot", rot.ordinal());
            t.putInt("State", state);
            t.putIntArray("Got", got);
            t.putInt("Progress", progress);
            if (crate != null) t.putLong("Crate", crate.asLong());
            if (builder != null) Nbt.putUUID(t, "Builder", builder);
            return t;
        }

        static Building load(CompoundTag t) {
            Buildings.Type type;
            try {
                type = Buildings.Type.valueOf(Nbt.getString(t, "Type"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            Building b = new Building(type, Nbt.getInt(t, "Variant"));
            if (t.contains("Origin")) b.origin = BlockPos.of(Nbt.getLong(t, "Origin"));
            b.rot = Rotation.values()[Math.max(0, Math.min(3, Nbt.getInt(t, "Rot")))];
            b.state = Nbt.getInt(t, "State");
            int[] got = Nbt.getIntArray(t, "Got");
            for (int i = 0; i < Math.min(got.length, b.got.length); i++) b.got[i] = got[i];
            b.progress = Nbt.getInt(t, "Progress");
            if (t.contains("Crate")) b.crate = BlockPos.of(Nbt.getLong(t, "Crate"));
            b.builder = Nbt.hasUUID(t, "Builder") ? Nbt.getUUID(t, "Builder") : null;
            return b;
        }
    }

    /** The building waiting for materials or being built, the earliest first (null: nothing under way). */
    public Building site() {
        for (Building b : buildings) if (b.state == BUILDING) return b;
        for (Building b : buildings) if (b.state == WAITING) return b;
        return null;
    }

    public int count(Buildings.Type type, boolean doneOnly) {
        int n = 0;
        for (Building b : buildings) if (b.type == type && (!doneOnly || b.state == DONE)) n++;
        return n;
    }

    public boolean allDone() {
        for (Building b : buildings) if (b.state != DONE) return false;
        return true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Pos", pos.asLong());
        t.putInt("Level", level);
        t.putInt("Prosperity", prosperity);
        t.putInt("Treasury", treasury);
        t.putBoolean("Supply", supply);
        t.putString("Palette", palette.name());
        if (mayor != null) Nbt.putUUID(t, "Mayor", mayor);
        t.putLong("Founded", founded);
        t.putInt("Villagers", villagers);
        ListTag list = new ListTag();
        for (Building b : buildings) list.add(b.save());
        t.put("Buildings", list);
        return t;
    }

    public static Town load(CompoundTag t) {
        Town town = new Town(BlockPos.of(Nbt.getLong(t, "Pos")));
        town.level = Nbt.getInt(t, "Level");
        town.prosperity = Nbt.getInt(t, "Prosperity");
        town.treasury = Nbt.getInt(t, "Treasury");
        town.supply = !t.contains("Supply") || Nbt.getBoolean(t, "Supply");
        try {
            town.palette = Buildings.Palette.valueOf(Nbt.getString(t, "Palette"));
        } catch (IllegalArgumentException e) {
            town.palette = Buildings.Palette.OAK;
        }
        town.mayor = Nbt.hasUUID(t, "Mayor") ? Nbt.getUUID(t, "Mayor") : null;
        town.founded = Nbt.getLong(t, "Founded");
        town.villagers = Nbt.getInt(t, "Villagers");
        ListTag list = Nbt.getList(t, "Buildings");
        for (int i = 0; i < list.size(); i++) {
            Building b = Building.load(Nbt.at(list, i));
            if (b != null) town.buildings.add(b);
        }
        return town;
    }
}
