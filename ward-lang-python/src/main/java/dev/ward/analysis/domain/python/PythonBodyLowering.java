package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Set;

/**
 * Lowers the statements of one Python body into a control-flow graph. A nested function or class is
 * one opaque node here; its own body is lowered separately.
 */
final class PythonBodyLowering {

  private static final String BLOCK = "block";
  private static final String NAMED_EXPR = "namedexpr_test";
  private static final String PATTERNS = "patterns";
  private static final String GUARD = "guard";
  private static final String TARGET = "exprlist";
  private static final String ITERABLE = "testlist_star_expr";
  private static final String HANDLER = "except_clause";
  private static final String ELSE = "else";
  private static final String FINALLY = "finally";
  private static final String ITEM = "with_item";
  private static final String CASE = "case_block";
  private static final String CALL = "(";
  private static final String AWAIT = "await";
  private static final String ALWAYS_TRUE_ONE = "1";
  private static final String ALWAYS_TRUE = "True";
  private static final Set<String> DEFINITIONS = Set.of("funcdef", "classdef", "decorated");
  private static final Set<String> NON_IDENTIFIERS = Set.of("None", ALWAYS_TRUE, "False");

  private final PythonAccesses accesses;
  private final ControlFlowBuilder flow;

  /**
   * Starts the lowering of one body.
   *
   * @param excluded the names the body declares {@code global} or {@code nonlocal}
   * @param tracked false when a star import makes every name of the body possibly bound elsewhere
   */
  PythonBodyLowering(final Set<String> excluded, final boolean tracked) {
    this.accesses = new PythonAccesses(excluded, tracked);
    this.flow = new ControlFlowBuilder(accesses);
  }

  /**
   * Lowers a body whose statements are in {@code block}.
   *
   * @param name the name of the body
   * @param block the block or file that holds the statements
   * @param parameters the parameters, which are bound at the entry of the body
   * @param span where the body is in the source
   * @return the graph of the body
   */
  ControlFlowGraph lower(
      final String name,
      final SyntaxNode block,
      final List<VariableAccess> parameters,
      final Span span) {
    flow.record(parameters, List.of());
    statements(block);
    return flow.build(name, span);
  }

  /** Lowers every statement under a block or a file, in source order. */
  private void statements(final SyntaxNode block) {
    for (final SyntaxNode leaf : PythonStatementLeaves.leaves(block)) {
      statement(leaf);
    }
  }

  private void statement(final SyntaxNode node) {
    switch (node.kind()) {
      case "if_stmt" -> ifStatement(node);
      case "while_stmt" -> whileStatement(node);
      case "for_stmt" -> forStatement(node);
      case "try_stmt" -> tryStatement(node);
      case "with_stmt" -> withStatement(node);
      case "match_stmt" -> matchStatement(node);
      case "break_stmt" -> {
        simple(node, false);
        flow.jumps().breakTo(null);
      }
      case "continue_stmt" -> {
        simple(node, false);
        flow.jumps().continueTo(null);
      }
      case "return_stmt" -> {
        simple(node, mayThrow(node));
        flow.tries().returnFrom();
      }
      case "raise_stmt" -> {
        simple(node, true);
        flow.tries().throwOut();
      }
      default -> simple(node, !DEFINITIONS.contains(node.kind()) && mayThrow(node));
    }
  }

  private void simple(final SyntaxNode node, final boolean mayThrow) {
    flow.add(node, mayThrow);
  }

