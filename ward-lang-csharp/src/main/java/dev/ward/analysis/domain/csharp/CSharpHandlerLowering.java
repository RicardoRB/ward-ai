package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Lowers the {@code switch} and {@code try} statements of one C# body. */
final class CSharpHandlerLowering {
  private static final String BLOCK = "block";
  private static final String STATEMENT_LIST = "statement_list";

  private final ControlFlowBuilder flow;
  private final Consumer<SyntaxNode> statements;

  CSharpHandlerLowering(final ControlFlowBuilder flow, final Consumer<SyntaxNode> statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void register(final Map<String, Consumer<SyntaxNode>> handlers) {
    handlers.put("switch_statement", this::switchStatement);
    handlers.put("try_statement", this::tryStatement);
  }

  private void switchStatement(final SyntaxNode node) {
    CSharpSyntax.child(node, "selector_expression").ifPresent(found -> flow.add(found, true));
    final int test = flow.current();
    final int after = flow.newBlock();
    final List<SyntaxNode> sections =
        CSharpSyntax.child(node, "switch_block")
            .map(block -> CSharpSyntax.children(block, "switch_section"))
            .orElse(List.of());
    flow.jumps().pushSwitch(after);
    boolean exhaustive = false;
    for (final SyntaxNode section : sections) {
      exhaustive |= hasDefaultLabel(section);
      section(section, test, after);
    }
    flow.jumps().popFrame();
    if (!exhaustive) {
      flow.edge(test, after, FlowEdgeKind.FALSE);
    }
    flow.moveTo(after);
  }

  private void section(final SyntaxNode section, final int test, final int after) {
    final int entry = flow.newBlock();
    flow.edge(test, entry, FlowEdgeKind.TRUE);
    flow.moveTo(entry);
    CSharpSyntax.children(section, "switch_label").forEach(label -> flow.add(label, false));
    CSharpSyntax.child(section, STATEMENT_LIST).ifPresent(statements);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
  }

  private static boolean hasDefaultLabel(final SyntaxNode section) {
    return CSharpSyntax.children(section, "switch_label").stream()
        .anyMatch(label -> CSharpSyntax.hasToken(label, "default"));
  }

  private void tryStatement(final SyntaxNode node) {
    final List<SyntaxNode> catches = catches(node);
    final Optional<SyntaxNode> cleanup = CSharpSyntax.child(node, "finally_clause");
    flow.tries().pushTry(!catches.isEmpty(), cleanup.isPresent());
    CSharpSyntax.child(node, BLOCK).ifPresent(statements);
    flow.tries().endTryBody();
    for (final SyntaxNode handler : catches) {
      flow.tries().beginCatch();
      CSharpSyntax.child(handler, BLOCK).ifPresent(statements);
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    cleanup.flatMap(clause -> CSharpSyntax.child(clause, BLOCK)).ifPresent(statements);
    flow.tries().popTry();
  }

  private static List<SyntaxNode> catches(final SyntaxNode node) {
    return CSharpSyntax.child(node, "catch_clauses")
        .map(
            clauses ->
                clauses.children().stream()
                    .filter(clause -> !clause.children().isEmpty())
                    .toList()
        )
        .orElse(List.of());
  }
}
