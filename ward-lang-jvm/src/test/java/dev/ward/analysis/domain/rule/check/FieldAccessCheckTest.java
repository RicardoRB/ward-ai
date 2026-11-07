package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldAccessCheckTest {

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
  void aPrivateFieldThatIsOnlyReadIsUnwritten() {
    final String source = box("  private String label;\n  String show() { return label; }\n");

    assertThat(run(FieldAccessCheck.Mode.UNWRITTEN, source)).containsExactly("2:18");
    assertThat(
            run(
                FieldAccessCheck.Mode.UNWRITTEN,
                box("  private String label;\n  String show() { return this.label; }\n")))
        .hasSize(1);
  }

  @Test
  void anyWriteInitializerOrNeverReadFieldIsNotUnwritten() {
    for (final String members :
        List.of(
            "  private String label;\n  Box(String l) { this.label = l; }\n  String s() { return label; }\n",
            "  private int count;\n  void up() { count += 1; }\n  int s() { return count; }\n",
            "  private int count;\n  void up() { count++; }\n  int s() { return count; }\n",
            "  private String label = \"x\";\n  String s() { return label; }\n",
            "  @Inject private String label;\n  String s() { return label; }\n",
            "  public String label;\n  String s() { return label; }\n",
            "  private String label;\n",
            "  private String label;\n  String s(String label) { return label; }\n")) {
      assertThat(run(FieldAccessCheck.Mode.UNWRITTEN, box(members))).as(members).isEmpty();
    }
  }

  @Test
  void aFieldThatOnlyHoldsNullIsReported() {
    assertThat(
            run(
                FieldAccessCheck.Mode.NULL_ONLY,
                box("  private String label = null;\n  String s() { return label; }\n")))
        .containsExactly("2:18");
    assertThat(
            run(
                FieldAccessCheck.Mode.NULL_ONLY,
                box(
                    "  private String label;\n  void clear() { label = null; }\n  String s() { return label; }\n")))
        .hasSize(1);
  }

  @Test
  void aFieldThatIsAlsoGivenAValueOrNeverReadIsNotNullOnly() {
    for (final String members :
        List.of(
            "  private String label = null;\n  void set() { label = \"x\"; }\n  String s() { return label; }\n",
            "  private String label = \"x\";\n  void clear() { label = null; }\n  String s() { return label; }\n",
            "  private String label;\n  String s() { return label; }\n",
            "  private String label = null;\n")) {
      assertThat(run(FieldAccessCheck.Mode.NULL_ONLY, box(members))).as(members).isEmpty();
    }
  }

  @Test
  void aMemberSelectedFromAnUnwrittenFieldIsReportedAtTheRead() {
    assertThat(
            run(
                FieldAccessCheck.Mode.DEREFERENCED,
                box("  private String label;\n  int n() { return label.length(); }\n")))
        .containsExactly("3:20");
    assertThat(
            run(
                FieldAccessCheck.Mode.DEREFERENCED,
                box("  private String label;\n  int n() { return this.label.length(); }\n")))
        .hasSize(1);
  }

  @Test
  void aPlainReadOrAWrittenFieldIsNotDereferenced() {
    assertThat(
            run(
                FieldAccessCheck.Mode.DEREFERENCED,
                box("  private String label;\n  String n() { return label; }\n")))
        .isEmpty();
    assertThat(
            run(
                FieldAccessCheck.Mode.DEREFERENCED,
                box(
                    "  private String label;\n  Box() { label = \"x\"; }\n  int n() { return label.length(); }\n")))
        .isEmpty();
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
}
