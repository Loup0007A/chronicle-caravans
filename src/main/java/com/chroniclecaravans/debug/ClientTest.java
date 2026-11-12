package com.chroniclecaravans.debug;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.client.guide.GuideScreen;
import com.chroniclecaravans.client.screen.AtlasScreen;
import com.chroniclecaravans.client.screen.CaravanScreen;
import com.chroniclecaravans.menu.AtlasMenu;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//#if neoforge
import net.neoforged.neoforge.client.event.ClientTickEvent;
//#else
//$$ import net.minecraftforge.event.TickEvent;
//#endif

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
            {960, 0, 100}, {1000, 1, 236}, {1040, 2, 70}, {1080, 2, 280}, {1120, 3, 60}, {1160, 3, 200}, {1200, 4, 120}, {1240, 4, 300}, {1280, 5, 80},
            {1320, 6, 150}, {1360, 6, 270}, {1400, 7, 120}, {1440, 7, 300}};

    private static int t;
    private static int total;
    private static boolean seenMaster, seenWagon, guideShown;
    private static boolean seenGuard, caravanShown, atlasShown;
    private static int caravanTicks, atlasTicks;
    private static String uiProblem;
    private static boolean done;

    private ClientTest() {}

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                c -> ChronicleCaravans.LOG.info("[CLIENTTEST] {}", c.getString()));
    }

    //#if neoforge
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        tick();
    }
    //#elif mc120
    //$$ @SubscribeEvent
    //$$ public static void tick(TickEvent.ClientTickEvent e) {
    //$$     if (e.phase == TickEvent.Phase.END) tick();
    //$$ }
    //#else
    //$$ @SubscribeEvent
    //$$ public static void tick(TickEvent.ClientTickEvent.Post e) {
    //$$     tick();
    //$$ }
    //#endif

    private static void tick() {
        if (!ON || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (++total > 14000) finish(mc, "FAIL timeout (never got into the world)");
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null && !(mc.screen instanceof GuideScreen) && !(mc.screen instanceof CaravanScreen) && !(mc.screen instanceof AtlasScreen)) return;
        t++;
        hideHud(mc, !(t > 420 && t < 700) && t < 950);
        if (t % 20 == 0 && t < 950) {
            for (Entity en : mc.level.entitiesForRendering()) {
                if (en instanceof CaravanMaster && !seenMaster) {
                    seenMaster = true;
                    ChronicleCaravans.LOG.info("[CLIENTTEST] caravan master visible at tick {}", t);
                }
                if (en instanceof com.chroniclecaravans.entity.CaravanGuard && !seenGuard) {
                    seenGuard = true;
                    ChronicleCaravans.LOG.info("[CLIENTTEST] caravan guard visible at tick {}", t);
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
        int last = GUIDE[GUIDE.length - 1][0];
        if (t == last + 30 && mc.screen instanceof GuideScreen) mc.setScreen(null);
        // The server opens the caravan's interface, then the atlas: one screenshot per tab
        if (mc.screen instanceof CaravanScreen cs) {
            caravanTicks++;
            int[] tabs = {20, 40, 60, 80};
            for (int i = 0; i < tabs.length; i++) {
                if (caravanTicks == tabs[i] - 12) cs.setTab(i);
                if (caravanTicks == tabs[i]) {
                    shot(mc, "interface_" + i);
                    caravanShown = true;
                }
            }
            if (caravanTicks == 10) {
                int offers = cs.getMenu().offerCount();
                ChronicleCaravans.LOG.info("[CLIENTTEST] caravan interface open: {} offers, level {}", offers, cs.getMenu().get(com.chroniclecaravans.menu.CaravanMenu.D_LEVEL));
                if (offers < 10) uiProblem = "FAIL the caravan interface shows only " + offers + " offers";
            }
            if (caravanTicks == 90) cs.onClose();
        }
        if (mc.screen instanceof AtlasScreen as) {
            atlasTicks++;
            if (atlasTicks == 12) {
                int stops = as.getMenu().count(AtlasMenu.D_STOPS);
                ChronicleCaravans.LOG.info("[CLIENTTEST] atlas open: {} places, {} routes", stops, as.getMenu().count(AtlasMenu.D_ROUTES));
                if (stops < 1) uiProblem = "FAIL the atlas shows no place";
            }
            if (atlasTicks == 20) shot(mc, "atlas_0");
            if (atlasTicks == 24) as.select(0);
            if (atlasTicks == 34) {
                shot(mc, "atlas_1");
                atlasShown = true;
            }
            if (atlasTicks == 44) as.onClose();
        }
        if ((atlasShown && mc.screen == null) || t > last + 1100) {
            String why = !seenMaster ? "FAIL the caravan master was never rendered"
                    : !seenWagon ? "FAIL the wagon was never rendered"
                    : !guideShown ? "FAIL the guide never opened"
                    : !caravanShown ? "FAIL the caravan interface never opened"
                    : !atlasShown ? "FAIL the atlas never opened"
                    : uiProblem != null ? uiProblem : "OK";
            finish(mc, why);
        }
    }

    private static void hideHud(Minecraft mc, boolean hidden) {
        //#if mc26
        //$$ if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle();
        //#else
        mc.options.hideGui = hidden;
        //#endif
    }

    private static void finish(Minecraft mc, String result) {
        done = true;
        ChronicleCaravans.LOG.info("[CLIENTTEST] RESULT: {}", result);
        mc.stop();
    }
}
