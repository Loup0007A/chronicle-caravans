package com.chroniclecaravans.debug;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.entity.CaravanMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Test client for the CI (JVM flag -Dchroniclecaravans.clienttest=true): joins the test server, watches the
 * scene staged by {@link SelfTest}, takes screenshots and quits.
 */
@EventBusSubscriber(modid = ChronicleCaravans.MODID, value = Dist.CLIENT)
public final class ClientTest {
    private static final boolean ON = Boolean.getBoolean("chroniclecaravans.clienttest");
    private static final int[] SHOTS = {200, 270, 340, 500, 600, 850, 930};

    private static int t;
    private static int total;
    private static boolean seen;
    private static boolean done;

    private ClientTest() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ON || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (++total > 12000) finish(mc, "FAIL timeout (never got into the world)");
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        t++;
        mc.options.hideGui = !(t > 420 && t < 700);
        if (t % 20 == 0 && !seen) {
            for (Entity en : mc.level.entitiesForRendering()) {
                if (en instanceof CaravanMaster) {
                    seen = true;
                    ChronicleCaravans.LOG.info("[CLIENTTEST] caravan master visible at tick {}", t);
                }
            }
        }
        for (int i = 0; i < SHOTS.length; i++) {
            if (t == SHOTS[i]) {
                Screenshot.grab(mc.gameDirectory, "caravan_" + i + ".png", mc.getMainRenderTarget(),
                        c -> ChronicleCaravans.LOG.info("[CLIENTTEST] {}", c.getString()));
            }
        }
        if (t == SHOTS[SHOTS.length - 1] + 40) finish(mc, seen ? "OK" : "FAIL the caravan master was never rendered");
    }

    private static void finish(Minecraft mc, String result) {
        done = true;
        ChronicleCaravans.LOG.info("[CLIENTTEST] RESULT: {}", result);
        mc.stop();
    }
}
