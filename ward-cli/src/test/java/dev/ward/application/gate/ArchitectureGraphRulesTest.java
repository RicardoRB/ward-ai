package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.graph.IndependenceRule;
import dev.ward.analysis.domain.rule.graph.SliceCycleRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The findings the architecture graph rules report over a dependency graph. */
class ArchitectureGraphRulesTest {

  private static final String ROOT = "src/main/java/com/acme";
  private static final int IMPORT_LINE = 7;
  private static final int SECOND_LINE = 5;

  @Test
  void reportsASliceCycleAsAnErrorFindingWithItsPath() {
    final DependencyGraph graph =
        new DependencyGraph(
            List.of(
                edge(ROOT + "/orders/Order.java", ROOT + "/billing/Invoice.java", 3),
                edge(ROOT + "/billing/Invoice.java", ROOT + "/orders/Order.java", SECOND_LINE)));

    final List<AnalysisFinding> findings =
        ArchitectureGraphRules.findings(
            List.of(ArchitectureGraphRules.cycles(List.of(ROOT))), graph);

    assertThat(findings).hasSize(1);
    final AnalysisFinding finding = findings.getFirst();
    assertThat(finding.ruleId()).isEqualTo(SliceCycleRule.ID);
    assertThat(finding.severity()).isEqualTo(AnalysisSeverity.ERROR);
    assertThat(finding.file()).isEqualTo(ROOT + "/billing/Invoice.java");
    assertThat(finding.line()).isEqualTo(SECOND_LINE);
    assertThat(finding.path()).hasSize(2);
  }

  @Test
  void reportsAnIndependenceImportAtItsLine() {
    final DependencyGraph graph =
        new DependencyGraph(
            List.of(edge("shop/orders/A.java", "shop/billing/B.java", IMPORT_LINE)));

    final List<AnalysisFinding> findings =
        ArchitectureGraphRules.findings(
            List.of(
                ArchitectureGraphRules.independence(
                    List.of(List.of("shop/orders", "shop/billing")))),
            graph);

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().ruleId()).isEqualTo(IndependenceRule.ID);
    assertThat(findings.getFirst().message())
        .isEqualTo("shop/orders must not depend on shop/billing");
    assertThat(findings.getFirst().line()).isEqualTo(IMPORT_LINE);
  }

  @Test
  void ignoresRulesThatAreNotGraphRules() {
    final RuleDefinition other = ArchitectureGraphRules.cycles(List.of(ROOT));
    final DependencyGraph graph = new DependencyGraph(List.of());

    assertThat(ArchitectureGraphRules.findings(List.of(other), graph)).isEmpty();
  }

  private static DependencyEdge edge(final String from, final String to, final int line) {
    final Span span = new Span(line, 1, line, 2, line * 100, line * 100 + 1);
    return new DependencyEdge(from, to, new Reference(to, span));
  }
}
