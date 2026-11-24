package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers the statements of one Python body into a control-flow graph. A nested function or class is
 * one opaque node here; its own body is lowered separately. Loops and the exception and match
 * statements are lowered by their own classes, which share this body's builder.
 */
final class PythonBodyLowering {
  private static final String BLOCK = "block";
  private static final String NAMED_EXPR = "namedexpr_test";
  private static final Set<String> DEFINITIONS = Set.of("funcdef", "classdef", "decorated");

  private final PythonAccesses accesses;
  private final ControlFlowBuilder flow;
  private final PythonLoopLowering loops;
  private final PythonHandlerLowering handlers;
  private final Map<String, String> imports;
  private final Set<String> collections;
  private final PythonGuardRegions regions;

  /**
   * Starts the lowering of one body.
   *
   * @param excluded the names the body declares {@code global} or {@code nonlocal}
   * @param tracked false when a star import makes every name of the body possibly bound elsewhere
   * @param facts what the whole file says that the body needs
   */
  PythonBodyLowering(
      final Set<String> excluded,
      final boolean tracked,
      final PythonFileFacts facts
  ) {
    this.imports = facts.imports();
    this.collections = facts.collections();
    this.accesses = new PythonAccesses(excluded, tracked);
    final PythonValidatedSites validated =
        new PythonValidatedSites(imports, facts.module(), facts.functions());
    this.regions = new PythonGuardRegions(validated);
    this.flow = new ControlFlowBuilder(accesses, new PythonFlowSites(imports, validated));
    this.loops = new PythonLoopLowering(flow, accesses, this::statements);
    this.handlers = new PythonHandlerLowering(flow, this::statements);
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
      final PythonParameters parameters,
      final Span span
  ) {
    accesses.declare(parameters.accesses());
    flow.record(parameters.accesses(), parameters.sites());
    flow.record(
        List.of(),
        PythonHostValidators.marks(
            block,
            parameters.accesses().stream()
                .filter(access -> access.kind() == AccessKind.PARAMETER)
                .map(VariableAccess::name)
                .toList()
        )
    );
    statements(block);
    return flow.build(name, span);
  }

  /** Lowers every statement under a block or a file, in source order. */
  private void statements(final SyntaxNode block) {
    int entered = 0;
    final List<SyntaxNode> leaves = PythonStatementLeaves.leaves(block);
    for (int index = 0; index < leaves.size(); index++) {
      statement(leaves.get(index));
      if (regions.enterCarried(leaves.subList(index + 1, leaves.size()))) {
        entered++;
      }
    }
    for (int leave = 0; leave < entered; leave++) {
      regions.leave();
    }
  }

  private void statement(final SyntaxNode node) {
    switch (node.kind()) {
      case "break_stmt", "continue_stmt", "return_stmt", "raise_stmt" -> jump(node);
      default -> {
        if (PythonNoReturnCalls.isNoReturn(node, imports)) {
          simple(node, true);
          flow.tries().throwOut();
        } else {
          compound(node);
        }
      }
    }
  }

  /** Lowers a statement that splits the graph, or adds any other statement as one step. */
  private void compound(final SyntaxNode node) {
    switch (node.kind()) {
      case "if_stmt" -> ifStatement(node);
      case "while_stmt" -> loops.whileStatement(node);
      case "for_stmt" -> loops.forStatement(node);
      case "try_stmt" -> handlers.tryStatement(node);
      case "with_stmt" -> handlers.withStatement(node);
      case "match_stmt" -> handlers.matchStatement(node);
      default -> {
        simple(node, !DEFINITIONS.contains(node.kind()) && PythonFlowSyntax.mayThrow(node));
        PythonFixedLookups.target(node, collections)
            .ifPresent(name -> clean(name, after(node.span())));
      }
    }
  }

  /** A statement that leaves the block: a loop jump, a return, or a raise. */
  private void jump(final SyntaxNode node) {
    switch (node.kind()) {
      case "break_stmt" -> {
        simple(node, false);
        flow.jumps().breakTo(null);
      }
      case "continue_stmt" -> {
        simple(node, false);
        flow.jumps().continueTo(null);
      }
      case "return_stmt" -> {
        simple(node, PythonFlowSyntax.mayThrow(node));
        flow.tries().returnFrom();
      }
      default -> {
        simple(node, true);
        flow.tries().throwOut();
      }
    }
  }

  private void simple(final SyntaxNode node, final boolean mayThrow) {
    flow.add(node, mayThrow);
  }

  private void ifStatement(final SyntaxNode node) {
    final List<SyntaxNode> conditions = PythonFlowSyntax.children(node, NAMED_EXPR);
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    final int after = flow.newBlock();
    for (int index = 0; index < conditions.size(); index++) {
      simple(conditions.get(index), PythonFlowSyntax.mayThrow(conditions.get(index)));
      final int then = flow.newBlock();
      final int next = flow.newBlock();
      flow.branch(then, next);
      flow.moveTo(then);
      final SyntaxNode condition = conditions.get(index);
      PythonMembershipGuards.inside(condition, collections).ifPresent(
          name -> clean(name, condition)
      );
      regions.enterInside(condition, blocks.get(index));
      statements(blocks.get(index));
      regions.leave();
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
      flow.moveTo(next);
    }
    if (blocks.size() > conditions.size()) {
      regions.enterElse(
          conditions.get(conditions.size() - 1),
          blocks.get(conditions.size()),
          conditions.size() == 1
      );
      statements(blocks.get(conditions.size()));
      regions.leave();
    }
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
    if (conditions.size() == 1 && blocks.size() == 1) {
      PythonMembershipGuards.past(conditions.get(0), blocks.get(0), imports, collections)
          .ifPresent(name -> clean(name, node));
      if (PythonMembershipGuards.exits(blocks.get(0), imports)) {
        regions.carry(conditions.get(0));
      }
    }
  }

  /** A span just past a statement, which no statement contains, so its write takes no taint. */
  private static Span after(final Span statement) {
    return new Span(
        statement.endLine(),
        statement.endColumn(),
        statement.endLine(),
        statement.endColumn() + 1,
        statement.endOffset(),
        statement.endOffset() + 1
    );
  }

  private void clean(final String name, final SyntaxNode guard) {
    clean(name, guard.span());
  }

  private void clean(final String name, final Span span) {
    final List<VariableAccess> clean = accesses.clean(name, span);
    if (!clean.isEmpty()) {
      flow.record(clean, List.of());
    }
  }
}
