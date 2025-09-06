package com.cavetale.windicator;

import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@RequiredArgsConstructor
public final class WindicatorCommand implements TabExecutor {
    final WindicatorPlugin plugin;

    static final class Wrong extends Exception {
        Wrong(final String msg) {
            super(msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) return false;
        try {
            return onCommand(sender, args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (Wrong w) {
            sender.sendMessage(text(w.getMessage(), RED));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 0) return null;
        String cmd = args[0];
        String arg = args[args.length - 1];
        if (args.length == 1) {
            return complete(cmd, Stream.of("info",
                                           "start", "stop",
                                           "victory",
                                           "addcore", "removecore", "clearcores",
                                           "clearscores", "rewardscores"));
        }
        if (args.length == 2) {
            switch (args[0]) {
            case "addcore": case "removecore": {
                final String lower = args[1].toLowerCase();
                List<String> result = new ArrayList<>();
                for (CoreType coreType : CoreType.values()) {
                    final String name = coreType.name().toLowerCase();
                    if (name.contains(lower)) result.add(name);
                }
                return result;
            }
            default: break;
            }
        }
        return List.of();
    }

    public boolean onCommand(CommandSender sender, String cmd, String[] args) throws Wrong {
        switch (cmd) {
        case "info": {
            if (args.length != 0) return false;
            sender.sendMessage("isValid=" + plugin.getWindicator().isValid());
            sender.sendMessage("state=" + Json.serialize(plugin.getWindicator().getState()));
            return true;
        }
        case "start": {
            plugin.getWindicator().setEnabled(true);
            plugin.getWindicator().save();
            if (plugin.getWindicator().isValid()) {
                sender.sendMessage("Game started");
            } else {
                sender.sendMessage("Game started but still not valid.");
            }
            return true;
        }
        case "stop": {
            plugin.getWindicator().setEnabled(false);
            plugin.getWindicator().save();
            sender.sendMessage("Game stopped");
            return true;
        }
        case "victory": {
            plugin.getWindicator().setVictory(!plugin.getWindicator().isVictory());
            plugin.getWindicator().save();
            sender.sendMessage("Victory set to: " + plugin.getWindicator().isVictory());
            return true;
        }
        case "addcore": {
            if (args.length != 1) return false;
            Player player = playerOf(sender);
            String name = args[0];
            final CoreType coreType;
            try {
                coreType = CoreType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new Wrong("Unknown core type: " + name);
            }
            Block block = player.getTargetBlockExact(5);
            if (block == null) throw new Wrong("Not looking at block");
            if (!plugin.getWindicator().addCore(block, coreType)) {
                throw new Wrong("Core block already contained.");
            }
            plugin.getWindicator().save();
            sender.sendMessage("Core block added: " + coreType + ": " + Vec3i.of(block));
            return true;
        }
        case "removecore": {
            if (args.length != 1) return false;
            Player player = playerOf(sender);
            String name = args[0];
            final CoreType coreType;
            try {
                coreType = CoreType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new Wrong("Unknown core type: " + name);
            }
            Block block = player.getTargetBlockExact(5);
            if (block == null) throw new Wrong("Not looking at block");
            boolean res = plugin.getWindicator().removeCore(block, coreType, true);
            if (res) {
                plugin.getWindicator().save();
                sender.sendMessage("Core block removed: " + coreType + ": " + Vec3i.of(block));
            } else {
                throw new Wrong("Not a core block: " + Vec3i.of(block));
            }
            return true;
        }
        case "clearcores": {
            if (args.length != 0) return false;
            plugin.getWindicator().clearCores();
            plugin.getWindicator().save();
            sender.sendMessage("Cores cleared.");
            return true;
        }
        case "clearscores": {
            plugin.getWindicator().getState().getScores().clear();
            plugin.getWindicator().computeHighscore();
            sender.sendMessage(text("Scores cleared", YELLOW));
            return true;
        }
        case "rewardscores": {
            final int count = Highscore.reward(plugin.getWindicator().getState().getScores(),
                                               "windicator",
                                               TrophyCategory.SWORD,
                                               text("Windicator", GOLD, BOLD),
                                               hi -> "You collected " + hi.score + " point" + (hi.score == 1 ? "" : "s"));
            List<Highscore> highscore = Highscore.of(plugin.getWindicator().getState().getScores());
            List<Component> highscoreLines = Highscore.sidebar(highscore, TrophyCategory.SWORD);
            for (Component line : highscoreLines) {
                sender.sendMessage(line);
            }
            sender.sendMessage(text(count + " players rewarded", YELLOW));
            return true;
        }
        default:
            return false;
        }
    }

    List<String> complete(String arg, Stream<String> args) {
        return args.filter(a -> a.startsWith(arg))
            .collect(Collectors.toList());
    }

    Player playerOf(CommandSender sender) throws Wrong {
        if (!(sender instanceof Player)) {
            throw new Wrong("Player expected");
        }
        return (Player) sender;
    }
}
