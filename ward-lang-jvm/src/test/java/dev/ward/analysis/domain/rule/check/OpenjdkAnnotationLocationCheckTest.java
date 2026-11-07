package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenjdkAnnotationLocationCheckTest {
  private static final int NUMBER_4 = 4;
  private static final int NUMBER_8 = 8;

  private static final String MESSAGE = "message";
  private static final String ANNOTATION = "annotation";
  private static final String PUBLIC = "public";
  private static final String BODY = "classBodyDeclaration";

  @Test
  void checksTheLinesOfAnnotationsAndTheirDeclaration() {
    assertThat(run(group(BODY, tok(ANNOTATION, "@A", 1, 0), tok(PUBLIC, PUBLIC, 1, NUMBER_4))))
        .isEmpty();
    assertThat(run(group(BODY, tok(ANNOTATION, "@A", 1, 0), tok(PUBLIC, PUBLIC, 2, NUMBER_4))))
        .isEmpty();
    assertThat(
            run(
                group(
                    BODY,
                    tok(ANNOTATION, "@A", 1, 0),
                    tok(ANNOTATION, "@B", 1, NUMBER_4),
                    tok(PUBLIC, PUBLIC, 2, NUMBER_8))))
        .hasSize(1);
    assertThat(run(group(BODY, tok(PUBLIC, PUBLIC, 1, 0)))).isEmpty();
    assertThat(run(group("modifier", tok(ANNOTATION, "@A", 1, 0), tok("x", "x", 2, 0)))).isEmpty();
  }

  private static List<RuleMatch> run(final SyntaxNode decl) {
    return unitMatches(new OpenjdkAnnotationLocationCheck(MESSAGE), decl);
  }
}
