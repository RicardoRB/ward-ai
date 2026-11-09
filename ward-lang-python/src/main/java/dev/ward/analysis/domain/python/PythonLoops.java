package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the parts of a {@code for} loop that the flow checks need: whether its iterable always
 * yields a value, and which names its target binds when the iterable may be empty.
 */
public final class PythonLoops {

  private static final String FOR = "for_stmt";
  private static final String TARGET = "exprlist";
  private static final String NAME = "name";
  private static final String RANGE = "range";
  private static final String TRAILER = "trailer";
  private static final String COMMA = ",";
  private static final String FOR_KEYWORD = "for";
  private static final String OPEN_LIST = "[";
  private static final String OPEN_SET = "{";
  private static final String OPEN_TUPLE = "(";
  private static final Set<String> OTHER_SCOPE = Set.of("funcdef", "classdef");

  private PythonLoops() {}

  /**
   * Reports whether an iterable always yields at least one value: a {@code range(...)} call, or a
   * non-empty list, set, dict or tuple display. Any other iterable may be empty.
   *
   * @param iterable the expression after {@code in}
   * @return true when the loop body runs at least once
   */
  public static boolean alwaysRuns(final SyntaxNode iterable) {
    final List<SyntaxNode> tokens = PythonSyntax.tokens(iterable);
    if (tokens.isEmpty()) {
      return false;
    }
    final String first = PythonSyntax.text(tokens.get(0));
    if (RANGE.equals(first)) {
      return tokens.size() > 1 && OPEN_TUPLE.equals(PythonSyntax.text(tokens.get(1)));
    }
    if (OPEN_LIST.equals(first) || OPEN_SET.equals(first) || OPEN_TUPLE.equals(first)) {
      return tokens.size() > 2 && !isComprehension(tokens) && hasDisplayElement(tokens, first);
    }
    return false;
  }

  /** A comprehension may produce no value, even though its brackets hold tokens. */
  private static boolean isComprehension(final List<SyntaxNode> tokens) {
    return tokens.stream().anyMatch(token -> FOR_KEYWORD.equals(PythonSyntax.text(token)));
  }

  /** A display has an element when it holds more than its brackets, and a tuple needs a comma. */
  private static boolean hasDisplayElement(final List<SyntaxNode> tokens, final String open) {
    if (OPEN_TUPLE.equals(open)) {
      return tokens.stream().anyMatch(token -> COMMA.equals(PythonSyntax.text(token)));
    }
    return true;
  }

  /**
   * The spans of the names a {@code for} loop binds when its iterable may be empty. A loop whose
   * iterable always runs binds its names on every path, so none is listed.
   *
   * @param root the syntax tree of the file
   * @return the spans of the target names, in source order
   */
  public static List<Span> unsafeTargets(final SyntaxNode root) {
    final List<Span> spans = new ArrayList<>();
    collect(root, spans);
    return List.copyOf(spans);
  }

  private static void collect(final SyntaxNode node, final List<Span> spans) {
    if (FOR.equals(node.kind())) {
      addTarget(node, spans);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, spans);
    }
  }

  private static void addTarget(final SyntaxNode loop, final List<Span> spans) {
    final List<SyntaxNode> parts = loop.children();
    final SyntaxNode target = child(parts, TARGET);
    final SyntaxNode iterable = child(parts, "testlist_star_expr");
    if (target == null || iterable == null || alwaysRuns(iterable)) {
      return;
    }
    final List<SyntaxNode> names = new ArrayList<>();
    collectNames(target, names);
    for (final SyntaxNode name : names) {
      spans.add(name.span());
    }
  }

  private static void collectNames(final SyntaxNode node, final List<SyntaxNode> names) {
    if (NAME.equals(node.kind())) {
      names.add(node);
      return;
    }
    if (TRAILER.equals(node.kind()) || OTHER_SCOPE.contains(node.kind())) {
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectNames(child, names);
    }
  }

  private static SyntaxNode child(final List<SyntaxNode> parts, final String kind) {
    return parts.stream().filter(part -> kind.equals(part.kind())).findFirst().orElse(null);
  }
}
