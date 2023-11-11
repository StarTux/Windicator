package com.cavetale.windicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

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
            sender.sendMessage(Component.text(w.getMessage(), NamedTextColor.RED));
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
                                           "addcore", "removecore", "clearcores"));
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

    boolean onCommand(CommandSender sender, String cmd, String[] args) throws Wrong {
        switch (cmd) {
        case "info": {
            if (args.length != 0) return false;
            sender.sendMessage("isValid=" + plugin.windicator.isValid());
            sender.sendMessage("state=" + plugin.json.toJson(plugin.windicator.getState()));
            return true;
        }
        case "start": {
            plugin.windicator.setEnabled(true);
            plugin.windicator.save();
            if (plugin.windicator.isValid()) {
                sender.sendMessage("Game started");
            } else {
                sender.sendMessage("Game started but still not valid.");
            }
            return true;
        }
        case "stop": {
            plugin.windicator.setEnabled(false);
            plugin.windicator.save();
            sender.sendMessage("Game stopped");
            return true;
        }
        case "victory": {
            plugin.windicator.setVictory(!plugin.windicator.isVictory());
            plugin.windicator.save();
            sender.sendMessage("Victory set to: " + plugin.windicator.isVictory());
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
            if (!plugin.windicator.addCore(block, coreType)) {
                throw new Wrong("Core block already contained.");
            }
            plugin.windicator.save();
            sender.sendMessage("Core block added: " + coreType + ": " + Vec3.of(block));
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
            boolean res = plugin.windicator.removeCore(block, coreType);
            if (res) {
                plugin.windicator.save();
                sender.sendMessage("Core block removed: " + coreType + ": " + Vec3.of(block));
            } else {
                throw new Wrong("Not a core block: " + Vec3.of(block));
            }
            return true;
        }
        case "clearcores": {
            if (args.length != 0) return false;
            plugin.windicator.clearCores();
            plugin.windicator.save();
            sender.sendMessage("Cores cleared.");
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
