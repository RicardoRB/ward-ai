package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;

/**
 * Lowers the statements of one Swift body into a control-flow graph. A nested function or type is
 * another body, so it is left out here, and a closure is part of the statement that holds it. Loops
 * and jumps, and the switch and do statements, are lowered by their own classes, which share this
 * body's builder. shortcut: names bound by a {@code case} pattern or a {@code where} clause take no
 * taint from the subject; upgrade by lowering each case with its subject.
 */
final class SwiftBodyLowering {
  private static final String CODE_BLOCK = "code_block";
  private static final String CONDITION_LIST = "condition_list";

  private final ControlFlowBuilder flow;
  private final SwiftConditions conditions;
  private final SwiftLoopLowering loops;
  private final SwiftHandlerLowering handlers;
  private final SwiftJumpLowering jumps;

  /**
   * Starts the lowering of one body.
   *
   * @param accesses the finder of the variable accesses of the body
   * @param sites the finder of the flow sites of the body
   */
  SwiftBodyLowering(final SwiftAccesses accesses, final SwiftFlowSites sites) {
    this.flow = new ControlFlowBuilder(accesses, sites);
    this.conditions = new SwiftConditions(flow);
    this.loops = new SwiftLoopLowering(flow, conditions, this::statements);
    this.handlers = new SwiftHandlerLowering(flow, this::statements);
    this.jumps = new SwiftJumpLowering(flow);
  }

  /**
   * Lowers a body.
   *
   * @param name the name of the body
   * @param entry the parameter accesses, bound at the entry
   * @param block the code block of the body
   * @param span where the body is in the source
   * @return the graph of the body
   */
  ControlFlowGraph lower(
      final String name,
      final List<VariableAccess> entry,
      final SyntaxNode block,
      final Span span
  ) {
    flow.record(entry, List.<FlowSite> of());
    statements(block);
    return flow.build(name, span);
  }

  private void statements(final SyntaxNode block) {
    SwiftFlowSyntax.statements(block).forEach(this::statement);
  }

  private void statement(final SyntaxNode statement) {
    final SyntaxNode inner = statement.children().get(0);
    switch (inner.kind()) {
      case "branch_statement" -> branch(inner.children().get(0));
      case "loop_statement" -> loops.loop(inner.children().get(0), null);
      case "labeled_statement" -> labeled(inner);
      case "control_transfer_statement" -> jumps.transfer(inner.children().get(0));
      case "do_statement" -> handlers.doStatement(inner);
      case "declaration" -> declaration(inner);
      case "expression" -> SwiftFlowSyntax.step(flow, inner);
      default -> {
        // A defer block and a compiler directive add no step.
      }
    }
  }

  private void declaration(final SyntaxNode node) {
    final boolean body =
        node.children().stream()
            .anyMatch(child -> SwiftFlowSyntax.OTHER_BODIES.contains(child.kind()));
    if (!body) {
      SwiftFlowSyntax.step(flow, node);
    }
  }

  private void labeled(final SyntaxNode node) {
    final String label = SwiftFlowSyntax.text(SwiftFlowSyntax.child(node, "statement_label"));
    final SyntaxNode inner = node.children().get(1);
    final String name = label.endsWith(":") ? label.substring(0, label.length() - 1) : label;
    switch (inner.kind()) {
      case "loop_statement" -> loops.loop(inner.children().get(0), name);
      case "if_statement" -> ifStatement(inner);
      case "switch_statement" -> handlers.switchStatement(inner);
      default -> handlers.doStatement(inner);
    }
  }

  private void branch(final SyntaxNode node) {
    switch (node.kind()) {
      case "if_statement" -> ifStatement(node);
      case "guard_statement" -> guardStatement(node);
      default -> handlers.switchStatement(node);
    }
  }

  private void ifStatement(final SyntaxNode node) {
    final int after = flow.newBlock();
    final int otherwise = flow.newBlock();
    conditions.add(SwiftFlowSyntax.child(node, CONDITION_LIST), otherwise);
    statements(SwiftFlowSyntax.child(node, CODE_BLOCK));
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(otherwise);
    final SyntaxNode clause = SwiftFlowSyntax.child(node, "else_clause");
    if (clause != null) {
      final SyntaxNode chained = SwiftFlowSyntax.child(clause, "if_statement");
      if (chained == null) {
        statements(SwiftFlowSyntax.child(clause, CODE_BLOCK));
      } else {
        ifStatement(chained);
      }
    }
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  private void guardStatement(final SyntaxNode node) {
    final int fail = flow.newBlock();
    conditions.add(SwiftFlowSyntax.child(node, CONDITION_LIST), fail);
    final int pass = flow.current();
    flow.moveTo(fail);
    statements(SwiftFlowSyntax.child(node, CODE_BLOCK));
    flow.moveTo(pass);
  }
}
