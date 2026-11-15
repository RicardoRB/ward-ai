package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;

/**
 * Lowers the statements of one Kotlin body into a control-flow graph. A statement that does not
 * split the graph is one node; the jumps are lowered by {@link KotlinJumpLowering}, the branches,
 * handlers, loops and operators by {@link KotlinConstructLowering}, and a statement that holds a
 * lambda or a branching value by {@link KotlinValueLowering}.
 */
final class KotlinBodyLowering implements KotlinStatements {

  private static final String EXPRESSION = "expression";
  private static final String BLOCK = "block";
  private static final List<String> LEAVING_DECLARATIONS =
      List.of("functionDeclaration", "classDeclaration", "typeAlias");

  private final KotlinFlowSites sites;
  private final ControlFlowBuilder flow;
  private final KotlinValueLowering values;
  private final KotlinConstructLowering constructs;
  private final KotlinJumpLowering jumps;

  /**
   * Starts the lowering of one body.
   *
   * @param accesses finds the variable accesses of each node
   * @param sites finds the flow sites of each node
   * @param view narrows what the finders report for the part of a statement being lowered
   */
  KotlinBodyLowering(
      final KotlinAccesses accesses, final KotlinFlowSites sites, final KotlinView view) {
    this.sites = sites;
    this.flow = new ControlFlowBuilder(accesses, sites);
    this.values = new KotlinValueLowering(flow, view, this);
    this.jumps = new KotlinJumpLowering(flow, values);
    this.constructs = new KotlinConstructLowering(flow, values, this);
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
    values.expression(returned, KotlinFlowSyntax.mayThrow(value), null);
    flow.tries().returnFrom();
  }

  @Override
  public void block(final SyntaxNode block, final KotlinResult result) {
    final SyntaxNode list = KotlinFlowSyntax.child(block, "statements");
    if (list != null) {
      tail(list, result);
    }
  }

  @Override
  public void controlBody(final SyntaxNode body, final KotlinResult result) {
    final SyntaxNode block = KotlinFlowSyntax.child(body, BLOCK);
    if (block != null) {
      block(block, result);
    } else {
      statement(body, result);
    }
  }

  @Override
  public void expression(final SyntaxNode node, final boolean mayThrow) {
    values.expression(node, mayThrow, null);
  }

  @Override
  public void tail(final SyntaxNode list, final KotlinResult result) {
    final List<SyntaxNode> all = KotlinFlowSyntax.children(list, "statement");
    for (int index = 0; index < all.size(); index++) {
      statement(all.get(index), index == all.size() - 1 ? result : null);
    }
  }

  @Override
  public void statement(final SyntaxNode node) {
    statement(node, null);
  }

  @Override
  public void statement(final SyntaxNode node, final KotlinResult result) {
    final SyntaxNode construct = KotlinFlowSyntax.unwrap(node);
    final String label = KotlinLabels.of(node);
    switch (construct.kind()) {
      case "declaration" -> declaration(construct);
      case "jumpExpression" -> jumps.jump(construct);
      default -> constructs.lower(construct, label, result);
    }
  }

  /** A property is a node; a nested function, class or alias is another body, so it is skipped. */
  private void declaration(final SyntaxNode node) {
    final boolean other =
        KotlinFlowSyntax.parts(node).stream()
            .anyMatch(part -> LEAVING_DECLARATIONS.contains(part.kind()));
    if (!other) {
      values.expression(node, KotlinFlowSyntax.mayThrow(node), null);
    }
  }
}
