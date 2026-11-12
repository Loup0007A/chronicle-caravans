package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.client.guide.GuideScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//#if neoforge
import net.neoforged.neoforge.client.event.ClientTickEvent;
//#else
//$$ import net.minecraftforge.event.TickEvent;
//#endif
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the Caravan Guide like Create's Ponder: hold the guide key (W) while hovering the milestone in an
 * inventory. Also {@code /caravanguide [scene]}.
 */
@EventBusSubscriber(modid = ChronicleCaravans.MODID, value = Dist.CLIENT)
public final class GuideKeys {
    public static final KeyMapping GUIDE = new KeyMapping("key.chroniclecaravans.guide", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_W, "key.categories.chroniclecaravans");
    private static final int HOLD_TICKS = 14;

    private static int clientTicks;
    private static int lastTooltip = -100;
    private static int hold;
    private static int pendingScene = -1;

    private GuideKeys() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent e) {
        if (e.getEntity() == null || !e.getItemStack().is(ChronicleCaravans.MILESTONE_ITEM.get())) return;
        lastTooltip = clientTicks;
        String text = Component.translatable("guide.chroniclecaravans.hold", GUIDE.getTranslatedKeyMessage()).getString();
        int filled = Math.round(text.length() * Math.min(1F, hold / (float) HOLD_TICKS));
        MutableComponent line = Component.literal(text.substring(0, filled)).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text.substring(filled)).withStyle(ChatFormatting.DARK_GRAY));
        e.getToolTip().add(line);
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
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (pendingScene >= 0 && !(mc.screen instanceof ChatScreen) && mc.level != null) {
            mc.setScreen(new GuideScreen(mc.screen, pendingScene));
            pendingScene = -1;
            return;
        }
        boolean shown = mc.screen != null && !(mc.screen instanceof GuideScreen) && clientTicks - lastTooltip <= 2;
        InputConstants.Key key = GUIDE.getKey();
        boolean down = shown && key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        if (down) {
            if (++hold >= HOLD_TICKS) {
                hold = 0;
                mc.setScreen(new GuideScreen(mc.screen, 0));
            }
        } else {
            hold = Math.max(0, hold - 2);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("caravanguide")
                .executes(c -> {
                    pendingScene = 0;
                    return 1;
                })
                .then(Commands.argument("scene", IntegerArgumentType.integer(1, 6)).executes(c -> {
                    pendingScene = IntegerArgumentType.getInteger(c, "scene") - 1;
                    return 1;
                })));
    }
}
