package com.cavetale.windicator;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class WindicatorCommand extends AbstractCommand<WindicatorPlugin> {
    public WindicatorCommand(final WindicatorPlugin plugin) {
        super(plugin, "windicator");
    }

    @Override
    public void onEnable() {
        rootNode.addChild("info").denyTabCompletion()
            .description("Dump some game info")
            .senderCaller(this::info);
        rootNode.addChild("start").denyTabCompletion()
            .description("Start the game")
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .senderCaller(this::stop);
        rootNode.addChild("victory").denyTabCompletion()
            .description("Toggle victory")
            .senderCaller(this::victory);
        rootNode.addChild("addcore").arguments("<type>")
            .description("Add a core")
            .completers(CommandArgCompleter.enumLowerList(CoreType.class))
            .playerCaller(this::addCore);
        rootNode.addChild("removecore").arguments("<type>")
            .description("Remove a core")
            .completers(CommandArgCompleter.enumLowerList(CoreType.class))
            .playerCaller(this::removeCore);
        rootNode.addChild("autocore").arguments("<type>")
            .description("Automatically turn trial spawners into cores")
            .completers(CommandArgCompleter.enumLowerList(CoreType.class))
            .playerCaller(this::autoCore);
        rootNode.addChild("clearCores").denyTabCompletion()
            .description("Clear all cores")
            .senderCaller(this::clearCores);
        rootNode.addChild("clearscores").denyTabCompletion()
            .description("Clear all scores")
            .senderCaller(this::clearScores);
        rootNode.addChild("rewardscores").denyTabCompletion()
            .description("Reward player scores")
            .senderCaller(this::rewardScores);
    }

    private void info(CommandSender sender) {
        sender.sendMessage(text("isValid=" + plugin.getWindicator().isValid(), YELLOW));
        sender.sendMessage(text("state=" + Json.serialize(plugin.getWindicator().getState()), YELLOW));
    }

    private void start(CommandSender sender) {
            plugin.getWindicator().setEnabled(true);
            plugin.getWindicator().save();
            if (plugin.getWindicator().isValid()) {
                sender.sendMessage(text("Game started", GREEN));
            } else {
                sender.sendMessage(text("Game started but still not valid", RED));
            }
    }

    private void stop(CommandSender sender) {
        plugin.getWindicator().setEnabled(false);
        plugin.getWindicator().save();
        sender.sendMessage(text("Game stopped", YELLOW));
    }

    private void victory(CommandSender sender) {
        plugin.getWindicator().setVictory(!plugin.getWindicator().isVictory());
        plugin.getWindicator().save();
        sender.sendMessage(text("Victory set to: " + plugin.getWindicator().isVictory(), YELLOW));
    }

    private boolean addCore(Player player, String[] args) {
        if (args.length != 1) return false;
        final CoreType coreType = CommandArgCompleter.requireEnum(CoreType.class, args[0]);
        final Block block = player.getTargetBlockExact(5);
        if (block == null) throw new CommandWarn("Not looking at block");
        if (!plugin.getWindicator().addCore(block, coreType)) {
            throw new CommandWarn("Core block already contained.");
        }
        plugin.getWindicator().save();
        player.sendMessage(text("Core block added: " + coreType + ": " + Vec3i.of(block), YELLOW));
        return true;
    }

    private boolean removeCore(Player player, String[] args) {
        if (args.length != 1) return false;
        final CoreType coreType = CommandArgCompleter.requireEnum(CoreType.class, args[0]);
        final Block block = player.getTargetBlockExact(5);
        if (block == null) throw new CommandWarn("Not looking at block");
        if (!plugin.getWindicator().removeCore(block, coreType, true)) {
            throw new CommandWarn("Not a core block: " + Vec3i.of(block));
        }
        plugin.getWindicator().save();
        player.sendMessage(text("Core block removed: " + coreType + ": " + Vec3i.of(block), YELLOW));
        return true;
    }

    private boolean autoCore(Player player, String[] args) {
        if (args.length != 1) return false;
        final CoreType coreType = CommandArgCompleter.requireEnum(CoreType.class, args[0]);
        int count = 0;
        for (Vec3i vec : Cuboid.requireSelectionOf(player)) {
            final Block block = vec.toBlock(player.getWorld());
            if (block.getType() != Material.TRIAL_SPAWNER) continue;
            if (!plugin.getWindicator().addCore(block, coreType)) {
                continue;
            }
            count += 1;
            final String cmd = "/tp " + vec.x + " " + vec.y + " " + vec.z;
            player.sendMessage(textOfChildren(text("#" + count + " ", GRAY),
                                              text(" Core added: ", YELLOW),
                                              text("" + vec, WHITE))
                               .hoverEvent(showText(text(cmd, GRAY)))
                               .clickEvent(runCommand(cmd)));
        }
        if (count == 0) {
            throw new CommandWarn("No trial spawners found in your selection");
        }
        plugin.getWindicator().save();
        player.sendMessage(text(count + " total core blocks added", YELLOW));
        return true;
    }

    private void clearCores(CommandSender sender) {
        plugin.getWindicator().clearCores();
        plugin.getWindicator().save();
        sender.sendMessage("Cores cleared.");
    }

    private void clearScores(CommandSender sender) {
        plugin.getWindicator().getState().getScores().clear();
        plugin.getWindicator().computeHighscore();
        sender.sendMessage(text("Scores cleared", YELLOW));
    }

    private void rewardScores(CommandSender sender) {
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
    }
}
