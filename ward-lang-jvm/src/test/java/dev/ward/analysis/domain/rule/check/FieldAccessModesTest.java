package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldAccessModesTest {

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();

  /** Runs the check on {@code source} and returns each finding as {@code line:column}. */
  private static List<String> run(final FieldAccessCheck.Mode mode, final String source) {
    final List<RuleMatch> found =
        new FieldAccessCheck(mode, "{value}")
            .matches(
                FRONTEND.parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, source)).tree());
    return found.stream().map(m -> m.span().startLine() + ":" + m.span().startColumn()).toList();
  }

  private static String box(final String members) {
    return "class Box {\n" + members + "}\n";
  }

  @Test
  void aConstructorThatReadsAFieldBeforeWritingItIsReported() {
    assertThat(
            run(
                FieldAccessCheck.Mode.UNINITIALIZED_READ,
                box(
                    "  private int size;\n  private int twice;\n  Box() {\n    twice = size * 2;\n    size = 1;\n  }\n")))
        .containsExactly("5:13");
    for (final String body : List.of("size += 1;", "size++;", "size = size + 1;")) {
      assertThat(
              run(
                  FieldAccessCheck.Mode.UNINITIALIZED_READ,
                  box("  private int size;\n  Box() { " + body + " }\n")))
          .as(body)
          .hasSize(1);
    }
  }

  @Test
  void aFieldThatIsWrittenFirstOrSetUpElsewhereIsNotAnUninitializedRead() {
    for (final String members :
        List.of(
            "  private int size;\n  private int twice;\n  Box() { size = 1; twice = size * 2; }\n",
            "  private int size;\n  Box() { this(1); int x = size; }\n  Box(int s) { size = s; }\n",
            "  private int size;\n  { size = 1; }\n  Box() { int x = size; }\n",
            "  private static int size;\n  Box() { int x = size; }\n",
            "  private int size = 1;\n  Box() { int x = size; }\n",
            "  @Inject private int size;\n  Box() { int x = size; }\n",
            "  private int size;\n  Box(int size) { int x = size; }\n")) {
      assertThat(run(FieldAccessCheck.Mode.UNINITIALIZED_READ, box(members))).as(members).isEmpty();
    }
  }

  @Test
  void aLocalVariableNamedLikeAFieldThatIsNeverWrittenIsReported() {
    assertThat(
            run(
                FieldAccessCheck.Mode.LOCAL_MASKS,
                box(
                    "  private int total;\n  int sum() { int total = 0; return total; }\n  int get() { return total; }\n")))
        .containsExactly("3:19");
  }

  @Test
  void aLocalNamedLikeAWrittenInitializedOrAnnotatedFieldIsNotReported() {
    for (final String members :
        List.of(
            "  private int total;\n  void set() { this.total = 1; }\n  int sum() { int total = 0; return total; }\n",
            "  private int total = 1;\n  int sum() { int total = 0; return total; }\n",
            "  @Inject private int total;\n  int sum() { int total = 0; return total; }\n",
            "  private int total;\n  int sum() { int other = 0; return other; }\n")) {
      assertThat(run(FieldAccessCheck.Mode.LOCAL_MASKS, box(members))).as(members).isEmpty();
    }
  }
}
