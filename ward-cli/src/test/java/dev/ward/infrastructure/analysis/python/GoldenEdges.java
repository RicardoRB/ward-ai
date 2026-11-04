package dev.ward.infrastructure.analysis.python;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the root packages and file edges of an {@code import-linter-edges.json} file, or of the
 * same JSON that the grimp script prints. Both are written by {@code json.dumps} with indentation,
 * so the edges are the two-string arrays that follow the {@code "edges"} key.
 */
final class GoldenEdges {

  private static final String ROOTS_KEY = "\"roots\"";
  private static final String EDGES_KEY = "\"edges\"";
  private static final Pattern PAIR = Pattern.compile("\\[\\s*\"([^\"]+)\",\\s*\"([^\"]+)\"\\s*]");
  private static final Pattern NAME = Pattern.compile("\"([^\"]+)\"");

  private GoldenEdges() {}

  /** The root packages, in file order. */
  static List<String> roots(final String json) {
    final String section = json.substring(json.indexOf(ROOTS_KEY));
    final String list = section.substring(section.indexOf('['), section.indexOf(']') + 1);
    final Matcher matcher = NAME.matcher(list);
    final List<String> names = new ArrayList<>();
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /** The edges as {@code "from -> to"} keys, sorted, so two sets compare by equality. */
  static Set<String> edges(final String json) {
    final String section = json.substring(json.indexOf(EDGES_KEY));
    final Matcher matcher = PAIR.matcher(section);
    final Set<String> edges = new TreeSet<>();
    while (matcher.find()) {
      edges.add(key(matcher.group(1), matcher.group(2)));
    }
    return edges;
  }

  /** The key of one edge, the form the reference and smoke tests compare. */
  static String key(final String from, final String to) {
    return from + " -> " + to;
  }
}
