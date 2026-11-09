package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin class-lines and function-count checks over hand-made class trees. */
class KotlinClassSizeChecksTest {

  private static final String MESSAGE = "m";
  private static final String CLASS = "classDeclaration";
  private static final String BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final int OFFSET = 0;
  private static final int EXACT_LINES = 3;

  private static SyntaxNode lineOf(final int line) {
    return tok("token", "x", line, OFFSET);
  }

  private static SyntaxNode classOf(final int line, final SyntaxNode... members) {
    final SyntaxNode[] parts = new SyntaxNode[members.length + 2];
    parts[0] = tok("'class'", "class", line, OFFSET);
    parts[1] = group(BODY, members);
    System.arraycopy(members, 0, parts, 2, members.length);
    return group(CLASS, parts);
  }

  private static SyntaxNode function(final int line, final String name) {
    return group(
        MEMBER,
        group(
            "functionDeclaration",
            tok("'fun'", "fun", line, OFFSET),
            group("identifier", tok("simpleIdentifier", name, line, OFFSET))));
  }

  @Test
  void doesNotCountTheLinesOfANestedClassAsTheOuterClassLines() {
    final SyntaxNode inner = classOf(3, lineOf(3), lineOf(4), lineOf(5));
    final SyntaxNode outer = classOf(1, lineOf(2), group(MEMBER, inner), lineOf(6));

    assertThat(outerLines(new KotlinClassLinesCheck(Set.of(CLASS), 2, MESSAGE), outer)).hasSize(1);
    assertThat(outerLines(new KotlinClassLinesCheck(Set.of(CLASS), EXACT_LINES, MESSAGE), outer))
        .isEmpty();
  }

  private static List<RuleMatch> outerLines(final RuleCheck check, final SyntaxNode outer) {
    return unitMatches(check, outer).stream()
        .filter(match -> match.span().startLine() == 1)
        .toList();
  }

  @Test
  void countsTheFunctionsOfAClassAgainstItsThreshold() {
    final SyntaxNode clazz = classOf(1, function(2, "a"), function(3, "b"));
    final KotlinFunctionCountCheck.Policy two =
        new KotlinFunctionCountCheck.Policy(11, 2, 11, 11, 11, false, false, false);
    final KotlinFunctionCountCheck.Policy three =
        new KotlinFunctionCountCheck.Policy(11, 3, 11, 11, 11, false, false, false);

    assertThat(unitMatches(new KotlinFunctionCountCheck(two, MESSAGE), clazz)).hasSize(1);
    assertThat(unitMatches(new KotlinFunctionCountCheck(three, MESSAGE), clazz)).isEmpty();
  }
}
