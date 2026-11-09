package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/**
 * Source-text and tree lookups of the prefer-const check: declarations, patterns and assignments.
 */
final class PreferConstSyntax {

  private static final String DECLARATION = "variableDeclaration";

  private PreferConstSyntax() {}

  /** The innermost variable declaration whose span holds {@code at}. */
  static Optional<SyntaxNode> enclosingDeclaration(final SyntaxNode node, final Span at) {
    Optional<SyntaxNode> found = Optional.empty();
    if (DECLARATION.equals(node.kind()) && holds(node.span(), at)) {
      found = Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> inner = enclosingDeclaration(child, at);
      if (inner.isPresent()) {
        found = inner;
      }
    }
    return found;
  }

  /** The declaration text when it starts with a destructuring pattern, or empty otherwise. */
  static String patternText(final SyntaxNode declaration) {
    final String text = NodeSyntax.text(declaration);
    return text.startsWith("{") || text.startsWith("[") ? text : "";
  }

  /**
   * Whether a destructuring declaration has an initializer: a plain {@code =} after its pattern.
   */
  static boolean initializedAfterPattern(final String declaration) {
    final int equals = declaration.indexOf('=', firstClose(declaration) + 1);
    return equals >= 0
        && (equals + 1 >= declaration.length() || declaration.charAt(equals + 1) != '=');
  }

  /** Whether the name at {@code offset} is followed by a plain {@code =} in the source. */
  static boolean assignsAfter(final int offset, final SourceUnit unit) {
    final String text = unit.text();
    int at = offset;
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return at < text.length()
        && text.charAt(at) == '='
        && (at + 1 >= text.length() || text.charAt(at + 1) != '=');
  }

  /**
   * Whether the write is the whole of a statement that starts it and assigns with a plain {@code
   * =}.
   */
  static boolean plainAssignment(final Span write, final SourceUnit unit) {
    final String text = unit.text();
    int before = write.startOffset() - 1;
    while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
      before--;
    }
    final boolean statementStart = before < 0 || ";{}".indexOf(text.charAt(before)) >= 0;
    return statementStart && assignsAfter(write.endOffset(), unit);
  }

  private static int firstClose(final String text) {
    final int brace = text.indexOf('}');
    final int bracket = text.indexOf(']');
    return brace < 0 ? bracket : bracket < 0 ? brace : Math.min(brace, bracket);
  }

  private static boolean holds(final Span span, final Span at) {
    return span.startOffset() <= at.startOffset() && at.startOffset() < span.endOffset();
  }
}
