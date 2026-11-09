package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the arrow callback check on parsed JavaScript with the given options. */
class NodeArrowCallbackCheckTest {

  @Test
  void anonymousCallbackIsReported() {
    assertThat(run("run(function () { return 1; });", false, true)).hasSize(1);
  }

  @Test
  void namedCallbackIsReportedUnlessAllowed() {
    final String source = "run(function f() { return 1; });";

    assertThat(run(source, false, true)).hasSize(1);
    assertThat(run(source, true, true)).isEmpty();
  }

  @Test
  void namedCallbackReferringToItsNameIsNotReported() {
    assertThat(run("run(function f(n) { return f(n); });", false, true)).isEmpty();
  }

  @Test
  void thisUsingCallbackIsSkippedOnlyWhileUnboundThisIsAllowed() {
    final String source = "run(function () { return this.x; });";

    assertThat(run(source, false, true)).isEmpty();
    assertThat(run(source, false, false)).hasSize(1);
  }

  @Test
  void thisOfANestedFunctionDoesNotCountButThisOfANestedArrowDoes() {
    assertThat(run("run(function () { return function () { return this; }; });", false, true))
        .hasSize(1);
    assertThat(run("run(function () { return () => this.x; });", false, true)).isEmpty();
  }

  @Test
  void argumentsUseSkipsTheCallback() {
    assertThat(run("run(function () { return arguments.length; });", false, false)).isEmpty();
  }

  @Test
  void generatorIsNotReported() {
    assertThat(run("run(function* () { yield 1; });", false, true)).isEmpty();
  }

  @Test
  void boundThisCallbackIsReportedEvenWithDefaults() {
    assertThat(run("run(function () { return this.x; }.bind(this));", false, true)).hasSize(1);
  }

  private static List<RuleMatch> run(
      final String source, final boolean allowNamed, final boolean allowUnboundThis) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source))
            .tree();
    return new NodeArrowCallbackCheck(allowNamed, allowUnboundThis, "arrow").matches(root);
  }
}
