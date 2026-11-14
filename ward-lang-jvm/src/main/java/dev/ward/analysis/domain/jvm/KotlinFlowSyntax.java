package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the Kotlin parse tree for control-flow lowering: the grammar parts of a node without
 * comments, newlines and error nodes, and the expression wrappers that hide a statement's shape.
 */
final class KotlinFlowSyntax {

  private static final Set<String> NOISE = Set.of("comment", "error", "NL");
  private static final Set<String> WRAPPERS =
      Set.of(
          "statement",
          "loopExpression",
          "blockLevelExpression",
          "controlStructureBody",
          "expression",
          "disjunction",
          "conjunction",
          "equalityComparison",
          "comparison",
          "namedInfix",
          "elvisExpression",
          "infixFunctionCall",
          "rangeExpression",
          "additiveExpression",
          "multiplicativeExpression",
          "typeRHS",
          "prefixUnaryExpression",
          "postfixUnaryExpression",
          "atomicExpression",
          "conditionalExpression");
  private static final Set<String> PREFIX_MARKS = Set.of("labelDefinition", "annotations");
  private static final String PREFIX = "prefixUnaryExpression";
  private static final String LABEL_DEFINITION = "labelDefinition";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String TEXT = "text";

  private KotlinFlowSyntax() {}

  /** The children of {@code node} that are grammar parts, in source order. */
  static List<SyntaxNode> parts(final SyntaxNode node) {
    final List<SyntaxNode> parts = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!NOISE.contains(child.kind())) {
        parts.add(child);
      }
    }
    return parts;
  }

  /** The first child of {@code node} of {@code kind}, or null. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    for (final SyntaxNode child : parts(node)) {
      if (kind.equals(child.kind())) {
        return child;
      }
    }
    return null;
  }

  /** The children of {@code node} of {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return parts(node).stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** Whether {@code node} has a direct token child with exactly this text. */
  static boolean hasToken(final SyntaxNode node, final String text) {
    return parts(node).stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(tokenText(child)));
  }

  /** The text of a token node, or an empty string for a rule node. */
  static String tokenText(final SyntaxNode node) {
    return node.fields().getOrDefault(TEXT, "");
  }

  /** The source text of {@code node} without comments or spacing. */
  static String text(final SyntaxNode node) {
    return JavaSyntax.dotted(node);
  }

  /**
   * Strips the single-child wrappers and the labels of an expression statement.
   *
   * @param node a statement, a block-level expression or an expression
   * @return the construct it is, such as an {@code ifExpression}, or the first node that has more
   *     than one part
   */
  static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    while (WRAPPERS.contains(current.kind())) {
      final List<SyntaxNode> parts = parts(current);
      if (parts.size() == 1) {
        current = parts.get(0);
      } else if (isLabelled(current)) {
        current = parts.get(parts.size() - 1);
      } else {
        break;
      }
    }
    return current;
  }

  /** The label written before a loop, or null when the statement has none. */
  static String labelOf(final SyntaxNode node) {
    SyntaxNode current = node;
    while (WRAPPERS.contains(current.kind())) {
      final List<SyntaxNode> parts = parts(current);
      if (isLabelled(current)) {
        return labelName(parts.get(0));
      }
      if (parts.size() != 1) {
        return null;
      }
      current = parts.get(0);
    }
    return null;
  }

  private static boolean isLabelled(final SyntaxNode node) {
    final List<SyntaxNode> parts = parts(node);
    return PREFIX.equals(node.kind())
        && parts.size() > 1
        && parts.subList(0, parts.size() - 1).stream()
            .allMatch(part -> PREFIX_MARKS.contains(part.kind()) || isMark(part));
  }

  private static boolean isMark(final SyntaxNode part) {
    return "prefixUnaryOperation".equals(part.kind())
        && parts(part).stream().allMatch(inner -> PREFIX_MARKS.contains(inner.kind()));
  }

  private static String labelName(final SyntaxNode mark) {
    final SyntaxNode definition = LABEL_DEFINITION.equals(mark.kind()) ? mark : firstLabel(mark);
    if (definition == null) {
      return null;
    }
    final String written = text(definition);
    return written.endsWith("@") ? written.substring(0, written.length() - 1) : written;
  }

  private static SyntaxNode firstLabel(final SyntaxNode node) {
    for (final SyntaxNode child : parts(node)) {
      if (LABEL_DEFINITION.equals(child.kind())) {
        return child;
      }
      final SyntaxNode inner = firstLabel(child);
      if (inner != null) {
        return inner;
      }
    }
    return null;
  }

  /** A call, a throw or a non-null assertion may raise, so an enclosing try can catch it. */
  static boolean mayThrow(final SyntaxNode node) {
    if (CALL_SUFFIX.equals(node.kind())) {
      return true;
    }
    if (node.children().isEmpty()) {
      final String token = tokenText(node);
      return "throw".equals(token) || "!!".equals(token);
    }
    return node.children().stream().anyMatch(KotlinFlowSyntax::mayThrow);
  }

  /** The span from the start of {@code first} to the end of {@code last}. */
  static Span between(final Span first, final Span last) {
    return new Span(
        first.startLine(),
        first.startColumn(),
        last.endLine(),
        last.endColumn(),
        first.startOffset(),
        last.endOffset());
  }
}
