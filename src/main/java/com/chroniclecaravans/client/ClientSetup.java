package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client side: renderers, the wagon model, the guide key. */
@EventBusSubscriber(modid = ChronicleCaravans.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ChronicleCaravans.CARAVAN_MASTER.get(), WanderingTraderRenderer::new);
        e.registerEntityRenderer(ChronicleCaravans.WAGON.get(), WagonRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(WagonModel.LAYER, WagonModel::createLayer);
    }

    //#if mc26
    //$$ @SubscribeEvent
    //$$ public static void pictureInPicture(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent e) {
    //$$     com.chroniclecaravans.client.guide.GuideRender.register(e);
    //$$ }
    //#endif

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        //#if mc26
        //$$ e.registerCategory(GuideKeys.CATEGORY);
        //#endif
        e.register(GuideKeys.GUIDE);
    }
}
