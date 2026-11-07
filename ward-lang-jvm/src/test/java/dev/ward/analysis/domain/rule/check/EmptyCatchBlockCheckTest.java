package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.fileMatches;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.n;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.t;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EmptyCatchBlockCheckTest {
  private static final int NUMBER_5 = 5;
  private static final int NUMBER_9 = 9;
  private static final int NUMBER_10 = 10;
  private static final int NUMBER_11 = 11;
  private static final int NUMBER_14 = 14;
  private static final int NUMBER_15 = 15;
  private static final int NUMBER_16 = 16;

  private static final String MESSAGE = "message";
  private static final String IDENTIFIER = "identifier";
  private static final Pattern ANY_COMMENT = Pattern.compile(".*");
  private static final Pattern NO_COMMENT = Pattern.compile("^$");
  private static final Pattern IGNORED = Pattern.compile("^ignored$");

  @Test
  void reportsAnEmptyCatchBlockUnlessItHasAnExemptNameOrAStatement() {
    assertThat(run(ANY_COMMENT, NO_COMMENT, "{}", "e", 2)).hasSize(1);
    assertThat(run(ANY_COMMENT, IGNORED, "{}", "ignored", 2)).isEmpty();
    assertThat(run(ANY_COMMENT, IGNORED, "{}", "e", 2)).hasSize(1);
    final SyntaxNode filled =
        catchClause("e", Trees.node("block", 0, 2, 1, n("blockStatement", t(IDENTIFIER, "x"))));
    assertThat(
            fileMatches(new EmptyCatchBlockCheck(ANY_COMMENT, NO_COMMENT, MESSAGE), "{}", filled))
        .isEmpty();
    final SyntaxNode notBlock = n("catchClause", t("'catch'", "catch"), t(IDENTIFIER, "e"));
    assertThat(
            fileMatches(new EmptyCatchBlockCheck(ANY_COMMENT, NO_COMMENT, MESSAGE), "", notBlock))
        .isEmpty();
  }

  @Test
  void readsLineAndBlockCommentsAgainstTheFormat() {
    assertThat(run(Pattern.compile("ok"), NO_COMMENT, "{ // ok\n}", "e", NUMBER_9)).isEmpty();
    assertThat(run(Pattern.compile("nope"), NO_COMMENT, "{ // ok\n}", "e", NUMBER_9)).hasSize(1);
    assertThat(run(Pattern.compile("note"), NO_COMMENT, "{ /* note */ }", "e", NUMBER_14))
        .isEmpty();
    assertThat(run(Pattern.compile("note"), NO_COMMENT, "{ /* other */ }", "e", NUMBER_15))
        .hasSize(1);
    assertThat(run(Pattern.compile("note"), NO_COMMENT, "{ /* note }", "e", NUMBER_11)).isEmpty();
    assertThat(run(Pattern.compile("a"), NO_COMMENT, "{ // a /* b */ }", "e", NUMBER_16)).isEmpty();
    assertThat(run(Pattern.compile("b"), NO_COMMENT, "{ /* b */ // a }", "e", NUMBER_16)).isEmpty();
    assertThat(run(Pattern.compile("tail"), NO_COMMENT, "{ // tail}", "e", NUMBER_10)).isEmpty();
  }

  @Test
  void treatsABlockOutsideTheTextAsEmpty() {
    assertThat(run(ANY_COMMENT, NO_COMMENT, "", "e", NUMBER_5)).hasSize(1);
    assertThat(run(ANY_COMMENT, NO_COMMENT, "{}", "e", 0)).hasSize(1);
  }

  private static List<RuleMatch> run(
      final Pattern comment,
      final Pattern exception,
      final String text,
      final String name,
      final int end) {
    return fileMatches(
        new EmptyCatchBlockCheck(comment, exception, MESSAGE),
        text,
        catchClause(name, Trees.node("block", 0, end, 1)));
  }

  private static SyntaxNode catchClause(final String name, final SyntaxNode block) {
    return n("catchClause", t("'catch'", "catch"), t(IDENTIFIER, name), block);
  }
}
