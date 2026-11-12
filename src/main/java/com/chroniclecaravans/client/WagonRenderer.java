package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.entity.CaravanWagon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the covered wagon: canopy in the colour of the caravan's region, rolling wheels, glowing lantern. */
public class WagonRenderer extends EntityRenderer<CaravanWagon> {
    public static final ResourceLocation TEXTURE = ChronicleCaravans.id("textures/entity/wagon.png");
    private final WagonModel model;

    public WagonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new WagonModel(context.bakeLayer(WagonModel.LAYER));
        this.shadowRadius = 1.5F;
        this.shadowStrength = 0.8F;
    }

    /** Canopy colour of each region (multiplied with the off-white canvas). */
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
    public void render(CaravanWagon wagon, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        float yaw = Mth.rotLerp(partialTick, wagon.yRotO, wagon.getYRot());
        float pitch = Mth.lerp(partialTick, wagon.xRotO, wagon.getXRot());
        renderWagon(model, wagon.region(), yaw, pitch, Mth.lerp(partialTick, wagon.rollO, wagon.roll), poseStack, buffer, light);
        super.render(wagon, entityYaw, partialTick, poseStack, buffer, light);
    }

    /** Also used by the guide screen. */
    /** The model is drawn 15% bigger than its pixels. */
    public static final float SCALE = 1.15F;

    public static void renderWagon(WagonModel model, Region region, float yaw, float pitch, float roll, PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.scale(-SCALE, -SCALE, SCALE);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.setRoll(roll / SCALE);
        VertexConsumer vc = buffer.getBuffer(model.renderType(TEXTURE));
        model.body.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        //#if mc120
        //$$ int c = tint(region);
        //$$ model.canopy.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, 1F);
        //#else
        model.canopy.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, tint(region));
        //#endif
        model.lantern.render(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CaravanWagon wagon) {
        return TEXTURE;
    }
}
