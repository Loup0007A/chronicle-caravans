package com.chroniclecaravans.command;

import java.util.List;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.Region;
import com.chroniclecaravans.Tier;
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
 * /caravans — your reputation · /caravans stops — the milestones ·
 * (operators) /caravans summon [region] · /caravans ambush · /caravans reputation &lt;player&gt; &lt;value&gt;
 */
@EventBusSubscriber(modid = ChronicleCaravans.MODID)
public final class CaravanCommands {
    private CaravanCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("caravans")
                .executes(CaravanCommands::status)
                .then(Commands.literal("stops").executes(CaravanCommands::stops))
                .then(Commands.literal("summon").requires(s -> s.hasPermission(2))
                        .executes(c -> summon(c, null))
                        .then(Commands.argument("region", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Region.values()).map(Region::key), b))
                                .executes(c -> summon(c, Region.byName(StringArgumentType.getString(c, "region"))))))
                .then(Commands.literal("ambush").requires(s -> s.hasPermission(2)).executes(CaravanCommands::ambush))
                .then(Commands.literal("reputation").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100000))
                                        .executes(CaravanCommands::setReputation)))));
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CaravanData data = CaravanData.get(p.serverLevel());
        int rep = data.reputation(p.getUUID());
        Tier tier = Tier.of(rep);
        Tier next = tier.next();
        if (!CCConfig.REPUTATION_ENABLED.get()) {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep_disabled").withStyle(ChatFormatting.GRAY));
        } else if (next == null) {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep_max", rep, tier.label()));
        } else {
            p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.rep", rep, tier.label(), next.threshold - rep, next.label()));
        }
        p.sendSystemMessage(Component.translatable("msg.chroniclecaravans.own_stops", data.stopsOf(p.getUUID()).size(),
                CaravanSpawner.caravans(p.serverLevel()).size()).withStyle(ChatFormatting.GRAY));
        return rep;
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
            BlockPos at = CaravanSpawner.findGround(level, p.blockPosition(), 4, 10, level.random);
            if (region == null) region = Region.foreignTo(Region.at(level, p.blockPosition()), level.random);
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
        CaravanData.get(target.serverLevel()).setReputation(target.getUUID(), value);
        c.getSource().sendSuccess(() -> Component.translatable("msg.chroniclecaravans.rep_set", target.getDisplayName(), value, Tier.of(value).label()), true);
        return value;
    }
}
