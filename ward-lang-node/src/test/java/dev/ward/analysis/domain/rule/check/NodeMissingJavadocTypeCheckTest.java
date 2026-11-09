package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the missing-javadoc check on parsed TypeScript. */
class NodeMissingJavadocTypeCheckTest {

  @Test
  void exportedClassWithoutJavadocIsReported() {
    assertThat(run(true, "export class A {}\n")).hasSize(1);
  }

  @Test
  void javadocDirectlyBeforeTheExportIsAccepted() {
    assertThat(run(true, "/** Doc. */\nexport interface A { x: number }\n")).isEmpty();
  }

  @Test
  void lineCommentIsNotJavadoc() {
    assertThat(run(true, "// Doc.\nexport enum A { X }\n")).hasSize(1);
  }

  @Test
  void javadocWithCodeBetweenIsNotAccepted() {
    assertThat(run(true, "/** Doc. */\nconst x = 1;\nexport class A {}\n")).hasSize(1);
  }

  @Test
  void localTypeIsSkippedWhenOnlyPublicTypesAreRequired() {
    assertThat(run(true, "class A {}\n")).isEmpty();
  }

  @Test
  void localTypeIsReportedWhenAllTypesAreRequired() {
    assertThat(run(false, "class A {}\ntype B = number;\n")).hasSize(2);
  }

  @Test
  void typeAliasWithoutJavadocIsReported() {
    assertThat(run(true, "export type A = string;\n")).hasSize(1);
  }

  private static List<RuleMatch> run(final boolean publicOnly, final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeMissingJavadocTypeCheck(publicOnly, "no javadoc").matches(root);
  }
}
