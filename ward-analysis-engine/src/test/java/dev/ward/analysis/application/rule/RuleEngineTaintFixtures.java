package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.check.CheckedRule;
import dev.ward.analysis.domain.rule.check.TaintCheck;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A taint rule and the graph it reads, for the rule engine's data-flow tests. */
final class RuleEngineTaintFixtures {

  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);
  private static final String MESSAGE = "untrusted {source} reaches {sink}";

  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "source", Optional.empty(), Optional.empty(), List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "sink", Optional.empty(), Optional.empty(), List.of(0))),
          List.of(),
          List.of());

  private RuleEngineTaintFixtures() {}

  /** Compiles every rule to the taint check over the fixture specification. */
  static RuleMatcher matcher() {
    return RuleEngineTaintFixtures::taintRule;
  }

  private static CompiledRule taintRule(final RuleDefinition definition) {
    return new CheckedRule(definition, new TaintCheck(SPEC, MESSAGE));
  }

  /** The source call writes x in the first statement; the sink reads x in its argument. */
  static ControlFlowGraph graph() {
    final Span declaration = new Span(1, 1, 1, 2, 0, 1);
    final Span write = new Span(1, 1, 1, 2, 0, 1);
    final Span source = new Span(1, 5, 1, 11, 4, 10);
    final Span read = new Span(2, 11, 2, 12, 16, 17);
    final Span sink = new Span(2, 5, 2, 15, 11, 20);
    final SyntaxNode first =
        new SyntaxNode("statement", new Span(1, 1, 1, 11, 0, 10), Map.of(), List.of());
    final SyntaxNode second =
        new SyntaxNode("statement", new Span(2, 1, 2, 16, 11, 20), Map.of(), List.of());
    final BasicBlock body =
        new BasicBlock(
            0,
            List.of(first, second),
            List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)),
            List.of(
                new VariableAccess("x", AccessKind.WRITE, write, declaration),
                new VariableAccess("x", AccessKind.READ, read, declaration)),
            List.of(
                new FlowSite(
                    FlowSiteKind.CALL,
                    "source",
                    "",
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    source),
                new FlowSite(
                    FlowSiteKind.CALL,
                    "sink",
                    "",
                    Optional.empty(),
                    List.of(),
                    List.of(read),
                    sink)));
    final BasicBlock exit = new BasicBlock(1, List.of(), List.of());
    return new ControlFlowGraph("taint", AT, List.of(body, exit), 0, 1);
  }
}
