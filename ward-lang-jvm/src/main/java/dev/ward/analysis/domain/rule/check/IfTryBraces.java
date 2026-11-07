package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Finds the closing braces of if, else, try, catch and finally blocks. */
final class IfTryBraces {

  private static final String CLOSE = "'}'";
  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String IF = "'if'";
  private static final String TRY = "'try'";

  private IfTryBraces() {}

  /** The closing braces of the if, else, try, catch and finally blocks, by identity. */
  static Set<SyntaxNode> closingBraces(final SyntaxNode root) {
    final Set<SyntaxNode> closers = Collections.newSetFromMap(new IdentityHashMap<>());
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of(STATEMENT, "catchClause", "finallyBlock"))) {
      final SyntaxNode node = visit.node();
      if (!STATEMENT.equals(node.kind()) || startsWithIfOrTry(node)) {
        node.children().forEach(child -> addClosingBrace(child, closers));
      }
    }
    return closers;
  }

  private static boolean startsWithIfOrTry(final SyntaxNode statement) {
    return !statement.children().isEmpty()
        && (IF.equals(statement.children().get(0).kind())
            || TRY.equals(statement.children().get(0).kind()));
  }

  private static void addClosingBrace(final SyntaxNode child, final Set<SyntaxNode> closers) {
    final SyntaxNode block =
        STATEMENT.equals(child.kind()) && !child.children().isEmpty()
            ? child.children().get(0)
            : child;
    if (BLOCK.equals(block.kind()) && !block.children().isEmpty()) {
      final SyntaxNode last = block.children().get(block.children().size() - 1);
      if (CLOSE.equals(last.kind())) {
        closers.add(last);
      }
    }
  }
}
