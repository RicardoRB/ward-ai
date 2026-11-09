package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwiftRedundantVoidReturnCheckTest {

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("x.swift", AnalysisLanguage.SWIFT, source);
    return new SwiftRedundantVoidReturnCheck("redundant")
        .matches(new AntlrLanguageFrontend().parse(unit).tree());
  }

  @Test
  void reportsTheEndOfTheParametersBeforeAVoidReturn() {
    final List<RuleMatch> found = run("func foo() -> Void {}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(1);
    assertThat(found.get(0).span().startColumn()).isEqualTo(11);
  }

  @Test
  void reportsAnEmptyTupleReturnAndAVoidClosureReturn() {
    assertThat(run("func foo() -> () {}\n")).hasSize(1);
    assertThat(run("func foo() -> ( ) {}\n")).hasSize(1);
    assertThat(run("doSomething { arg -> Void in }\n")).hasSize(1);
  }

  @Test
  void ignoresReturnTypesThatAreNotVoidAndFunctionTypes() {
    assertThat(run("func foo() -> Int {}\n")).isEmpty();
    assertThat(run("func foo() -> VoidResponse\n")).isEmpty();
    assertThat(run("func foo() -> Void?\n")).isEmpty();
    assertThat(run("let foo: (Int) -> Void\n")).isEmpty();
  }
}
