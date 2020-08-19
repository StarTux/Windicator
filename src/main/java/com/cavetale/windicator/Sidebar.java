package com.cavetale.windicator;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class Sidebar {
    private final JavaPlugin plugin;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Line> lines;
    private int cursor = 0;
    static final String CHARS = "!\"#$%&'()*+,-./FGHIJPQRSTUVWXYZ";

    public Sidebar(@NonNull final JavaPlugin plugin) {
        this.plugin = plugin;
        scoreboard = Bukkit.getServer().getScoreboardManager()
            .getNewScoreboard();
        objective = scoreboard.registerNewObjective("sidebar", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        lines = new ArrayList<>();
    }

    public void setCursor(final int newCursor) {
        if (newCursor < 0 || newCursor > lines.size()) {
            throw new IllegalStateException("Sidebar: setCursor: " + newCursor
                                            + "/" + lines.size());
        }
        cursor = newCursor;
    }

    public void newLine(@NonNull String text) {
        if (cursor < 0 || cursor > lines.size()) {
            throw new IllegalStateException("Sidebar cursor: " + cursor
                                            + "/" + lines.size());
        }
        Line line;
        if (cursor == lines.size()) {
            if (lines.size() >= 15) {
                plugin.getLogger().warning("Sidebar line count exceeds 15!");
                return;
            }
            line = new Line(cursor);
            lines.add(line);
        } else {
            line = lines.get(cursor);
        }
        cursor += 1;
        line.now = text;
    }

    public void setLine(final int lineNumber,
                        @NonNull String text) {
        if (lineNumber < 0 || lineNumber >= lines.size()) {
            plugin.getLogger().warning("Sidebar setLine index out of bounds:"
                                       + lineNumber + "/" + lines.size());
            return;
        }
        lines.get(lineNumber).now = text;
    }

    public int countLines() {
        return lines.size();
    }

    public void setTitle(@NonNull String text) {
        objective.setDisplayName(text);
    }

    public void clear() {
        for (Line line : lines) {
            line.now = "";
        }
        cursor = 0;
    }

    public void reset() {
        for (Line line : lines) {
            if (line.format == null) continue;
            scoreboard.resetScores(line.format);
        }
        lines.clear();
        cursor = 0;
    }

    public void addPlayer(@NonNull Player player) {
        if (!player.getScoreboard().equals(scoreboard)) {
            player.setScoreboard(scoreboard);
        }
    }

    public void removePlayer(@NonNull Player player) {
        if (player.getScoreboard().equals(scoreboard)) {
            player.setScoreboard(Bukkit.getServer().getScoreboardManager()
                                 .getMainScoreboard());
        }
    }

    public void update() {
        for (Line line : lines) {
            if (line.didChange()) {
                if (line.format != null) {
                    scoreboard.resetScores(line.format);
                }
                line.update();
                objective.getScore(line.format).setScore(1);
            }
        }
    }

    @RequiredArgsConstructor
    private static final class Line {
        final int index;
        String old = "";
        String now = "";
        String format = null;

        String makeFormat() {
            String txt = "" + ChatColor.COLOR_CHAR + CHARS.charAt(index) + now;
            final int max = 40;
            return txt.length() <= max ? txt : txt.substring(0, max);
        }

        boolean didChange() {
            return format == null || !now.equals(old);
        }

        void update() {
            format = makeFormat();
            old = now;
        }
    }
}
