package com.chroniclecaravans.platform;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * Reading and writing NBT: the getters return plain values before 1.21.5 and Optionals after.
 * Reference variant (1.20.1 to 1.21.4); targets/neoforge-26.3/overrides has the other one.
 */
public final class Nbt {
    private Nbt() {}

    public static int getInt(CompoundTag t, String k) {
        return t.getInt(k);
    }

    public static long getLong(CompoundTag t, String k) {
        return t.getLong(k);
    }

    public static double getDouble(CompoundTag t, String k) {
        return t.getDouble(k);
    }

    public static String getString(CompoundTag t, String k) {
        return t.getString(k);
    }

    public static boolean getBoolean(CompoundTag t, String k) {
        return t.getBoolean(k);
    }

    public static java.util.Set<String> keys(CompoundTag t) {
        return t.getAllKeys();
    }

    public static CompoundTag getCompound(CompoundTag t, String k) {
        return t.getCompound(k);
    }

    /** A list of compounds (empty when missing). */
    public static ListTag getList(CompoundTag t, String k) {
        return t.getList(k, Tag.TAG_COMPOUND);
    }

    public static CompoundTag at(ListTag list, int i) {
        return list.getCompound(i);
    }

    public static long[] getLongArray(CompoundTag t, String k) {
        return t.getLongArray(k);
    }

    public static int[] getIntArray(CompoundTag t, String k) {
        return t.getIntArray(k);
    }

    public static boolean hasUUID(CompoundTag t, String k) {
        return t.hasUUID(k);
    }

    public static UUID getUUID(CompoundTag t, String k) {
        return t.getUUID(k);
    }

    public static void putUUID(CompoundTag t, String k, UUID id) {
        t.putUUID(k, id);
    }

    public static void putUUIDs(CompoundTag t, String k, Iterable<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(NbtUtils.createUUID(id));
        t.put(k, list);
    }

    public static void getUUIDs(CompoundTag t, String k, Collection<UUID> into) {
        into.clear();
        for (Tag tag : t.getList(k, Tag.TAG_INT_ARRAY)) into.add(NbtUtils.loadUUID(tag));
    }
}
