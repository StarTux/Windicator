package com.cavetale.windicator;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class WindicatorCommand extends AbstractCommand<WindicatorPlugin> {
    public WindicatorCommand(final WindicatorPlugin plugin) {
        super(plugin, "windicator");
    }

    @Override
    public void onEnable() {
        rootNode.addChild("info").denyTabCompletion()
            .senderCaller(this::info);
        rootNode.addChild("start").denyTabCompletion()
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .senderCaller(this::stop);
        rootNode.addChild("victory").denyTabCompletion()
            .senderCaller(this::victory);
        rootNode.addChild("addcore").arguments("<type>")
            .completers(CommandArgCompleter.enumLowerList(CoreType.class))
            .playerCaller(this::addCore);
        rootNode.addChild("removecore").arguments("<type>")
            .completers(CommandArgCompleter.enumLowerList(CoreType.class))
            .playerCaller(this::removeCore);
        rootNode.addChild("clearCores").denyTabCompletion()
            .senderCaller(this::clearCores);
        rootNode.addChild("clearscores").denyTabCompletion()
            .senderCaller(this::clearScores);
        rootNode.addChild("rewardscores").denyTabCompletion()
            .senderCaller(this::rewardScores);
    }

    private void info(CommandSender sender) {
        sender.sendMessage("isValid=" + plugin.getWindicator().isValid());
        sender.sendMessage("state=" + Json.serialize(plugin.getWindicator().getState()));
    }

    private void start(CommandSender sender) {
            plugin.getWindicator().setEnabled(true);
            plugin.getWindicator().save();
            if (plugin.getWindicator().isValid()) {
                sender.sendMessage("Game started");
            } else {
                sender.sendMessage("Game started but still not valid.");
            }
    }

    private void stop(CommandSender sender) {
        plugin.getWindicator().setEnabled(false);
        plugin.getWindicator().save();
        sender.sendMessage("Game stopped");
    }

    private void victory(CommandSender sender) {
        plugin.getWindicator().setVictory(!plugin.getWindicator().isVictory());
        plugin.getWindicator().save();
        sender.sendMessage("Victory set to: " + plugin.getWindicator().isVictory());
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
        player.sendMessage("Core block added: " + coreType + ": " + Vec3i.of(block));
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
        player.sendMessage("Core block removed: " + coreType + ": " + Vec3i.of(block));
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
