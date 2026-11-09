package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reports a returned value in a constructor, in JavaScript and TypeScript sources. */
class NodeConstructorReturnCheckTest {

  private static final int LINE = 3;
  private static final int COLUMN = 5;
  private static final String MESSAGE = "unexpected return value from constructor";

  /** Scenario: a value returned from a constructor is reported at the return keyword. */
  @Test
  void returnedValueInAConstructorIsReported() {
    final List<RuleMatch> found =
        run(AnalysisLanguage.JAVASCRIPT, "class Foo {\n  constructor() {\n    return 1;\n  }\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(LINE);
    assertThat(found.get(0).span().startColumn()).isEqualTo(COLUMN);
    assertThat(found.get(0).message()).isEqualTo(MESSAGE);
  }

  /** Scenario: a bare return and a return inside a nested arrow function are not reported. */
  @Test
  void bareReturnAndNestedFunctionReturnAreNotReported() {
    final String source =
        "class Foo {\n  constructor() {\n    const inner = () => {\n      return 1;\n    };\n"
            + "    return;\n  }\n}\n";

    assertThat(run(AnalysisLanguage.TYPESCRIPT, source)).isEmpty();
  }

  /** Scenario: the TypeScript constructor declaration is checked the same way. */
  @Test
  void typeScriptConstructorDeclarationIsChecked() {
    final List<RuleMatch> found =
        run(AnalysisLanguage.TYPESCRIPT, "class Foo {\n  constructor() {\n    return 1;\n  }\n}\n");

    assertThat(found).hasSize(1);
  }

  private static List<RuleMatch> run(final AnalysisLanguage language, final String source) {
    final SourceUnit unit = new SourceUnit("a", language, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeConstructorReturnCheck(MESSAGE).matches(root);
  }
}
