package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the Java example rule of each generic check kind over real sources. */
class JavaExampleRulesTest {

  private static final String JAVA = "ward:java/example-";
  private static final String JAVA_SOURCE =
      """
      import a.B;
      import a.B;
      class A {
        static final int maxSize = 3;
        public static final int OK = 5;
        static public int bad = 1;
        int one(int x) {
          switch (x) { case 1: return 7; }
          if (x > 0) { if (x > 1) { if (x > 2) { return x > 5 ? 1 : 0; } } }
          return 0;
        }
        int two() { return 0; }
        int three() { return 1; }
        // TODO: later
      }
      """;
  private static final String JAVA_CLEAN =
      """
      import a.B;
      class A {
        static final int MAX = 3;
        public static final int OK = 5;
        int one(int x) {
          switch (x) { case 1: return 0; default: return 1; }
        }
      }
      """;

  @Test
  void javaExamplesReportTheirViolations() {
    final List<AnalysisFinding> findings =
        ExampleRuns.run(AnalysisLanguage.JAVA, "java", JAVA_SOURCE);

    assertThat(ExampleRuns.lines(findings, JAVA + "constant-name"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "maxSize"));
    assertThat(ExampleRuns.lines(findings, JAVA + "inline-conditional"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "x > 5 ?"));
    assertThat(ExampleRuns.lines(findings, JAVA + "switch-default"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "switch (x)"));
    assertThat(ExampleRuns.lines(findings, JAVA + "method-count"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "class A"));
    assertThat(ExampleRuns.lines(findings, JAVA + "nested-if"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "x > 5 ?"));
    assertThat(ExampleRuns.lines(findings, JAVA + "todo-comment"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "// TODO"));
    assertThat(ExampleRuns.lines(findings, JAVA + "modifier-order"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "static public"));
    assertThat(ExampleRuns.lines(findings, JAVA + "magic-number"))
        .containsExactlyElementsOf(ExampleRuns.at(JAVA_SOURCE, "return 7", "x > 5 ?"));
    assertThat(ExampleRuns.lines(findings, JAVA + "redundant-import")).containsExactly(2);
  }

  @Test
  void javaExampleMessagesNameTheOffender() {
    final List<AnalysisFinding> findings =
        ExampleRuns.run(AnalysisLanguage.JAVA, "java", JAVA_SOURCE);

    assertThat(ExampleRuns.messages(findings, JAVA + "constant-name"))
        .containsExactly("constant 'maxSize' must match [A-Z][A-Z0-9_]*");
    assertThat(ExampleRuns.messages(findings, JAVA + "method-count"))
        .containsExactly("class has 3 methods; the limit is 2");
    assertThat(ExampleRuns.messages(findings, JAVA + "modifier-order"))
        .containsExactly("modifier 'public' is out of order");
  }

  @Test
  void aCompliantJavaFileHasNoExampleFinding() {
    assertThat(ExampleRuns.run(AnalysisLanguage.JAVA, "java", JAVA_CLEAN)).isEmpty();
  }

  @Test
  void everyExampleRuleLoads() {
    assertThat(ExampleRuns.rules("java")).hasSize(GenericKind.values().length);
    assertThat(ExampleRuns.rules("python")).hasSize(GenericKind.values().length);
  }
}
