package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Runs the taint analysis with a member-write sink, which guards argument 0 of the write. */
class NodeMemberWriteTaintTest {

  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "source", Optional.empty(), Optional.empty(), List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.MEMBER_WRITE,
                  "innerHTML",
                  Optional.empty(),
                  Optional.empty(),
                  List.of(0))),
          List.of(),
          List.of());

  /** Scenario: Tainted value written to a sink property. The path ends at the write. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void taintedValueWrittenToSinkPropertyIsReported(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(language, "function run(el) { const q = source(); el.innerHTML = q; }");
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: source", "sink: innerHTML argument 0");
  }

  /** Scenario: Other property. A write to a property that is not a sink reports nothing. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void writeToAnotherPropertyIsNotReported(final AnalysisLanguage language) {
    assertThat(run(language, "function run(el) { const q = source(); el.title = q; }")).isEmpty();
  }

  private static List<TaintFinding> run(final AnalysisLanguage language, final String source) {
    return TaintAnalysis.run(NodeTaintFixtures.graph(language, source, "run"), SPEC);
  }
}
