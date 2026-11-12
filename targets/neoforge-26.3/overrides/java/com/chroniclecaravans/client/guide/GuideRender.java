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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Draws the 3D scene of the guide, NeoForge 26.x variant: the GUI cannot draw 3D directly any more, so the
 * scene is a "picture in picture", rendered into a texture by {@link Renderer} and blitted over the screen.
 */
public final class GuideRender {
    private GuideRender() {}

    private static final BlockDisplayContext BLOCKS = BlockDisplayContext.create();

    public static void register(RegisterPictureInPictureRenderersEvent e) {
        e.register(State.class, Renderer::new);
    }

    /** What the picture needs: the screen (camera, entities...), the scene and the time. */
    record State(GuideScreen screen, GuideScene scene, float t, float partialTick, int x0, int y0, int x1, int y1,
                 ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return 1F;
        }
    }

    static void draw(GuideScreen screen, GuiGraphicsExtractor g, GuideScene s, float t, float partialTick) {
        ScreenRectangle area = new ScreenRectangle(0, 0, screen.width, screen.height);
        g.submitPictureInPictureRenderState(new State(screen, s, t, partialTick, 0, 0, screen.width, screen.height, null, area));
    }

    static final class Renderer extends PictureInPictureRenderer<State> {
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected String getTextureLabel() {
            return "chroniclecaravans_guide";
        }

        /** The origin of the scene, like {@link GuideScreen#view}: centred, at 52% of the height. */
        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height * 0.52F;
        }

        @Override
        protected void renderToTexture(State st, PoseStack ps, SubmitNodeCollector out) {
            drawScene(st.screen(), st.scene(), st.t(), st.partialTick(), ps, out);
        }
    }

    private static void drawScene(GuideScreen screen, GuideScene s, float t, float partialTick, PoseStack ps, SubmitNodeCollector out) {
        Minecraft minecraft = screen.minecraft();
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        int light = LightCoordsUtil.FULL_BRIGHT;
        float scale = screen.sceneScale(s);
        ps.pushPose();
        // y points down as in the GUI, but depth goes the other way: flip z too
        ps.scale(scale, -scale, -scale);
        ps.rotateDegrees(Axis.XP, screen.pitch);
        ps.rotateDegrees(Axis.YP, screen.yaw);
        ps.translate(-s.sizeX / 2F, -1F, -s.sizeZ / 2F);
        CameraRenderState camera = new CameraRenderState();

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
            BlockModelRenderState block = new BlockModelRenderState();
            minecraft.getBlockModelResolver().update(block, c.state(), BLOCKS);
            block.submit(ps, out, light, OverlayTexture.NO_OVERLAY, 0);
            BlockEntity be = screen.blockEntity(p, c.state());
            if (be != null) {
                try {
                    BlockEntityRenderState bes = minecraft.getBlockEntityRenderDispatcher().tryExtractRenderState(be, partialTick, null, false);
                    if (bes != null) {
                        bes.lightCoords = light;
                        minecraft.getBlockEntityRenderDispatcher().submit(bes, ps, out, camera);
                    }
                } catch (RuntimeException ignored) {
                    // a block entity outside a world: draw the block alone
                }
            }
            ps.popPose();
        }

        // Pulsing outline around what a bubble talks about
        for (TextCue c : s.texts) {
            if (c.highlight == null || t < c.start || t > c.end) continue;
            int a = (int) (255 * (0.55F + 0.35F * Mth.sin(t * 0.25F)));
            ps.pushPose();
            ps.translate(c.highlight.minX, c.highlight.minY, c.highlight.minZ);
            out.submitShapeOutline(ps, Shapes.box(-0.02, -0.02, -0.02, c.highlight.getXsize() + 0.02, c.highlight.getYsize() + 0.02, c.highlight.getZsize() + 0.02),
                    RenderTypes.lines(), (a << 24) | 0xF2B84C, 2.5F, false);
            ps.popPose();
        }

        // Flying items (rewards, the emerald block)
        for (ItemCue c : s.items) {
            if (t < c.start() || t > c.end()) continue;
            float k = GuideScreen.ease((t - c.start()) / Math.max(1F, c.land() - c.start()));
            Vec3 p = c.from().lerp(c.to(), k).add(0, Mth.sin(k * Mth.PI) * 0.8, 0);
            ps.pushPose();
            ps.translate(p.x, p.y + (k >= 1 ? Mth.sin(t * 0.15F) * 0.05 : 0), p.z);
            ps.rotateDegrees(Axis.YP, t * 6F);
            ps.scale(1.3F, 1.3F, 1.3F);
            ItemStackRenderState item = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(item, c.stack(), ItemDisplayContext.GROUND, minecraft.level, null, 0);
            item.submit(ps, out, light, OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }

        // Entities
        if (minecraft.level != null) {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            for (Actor a : s.actors) {
                if (!a.visible(t)) continue;
                Entity e = screen.entity(a);
                if (e == null) continue;
                screen.pose(a, e, t);
                if (a.each != null) a.each.accept(e, t);
                EntityRenderState state = dispatcher.extractEntity(e, partialTick);
                state.lightCoords = light;
                state.shadowRadius = 0F;
                state.shadowPieces.clear();
                float pop = GuideScreen.ease((t - a.appear) / 5F);
                Vec3 p = a.pos(t);
                ps.pushPose();
                ps.translate(p.x, p.y, p.z);
                ps.scale(pop, pop, pop);
                dispatcher.submit(state, camera, 0, 0, 0, ps, out);
                ps.popPose();
            }
        }
        ps.popPose();
    }
}
