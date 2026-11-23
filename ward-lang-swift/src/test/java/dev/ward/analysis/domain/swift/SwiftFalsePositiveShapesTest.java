package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.UnusedBindingCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The shapes of the strict Swift corpus that were false positives (#752, #753, #754). */
class SwiftFalsePositiveShapesTest {
  private static final String MESSAGE = "finding";

  private static List<RuleMatch> unusedLocals(final String source) {
    return LintRuns.runScoped(new UnusedBindingCheck(MESSAGE), source);
  }

  private static List<RuleMatch> unusedParameters(final String source) {
    return LintRuns.runScoped(new SwiftUnusedParameterCheck(MESSAGE), source);
  }

  private static List<RuleMatch> weakRandom(final String source) {
    final SourceUnit unit = new SourceUnit("x.swift", AnalysisLanguage.SWIFT, source);
    return new SwiftWeakRandomCheck(MESSAGE)
        .matches(new AntlrLanguageFrontend().parse(unit).tree(), FlowView.NONE, unit);
  }

  @Test
  void aNameDeclaredInEachDirectiveBranchAndReadAfterIsUsed() {
    final String source =
        "func f() {\n#if os(x)\nlet v = 1\n#else\nlet v = 2\n#endif\nprint(v)\n}\n";

    assertThat(unusedLocals(source)).isEmpty();
  }

  @Test
  void aNameDeclaredInOneDirectiveBranchAndNeverReadIsUnused() {
    final String source = "func f() {\n#if DEBUG\nlet v = 1\n#endif\n}\n";

    assertThat(unusedLocals(source)).hasSize(1);
  }

  @Test
  void aGuardCaseThatRebindsAParameterReadsIt() {
    final String source =
        "func f(t: Int) {\nguard case .some(let t) = t else { return }\nprint(t)\n}\n";

    assertThat(unusedParameters(source)).isEmpty();
  }

  @Test
  void aGuardCaseThatReadsOtherNamesLeavesTheParameterUnused() {
    final String source =
        "func f(t: Int, o: Int?) {\nguard case .some(let v) = o else { return }\nprint(v)\n}\n";

    assertThat(unusedParameters(source)).hasSize(1);
  }

  @Test
  void drand48SeededWithAParameterOrAConstantIsAccepted() {
    assertThat(
        weakRandom("struct G {\ninit(seed: Int) {\nsrand48(seed)\n}\nfunc n() { drand48() }\n}\n")
    )
        .isEmpty();
    assertThat(weakRandom("func a() {\nsrand48(7)\n_ = lrand48()\n}\n")).isEmpty();
  }

  @Test
  void drand48SeededFromTheClockOrNotAtAllIsReported() {
    assertThat(weakRandom("func a() {\nsrand48(Int(time(nil)))\n_ = drand48()\n}\n")).hasSize(1);
    assertThat(weakRandom("func a() { _ = drand48() }\n")).hasSize(1);
    assertThat(weakRandom("func a() {\nlet s = 1\nsrand48(s)\n_ = drand48()\n}\n")).hasSize(1);
  }

  @Test
  void rand48IsNotAcceptedForTheOtherGenerators() {
    assertThat(weakRandom("func a() {\nsrand48(7)\n_ = rand()\n}\n")).hasSize(1);
  }
}
