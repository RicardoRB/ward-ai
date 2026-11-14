package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;

/**
 * Lowers the statements of one Kotlin body into a control-flow graph. A statement that does not
 * split the graph is one node; {@code if} and the jumps are lowered here, loops and the {@code try}
 * and {@code when} statements by their own classes, which share this body's builder. An {@code if},
 * {@code when} or {@code try} that is used as a value stays inside its statement.
 */
final class KotlinBodyLowering implements KotlinStatements {

  private static final String EXPRESSION = "expression";
  private static final String BODY = "controlStructureBody";
  private static final String BLOCK = "block";
  private static final String RETURN = "return";
  private static final String AT = "@";
  private static final List<String> LEAVING_DECLARATIONS =
      List.of("functionDeclaration", "classDeclaration", "typeAlias");

  private final KotlinFlowSites sites;
  private final ControlFlowBuilder flow;
  private final KotlinLoopLowering loops;
  private final KotlinHandlerLowering handlers;

  /**
   * Starts the lowering of one body.
   *
   * @param accesses finds the variable accesses of each node
   * @param sites finds the flow sites of each node
   */
  KotlinBodyLowering(final KotlinAccesses accesses, final KotlinFlowSites sites) {
    this.sites = sites;
    this.flow = new ControlFlowBuilder(accesses, sites);
    this.loops = new KotlinLoopLowering(flow, this);
    this.handlers = new KotlinHandlerLowering(flow, this);
  }

  /**
   * Lowers one body.
   *
   * @param name the name of the body
   * @param entry the parameter accesses, bound at the entry of the body
   * @param parameters the parameter list, or null when the body has none
   * @param body a block, a function body with an expression, or null for a body without code
   * @param span where the body is in the source
   * @return the graph of the body
   */
  ControlFlowGraph lower(
      final String name,
      final List<VariableAccess> entry,
      final SyntaxNode parameters,
      final SyntaxNode body,
      final Span span) {
    flow.record(entry, parameters == null ? List.of() : sites.of(parameters));
    if (body != null) {
      lowerBody(body);
    }
    return flow.build(name, span);
  }

  private void lowerBody(final SyntaxNode body) {
    if (BLOCK.equals(body.kind())) {
      block(body);
      return;
    }
    final SyntaxNode value = KotlinFlowSyntax.child(body, EXPRESSION);
    final SyntaxNode returned =
        new SyntaxNode(
            "returnStatement",
            body.span(),
            Map.of(),
            List.of(KotlinFlowSyntax.parts(body).get(0), value));
    flow.add(returned, KotlinFlowSyntax.mayThrow(value));
    flow.tries().returnFrom();
  }

  @Override
  public void block(final SyntaxNode block) {
    final SyntaxNode list = KotlinFlowSyntax.child(block, "statements");
    if (list != null) {
      KotlinFlowSyntax.children(list, "statement").forEach(this::statement);
    }
  }

  @Override
  public void controlBody(final SyntaxNode body) {
    final SyntaxNode block = KotlinFlowSyntax.child(body, BLOCK);
    if (block != null) {
      block(block);
    } else {
      statement(body);
    }
  }

  @Override
  public void statement(final SyntaxNode node) {
    final SyntaxNode construct = KotlinFlowSyntax.unwrap(node);
    final String label = KotlinFlowSyntax.labelOf(node);
    switch (construct.kind()) {
      case "declaration" -> declaration(construct);
      case "ifExpression" -> ifStatement(construct);
      case "whenExpression" -> handlers.whenStatement(construct);
      case "tryExpression" -> handlers.tryStatement(construct);
      case "whileExpression" -> loops.whileLoop(construct, label);
      case "doWhileExpression" -> loops.doWhileLoop(construct, label);
      case "forExpression" -> loops.forLoop(construct, label);
      case "jumpExpression" -> jump(construct);
      default -> flow.add(construct, KotlinFlowSyntax.mayThrow(construct));
    }
  }

  /** A property is a node; a nested function, class or alias is another body, so it is skipped. */
  private void declaration(final SyntaxNode node) {
    final boolean other =
        KotlinFlowSyntax.parts(node).stream()
            .anyMatch(part -> LEAVING_DECLARATIONS.contains(part.kind()));
    if (!other) {
      flow.add(node, KotlinFlowSyntax.mayThrow(node));
    }
  }

  private void ifStatement(final SyntaxNode node) {
    final SyntaxNode condition = KotlinFlowSyntax.child(node, EXPRESSION);
    SyntaxNode thenBody = null;
    SyntaxNode elseBody = null;
    boolean seenElse = false;
    for (final SyntaxNode part : KotlinFlowSyntax.parts(node)) {
      if ("else".equals(KotlinFlowSyntax.tokenText(part))) {
        seenElse = true;
      } else if (BODY.equals(part.kind())) {
        if (seenElse) {
          elseBody = part;
        } else {
          thenBody = part;
        }
      }
    }
    final boolean hasElse = seenElse;
    final int thenBlock = flow.newBlock();
    final int after = flow.newBlock();
    final int elseBlock = hasElse ? flow.newBlock() : after;
    flow.add(condition, KotlinFlowSyntax.mayThrow(condition));
    flow.branch(thenBlock, elseBlock);
    flow.moveTo(thenBlock);
    run(thenBody);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    if (hasElse) {
      flow.moveTo(elseBlock);
      run(elseBody);
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
  }

  private void run(final SyntaxNode body) {
    if (body != null) {
      controlBody(body);
    }
  }

  /** Lowers {@code return}, {@code throw}, {@code break} and {@code continue}. */
  private void jump(final SyntaxNode node) {
    final String keyword = KotlinFlowSyntax.tokenText(KotlinFlowSyntax.parts(node).get(0));
    if (RETURN.equals(keyword)) {
      flow.add(node, KotlinFlowSyntax.mayThrow(node));
      flow.tries().returnFrom();
    } else if ("throw".equals(keyword)) {
      flow.add(node, true);
      flow.tries().throwOut();
    } else if (keyword.startsWith("break")) {
      flow.add(node, false);
      flow.jumps().breakTo(labelOf(keyword));
    } else if (keyword.startsWith("continue")) {
      flow.add(node, false);
      flow.jumps().continueTo(labelOf(keyword));
    } else {
      // A labelled return leaves an inlined lambda, not this body.
      flow.add(node, KotlinFlowSyntax.mayThrow(node));
    }
  }

  private static String labelOf(final String keyword) {
    final int at = keyword.indexOf(AT);
    return at < 0 ? null : keyword.substring(at + 1);
  }
}
