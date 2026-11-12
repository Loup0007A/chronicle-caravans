package com.chroniclecaravans.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Goods entrusted to the caravans: carried from stop to stop until they reach their destination.
 * The items are kept as NBT (written once, with the registries, when the parcel is made), so a parcel can be
 * stored anywhere — world data, a caravan, an item — without needing the registries again.
 */
public final class Parcel {
    public UUID id = UUID.randomUUID();
    public UUID sender;
    public String senderName = "";
    public BlockPos origin = BlockPos.ZERO, dest = BlockPos.ZERO;
    public long created;
    /** From a loading dock: unpacked into the docks at the destination, rather than delivered as a parcel. */
    public boolean bulk;
    public int stacks;
    public ListTag items = new ListTag();

    public static Parcel of(Level level, List<ItemStack> stacks, BlockPos origin, BlockPos dest, UUID sender, String senderName, boolean bulk) {
        Parcel p = new Parcel();
        p.origin = origin.immutable();
        p.dest = dest.immutable();
        p.sender = sender;
        p.senderName = senderName == null ? "" : senderName;
        p.created = level.getGameTime();
        p.bulk = bulk;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            p.items.add(Compat.saveStack(level, s));
            p.stacks++;
        }
        return p;
    }

    public List<ItemStack> unpack(Level level) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = Compat.loadStack(level, Nbt.at(items, i));
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        Nbt.putUUID(t, "Id", id);
        if (sender != null) Nbt.putUUID(t, "Sender", sender);
        t.putString("SenderName", senderName);
        t.putLong("Origin", origin.asLong());
        t.putLong("Dest", dest.asLong());
        t.putLong("Created", created);
        t.putBoolean("Bulk", bulk);
        t.putInt("Stacks", stacks);
        t.put("Items", items.copy());
        return t;
    }

    public static Parcel load(CompoundTag t) {
        Parcel p = new Parcel();
        if (Nbt.hasUUID(t, "Id")) p.id = Nbt.getUUID(t, "Id");
        if (Nbt.hasUUID(t, "Sender")) p.sender = Nbt.getUUID(t, "Sender");
        p.senderName = Nbt.getString(t, "SenderName");
        p.origin = BlockPos.of(Nbt.getLong(t, "Origin"));
        p.dest = BlockPos.of(Nbt.getLong(t, "Dest"));
        p.created = Nbt.getLong(t, "Created");
        p.bulk = Nbt.getBoolean(t, "Bulk");
        p.stacks = Nbt.getInt(t, "Stacks");
        p.items = Nbt.getList(t, "Items").copy();
        return p;
    }

    public static ListTag saveAll(List<Parcel> parcels) {
        ListTag list = new ListTag();
        for (Parcel p : parcels) list.add(p.save());
        return list;
    }

    public static void loadAll(ListTag list, List<Parcel> into) {
        into.clear();
        for (int i = 0; i < list.size(); i++) into.add(load(Nbt.at(list, i)));
    }
}
