package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class NonTerminatingLoopCheckTest {

  private static final NonTerminatingLoopCheck CHECK = new NonTerminatingLoopCheck("message");

  /** Returns each finding of the check on the body {@code statements} as {@code line:column}. */
  private static List<String> run(final String statements) {
    final String source = "class C {\n  void run(int n) {\n" + statements + "  }\n}\n";
    final SourceUnit unit = new SourceUnit("C.java", AnalysisLanguage.JAVA, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    final FlowView flow = new FlowView(new JavaControlFlow().lower(unit, parsed.tree()).graphs());
    final List<RuleMatch> found = CHECK.matches(parsed.tree(), flow);
    return found.stream().map(m -> m.span().startLine() + ":" + m.span().startColumn()).toList();
  }

  @Test
  void aLoopWithoutAnExitAndWithoutCallsIsReportedAtItsFirstNode() {
    assertThat(run("    int i = 0;\n    while (true) {\n      i++;\n    }\n"))
        .containsExactly("4:12");
    assertThat(run("    for (;;) {\n      n++;\n    }\n")).hasSize(1);
  }

  @Test
  void aLoopWithAWayOutIsNotReported() {
    assertThat(run("    int i = 0;\n    while (i < n) {\n      i++;\n    }\n")).isEmpty();
    assertThat(run("    while (true) {\n      if (n > 1) {\n        break;\n      }\n    }\n"))
        .isEmpty();
    assertThat(run("    while (true) {\n      if (n > 1) {\n        return;\n      }\n    }\n"))
        .isEmpty();
    assertThat(run("    while (true) {\n      throw new IllegalStateException();\n    }\n"))
        .isEmpty();
  }

  @Test
  void aLoopThatCallsOrCreatesSomethingIsNotReported() {
    assertThat(run("    while (true) {\n      System.out.println(n);\n    }\n")).isEmpty();
    assertThat(run("    while (true) {\n      Object o = new Object();\n    }\n")).isEmpty();
  }

  @Test
  void withoutGraphsOrWithoutFlowNothingIsReported() {
    assertThat(
            CHECK.matches(
                new AntlrLanguageFrontend()
                    .parse(new SourceUnit("C.java", AnalysisLanguage.JAVA, "class C {}\n"))
                    .tree()))
        .isEmpty();
    assertThat(run("    int x = 1;\n")).isEmpty();
  }
}
