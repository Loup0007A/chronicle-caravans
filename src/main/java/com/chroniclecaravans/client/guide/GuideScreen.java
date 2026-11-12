package com.chroniclecaravans.client.guide;

import java.util.List;
import java.util.Map;

import com.chroniclecaravans.client.guide.GuideScene.Actor;
import com.chroniclecaravans.client.guide.GuideScene.BlockCue;
import com.chroniclecaravans.client.guide.GuideScene.ItemCue;
import com.chroniclecaravans.client.guide.GuideScene.TextCue;
import com.chroniclecaravans.entity.CaravanWagon;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The Caravan Guide: animated 3D scenes with text bubbles, in the spirit of Create's "Ponder".
 * Drag to turn the scene, scroll to zoom, click the timeline to jump, arrows to change scene.
 */
public class GuideScreen extends Screen {
    private static final int GOLD = 0xFFE0A84A, BROWN = 0xFF6E4A22, BG = 0xF0141012;

    private final Screen previous;
    private final List<GuideScene> scenes = GuideScenes.all();
    private int index;
    private int time;
    private boolean paused;
    private float yaw = -32F, pitch = 28F, zoom = 1F;
    private boolean dragging;

    public GuideScreen(Screen previous, int scene) {
        super(Component.translatable("guide.chroniclecaravans.title"));
        this.previous = previous;
        this.index = Mth.clamp(scene, 0, scenes.size() - 1);
    }

    public GuideScene scene() {
        return scenes.get(index);
    }

    public int sceneCount() {
        return scenes.size();
    }

    /** Shows {@code scene} from {@code tick} (also used by the test client). */
    public void jump(int scene, int tick) {
        if (scene != index) {
            for (Actor a : scene().actors) a.entity = null;
            blockEntities.clear();
            index = Mth.clamp(scene, 0, scenes.size() - 1);
        }
        time = Mth.clamp(tick, 0, scene().length);
        paused = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }

    @Override
    public void tick() {
        GuideScene s = scene();
        if (!paused && time < s.length) time++;
        if (minecraft.level == null) return;
        for (Actor a : s.actors) {
            if (!a.visible(time)) continue;
            Entity e = entity(a);
            if (e == null) continue;
            e.tickCount++;
            if (e instanceof LivingEntity living) {
                Vec3 now = a.pos(time), before = a.pos(time - 1);
                double moved = Math.sqrt((now.x - before.x) * (now.x - before.x) + (now.z - before.z) * (now.z - before.z));
                living.walkAnimation.update(Math.min((float) moved * 4F, 1F), 0.4F);
            }
        }
    }

    private Entity entity(Actor a) {
        if (a.entity == null && minecraft.level != null) a.entity = a.factory.apply(minecraft.level);
        return a.entity;
    }

    // ================================================================ rendering

