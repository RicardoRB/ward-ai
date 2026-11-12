package dev.ward.infrastructure.secrets;

import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.domain.secrets.HistoryPatch.AddedLine;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the output of {@code git log -p -U0}: the added lines per commit and file. */
final class PatchParser {

  private static final Pattern HUNK = Pattern.compile("^@@ -\\S+ \\+(\\d+)(?:,\\d+)? @@");
  private static final String FILE_MARK = "+++ b/";
  private static final String NO_FILE = "+++ /dev/null";

  private PatchParser() {}

  /** Returns how many commits {@code output} holds. */
  static int commits(final String output, final String marker) {
    int count = 0;
    for (int at = output.indexOf(marker); at >= 0; at = output.indexOf(marker, at + 1)) {
      count++;
    }
    return count;
  }

  /** Returns the short id of the first commit of {@code output}. */
  static String firstCommit(final String output, final String marker, final int shortId) {
    final int at = output.indexOf(marker);
    if (at < 0) {
      return "";
    }
    final int start = at + marker.length();
    return output.substring(start, Math.min(output.length(), start + shortId));
  }

  /** Returns one patch per commit and file with added lines. */
  static List<HistoryPatch> parse(final String output, final String marker, final int shortId) {
    final List<HistoryPatch> patches = new ArrayList<>();
    String commit = "";
    String path = null;
    List<AddedLine> lines = new ArrayList<>();
    int number = 0;
    for (final String line : output.split("\n", -1)) {
      if (line.startsWith(marker)) {
        flush(patches, commit, path, lines);
        commit =
            line.substring(marker.length(), Math.min(line.length(), marker.length() + shortId));
        path = null;
        lines = new ArrayList<>();
      } else if (line.startsWith("diff --git ")) {
        flush(patches, commit, path, lines);
        path = null;
        lines = new ArrayList<>();
      } else if (line.startsWith(FILE_MARK)) {
        path = line.substring(FILE_MARK.length());
      } else if (line.startsWith(NO_FILE)) {
        path = null;
      } else {
        final Matcher hunk = HUNK.matcher(line);
        if (hunk.find()) {
          number = Integer.parseInt(hunk.group(1));
        } else if (path != null && line.startsWith("+")) {
          lines.add(new AddedLine(number++, line.substring(1)));
        }
      }
    }
    flush(patches, commit, path, lines);
    return patches;
  }

  private static void flush(
      final List<HistoryPatch> patches,
      final String commit,
      final String path,
      final List<AddedLine> lines) {
    if (path != null && !lines.isEmpty()) {
      patches.add(new HistoryPatch(commit, path, lines));
    }
  }
}
