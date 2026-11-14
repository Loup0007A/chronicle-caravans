package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.entity.CaravanWagon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Draws the covered wagon (NeoForge 26.x: render state + submitted models). */
public class WagonRenderer extends EntityRenderer<CaravanWagon, WagonRenderer.State> {
    public static final Identifier TEXTURE = ChronicleCaravans.id("textures/entity/wagon.png");
    /** The model is drawn 15% bigger than its pixels. */
    public static final float SCALE = 1.15F;

    public static class State extends EntityRenderState {
        public Region region = Region.STEPPE;
        public float yaw, pitch, roll;
        public int parcels;
    }

    /** The three parts are drawn separately: plain body, tinted canopy, full-bright lantern. */
    private final Model<State> body, canopy, lantern;

    public WagonRenderer(EntityRendererProvider.Context context) {
        super(context);
        WagonModel model = new WagonModel(context.bakeLayer(WagonModel.LAYER));
        this.body = new Model<>(model.body, RenderTypes::entityCutout) {
            @Override
            public void setupAnim(State s) {
                super.setupAnim(s);
                model.setRoll(s.roll / SCALE);
                model.showParcels(s.parcels);
            }
        };
        this.canopy = new Model<>(model.canopy, RenderTypes::entityCutout) {};
        this.lantern = new Model<>(model.lantern, RenderTypes::entityCutout) {};
        this.shadowRadius = 1.5F;
        this.shadowStrength = 0.8F;
    }

    public static int tint(Region region) {
        return switch (region) {
            case DUNES -> 0xFFEBB466;
            case FROST -> 0xFFA6CFEE;
            case JUNGLE -> 0xFF78BC5E;
            case HIGHLANDS -> 0xFFAFA2C4;
            case COAST -> 0xFF5E92DC;
            case MARSH -> 0xFF97A35A;
            case WOODS -> 0xFF4F9A80;
            case STEPPE -> 0xFFDC6C4E;
        };
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CaravanWagon wagon, State s, float partialTick) {
        super.extractRenderState(wagon, s, partialTick);
        s.region = wagon.region();
        s.yaw = Mth.rotLerp(partialTick, wagon.yRotO, wagon.getYRot());
        s.pitch = Mth.lerp(partialTick, wagon.xRotO, wagon.getXRot());
        s.roll = Mth.lerp(partialTick, wagon.rollO, wagon.roll);
        s.parcels = wagon.parcels();
    }

    @Override
    protected AABB getBoundingBoxForCulling(CaravanWagon wagon, float partialTick) {
        return super.getBoundingBoxForCulling(wagon, partialTick).inflate(2.0, 0.5, 2.0);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - s.yaw);
        poseStack.rotateDegrees(Axis.XP, -s.pitch);
        poseStack.scale(-SCALE, -SCALE, SCALE);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        collector.submitModel(body, s, poseStack, type, s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
        collector.submitModel(canopy, s, poseStack, type, s.lightCoords, OverlayTexture.NO_OVERLAY, tint(s.region), null, s.outlineColor);
        collector.submitModel(lantern, s, poseStack, type, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
