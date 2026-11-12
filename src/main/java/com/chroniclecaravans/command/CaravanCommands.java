package com.chroniclecaravans.command;

import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Guild;
import com.chroniclecaravans.item.AtlasItem;
import com.chroniclecaravans.menu.AtlasMenu;
import com.chroniclecaravans.platform.Compat;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.world.CaravanData;
import com.chroniclecaravans.world.CaravanSpawner;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /caravans — your standing · /caravans stops — the stops · /caravans atlas (operators) ·
 * /caravans rename stop|route … &lt;name&gt; (used by the atlas) ·
 * (operators) /caravans summon [region] · /caravans ambush · /caravans reputation &lt;player&gt; &lt;xp&gt;
 */
@EventBusSubscriber(modid = ChronicleCaravans.MODID)
public final class CaravanCommands {
    private CaravanCommands() {}

    //#if mc26
    //$$ private static final java.util.function.Predicate<CommandSourceStack> OPS = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    //#else
    private static final java.util.function.Predicate<CommandSourceStack> OPS = s -> s.hasPermission(2);
    //#endif

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("caravans")
                .executes(CaravanCommands::status)
                .then(Commands.literal("stops").executes(CaravanCommands::stops))
                .then(Commands.literal("summon").requires(OPS)
                        .executes(c -> summon(c, null))
                        .then(Commands.argument("region", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Region.values()).map(Region::key), b))
                                .executes(c -> summon(c, Region.byName(StringArgumentType.getString(c, "region"))))))
                .then(Commands.literal("ambush").requires(OPS).executes(CaravanCommands::ambush))
                .then(Commands.literal("atlas").requires(OPS).executes(c -> {
                    AtlasItem.open(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("dock")
                        .then(Commands.argument("address", StringArgumentType.greedyString()).executes(c -> dockAddress(c, StringArgumentType.getString(c, "address")))))
                .then(Commands.literal("rename")
                        .then(Commands.literal("stop").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> renameStop(c, StringArgumentType.getString(c, "name"))))
                                .then(Commands.literal("-").executes(c -> renameStop(c, "")))))
                        .then(Commands.literal("route").then(Commands.argument("a", BlockPosArgument.blockPos()).then(Commands.argument("b", BlockPosArgument.blockPos())
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> renameRoute(c, StringArgumentType.getString(c, "name"))))
                                .then(Commands.literal("-").executes(c -> renameRoute(c, "")))))))
                .then(Commands.literal("reputation").requires(OPS)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(-200, 100000))
                                        .executes(CaravanCommands::setReputation)))));
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CaravanData data = CaravanData.get(p.serverLevel());
        int xp = data.xp(p.getUUID());
        int skills = data.skills(p.getUUID());
        if (!CCConfig.REPUTATION_ENABLED.get()) {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep_disabled").withStyle(ChatFormatting.GRAY));
        } else {
            int lvl = Guild.levelOf(xp);
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep", Guild.describe(xp, skills), Guild.progress(xp), Guild.needed(lvl)));
            int points = Guild.points(xp, skills);
            if (points > 0) p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.points", points).withStyle(ChatFormatting.GOLD));
        }
        p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.own_stops", data.stopsOf(p.getUUID()).size(),
                data.caravans().size()).withStyle(ChatFormatting.GRAY));
        return xp;
    }

    private static boolean canRename(ServerPlayer p, java.util.UUID owner, boolean village) {
        return village || Compat.isOp(p) || p.getUUID().equals(owner);
    }

    private static String clean(String name) {
        name = name.strip();
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    private static void refreshAtlas(ServerPlayer p) {
        if (p.containerMenu instanceof AtlasMenu m) m.refresh(p);
    }

    private static int renameStop(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        CaravanData data = CaravanData.get(p.serverLevel());
        CaravanData.Stop s = data.stop(pos);
        CaravanData.Inn inn = data.inn(pos);
        if (s == null && inn == null) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.no_stop"));
            return 0;
        }
        if (s != null ? !canRename(p, s.owner, s.village) : !canRename(p, inn.owner, false)) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.not_yours"));
            return 0;
        }
        if (s != null) s.name = clean(name);
        else inn.name = clean(name);
        data.setDirty();
        Component shown = s != null ? s.displayName() : inn.displayName();
        c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.renamed", shown).withStyle(ChatFormatting.GREEN), false);
        refreshAtlas(p);
        return 1;
    }

    /** Sets the address of the loading dock the player has open ("-" makes it a receiving dock). */
    private static int dockAddress(CommandContext<CommandSourceStack> c, String address) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!(p.containerMenu instanceof com.chroniclecaravans.menu.DockMenu menu) || menu.dock() == null) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.dock_not_open"));
            return 0;
        }
        com.chroniclecaravans.block.DockBlockEntity dock = menu.dock();
        if (dock.owner() != null && !dock.owner().equals(p.getUUID()) && !Compat.isOp(p)) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.not_yours"));
            return 0;
        }
        String a = address.strip();
        dock.setAddress(a.equals("-") ? "" : clean(a));
        menu.refreshNow();
        c.getSource().sendSuccess(() -> Component.translatable(dock.outgoing() ? "msg.chroniclecaravans.dock_address" : "msg.chroniclecaravans.dock_cleared",
                dock.address()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int renameRoute(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CaravanData data = CaravanData.get(p.serverLevel());
        CaravanData.Stop a = data.stop(BlockPosArgument.getBlockPos(c, "a")), b = data.stop(BlockPosArgument.getBlockPos(c, "b"));
        if (a == null || b == null) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.no_stop"));
            return 0;
        }
        boolean may = Compat.isOp(p) || p.getUUID().equals(a.owner) || p.getUUID().equals(b.owner) || (a.village && b.village);
        if (!may) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.not_yours"));
            return 0;
        }
        String n = clean(name);
        data.routeOrCreate(a.pos, b.pos).name = n;
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.route_renamed", a.displayName(), b.displayName(),
                n.isEmpty() ? "-" : n).withStyle(ChatFormatting.GREEN), false);
        refreshAtlas(p);
        return 1;
    }

    private static int stops(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        CaravanData data = CaravanData.get(level);
        if (data.stops().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.no_stops").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (CaravanData.Stop s : data.stops()) {
            if (s.village && !CCConfig.VILLAGE_STOPS.get()) continue;
            c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.stop_line", s.displayName(),
                    s.pos.getX(), s.pos.getY(), s.pos.getZ()), false);
        }
        return data.stops().size();
    }

    private static int summon(CommandContext<CommandSourceStack> c, Region region) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        CaravanData data = CaravanData.get(level);
        CaravanData.Stop stop = data.nearestStop(p.blockPosition(), CCConfig.LINK_RANGE.get(), null);
        CaravanMaster master;
        if (stop != null) {
            master = CaravanSpawner.spawnCaravan(level, stop, region);
        } else {
            BlockPos at = CaravanSpawner.findGround(level, p.blockPosition(), 4, 10, level.getRandom());
            if (region == null) region = Region.foreignTo(Region.at(level, p.blockPosition()), level.getRandom());
            master = at == null ? null : CaravanSpawner.spawnCaravanAt(level, at, null, region);
        }
        if (master == null) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.no_room"));
            return 0;
        }
        final CaravanMaster m = master;
        c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.summoned", m.region().caravanName().withStyle(m.region().color),
                m.blockPosition().getX(), m.blockPosition().getY(), m.blockPosition().getZ()), true);
        return 1;
    }

    private static int ambush(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<CaravanMaster> list = CaravanSpawner.caravans(p.serverLevel());
        CaravanMaster best = null;
        for (CaravanMaster m : list) if (best == null || m.distanceToSqr(p) < best.distanceToSqr(p)) best = m;
        int n = best == null ? 0 : best.startAmbush(p.serverLevel());
        if (n == 0) {
            c.getSource().sendFailure(Component.translatable("msg.chroniclecaravans.no_caravan"));
            return 0;
        }
        return n;
    }

    private static int setReputation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        int value = IntegerArgumentType.getInteger(c, "value");
        CaravanData data = CaravanData.get(target.serverLevel());
        data.setXp(target.getUUID(), value);
        c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.rep_set", target.getDisplayName(), value,
                Guild.describe(value, data.skills(target.getUUID()))), true);
        return value;
    }
}
