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
        e.registerEntityRenderer(ChronicleCaravans.GUARD.get(), ctx -> new HelperRenderer<>(ctx, "caravan_guard"));
        e.registerEntityRenderer(ChronicleCaravans.BUILDER.get(), ctx -> new HelperRenderer<>(ctx, "road_builder"));
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(WagonModel.LAYER, WagonModel::createLayer);
        e.registerLayerDefinition(HelperRenderer.LAYER, HelperRenderer::createLayer);
    }

    //#if neoforge
    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) {
        e.register(ChronicleCaravans.CARAVAN_MENU.get(), com.chroniclecaravans.client.screen.CaravanScreen::new);
        e.register(ChronicleCaravans.ATLAS_MENU.get(), com.chroniclecaravans.client.screen.AtlasScreen::new);
        e.register(ChronicleCaravans.DOCK_MENU.get(), com.chroniclecaravans.client.screen.DockScreen::new);
        e.register(ChronicleCaravans.POST_MENU.get(), com.chroniclecaravans.client.screen.TradingPostScreen::new);
        e.register(ChronicleCaravans.PARCEL_MENU.get(), com.chroniclecaravans.client.screen.ParcelScreen::new);
    }
    //#else
    //$$ @SubscribeEvent
    //$$ public static void screens(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
    //$$     e.enqueueWork(() -> {
    //$$         net.minecraft.client.gui.screens.MenuScreens.register(ChronicleCaravans.CARAVAN_MENU.get(), com.chroniclecaravans.client.screen.CaravanScreen::new);
    //$$         net.minecraft.client.gui.screens.MenuScreens.register(ChronicleCaravans.ATLAS_MENU.get(), com.chroniclecaravans.client.screen.AtlasScreen::new);
    //$$         net.minecraft.client.gui.screens.MenuScreens.register(ChronicleCaravans.DOCK_MENU.get(), com.chroniclecaravans.client.screen.DockScreen::new);
    //$$         net.minecraft.client.gui.screens.MenuScreens.register(ChronicleCaravans.POST_MENU.get(), com.chroniclecaravans.client.screen.TradingPostScreen::new);
    //$$         net.minecraft.client.gui.screens.MenuScreens.register(ChronicleCaravans.PARCEL_MENU.get(), com.chroniclecaravans.client.screen.ParcelScreen::new);
    //$$     });
    //$$ }
    //#endif

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
