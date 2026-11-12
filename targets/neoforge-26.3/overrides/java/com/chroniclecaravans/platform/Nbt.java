package com.chroniclecaravans.platform;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** NBT access, 1.21.5+ variant: getters return Optionals, UUIDs go through codecs. */
public final class Nbt {
    private Nbt() {}

    public static int getInt(CompoundTag t, String k) {
        return t.getIntOr(k, 0);
    }

    public static long getLong(CompoundTag t, String k) {
        return t.getLongOr(k, 0L);
    }

    public static double getDouble(CompoundTag t, String k) {
        return t.getDoubleOr(k, 0.0);
    }

    public static String getString(CompoundTag t, String k) {
        return t.getStringOr(k, "");
    }

    public static boolean getBoolean(CompoundTag t, String k) {
        return t.getBooleanOr(k, false);
    }

    public static java.util.Set<String> keys(CompoundTag t) {
        return t.keySet();
    }

    public static CompoundTag getCompound(CompoundTag t, String k) {
        return t.getCompoundOrEmpty(k);
    }

    public static ListTag getList(CompoundTag t, String k) {
        return t.getListOrEmpty(k);
    }

    public static CompoundTag at(ListTag list, int i) {
        return list.getCompoundOrEmpty(i);
    }

    public static long[] getLongArray(CompoundTag t, String k) {
        return t.getLongArray(k).orElse(new long[0]);
    }

    public static int[] getIntArray(CompoundTag t, String k) {
        return t.getIntArray(k).orElse(new int[0]);
    }

    public static boolean hasUUID(CompoundTag t, String k) {
        return t.read(k, UUIDUtil.CODEC).isPresent();
    }

    public static UUID getUUID(CompoundTag t, String k) {
        return t.read(k, UUIDUtil.CODEC).orElse(null);
    }

    public static void putUUID(CompoundTag t, String k, UUID id) {
        t.store(k, UUIDUtil.CODEC, id);
    }

    public static void putUUIDs(CompoundTag t, String k, Iterable<UUID> ids) {
        List<UUID> list = new java.util.ArrayList<>();
        ids.forEach(list::add);
        t.store(k, UUIDUtil.CODEC.listOf(), list);
    }

    public static void getUUIDs(CompoundTag t, String k, Collection<UUID> into) {
        into.clear();
        t.read(k, UUIDUtil.CODEC.listOf()).ifPresent(into::addAll);
    }
}
