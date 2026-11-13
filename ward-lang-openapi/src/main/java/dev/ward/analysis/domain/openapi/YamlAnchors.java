package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** The anchors of a document and the budget that stops an alias bomb. */
final class YamlAnchors {

  /** The most nodes the aliases of one document may expand to. */
  static final long MAX_EXPANDED = 1_000_000L;

  private final Map<String, SyntaxNode> anchors = new HashMap<>();
  private final Map<SyntaxNode, Long> sizes = new IdentityHashMap<>();
  private long expanded;

  void define(final String name, final SyntaxNode node) {
    anchors.put(name, node);
  }

  /** Reads the name after an {@code &} or {@code *} and leaves the cursor after it. */
  static String name(final YamlCursor cursor) {
    final int start = cursor.pos() + 1;
    int end = start;
    while (end < cursor.text().length() && isNameChar(cursor.charAt(end))) {
      end++;
    }
    if (end == start) {
      throw cursor.fail("an anchor or alias needs a name", cursor.pos());
    }
    cursor.moveTo(end);
    return cursor.text().substring(start, end);
  }

  /** The anchored node as it reads at {@code site}; fails for an unknown name or a bomb. */
  SyntaxNode expand(final String name, final Span site, final YamlCursor cursor) {
    final SyntaxNode original = anchors.get(name);
    if (original == null) {
      throw cursor.fail("the alias '" + name + "' has no anchor before it", site.startOffset());
    }
    expanded += size(original);
    if (expanded > MAX_EXPANDED) {
      throw cursor.fail("the aliases expand to too many nodes", site.startOffset());
    }
    return YamlNodes.alias(original, site, name);
  }

  private long size(final SyntaxNode node) {
    final Long known = sizes.get(node);
    if (known != null) {
      return known;
    }
    long total = 1;
    for (final SyntaxNode child : node.children()) {
      total += size(child);
    }
    sizes.put(node, total);
    return total;
  }

  private static boolean isNameChar(final char c) {
    return c > ' ' && ",[]{}".indexOf(c) < 0;
  }
}
