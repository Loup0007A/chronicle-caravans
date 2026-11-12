package com.chroniclecaravans.client.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One scene of the guide: a small diorama that plays out over time. Everything is a function of the scene
 * time, so the timeline can be replayed or jumped through freely.
 */
public final class GuideScene {
    /** Marks an arrow between two icons of a text bubble. */
    public static final ItemStack ARROW = ItemStack.EMPTY;

    public final String key;
    public final int length;
    public final int sizeX, sizeZ;
    public final ItemStack icon;
    final Map<BlockPos, List<BlockCue>> blocks = new HashMap<>();
    final List<TextCue> texts = new ArrayList<>();
    final List<Actor> actors = new ArrayList<>();
    final List<ItemCue> items = new ArrayList<>();

    public GuideScene(String key, int length, int sizeX, int sizeZ, ItemStack icon) {
        this.key = key;
        this.length = length;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.icon = icon;
    }

    public Component title() {
        return Component.translatable("guide.chroniclecaravans." + key + ".title");
    }

    // ---------------------------------------------------------------- blocks

    record BlockCue(BlockState state, int tick) {}

    /** A block that drops into place at {@code tick} (replacing whatever was there). */
    public GuideScene block(int x, int y, int z, BlockState state, int tick) {
        List<BlockCue> list = blocks.computeIfAbsent(new BlockPos(x, y, z), p -> new ArrayList<>());
        list.add(new BlockCue(state, tick));
        list.sort((a, b) -> Integer.compare(a.tick, b.tick));
        return this;
    }

    /** The ground: one layer of blocks, appearing in a wave from one corner. */
    public GuideScene plate(BlockState state) {
        for (int x = 0; x < sizeX; x++) for (int z = 0; z < sizeZ; z++) block(x, 0, z, state, (x + z) / 2);
        return this;
    }

    /** The cue visible at {@code t}, or null. */
    static BlockCue blockAt(List<BlockCue> list, float t) {
        BlockCue cur = null;
        for (BlockCue c : list) if (c.tick <= t) cur = c;
        return cur;
    }

    // ---------------------------------------------------------------- texts

    public interface Anchor {
        Vec3 at(float t);
    }

    public static final class TextCue {
        final int start, end;
        final Component text;
        final Anchor anchor;
        List<ItemStack> icons = List.of();
        AABB highlight;

        TextCue(int start, int end, Component text, Anchor anchor) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.anchor = anchor;
        }

        public TextCue icons(ItemStack... stacks) {
            this.icons = Arrays.asList(stacks);
            return this;
        }

        public TextCue highlight(int x, int y, int z) {
            this.highlight = new AABB(x, y, z, x + 1, y + 1, z + 1);
            return this;
        }

