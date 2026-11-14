package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Reads the label that a Kotlin statement carries before a loop, as in {@code outer@ for}. */
final class KotlinLabels {

  private static final Set<String> PREFIX_MARKS = Set.of("labelDefinition", "annotations");
  private static final String PREFIX = "prefixUnaryExpression";
  private static final String LABEL_DEFINITION = "labelDefinition";
  private static final int SINGLE = 1;

  private KotlinLabels() {}

  /** The label written before a loop, or null when the statement has none. */
  static String of(final SyntaxNode node) {
    SyntaxNode current = node;
    while (KotlinFlowSyntax.isWrapper(current)) {
      final List<SyntaxNode> parts = KotlinFlowSyntax.parts(current);
      if (isLabelled(current)) {
        return labelName(parts.get(0));
      }
      if (parts.size() != SINGLE) {
        return null;
      }
      current = parts.get(0);
    }
    return null;
  }

  static boolean isLabelled(final SyntaxNode node) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(node);
    return PREFIX.equals(node.kind())
        && parts.size() > SINGLE
        && parts.subList(0, parts.size() - 1).stream()
            .allMatch(part -> PREFIX_MARKS.contains(part.kind()) || isMark(part));
  }

  private static boolean isMark(final SyntaxNode part) {
    return "prefixUnaryOperation".equals(part.kind())
        && KotlinFlowSyntax.parts(part).stream()
            .allMatch(inner -> PREFIX_MARKS.contains(inner.kind()));
  }

  private static String labelName(final SyntaxNode mark) {
    final SyntaxNode definition = LABEL_DEFINITION.equals(mark.kind()) ? mark : firstLabel(mark);
    if (definition == null) {
      return null;
    }
    final String written = KotlinFlowSyntax.text(definition);
    return written.endsWith("@") ? written.substring(0, written.length() - 1) : written;
  }

  private static SyntaxNode firstLabel(final SyntaxNode node) {
    for (final SyntaxNode child : KotlinFlowSyntax.parts(node)) {
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
}
