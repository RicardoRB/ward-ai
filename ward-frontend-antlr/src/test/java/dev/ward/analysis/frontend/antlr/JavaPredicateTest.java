package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Grammar predicates and actions for JavaPredicate, exercised through the frontend. */
class JavaPredicateTest {

  private static ParseResult parse(final AnalysisLanguage language, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit("sample", language, text));
  }

  @Test
  void javaVarargsComponentMustBeLast() {
    assertThat(parse(AnalysisLanguage.JAVA, "record R(int... a, int b) {}").diagnostics())
        .isNotEmpty();
  }

  @Test
  void javaVarargsComponentAsLastParses() {
    assertThat(parse(AnalysisLanguage.JAVA, "record R(int b, int... a) {}").diagnostics())
        .isEmpty();
  }

  @Test
  void javaAnnotationWithNamedValueParses() {
    assertThat(
            parse(AnalysisLanguage.JAVA, "@SuppressWarnings(value = \"x\") class A {}")
                .diagnostics())
        .isEmpty();
  }

  @Test
  void validJavaConstructsParseWithoutDiagnostics() {
    final String[] snippets = {
      "record R() {}",
      "record R(int a, int... b) {}",
      "@Deprecated(since = \"1\") class A {}",
      "class A { @Override public String toString() { return \"\"; } }",
    };
    for (final String snippet : snippets) {
      assertThat(parse(AnalysisLanguage.JAVA, snippet).diagnostics()).as(snippet).isEmpty();
    }
  }

  @ParameterizedTest
  @CsvSource({"JAVA, 'class A { int[] a; }'", "KOTLIN, 'fun main() {}'"})
  void validSourceParsesWithoutDiagnostics(final AnalysisLanguage language, final String text) {
    assertThat(parse(language, text).diagnostics()).isEmpty();
  }
}
