package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Tells whether a resource is closed by a later statement of its block, after other calls. */
final class CloseAfterCalls {

  private static final int FIRST_CLOSE = 1;

  private CloseAfterCalls() {}

  static boolean closedAfterCalls(final String name, final List<SyntaxNode> rest) {
    final String closing = name + ".close();";
    final List<String> texts = rest.stream().map(SyntaxWalk::text).toList();
    final int close = texts.indexOf(closing);
    if (close < FIRST_CLOSE) {
      return false;
    }
    final boolean calls = texts.subList(0, close).stream().anyMatch(text -> text.contains("("));
    return calls
        && rest.stream()
            .filter(node -> !closing.equals(SyntaxWalk.text(node)))
            .allMatch(node -> untouched(name, node));
  }

  /** A statement that is no try and does not use the name other than as a call receiver. */
  private static boolean untouched(final String name, final SyntaxNode statement) {
    final List<String> tokens =
        SyntaxWalk.leaves(statement).stream().map(SyntaxWalk::text).toList();
    return !tokens.contains("try") && !CloseResourceCheck.closedOrHandedOn(name, tokens);
  }
}
