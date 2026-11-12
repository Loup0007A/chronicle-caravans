package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PathfinderMob;

/** The caravan's helpers (guards, road crews), 26.x variant: render states. */
public class HelperRenderer<T extends PathfinderMob> extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ChronicleCaravans.id("helper"), "main");

    private final Identifier texture;

    public HelperRenderer(EntityRendererProvider.Context ctx, String skin) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(LAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new),
                ctx.getEquipmentRenderer()));
        this.texture = ChronicleCaravans.id("textures/entity/" + skin + ".png");
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 64);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }
}
