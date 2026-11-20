package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.ir.VariableAccesses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lowers the statements of one C# body into a control-flow graph. A nested function is a body of
 * its own, so it is left out, and a lambda is part of the statement that holds it. Loops, jumps,
 * and the switch and try statements are lowered by their own classes, which share this body's
 * builder. {@code goto} is a step that does not move control to its label. shortcut: an exception
 * filter and a {@code when} guard are steps, not branches; upgrade when a rule needs their paths.
 */
final class CSharpBodyLowering {
  private static final Set<String> WRAPPERS =
      Set.of(
          "statement",
          "embedded_statement",
          "selection_statement",
          "iteration_statement",
          "jump_statement",
          "method_body",
          "accessor_body",
          "statement_list",
          "global_statement"
      );
  private static final Set<String> GUARDED =
      Set.of(
          "lock_statement",
          "using_statement",
          "checked_statement",
          "unchecked_statement",
          "unsafe_statement",
          "fixed_statement"
      );
  private static final String BLOCK = "block";
  private static final int THEN = 0;
  private static final int ELSE = 1;

  private final ControlFlowBuilder flow;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();

  /**
   * Starts the lowering of one body.
   *
   * @param accesses the finder of the variable accesses of the body
   * @param sites the finder of the flow sites of the body
   */
  CSharpBodyLowering(final VariableAccesses accesses, final FlowSites sites) {
    flow = new ControlFlowBuilder(accesses, sites);
    final Consumer<SyntaxNode> statements = this::statement;
    final CSharpLoopLowering loops = new CSharpLoopLowering(flow, statements);
    final CSharpHandlerLowering handling = new CSharpHandlerLowering(flow, statements);
    final CSharpJumpLowering jumps = new CSharpJumpLowering(flow);
    handlers.put(BLOCK, this::block);
    handlers.put("if_statement", this::ifStatement);
    handlers.put("labeled_statement", this::labeled);
    handlers.put("declaration_statement", this::declaration);
    handlers.put("empty_statement", node -> {});
    handlers.put("local_function_declaration", node -> {});
    GUARDED.forEach(kind -> handlers.put(kind, this::guarded));
    loops.register(handlers);
    handling.register(handlers);
    jumps.register(handlers);
  }

  /**
   * Lowers {@code body}, a block or an expression, into the graph named {@code name}.
   *
   * @param entry the parameter accesses, bound at the entry
   */
  ControlFlowGraph lower(
      final String name,
      final List<VariableAccess> entry,
      final SyntaxNode body,
      final Span span
  ) {
    flow.record(entry, List.<FlowSite> of());
    statement(body);
    return flow.build(name, span);
  }

  private void statement(final SyntaxNode node) {
    final Consumer<SyntaxNode> handler = handlers.get(node.kind());
    if (handler != null) {
      handler.accept(node);
    } else if (WRAPPERS.contains(node.kind())) {
      node.children().forEach(this::statement);
    } else if (!node.children().isEmpty()) {
      flow.add(node, true);
    }
  }

  private void block(final SyntaxNode block) {
    CSharpSyntax.child(block, "statement_list").ifPresent(this::statement);
  }

  private void declaration(final SyntaxNode node) {
    if (CSharpSyntax.child(node, "local_function_declaration").isEmpty()) {
      flow.add(node, true);
    }
  }

  private void labeled(final SyntaxNode node) {
    CSharpSyntax.child(node, "statement").ifPresent(this::statement);
  }

  private void guarded(final SyntaxNode node) {
    final List<SyntaxNode> bodies = CSharpLoopLowering.bodies(node);
    node.children().stream()
        .filter(child -> !bodies.contains(child) && !child.children().isEmpty())
        .forEach(child -> flow.add(child, true));
    bodies.forEach(this::statement);
  }

  private void ifStatement(final SyntaxNode node) {
    final int then = flow.newBlock();
    final int otherwise = flow.newBlock();
    final int after = flow.newBlock();
    CSharpLoopLowering.condition(flow, node);
    flow.branch(then, otherwise);
    final List<SyntaxNode> bodies = CSharpSyntax.children(node, "embedded_statement");
    flow.moveTo(then);
    statement(bodies.get(THEN));
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(otherwise);
    if (bodies.size() > ELSE) {
      statement(bodies.get(ELSE));
    }
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }
}