        public TextCue highlight(AABB box) {
            this.highlight = box;
            return this;
        }
    }

    /** A text bubble pointing at {@code anchor}, from tick {@code start} to {@code end}. */
    public TextCue text(int start, int end, String id, Anchor anchor) {
        return text(start, end, Component.translatable("guide.chroniclecaravans." + key + "." + id), anchor);
    }

    public TextCue text(int start, int end, Component text, Anchor anchor) {
        TextCue c = new TextCue(start, end, text, anchor);
        texts.add(c);
        return c;
    }

    public static Anchor at(double x, double y, double z) {
        Vec3 v = new Vec3(x, y, z);
        return t -> v;
    }

    // ---------------------------------------------------------------- floating items

    record ItemCue(ItemStack stack, int start, int land, int end, Vec3 from, Vec3 to) {}

    /** An item that flies from {@code from} to {@code to} (landing at {@code land}) and stays until {@code end}. */
    public GuideScene item(ItemStack stack, int start, int land, int end, Vec3 from, Vec3 to) {
        items.add(new ItemCue(stack, start, land, end, from, to));
        return this;
    }

    // ---------------------------------------------------------------- actors

    /** An entity moving through keyframes {tick, x, y, z}; between two keys it walks in a straight line. */
    public static final class Actor {
        final Function<Level, Entity> factory;
        final List<double[]> keys = new ArrayList<>();
        Entity entity;
        int appear = 0, vanish = Integer.MAX_VALUE, dieAt = -1, hurtAt = -1;
        float restYaw = Float.NaN;
        Actor leashedTo;
        BiConsumer<Entity, Float> each;

        Actor(Function<Level, Entity> factory) {
            this.factory = factory;
        }

        public Actor key(int tick, double x, double y, double z) {
            keys.add(new double[]{tick, x, y, z});
            return this;
        }

        public Actor appear(int tick) {
            this.appear = tick;
            return this;
        }

        public Actor vanish(int tick) {
            this.vanish = tick;
            return this;
        }

        /** Falls over at {@code tick} (with the red flash of a hit just before), then disappears. */
        public Actor dies(int tick) {
            this.dieAt = tick;
            this.hurtAt = tick - 4;
            this.vanish = tick + 22;
            return this;
        }

        public Actor facing(float yaw) {
            this.restYaw = yaw;
            return this;
        }

        public Actor leash(Actor holder) {
            this.leashedTo = holder;
            return this;
        }

        /** Extra per-frame work (e.g. the wagon cycling through region colours). */
        public Actor each(BiConsumer<Entity, Float> action) {
            this.each = action;
            return this;
        }

        public Vec3 pos(float t) {
            double[] first = keys.get(0);
            if (t <= first[0] || keys.size() == 1) return new Vec3(first[1], first[2], first[3]);
            for (int i = 1; i < keys.size(); i++) {
                double[] a = keys.get(i - 1), b = keys.get(i);
                if (t <= b[0]) {
                    double k = (t - a[0]) / Math.max(1e-6, b[0] - a[0]);
                    return new Vec3(Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]), Mth.lerp(k, a[3], b[3]));
                }
            }
            double[] last = keys.get(keys.size() - 1);
            return new Vec3(last[1], last[2], last[3]);
        }

        /** Faces the direction it walks; when standing, the direction of its last move (or {@link #facing}). */
        public float yaw(float t) {
            Vec3 a = pos(t - 0.5F), b = pos(t + 0.5F);
            if (a.distanceToSqr(b) < 1e-6) {
                if (!Float.isNaN(restYaw)) return restYaw;
                for (int i = keys.size() - 1; i > 0; i--) {
                    double[] p = keys.get(i - 1), q = keys.get(i);
                    if (q[0] <= t && (p[1] != q[1] || p[3] != q[3])) return (float) (Mth.atan2(q[3] - p[3], q[1] - p[1]) * Mth.RAD_TO_DEG) - 90F;
                }
                for (int i = 1; i < keys.size(); i++) {
                    double[] p = keys.get(i - 1), q = keys.get(i);
                    if (p[1] != q[1] || p[3] != q[3]) return (float) (Mth.atan2(q[3] - p[3], q[1] - p[1]) * Mth.RAD_TO_DEG) - 90F;
                }
                return 0F;
            }
            return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90F;
        }

        /** Distance walked so far (turns the wagon wheels). */
        public float travelled(float t) {
            double d = 0;
            for (int i = 1; i < keys.size(); i++) {
                double[] a = keys.get(i - 1), b = keys.get(i);
                double seg = Math.sqrt((b[1] - a[1]) * (b[1] - a[1]) + (b[3] - a[3]) * (b[3] - a[3]));
                if (t >= b[0]) d += seg;
                else if (t > a[0]) d += seg * (t - a[0]) / Math.max(1e-6, b[0] - a[0]);
            }
            return (float) d;
        }

        public boolean visible(float t) {
            return t >= appear && t < vanish;
        }
    }

    public Actor actor(Function<Level, Entity> factory) {
        Actor a = new Actor(factory);
        actors.add(a);
        return a;
    }

    /** Anchor that follows an actor, {@code dy} blocks above its feet. */
    public static Anchor above(Actor actor, double dy) {
        return t -> actor.pos(t).add(0, dy, 0);
    }
}
