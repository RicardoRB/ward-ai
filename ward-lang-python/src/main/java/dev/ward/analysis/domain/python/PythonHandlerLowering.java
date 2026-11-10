package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lowers the {@code try}, {@code with} and {@code match} statements of a Python body. A {@code
 * with} runs its items and body in order; its bindings hold after it, as Pylint assumes.
 */
final class PythonHandlerLowering {

  private static final String BLOCK = "block";
  private static final String HANDLER = "except_clause";
  private static final String ELSE = "else";
  private static final String FINALLY = "finally";
  private static final String ITEM = "with_item";
  private static final String CASE = "case_block";
  private static final String PATTERNS = "patterns";
  private static final String GUARD = "guard";
  private static final String SUBJECT = "subject_expr";
  private static final int TRY_BODY = 0;
  private static final int FIRST_HANDLER = 1;

  private final ControlFlowBuilder flow;
  private final Consumer<SyntaxNode> statements;

  /**
   * Creates the lowering of the handlers of one body.
   *
   * @param flow the builder of the body
   * @param statements lowers the statements of a block of the body
   */
  PythonHandlerLowering(final ControlFlowBuilder flow, final Consumer<SyntaxNode> statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void tryStatement(final SyntaxNode node) {
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    final List<SyntaxNode> handlers = PythonFlowSyntax.children(node, HANDLER);
    final boolean hasElse = PythonFlowSyntax.hasToken(node, ELSE);
    final boolean hasFinally = PythonFlowSyntax.hasToken(node, FINALLY);
    final int elseIndex = handlers.size() + FIRST_HANDLER;
    flow.tries().pushTry(!handlers.isEmpty(), hasFinally);
    statements.accept(blocks.get(TRY_BODY));
    if (hasElse) {
      statements.accept(blocks.get(elseIndex));
    }
    flow.tries().endTryBody();
    for (int index = 0; index < handlers.size(); index++) {
      flow.tries().beginCatch();
      flow.add(handlers.get(index), false);
      statements.accept(blocks.get(index + FIRST_HANDLER));
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    if (hasFinally) {
      statements.accept(blocks.get(elseIndex + (hasElse ? 1 : 0)));
    }
    flow.tries().popTry();
  }

  void withStatement(final SyntaxNode node) {
    for (final SyntaxNode item : PythonFlowSyntax.children(node, ITEM)) {
      flow.add(item, PythonFlowSyntax.mayThrow(item));
    }
    statements.accept(PythonFlowSyntax.children(node, BLOCK).get(0));
  }

  void matchStatement(final SyntaxNode node) {
    final SyntaxNode subject = PythonFlowSyntax.children(node, SUBJECT).get(0);
    flow.add(subject, PythonFlowSyntax.mayThrow(subject));
    final int after = flow.newBlock();
    boolean exhaustive = false;
    int test = flow.current();
    for (final SyntaxNode caseBlock : PythonFlowSyntax.children(node, CASE)) {
      final int body = flow.newBlock();
      final boolean catchAll = PythonFlowSyntax.isCatchAll(caseBlock);
      if (catchAll) {
        flow.edge(test, body, FlowEdgeKind.NORMAL);
      } else {
        flow.moveTo(test);
        final int next = flow.newBlock();
        flow.branch(body, next);
        test = next;
      }
      flow.moveTo(body);
      caseBody(caseBlock, after);
      if (catchAll) {
        exhaustive = true;
        break;
      }
    }
    if (!exhaustive) {
      flow.edge(test, after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
  }

  private void caseBody(final SyntaxNode caseBlock, final int after) {
    flow.add(PythonFlowSyntax.children(caseBlock, PATTERNS).get(0), false);
    final List<SyntaxNode> guards = PythonFlowSyntax.children(caseBlock, GUARD);
    if (!guards.isEmpty()) {
      flow.add(guards.get(0), PythonFlowSyntax.mayThrow(guards.get(0)));
    }
    statements.accept(PythonFlowSyntax.children(caseBlock, BLOCK).get(0));
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
  }
}
