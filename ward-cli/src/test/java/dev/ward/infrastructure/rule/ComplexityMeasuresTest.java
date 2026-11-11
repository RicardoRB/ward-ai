package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The bundled Java and Kotlin complexity rules return every method's value, within the limit too.
 */
final class ComplexityMeasuresTest {

  @Test
  void javaMeasuresEveryMethodIncludingThoseWithinTheLimit() {
    final String source =
        "class A {\n"
            + "  void simple() {}\n"
            + "  int branchy(int a) {\n"
            + "    if (a > 0 && a < 9) { return 1; }\n"
            + "    return 0;\n"
            + "  }\n"
            + "}\n";

    assertThat(measure("ward:java/cyclomatic-complexity", AnalysisLanguage.JAVA, source))
        .containsExactly("2:1", "3:3");
  }

  @Test
  void kotlinMeasuresEveryFunctionIncludingThoseWithinTheLimit() {
    final String source =
        "fun simple() {}\nfun branchy(a: Int): Int {\n  if (a > 0) return 1\n  return 0\n}\n";

    assertThat(measure("ward:kotlin/cyclomatic-complexity", AnalysisLanguage.KOTLIN, source))
        .containsExactly("1:1", "2:2");
  }

  private static List<String> measure(
      final String id, final AnalysisLanguage language, final String text) {
    return new ClasspathRuleSource()
        .load().rules().stream()
            .filter(rule -> rule.id().equals(id))
            .findFirst()
            .map(BundledRuleMatchers.create()::compile)
            .orElseThrow()
            .measures(
                new AntlrLanguageFrontend()
                    .parse(new SourceUnit("src/A.txt", language, text))
                    .tree())
            .stream()
            .map(ComplexityMeasuresTest::line)
            .toList();
  }

  private static String line(final Measured measured) {
    return measured.span().startLine() + ":" + measured.value();
  }
}
