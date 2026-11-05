package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Taint on TypeScript call statements that the grammar parses as variable statements. */
class NodeStatementCallTaintTest {

  /**
   * Scenario: an unqualified sink both starts a method body and follows a const. Each finding has
   * its full path: the parameter source then the sink, and the source, the assignment, the sink.
   */
  @Test
  void unqualifiedSinksAtTheStartOfABodyAndAfterAConstAreBothFound() {
    final String source =
        "class Handler {\n  handle(@Untrusted input: string) {\n"
            + "    sink(input);\n    const c = 'x' + input;\n    sink(c);\n  }\n}\n";
    final List<TaintFinding> found =
        TaintAnalysis.run(
            NodeTaintFixtures.graph(AnalysisLanguage.TYPESCRIPT, source, "handle"),
            NodeTaintFixtures.SPEC);
    assertThat(found).hasSize(2);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: Untrusted", "sink: sink argument 0");
    assertThat(NodeTaintFixtures.messages(found.get(1).path()))
        .containsExactly("source: Untrusted", "assigned to c", "sink: sink argument 0");
  }
}
