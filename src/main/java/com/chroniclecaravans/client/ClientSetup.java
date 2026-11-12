package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client side: the caravan master is drawn like a wandering trader. */
@EventBusSubscriber(modid = ChronicleCaravans.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ChronicleCaravans.CARAVAN_MASTER.get(), WanderingTraderRenderer::new);
    }
}