  private void ifStatement(final SyntaxNode node) {
    final List<SyntaxNode> conditions = children(node, NAMED_EXPR);
    final List<SyntaxNode> blocks = children(node, BLOCK);
    final int after = flow.newBlock();
    for (int index = 0; index < conditions.size(); index++) {
      simple(conditions.get(index), mayThrow(conditions.get(index)));
      final int then = flow.newBlock();
      final int next = flow.newBlock();
      flow.branch(then, next);
      flow.moveTo(then);
      statements(blocks.get(index));
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
      flow.moveTo(next);
    }
    if (blocks.size() > conditions.size()) {
      statements(blocks.get(conditions.size()));
    }
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  private void whileStatement(final SyntaxNode node) {
    final SyntaxNode condition = children(node, NAMED_EXPR).get(0);
    final List<SyntaxNode> blocks = children(node, BLOCK);
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    final int otherwise = blocks.size() > 1 ? flow.newBlock() : after;
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    simple(condition, mayThrow(condition));
    if (isAlwaysTrue(condition)) {
      flow.edge(header, body, FlowEdgeKind.NORMAL);
    } else {
      flow.branch(body, otherwise);
    }
    loop(body, after, header, blocks.get(0));
    elseClause(blocks, otherwise, after);
  }

  private void forStatement(final SyntaxNode node) {
    final SyntaxNode iterable = children(node, ITERABLE).get(0);
    final SyntaxNode target = children(node, TARGET).get(0);
    final List<SyntaxNode> blocks = children(node, BLOCK);
    simple(iterable, mayThrow(iterable));
    final boolean alwaysRuns = PythonLoops.alwaysRuns(iterable);
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    final int otherwise = blocks.size() > 1 ? flow.newBlock() : after;
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    if (alwaysRuns) {
      flow.edge(header, body, FlowEdgeKind.NORMAL);
    } else {
      flow.branch(body, otherwise);
    }
    flow.moveTo(body);
    accesses.plan(target, true);
    simple(target, false);
    loop(body, after, header, blocks.get(0));
    if (alwaysRuns) {
      // The loop leaves from its last body block, so the target is bound on every path out.
      flow.edge(flow.current(), otherwise, FlowEdgeKind.NORMAL);
    }
    elseClause(blocks, otherwise, after);
  }

  /**
   * Lowers the body of a loop, whose {@code break} leaves to {@code after}, past the else clause.
   */
  private void loop(final int body, final int after, final int header, final SyntaxNode block) {
    flow.moveTo(body);
    flow.jumps().pushLoop(null, after, header);
    statements(block);
    flow.jumps().popFrame();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
  }

  /**
   * Lowers the {@code else} block of a loop, which runs only when the loop ends without a break.
   */
  private void elseClause(final List<SyntaxNode> blocks, final int otherwise, final int after) {
    if (blocks.size() > 1) {
      flow.moveTo(otherwise);
      statements(blocks.get(1));
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
  }

  private void tryStatement(final SyntaxNode node) {
    final List<SyntaxNode> blocks = children(node, BLOCK);
    final List<SyntaxNode> handlers = children(node, HANDLER);
    final boolean hasElse = hasToken(node, ELSE);
    final boolean hasFinally = hasToken(node, FINALLY);
    final int elseIndex = handlers.size() + 1;
    flow.tries().pushTry(!handlers.isEmpty(), hasFinally);
    statements(blocks.get(0));
    if (hasElse) {
      statements(blocks.get(elseIndex));
    }
    flow.tries().endTryBody();
    for (int index = 0; index < handlers.size(); index++) {
      flow.tries().beginCatch();
      simple(handlers.get(index), false);
      statements(blocks.get(index + 1));
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    if (hasFinally) {
      statements(blocks.get(elseIndex + (hasElse ? 1 : 0)));
    }
    flow.tries().popTry();
  }

  /**
   * A {@code with} runs its items and body in order; its bindings hold after it, as Pylint assumes.
   */
  private void withStatement(final SyntaxNode node) {
    for (final SyntaxNode item : children(node, ITEM)) {
      simple(item, mayThrow(item));
    }
    statements(children(node, BLOCK).get(0));
  }

  private void matchStatement(final SyntaxNode node) {
    final SyntaxNode subject = children(node, "subject_expr").get(0);
    simple(subject, mayThrow(subject));
    final int after = flow.newBlock();
    boolean exhaustive = false;
    int test = flow.current();
    for (final SyntaxNode caseBlock : children(node, CASE)) {
      final int body = flow.newBlock();
      final boolean catchAll = isCatchAll(caseBlock);
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
    simple(children(caseBlock, PATTERNS).get(0), false);
    final List<SyntaxNode> guards = children(caseBlock, GUARD);
    if (!guards.isEmpty()) {
      simple(guards.get(0), mayThrow(guards.get(0)));
    }
    statements(children(caseBlock, BLOCK).get(0));
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
  }

  /** A case that always matches: a single identifier pattern with no guard. */
  private static boolean isCatchAll(final SyntaxNode caseBlock) {
    if (!children(caseBlock, GUARD).isEmpty()) {
      return false;
    }
    final List<SyntaxNode> tokens = PythonSyntax.tokens(children(caseBlock, PATTERNS).get(0));
    if (tokens.size() != 1) {
      return false;
    }
    final String text = PythonSyntax.text(tokens.get(0));
    return text.matches("[A-Za-z_]\\w*") && !NON_IDENTIFIERS.contains(text);
  }

  /** A condition that is always true, so the loop never leaves through its condition. */
  private static boolean isAlwaysTrue(final SyntaxNode condition) {
    final List<SyntaxNode> tokens = PythonSyntax.tokens(condition);
    return tokens.size() == 1
        && (ALWAYS_TRUE.equals(PythonSyntax.text(tokens.get(0)))
            || ALWAYS_TRUE_ONE.equals(PythonSyntax.text(tokens.get(0))));
  }

  /** A call or an await may raise, so an enclosing try can catch it. */
  static boolean mayThrow(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      final String text = PythonSyntax.text(node);
      return CALL.equals(text) || AWAIT.equals(text);
    }
    return node.children().stream().anyMatch(PythonBodyLowering::mayThrow);
  }

  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return PythonSyntax.children(node, kind);
  }

  private static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(PythonSyntax.text(child)));
  }
}
