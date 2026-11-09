package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the javadoc position check on parsed TypeScript. */
class NodeJavadocPositionCheckTest {

  @Test
  void javadocBeforeADeclarationIsValid() {
    assertThat(run("/** Doc. */\nexport class A {}\n")).isEmpty();
  }

  @Test
  void javadocInsideAFunctionBodyIsReported() {
    assertThat(run("function f() {\n  /** x */\n  return 1;\n}\n")).hasSize(1);
  }

  @Test
  void javadocWithNothingAfterItIsReported() {
    assertThat(run("export class A {}\n/** dangling */\n")).hasSize(1);
  }

  private static List<RuleMatch> run(final String source) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source))
            .tree();
    return new NodeJavadocPositionCheck("position").matches(root);
  }
}
