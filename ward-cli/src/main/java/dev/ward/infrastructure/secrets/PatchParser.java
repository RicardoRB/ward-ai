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
  private static final String DIFF = "diff --git ";

  private final String marker;
  private final int shortId;
  private final List<HistoryPatch> patches = new ArrayList<>();
  private final List<AddedLine> lines = new ArrayList<>();
  private String commit = "";
  private String path = "";
  private int number;

  private PatchParser(final String marker, final int shortId) {
    this.marker = marker;
    this.shortId = shortId;
  }

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
    final PatchParser parser = new PatchParser(marker, shortId);
    for (final String line : output.split("\n", -1)) {
      parser.read(line);
    }
    parser.flush();
    return parser.patches;
  }

  private void read(final String line) {
    if (line.startsWith(marker)) {
      flush();
      commit = line.substring(marker.length(), Math.min(line.length(), marker.length() + shortId));
    } else if (line.startsWith(DIFF)) {
      flush();
    } else if (line.startsWith(FILE_MARK)) {
      path = line.substring(FILE_MARK.length());
    } else if (line.startsWith(NO_FILE)) {
      path = "";
    } else {
      content(line);
    }
  }

  private void content(final String line) {
    final Matcher hunk = HUNK.matcher(line);
    if (hunk.find()) {
      number = Integer.parseInt(hunk.group(1));
    } else if (!path.isEmpty() && line.startsWith("+")) {
      add(line.substring(1));
    }
  }

  private void add(final String text) {
    lines.add(new AddedLine(number, text));
    number++;
  }

  /** Ends the patch of the current file and commit. */
  private void flush() {
    if (!path.isEmpty() && !lines.isEmpty()) {
      patches.add(new HistoryPatch(commit, path, lines));
    }
    path = "";
    lines.clear();
  }
}
