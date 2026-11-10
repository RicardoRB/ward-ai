package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The condition, the update and the body of a {@code while} or classic {@code for} statement.
 *
 * @param condition the condition expression, or {@code null} when the loop has none
 * @param update the nodes of the update part of a {@code for}, empty for a {@code while}
 * @param body the body statement
 */
record JavaLoopParts(SyntaxNode condition, List<SyntaxNode> update, SyntaxNode body) {

  private static final String WHILE = "'while'";
  private static final String FOR = "'for'";
  private static final String EXPRESSION = "expression";
  private static final String SEPARATOR = "';'";
  private static final int SEPARATORS_BEFORE_UPDATE = 2;

  JavaLoopParts {
    update = List.copyOf(update);
  }

  /** Reads the parts of a statement, or nothing when it is not such a loop. */
  static Optional<JavaLoopParts> of(final SyntaxNode statement) {
    final List<SyntaxNode> parts = statement.children();
    final SyntaxNode body = parts.isEmpty() ? null : parts.get(parts.size() - 1);
    final String head = parts.isEmpty() ? "" : parts.get(0).kind();
    if (body == null || !"statement".equals(body.kind())) {
      return Optional.empty();
    }
    if (WHILE.equals(head)) {
      return Optional.of(new JavaLoopParts(condition(parts), List.of(), body));
    }
    return FOR.equals(head) ? forLoop(parts, body) : Optional.empty();
  }

  private static Optional<JavaLoopParts> forLoop(
      final List<SyntaxNode> parts, final SyntaxNode body) {
    return parts.stream()
        .filter(part -> "forControl".equals(part.kind()))
        .findFirst()
        .map(control -> new JavaLoopParts(condition(control.children()), update(control), body));
  }

  private static SyntaxNode condition(final List<SyntaxNode> parts) {
    return parts.stream().filter(part -> EXPRESSION.equals(part.kind())).findFirst().orElse(null);
  }

  private static List<SyntaxNode> update(final SyntaxNode control) {
    final List<SyntaxNode> update = new ArrayList<>();
    int separators = 0;
    for (final SyntaxNode part : control.children()) {
      if (separators >= SEPARATORS_BEFORE_UPDATE) {
        update.add(part);
      }
      separators += SEPARATOR.equals(part.kind()) ? 1 : 0;
    }
    return update;
  }
}
