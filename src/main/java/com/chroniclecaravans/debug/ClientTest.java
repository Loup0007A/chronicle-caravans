package com.chroniclecaravans.debug;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.client.guide.GuideScreen;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Test client for the CI (JVM flag -Dchroniclecaravans.clienttest=true): joins the test server, watches the
 * scene staged by {@link SelfTest} and takes screenshots, then opens every scene of the guide, and quits.
 */
@EventBusSubscriber(modid = ChronicleCaravans.MODID, value = Dist.CLIENT)
public final class ClientTest {
    private static final boolean ON = Boolean.getBoolean("chroniclecaravans.clienttest");
    private static final int[] SHOTS = {200, 270, 340, 500, 600, 850, 930};
    /** Guide: {client tick, scene, scene tick to jump to}; the screenshot is taken 14 ticks later. */
    private static final int[][] GUIDE = {
            {960, 0, 100}, {1000, 1, 236}, {1040, 2, 70}, {1080, 2, 280}, {1120, 3, 120}, {1160, 3, 300}, {1200, 4, 80}};

    private static int t;
    private static int total;
    private static boolean seenMaster, seenWagon, guideShown;
    private static boolean done;

    private ClientTest() {}

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                c -> ChronicleCaravans.LOG.info("[CLIENTTEST] {}", c.getString()));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ON || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (++total > 14000) finish(mc, "FAIL timeout (never got into the world)");
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null && !(mc.screen instanceof GuideScreen)) return;
        t++;
        mc.options.hideGui = !(t > 420 && t < 700) && t < 950;
        if (t % 20 == 0 && t < 950) {
            for (Entity en : mc.level.entitiesForRendering()) {
                if (en instanceof CaravanMaster && !seenMaster) {
                    seenMaster = true;
                    ChronicleCaravans.LOG.info("[CLIENTTEST] caravan master visible at tick {}", t);
                }
                if (en instanceof CaravanWagon w && !seenWagon) {
                    seenWagon = true;
                    ChronicleCaravans.LOG.info("[CLIENTTEST] wagon visible at tick {} (region {})", t, w.region());
                }
            }
        }
        for (int i = 0; i < SHOTS.length; i++) if (t == SHOTS[i]) shot(mc, "caravan_" + i);
        for (int i = 0; i < GUIDE.length; i++) {
            int[] g = GUIDE[i];
            if (t == g[0]) {
                if (!(mc.screen instanceof GuideScreen)) mc.setScreen(new GuideScreen(null, g[1]));
                ((GuideScreen) mc.screen).jump(g[1], g[2]);
            }
            if (t == g[0] + 14 && mc.screen instanceof GuideScreen gs) {
                guideShown = true;
                ChronicleCaravans.LOG.info("[CLIENTTEST] guide scene {} ({}) at {}", g[1] + 1, gs.scene().key, g[2] + 14);
                shot(mc, "guide_" + i);
            }
        }
        if (t == GUIDE[GUIDE.length - 1][0] + 40) {
            mc.setScreen(null);
            String why = !seenMaster ? "FAIL the caravan master was never rendered"
                    : !seenWagon ? "FAIL the wagon was never rendered"
                    : !guideShown ? "FAIL the guide never opened" : "OK";
            finish(mc, why);
        }
    }

    private static void finish(Minecraft mc, String result) {
        done = true;
        ChronicleCaravans.LOG.info("[CLIENTTEST] RESULT: {}", result);
        mc.stop();
    }
}