    //#if mc120
    //$$ @Override
    //$$ public void renderBackground(GuiGraphics g) {
    //$$     g.fillGradient(0, 0, width, height, 0xF2181C24, 0xF80A0C10);
    //$$ }
    //#else
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xF2181C24, 0xF80A0C10);
    }
    //#endif

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //#if mc120
        //$$ renderBackground(g);
        //#else
        renderBackground(g, mouseX, mouseY, partialTick);
        //#endif
        GuideScene s = scene();
        float t = Math.min(s.length, time + (paused || time >= s.length ? 0F : partialTick));
        Matrix4f view = renderScene(g, s, t, partialTick);
        g.pose().pushPose();
        g.pose().translate(0, 0, 800);
        for (TextCue c : s.texts) renderBubble(g, c, t, view);
        renderInterface(g, s, t, mouseX, mouseY);
        g.pose().popPose();
    }

    private float sceneScale(GuideScene s) {
        double span = Math.sqrt(s.sizeX * s.sizeX + s.sizeZ * s.sizeZ) + 2;
        return (float) Mth.clamp(Math.min(width * 0.8, height * 1.25) / span, 6, 56) * zoom;
    }

    private static float ease(float p) {
        p = Mth.clamp(p, 0F, 1F);
        return 1F - (1F - p) * (1F - p) * (1F - p);
    }

    private Matrix4f renderScene(GuiGraphics g, GuideScene s, float t, float partialTick) {
        PoseStack ps = g.pose();
        ps.pushPose();
        float scale = sceneScale(s);
        ps.translate(width / 2F, height * 0.52F, 400F);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-s.sizeX / 2F, -1F, -s.sizeZ / 2F);
        Matrix4f view = new Matrix4f(ps.last().pose());

        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        int light = LightTexture.FULL_BRIGHT;

        // Blocks drop into place
        for (Map.Entry<BlockPos, List<BlockCue>> en : s.blocks.entrySet()) {
            BlockCue c = GuideScene.blockAt(en.getValue(), t);
            if (c == null || c.state().isAir()) continue;
            float age = t - c.tick();
            boolean replacing = en.getValue().get(0) != c;
            float drop = age < 6 ? (1F - ease(age / 6F)) * (replacing ? 0.7F : 1.6F) : 0F;
            BlockPos p = en.getKey();
            ps.pushPose();
            ps.translate(p.getX(), p.getY() + drop, p.getZ());
            minecraft.getBlockRenderer().renderSingleBlock(c.state(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
            BlockEntity be = blockEntity(p, c.state());
            if (be != null) minecraft.getBlockEntityRenderDispatcher().renderItem(be, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }

        // Pulsing outline around what a bubble talks about
        for (TextCue c : s.texts) {
            if (c.highlight == null || t < c.start || t > c.end) continue;
            float a = 0.55F + 0.35F * Mth.sin(t * 0.25F);
            LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), c.highlight.inflate(0.02), 0.95F, 0.72F, 0.3F, a);
        }

        // Flying items (rewards, the emerald block)
        for (ItemCue c : s.items) {
            if (t < c.start() || t > c.end()) continue;
            float k = ease((t - c.start()) / Math.max(1F, c.land() - c.start()));
            Vec3 p = c.from().lerp(c.to(), k).add(0, Mth.sin(k * Mth.PI) * 0.8, 0);
            ps.pushPose();
            ps.translate(p.x, p.y + (k >= 1 ? Mth.sin(t * 0.15F) * 0.05 : 0), p.z);
            ps.mulPose(Axis.YP.rotationDegrees(t * 6F));
            ps.scale(1.3F, 1.3F, 1.3F);
            minecraft.getItemRenderer().renderStatic(c.stack(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buffers, minecraft.level, 0);
            ps.popPose();
        }

        // Entities
        if (minecraft.level != null) {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            for (Actor a : s.actors) {
                if (!a.visible(t)) continue;
                Entity e = entity(a);
                if (e == null) continue;
                pose(a, e, t);
                if (a.each != null) a.each.accept(e, t);
                float pop = ease((t - a.appear) / 5F);
                Vec3 p = a.pos(t);
                ps.pushPose();
                ps.translate(p.x, p.y, p.z);
                ps.scale(pop, pop, pop);
                dispatcher.render(e, 0, 0, 0, a.yaw(t), partialTick, ps, buffers, light);
                ps.popPose();
            }
            dispatcher.setRenderShadow(true);
        }
        g.flush();
        ps.popPose();
        Lighting.setupFor3DItems();
        return view;
    }

    private final Map<BlockPos, BlockEntity> blockEntities = new java.util.HashMap<>();

    /** Bells, chests...: the part drawn by a block entity renderer. */
    private BlockEntity blockEntity(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock eb)) return null;
        BlockEntity be = blockEntities.get(pos);
        if (be == null || be.getBlockState() != state) {
            be = eb.newBlockEntity(pos, state);
            if (be != null && minecraft.level != null) be.setLevel(minecraft.level);
            blockEntities.put(pos, be);
        }
        return be;
    }

    /** Puts an actor's entity where the scene wants it at time {@code t}. */
    private void pose(Actor a, Entity e, float t) {
        Vec3 p = a.pos(t);
        float yaw = a.yaw(t);
        e.setPos(p.x, p.y, p.z);
        e.xo = e.xOld = p.x;
        e.yo = e.yOld = p.y;
        e.zo = e.zOld = p.z;
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.setXRot(0F);
        e.xRotO = 0F;
        if (e instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = yaw;
            living.deathTime = a.dieAt >= 0 && t >= a.dieAt ? Math.min(20, (int) (t - a.dieAt) + 1) : 0;
            living.hurtTime = a.hurtAt >= 0 && t >= a.hurtAt && t < a.hurtAt + 10 ? 10 - (int) (t - a.hurtAt) : 0;
        }
        if (e instanceof CaravanWagon wagon) {
            wagon.roll = wagon.rollO = a.travelled(t);
        }
        if (a.leashedTo != null && e instanceof Mob mob) {
            Entity holder = a.leashedTo.entity;
            if (holder != null && a.leashedTo.visible(t) && mob.getLeashHolder() != holder) mob.setLeashedTo(holder, false);
        }
    }

    // ---------------------------------------------------------------- text bubbles

    private static int alpha(int argb, float a) {
        int al = Mth.clamp((int) (((argb >>> 24) & 0xFF) * a), 8, 255);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private void renderBubble(GuiGraphics g, TextCue c, float t, Matrix4f view) {
        if (t < c.start || t > c.end) return;
        float a = Math.min(Mth.clamp((t - c.start) / 5F, 0F, 1F), c.end >= scene().length ? 1F : Mth.clamp((c.end - t) / 5F, 0F, 1F));
        if (a <= 0.02F) return;
        Vec3 anchor = c.anchor.at(t);
        Vector4f v = view.transform(new Vector4f((float) anchor.x, (float) anchor.y, (float) anchor.z, 1F));
        int sx = (int) v.x, sy = (int) v.y;

        int maxW = Mth.clamp(width / 3, 120, 210);
        List<FormattedCharSequence> lines = font.split(c.text, maxW);
        int tw = 0;
        for (FormattedCharSequence l : lines) tw = Math.max(tw, font.width(l));
        int iconRow = c.icons.isEmpty() ? 0 : 20;
        int bw = Math.max(tw, c.icons.size() * 18) + 12;
        int bh = lines.size() * 10 + 9 + iconRow;
        int bx = Mth.clamp(sx + 26, 8, width - bw - 8);
        int by = Mth.clamp(sy - bh - 30, 44, Math.max(44, height - 72 - bh));

        // Connector: a dot on the anchor, then a line up (or down) to the bubble, then along it
        int lineColor = alpha(GOLD, a * 0.85F);
        g.fill(sx - 1, sy - 1, sx + 2, sy + 2, lineColor);
        int midY = Mth.clamp(sy, by, by + bh);
        if (sx >= bx && sx <= bx + bw) {
            g.fill(sx, Math.min(sy, by + bh), sx + 1, Math.max(sy, by + bh), lineColor);
        } else {
            int edge = sx < bx ? bx : bx + bw;
            g.fill(sx, Math.min(sy, midY), sx + 1, Math.max(sy, midY) + 1, lineColor);
            g.fill(Math.min(sx, edge), midY, Math.max(sx, edge), midY + 1, lineColor);
        }

        g.fill(bx, by, bx + bw, by + bh, alpha(BG, a));
        g.fillGradient(bx, by, bx + 1, by + bh, alpha(GOLD, a), alpha(BROWN, a));
        g.fillGradient(bx + bw - 1, by, bx + bw, by + bh, alpha(GOLD, a), alpha(BROWN, a));
        g.fill(bx, by, bx + bw, by + 1, alpha(GOLD, a));
        g.fill(bx, by + bh - 1, bx + bw, by + bh, alpha(BROWN, a));

        int y = by + 5;
        if (iconRow > 0) {
            int x = bx + 6;
            for (ItemStack stack : c.icons) {
                if (stack.isEmpty()) {
                    g.drawString(font, "→", x + 5, y + 4, alpha(0xFFE8D8B0, a), false);
                } else if (a > 0.6F) {
                    g.renderItem(stack, x, y);
                    g.renderItemDecorations(font, stack, x, y);
                }
                x += 18;
            }
            y += iconRow;
        }
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, bx + 6, y, alpha(0xFFF2EAD8, a), false);
            y += 10;
        }
    }

    // ---------------------------------------------------------------- interface

    private int buttonsX() {
        return width / 2 - (4 * 20 + 3 * 6) / 2;
    }

    private int buttonsY() {
        return height - 30;
    }

    private int buttonAt(double mx, double my) {
        int x0 = buttonsX(), y0 = buttonsY();
        if (my < y0 || my > y0 + 20) return -1;
        for (int i = 0; i < 4; i++) if (mx >= x0 + i * 26 && mx <= x0 + i * 26 + 20) return i;
        return -1;
    }

    private void renderInterface(GuiGraphics g, GuideScene s, float t, int mx, int my) {
        // Header: icon, guide name, scene title, scene number
        g.fill(12, 10, 38, 36, BG);
        g.renderOutline(12, 10, 26, 26, GOLD);
        g.renderItem(s.icon, 17, 15);
        g.drawString(font, Component.translatable("guide.chroniclecaravans.title"), 44, 12, 0xFFB09A78, false);
        g.drawString(font, s.title(), 44, 24, 0xFFFFFFFF, true);
        String n = (index + 1) + " / " + scenes.size();
        g.drawString(font, n, width - 12 - font.width(n), 12, 0xFFB09A78, false);

        // Timeline with a mark at each text
        int x0 = 20, x1 = width - 20, y = height - 42;
        g.fill(x0, y, x1, y + 2, 0x50FFFFFF);
        int px = x0 + (int) ((x1 - x0) * (t / s.length));
        g.fill(x0, y, px, y + 2, GOLD);
        for (TextCue c : s.texts) {
            int kx = x0 + (int) ((x1 - x0) * (c.start / (float) s.length));
            g.fill(kx, y - 2, kx + 1, y + 4, c.start <= t ? GOLD : 0xA0FFFFFF);
        }
        g.fill(px - 1, y - 3, px + 2, y + 5, 0xFFFFFFFF);

        // Buttons: previous, replay, pause/play, next
        int bx = buttonsX(), by = buttonsY();
        boolean ended = time >= s.length;
        for (int i = 0; i < 4; i++) {
            int x = bx + i * 26;
            boolean hover = buttonAt(mx, my) == i;
            boolean off = (i == 0 && index == 0) || (i == 3 && index == scenes.size() - 1);
            boolean pulse = i == 3 && ended && !off && (time / 8) % 2 == 0;
            g.fill(x, by, x + 20, by + 20, hover && !off ? 0xF0302418 : BG);
            g.renderOutline(x, by, 20, 20, pulse || hover ? GOLD : off ? 0x40FFFFFF : BROWN);
            int c = off ? 0x60FFFFFF : 0xFFF2EAD8;
            switch (i) {
                case 0 -> triangle(g, x + 13, by + 10, -1, c);
                case 1 -> replayIcon(g, x, by, c);
                case 2 -> {
                    if (paused || ended) triangle(g, x + 8, by + 10, 1, c);
                    else {
                        g.fill(x + 7, by + 6, x + 9, by + 14, c);
                        g.fill(x + 11, by + 6, x + 13, by + 14, c);
                    }
                }
                default -> triangle(g, x + 8, by + 10, 1, c);
            }
        }
        g.drawString(font, Component.translatable("guide.chroniclecaravans.controls"), 20, height - 58, 0xFF7C7468, false);
    }

    /** A square loop with an arrow head: "play again". */
    private static void replayIcon(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 6, y + 5, x + 11, y + 6, c);
        g.fill(x + 5, y + 6, x + 6, y + 14, c);
        g.fill(x + 6, y + 14, x + 14, y + 15, c);
        g.fill(x + 14, y + 9, x + 15, y + 14, c);
        g.fill(x + 11, y + 3, x + 12, y + 8, c);
        g.fill(x + 12, y + 4, x + 13, y + 7, c);
        g.fill(x + 13, y + 5, x + 14, y + 6, c);
    }

    /** A small filled triangle pointing left (-1) or right (1), its flat side at {@code x}. */
    private static void triangle(GuiGraphics g, int x, int cy, int dir, int color) {
        for (int i = 0; i < 5; i++) {
            int half = 4 - i;
            int cx = x + dir * i;
            g.fill(cx, cy - half - 1, cx + 1, cy + half, color);
        }
    }

    // ---------------------------------------------------------------- input

    private void previousScene() {
        if (index > 0) jump(index - 1, 0);
    }

    private void nextScene() {
        if (index < scenes.size() - 1) jump(index + 1, 0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int b = buttonAt(mx, my);
        if (b >= 0) {
            switch (b) {
                case 0 -> previousScene();
                case 1 -> jump(index, 0);
                case 2 -> {
                    if (time >= scene().length) jump(index, 0);
                    else paused = !paused;
                }
                default -> nextScene();
            }
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        int y = height - 42;
        if (my >= y - 5 && my <= y + 7 && mx >= 20 && mx <= width - 20) {
            time = Mth.clamp((int) ((mx - 20) / (width - 40) * scene().length), 0, scene().length);
            return true;
        }
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 0.8F;
            pitch = Mth.clamp(pitch + (float) dy * 0.6F, 5F, 85F);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    //#if mc120
    //$$ @Override
    //$$ public boolean mouseScrolled(double mx, double my, double scrollY) {
    //#else
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
    //#endif
        zoom = Mth.clamp(zoom * (1F + (float) scrollY * 0.1F), 0.4F, 3F);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case 263 -> previousScene();      // left arrow
            case 262 -> nextScene();          // right arrow
            case 32 -> paused = !paused;      // space
            case 82 -> jump(index, 0);        // R
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }
}
