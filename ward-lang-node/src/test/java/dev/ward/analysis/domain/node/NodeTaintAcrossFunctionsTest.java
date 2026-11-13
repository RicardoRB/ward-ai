package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Runs the taint analysis over the functions of one TypeScript or JavaScript file. */
class NodeTaintAcrossFunctionsTest {

  /** A value returned by one function and passed to a sinking function of the same file. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void flowAcrossFunctionsOfOneFileIsReported(final AnalysisLanguage language) {
    final String source =
        "function read() { return source(); }\n"
            + "function put(v) { sink(v); }\n"
            + "function run() { const d = read(); put(d); }\n"
            + "function safe() { put('x'); }\n";
    assertThat(NodeTaintFixtures.runFile(language, source)).hasSize(1);
  }
}
