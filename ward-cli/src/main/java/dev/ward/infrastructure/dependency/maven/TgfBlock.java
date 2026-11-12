package dev.ward.infrastructure.dependency.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One module's part of the log: a node list, a {@code #} line and an edge list, which starts at a
 * {@code dependency:<version>:tree (default-cli) @ <artifactId>} header. The first node is the
 * module itself; every other node has the one parent its edge names.
 */
final class TgfBlock {
  private static final String INFO = "[INFO] ";
  private static final Pattern HEADER =
      Pattern.compile("^-+\\s+\\S+:tree\\s+\\([^)]*\\)\\s+@\\s+(\\S+)\\s+-+$");
  private static final Pattern NODE = Pattern.compile("^(-?\\d+) (\\S.*:.*)$");
  private static final Pattern EDGE = Pattern.compile("^(-?\\d+) (-?\\d+) (\\S+)$");
  private static final String SEPARATOR = "#";

  private final String artifactId;
  private final Map<String, TgfNode> nodes = new LinkedHashMap<>();
  private final Map<String, String> parents = new HashMap<>();
  private String root = "";
  private boolean edgesStarted;

  private TgfBlock(final String artifactId) {
    this.artifactId = artifactId;
  }

  /** Splits the Maven log into one block per module. */
  static List<TgfBlock> parse(final String output) {
    final List<TgfBlock> blocks = new ArrayList<>();
    for (final String raw : output.lines().filter(line -> line.startsWith(INFO)).toList()) {
      final String line = raw.substring(INFO.length()).strip();
      final Matcher header = HEADER.matcher(line);
      if (header.matches()) {
        blocks.add(new TgfBlock(header.group(1)));
      } else if (!blocks.isEmpty()) {
        blocks.getLast().add(line);
      }
    }
    return blocks;
  }

  private void add(final String line) {
    if (SEPARATOR.equals(line)) {
      edgesStarted = true;
      return;
    }
    final Matcher edge = EDGE.matcher(line);
    final Matcher node = NODE.matcher(line);
    if (edgesStarted && edge.matches()) {
      parents.put(edge.group(2), edge.group(1));
    } else if (!edgesStarted && node.matches()) {
      nodes.put(node.group(1), TgfNode.parse(node.group(2)));
      if (root.isEmpty()) {
        root = node.group(1);
      }
    }
  }

  /** Returns the artifactId of the header, for messages. */
  String artifactId() {
    return artifactId;
  }

  /** Returns the key of the module's own node. */
  String root() {
    return root;
  }

  /** Returns every node by key, in the order of the log. */
  Map<String, TgfNode> nodes() {
    return nodes;
  }

  /** Returns the key of the parent of {@code key}, the module's own node when none is named. */
  String parent(final String key) {
    return parents.getOrDefault(key, root);
  }
}
