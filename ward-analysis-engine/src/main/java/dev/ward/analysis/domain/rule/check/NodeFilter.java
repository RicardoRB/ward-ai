package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The nodes a generic check looks at: the node kinds, and the filters every kind shares. A kind may
 * be written {@code parent>child}, which selects a node of kind {@code parent} that has a direct
 * child of kind {@code child}; the child may be a quoted token such as {@code '>'}. A node passes
 * when it has all of {@code modifiers} and none of {@code notModifiers}, some ancestor is in {@code
 * inside} (when given), no ancestor is in {@code notInside}, and the file passes {@code sources}.
 * The {@code inside} and {@code notInside} kinds may be written {@code parent>child} too, so that
 * {@code statement>'for'} names a loop.
 *
 * @param kinds the node kinds to look at
 * @param table how the language spells modifiers
 * @param modifiers the modifier names a node must have
 * @param notModifiers the modifier names a node must not have
 * @param inside the kinds of which some ancestor must be, if not empty
 * @param notInside the kinds of which no ancestor may be
 * @param sources the files to look at
 */
public record NodeFilter(
    Set<String> kinds,
    ModifierTable table,
    Set<String> modifiers,
    Set<String> notModifiers,
    Set<String> inside,
    Set<String> notInside,
    Sources sources) {

  /**
   * Copies the sets.
   *
   * @throws NullPointerException if a field is missing
   */
  public NodeFilter {
    kinds = Set.copyOf(kinds);
    Objects.requireNonNull(table, "table");
    modifiers = Set.copyOf(modifiers);
    notModifiers = Set.copyOf(notModifiers);
    inside = Set.copyOf(inside);
    notInside = Set.copyOf(notInside);
    Objects.requireNonNull(sources, "sources");
  }

  /** Looks at every node of {@code kinds} in every file. */
  public static NodeFilter of(final Set<String> kinds) {
    return new NodeFilter(
        kinds, ModifierTable.NONE, Set.of(), Set.of(), Set.of(), Set.of(), Sources.ALL);
  }

  /** Returns the nodes of the kinds that pass the filters, each with its ancestors. */
  List<SyntaxWalk.Visit> select(final SyntaxNode root, final SourceFile file) {
    if (!sources.admits(file.path())) {
      return List.of();
    }
    final Set<String> plain = kinds.stream().map(NodeFilter::parentOf).collect(Collectors.toSet());
    return SyntaxWalk.visits(root, plain).stream()
        .filter(visit -> isKind(visit.node()))
        .filter(this::contextAdmits)
        .filter(this::modifiersAdmit)
        .toList();
  }

  private boolean contextAdmits(final SyntaxWalk.Visit visit) {
    final boolean inOk =
        inside.isEmpty() || inside.stream().anyMatch(selector -> hasAncestor(visit, selector));
    return inOk && notInside.stream().noneMatch(selector -> hasAncestor(visit, selector));
  }

  private static boolean hasAncestor(final SyntaxWalk.Visit visit, final String selector) {
    return visit.ancestors().stream().anyMatch(ancestor -> selects(selector, ancestor));
  }

  private boolean modifiersAdmit(final SyntaxWalk.Visit visit) {
    if (modifiers.isEmpty() && notModifiers.isEmpty()) {
      return true;
    }
    final List<String> present = Declaration.of(visit, table.kinds()).modifiers();
    return modifiers.stream().allMatch(name -> table.has(present, name))
        && notModifiers.stream().noneMatch(name -> table.has(present, name));
  }

  /** Tells whether {@code node} is selected by one of the kinds. */
  boolean isKind(final SyntaxNode node) {
    return kinds.stream().anyMatch(selector -> selects(selector, node));
  }

  static boolean selects(final String selector, final SyntaxNode node) {
    final int split = separator(selector);
    if (split < 0) {
      return selector.equals(node.kind());
    }
    final String child = selector.substring(split + 1);
    return parentOf(selector).equals(node.kind())
        && node.children().stream().anyMatch(c -> child.equals(c.kind()));
  }

  private static String parentOf(final String selector) {
    final int split = separator(selector);
    return split < 0 ? selector : selector.substring(0, split);
  }

  /** Finds the {@code >} between parent and child, skipping a quoted token such as {@code '>'}. */
  private static int separator(final String selector) {
    boolean quoted = false;
    for (int index = 0; index < selector.length(); index++) {
      final char c = selector.charAt(index);
      quoted ^= c == '\'';
      if (c == '>' && !quoted) {
        return index;
      }
    }
    return -1;
  }
}
