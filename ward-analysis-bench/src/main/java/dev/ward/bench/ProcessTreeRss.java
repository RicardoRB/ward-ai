package dev.ward.bench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sums resident memory over a process and its descendants from `ps -A -o pid=,ppid=,rss=` output.
 */
final class ProcessTreeRss {

  private static final int RSS_GROUP = 3;
  private static final Pattern ROW = Pattern.compile("^\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$");

  private ProcessTreeRss() {}

  /**
   * Returns the summed rss in kilobytes of {@code root} and its descendants; unknown roots count as
   * zero.
   */
  static long sum(final long root, final String processTable) {
    final Map<Long, Long> rssByPid = new HashMap<>();
    final Map<Long, List<Long>> childrenByParent = new HashMap<>();
    processTable.lines().forEach(line -> addRow(line, rssByPid, childrenByParent));
    long total = 0;
    final Set<Long> seen = new HashSet<>();
    final Deque<Long> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      final long pid = pending.pop();
      if (seen.add(pid)) {
        total += rssByPid.getOrDefault(pid, 0L);
        pending.addAll(childrenByParent.getOrDefault(pid, List.of()));
      }
    }
    return total;
  }

  private static void addRow(
      final String line,
      final Map<Long, Long> rssByPid,
      final Map<Long, List<Long>> childrenByParent) {
    final Matcher row = ROW.matcher(line);
    if (!row.matches()) {
      return;
    }
    final long pid = Long.parseLong(row.group(1));
    final long ppid = Long.parseLong(row.group(2));
    rssByPid.put(pid, Long.parseLong(row.group(RSS_GROUP)));
    childrenByParent.computeIfAbsent(ppid, ignored -> new ArrayList<>()).add(pid);
  }
}
