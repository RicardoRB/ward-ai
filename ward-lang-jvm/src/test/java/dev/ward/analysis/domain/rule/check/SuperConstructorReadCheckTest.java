package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuperConstructorReadCheckTest {

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();
  private static final String BASE = "class Base {\n  Base() { init(); }\n  void init() {}\n}\n";

  private static List<String> run(final String source) {
    final List<RuleMatch> found =
        new SuperConstructorReadCheck("{value}")
            .matches(
                FRONTEND.parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, source)).tree());
    return found.stream().map(m -> m.span().startLine() + ":" + m.span().startColumn()).toList();
  }

  @Test
  void aSubclassMethodThatTheSuperConstructorCallsAndThatReadsAnUnsetFieldIsReported() {
    assertThat(
            run(
                BASE
                    + "class Child extends Base {\n  int size;\n  void init() { int c = size; }\n}\n"))
        .containsExactly("7:25");
    assertThat(
            run(
                "class Base {\n  Base() { this.init(); }\n  void init() {}\n}\n"
                    + "class Child extends Base<T> {\n  int size;\n  void init() { int c = this.size; }\n}\n"))
        .hasSize(1);
  }

  @Test
  void aWriteFirstAnInitializedFieldOrAnUncalledMethodIsNotReported() {
    for (final String child :
        List.of(
            "class Child extends Base {\n  int size;\n  void init() { size = 1; int c = size; }\n}\n",
            "class Child extends Base {\n  int size = 1;\n  void init() { int c = size; }\n}\n",
            "class Child extends Base {\n  int size;\n  { size = 1; }\n  void init() { int c = size; }\n}\n",
            "class Child extends Base {\n  static int size;\n  void init() { int c = size; }\n}\n",
            "class Child extends Base {\n  int size;\n  void other() { int c = size; }\n}\n")) {
      assertThat(run(BASE + child)).as(child).isEmpty();
    }
  }

  @Test
  void aSuperclassThatIsUnknownCallsNothingOrIsTheClassItselfIsSkipped() {
    final String child =
        "class Child extends Base {\n  int size;\n  void init() { int c = size; }\n}\n";

    assertThat(run(child)).isEmpty();
    assertThat(run("class Base {\n  Base() { }\n  void init() {}\n}\n" + child)).isEmpty();
    assertThat(
            run("class Child extends Child {\n  int size;\n  void init() { int c = size; }\n}\n"))
        .isEmpty();
    assertThat(run("class Child {\n  int size;\n}\n")).isEmpty();
  }
}
