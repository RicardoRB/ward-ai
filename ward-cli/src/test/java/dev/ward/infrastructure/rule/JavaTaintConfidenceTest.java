package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.ir.Confidence;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the confidence of Java taint findings: a flow is proven when every step was resolved, and
 * possible when it crosses a project call that is not followed or a field that another method
 * writes. A proven flow to the same place always wins.
 */
class JavaTaintConfidenceTest {
  private static final String OVERLOADS =
      "  String pick(String v) { return v; }\n  String pick(Object v) { return \"\"; }\n";

  @Test
  void aDirectFlowIsProven() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile("class Flow {\n  void run() {\n    sink(source());\n  }\n}\n");

    assertThat(found).singleElement()
        .satisfies(finding -> assertThat(finding.confidence()).isEqualTo(Confidence.PROVEN))
        .satisfies(finding -> assertThat(finding.assumptions()).isEmpty());
  }

  @Test
  void aFlowThroughAFollowedHelperIsProven() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile(
            "class Flow {\n  void run() {\n    String v = wrap(source());\n    sink(v);\n  }\n"
                + "  String wrap(String v) { return \"<\" + v; }\n}\n"
        );

    assertThat(found).extracting(TaintFinding::confidence).containsExactly(Confidence.PROVEN);
  }

  @Test
  void aFlowThroughAnAmbiguousOverloadIsPossible() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile(
            "class Flow {\n  void run() {\n    String v = pick(source());\n    sink(v);\n  }\n"
                + OVERLOADS
                + "}\n"
        );

    assertThat(found).singleElement()
        .satisfies(finding -> assertThat(finding.confidence()).isEqualTo(Confidence.POSSIBLE))
        .satisfies(
            finding ->
                assertThat(finding.assumptions()).containsExactly("the call pick is not followed")
        );
  }

  @Test
  void aSinkArgumentThatWrapsAnUnfollowedCallIsPossible() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile(
            "class Flow {\n  void run() {\n    String v = source();\n    sink(pick(v));\n  }\n"
                + OVERLOADS
                + "}\n"
        );

    assertThat(found).extracting(TaintFinding::confidence).containsExactly(Confidence.POSSIBLE);
  }

  @Test
  void aProvenFlowWinsOverAnAssumedOneToTheSameWrite() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile(
            "class Flow {\n  void run() {\n    String v = source();\n"
                + "    String w = pick(v) + v;\n    sink(w);\n  }\n"
                + OVERLOADS
                + "}\n"
        );

    assertThat(found).extracting(TaintFinding::confidence).containsExactly(Confidence.PROVEN);
  }

  @Test
  void aMethodThatReturnsAnAssumedFlowGivesItsCallersAPossibleSource() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile(
            "class Flow {\n  void run() {\n    sink(fetch());\n  }\n"
                + "  String fetch() { return pick(source()); }\n"
                + OVERLOADS
                + "}\n"
        );

    assertThat(found).singleElement()
        .satisfies(finding -> assertThat(finding.confidence()).isEqualTo(Confidence.POSSIBLE))
        .satisfies(
            finding ->
                assertThat(finding.assumptions()).containsExactly("the call pick is not followed")
        );
  }

  @Test
  void aFieldThatAnotherMethodWritesIsPossible() {
    final List<TaintFinding> found =
        JavaTaintFixtures.runFile(
            "class Flow {\n  String kept;\n  void store() {\n    this.kept = source();\n  }\n"
                + "  void run() {\n    sink(this.kept);\n  }\n}\n"
        );

    assertThat(found).singleElement()
        .satisfies(finding -> assertThat(finding.confidence()).isEqualTo(Confidence.POSSIBLE))
        .satisfies(
            finding ->
                assertThat(finding.assumptions())
                    .containsExactly("the field kept is written by another method")
        );
  }
}
