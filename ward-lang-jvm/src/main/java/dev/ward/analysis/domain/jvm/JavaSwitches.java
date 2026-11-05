package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.mayThrow;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.parts;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.text;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Lowers a Java switch statement or switch expression. A clause that does not end in an arrow falls
 * through to the next clause; an arrow clause ends at the block after the switch.
 */
final class JavaSwitches {

  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String SWITCH_GROUP = "switchBlockStatementGroup";
  private static final String SWITCH_RULE = "switchLabeledRule";
  private static final String SWITCH_LABEL = "switchLabel";
  private static final String ARROW = "->";
  private static final String DEFAULT = "default";
  private static final int SELECTOR_INDEX = 2;
  private static final int CLAUSES_START = 5;
  private static final Set<String> CLAUSES = Set.of(SWITCH_GROUP, SWITCH_RULE, SWITCH_LABEL);

  private final ControlFlowBuilder flow;
  private final JavaStatements statements;

  JavaSwitches(final ControlFlowBuilder flow, final JavaStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  /** Lowers a switch whose parts start with the {@code switch} keyword. */
  boolean switchOn(final List<SyntaxNode> parts) {
    final SyntaxNode selector = parts.get(SELECTOR_INDEX);
    flow.add(selector, mayThrow(selector));
    final int head = flow.current();
    final int after = flow.newBlock();
    flow.jumps().pushSwitch(after);
    boolean hasDefault = false;
    boolean fallsIn = false;
    for (final SyntaxNode clause : parts.subList(CLAUSES_START, parts.size() - 1)) {
      if (!CLAUSES.contains(clause.kind())) {
        continue;
      }
      final int entry = flow.newBlock();
      flow.edge(head, entry, FlowEdgeKind.NORMAL);
      if (fallsIn) {
        flow.edge(flow.current(), entry, FlowEdgeKind.NORMAL);
      }
      flow.moveTo(entry);
      hasDefault |= isDefault(clause);
      final boolean arrow = isArrow(clause);
      clauseStatements(clause);
      if (arrow) {
        flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
      }
      fallsIn = !arrow;
    }
    flow.jumps().popFrame();
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    if (!hasDefault) {
      flow.edge(head, after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
    return true;
  }

  private void clauseStatements(final SyntaxNode clause) {
    if (SWITCH_RULE.equals(clause.kind())) {
      final SyntaxNode outcome = JavaFlowSyntax.firstOf(parts(clause), "switchRuleOutcome");
      final SyntaxNode block = JavaFlowSyntax.firstOf(parts(outcome), BLOCK);
      if (block == null) {
        for (final SyntaxNode statement : JavaFlowSyntax.allOf(parts(outcome), BLOCK_STATEMENT)) {
          statements.blockStatement(statement);
        }
      } else {
        statements.blockStatements(block);
      }
    } else if (SWITCH_GROUP.equals(clause.kind())) {
      for (final SyntaxNode statement : JavaFlowSyntax.allOf(parts(clause), BLOCK_STATEMENT)) {
        statements.blockStatement(statement);
      }
    }
  }

  private static boolean isDefault(final SyntaxNode clause) {
    for (final SyntaxNode part : parts(clause)) {
      if (DEFAULT.equals(text(part))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isArrow(final SyntaxNode clause) {
    for (final SyntaxNode part : parts(clause)) {
      if (ARROW.equals(text(part))) {
        return true;
      }
    }
    return false;
  }
}
