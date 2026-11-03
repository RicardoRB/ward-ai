package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

class RuleSpanTest {

  private static final int LINES = 3;

  @Test
  void compilationUnitEndsAtTheLastLineNotAtTheEndOfFileToken() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {\n}\n// end\n"));

    assertThat(result.tree().span().endLine()).isEqualTo(LINES);
  }
}
