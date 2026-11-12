package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;

/** The caravan's helpers (guards, road crews): a humanoid with its own skin, its armour and its tool. */
public class HelperRenderer<T extends PathfinderMob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ChronicleCaravans.id("helper"), "main");

    private final ResourceLocation texture;

    public HelperRenderer(EntityRendererProvider.Context ctx, String skin) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(LAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        this.texture = ChronicleCaravans.id("textures/entity/" + skin + ".png");
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 64);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
