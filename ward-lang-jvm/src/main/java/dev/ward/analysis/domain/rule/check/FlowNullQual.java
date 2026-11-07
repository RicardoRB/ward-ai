package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * The type qualifier a nullness annotation gives a value or a sink, as the SpotBugs type-qualifier
 * checker reads it: {@code @NonNull} is always non-null, {@code @Nullable} may be null, and
 * {@code @Nonnull(when = NEVER)} is always null.
 */
enum FlowNullQual {
  /** Never null. */
  ALWAYS,
  /** May be null. */
  MAYBE,
  /** Always null. */
  NEVER,
  /** No annotation says. */
  NONE;

  /**
   * Returns the qualifier of the first nullness annotation in {@code annotations}, or the one the
   * {@code @NullMarked} default gives when there is none.
   */
  static FlowNullQual of(final List<SyntaxNode> annotations, final boolean marked) {
    for (final SyntaxNode annotation : annotations) {
      final FlowNullQual found = single(annotation);
      if (found != NONE) {
        return found;
      }
    }
    return marked ? ALWAYS : NONE;
  }

  private static FlowNullQual single(final SyntaxNode annotation) {
    final String text = SyntaxWalk.text(annotation);
    final String name = simpleName(annotation);
    return switch (name) {
      case "Nullable", "CheckForNull" -> MAYBE;
      case "Null" -> NEVER;
      case "NonNull", "NotNull", "Nonnull" -> text.contains("NEVER") ? NEVER : fromWhen(text);
      default -> NONE;
    };
  }

  private static FlowNullQual fromWhen(final String text) {
    if (text.contains("MAYBE")) {
      return MAYBE;
    }
    return text.contains("UNKNOWN") ? NONE : ALWAYS;
  }

  /** The simple name of an annotation node. */
  static String simpleName(final SyntaxNode annotation) {
    return annotation.children().stream()
        .filter(child -> "qualifiedName".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .map(name -> name.substring(name.lastIndexOf('.') + 1))
        .orElse("");
  }
}
