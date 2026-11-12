package com.chroniclecaravans.client.guide;

import java.util.List;
import java.util.Map;

import com.chroniclecaravans.client.guide.GuideScene.Actor;
import com.chroniclecaravans.client.guide.GuideScene.BlockCue;
import com.chroniclecaravans.client.guide.GuideScene.ItemCue;
import com.chroniclecaravans.client.guide.GuideScene.TextCue;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the 3D scene of the guide (blocks dropping in, outlines, flying items, entities).
 * Reference variant: straight into the GUI with a PoseStack. NeoForge 26.x has its own (picture-in-picture).
 */
final class GuideRender {
    private GuideRender() {}

    /** Draws the scene in the screen, through the screen's PoseStack. */
    static void draw(GuideScreen screen, GuiGraphics g, GuideScene s, float t, float partialTick) {
        Minecraft minecraft = screen.minecraft();
        PoseStack ps = g.pose();
        ps.pushPose();
        float scale = screen.sceneScale(s);
        ps.translate(screen.width / 2F, screen.height * 0.52F, 400F);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(screen.pitch));
        ps.mulPose(Axis.YP.rotationDegrees(screen.yaw));
        ps.translate(-s.sizeX / 2F, -1F, -s.sizeZ / 2F);

        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        int light = LightTexture.FULL_BRIGHT;

        // Blocks drop into place
        for (Map.Entry<BlockPos, List<BlockCue>> en : s.blocks.entrySet()) {
            BlockCue c = GuideScene.blockAt(en.getValue(), t);
            if (c == null || c.state().isAir()) continue;
            float age = t - c.tick();
            boolean replacing = en.getValue().get(0) != c;
            float drop = age < 6 ? (1F - GuideScreen.ease(age / 6F)) * (replacing ? 0.7F : 1.6F) : 0F;
            BlockPos p = en.getKey();
            ps.pushPose();
            ps.translate(p.getX(), p.getY() + drop, p.getZ());
            minecraft.getBlockRenderer().renderSingleBlock(c.state(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
            BlockEntity be = screen.blockEntity(p, c.state());
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
            float k = GuideScreen.ease((t - c.start()) / Math.max(1F, c.land() - c.start()));
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
                Entity e = screen.entity(a);
                if (e == null) continue;
                screen.pose(a, e, t);
                if (a.each != null) a.each.accept(e, t);
                float pop = GuideScreen.ease((t - a.appear) / 5F);
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
    }

}
