package org.tofu.tofunomics.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * プレイヤー 1 人ぶんのサイドバー（画面右のスコアボード）。
 * 一度作ったスコアボードを使い回し、文が変わった行だけを書き換える。
 * 以前は毎秒、全員ぶんのスコアボードを新しく作り直していた。
 *
 * 行は「見えない目印（色コードだけの文字列）」を 1 行ごとに固定で置き、
 * 表示する文はチームの接頭辞として持たせる。こうすると、行を消して作り直さずに文だけ変えられる。
 */
public class PlayerSidebar {

    /** サイドバーに出せる行数の上限（色コード 1 桁で区別するため 15 行まで） */
    static final int MAX_LINES = 15;
    private static final String COLOR_CODES = "0123456789abcde";

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Team> teams = new ArrayList<>();
    private List<String> shownLines = new ArrayList<>();

    public PlayerSidebar(String title) {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("tofunomics", "dummy", title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    /**
     * 表示する行（上から順）を渡す。前回と文が違う行だけを書き換える。
     */
    public void show(List<String> lines) {
        List<String> newLines = limit(lines);

        // 行数が変わったときだけ、行の並び（スコア）を付け直す
        if (newLines.size() != shownLines.size()) {
            for (int i = newLines.size(); i < shownLines.size(); i++) {
                scoreboard.resetScores(entryFor(i));
            }
            for (int i = 0; i < newLines.size(); i++) {
                teamFor(i);
                objective.getScore(entryFor(i)).setScore(newLines.size() - i);
            }
        }

        for (int index : changedIndices(shownLines, newLines)) {
            teamFor(index).setPrefix(newLines.get(index));
        }
        shownLines = newLines;
    }

    /**
     * 前回の行と今回の行を比べ、書き換えが要る行の番号を返す。
     * 前回に無かった行（行数が増えた分）も書き換えの対象。
     */
    static List<Integer> changedIndices(List<String> oldLines, List<String> newLines) {
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < newLines.size(); i++) {
            if (i >= oldLines.size() || !newLines.get(i).equals(oldLines.get(i))) {
                changed.add(i);
            }
        }
        return changed;
    }

    /** 上限を超えた行は切り捨てる */
    static List<String> limit(List<String> lines) {
        return new ArrayList<>(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
    }

    /** i 行目の目印（見えない文字列。行ごとに違う） */
    static String entryFor(int index) {
        return "§" + COLOR_CODES.charAt(index) + "§r";
    }

    private Team teamFor(int index) {
        while (teams.size() <= index) {
            int i = teams.size();
            Team team = scoreboard.registerNewTeam("tn_line_" + i);
            team.addEntry(entryFor(i));
            teams.add(team);
        }
        return teams.get(index);
    }
}
