package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks the collapsible {@code if} and the handler body checks over hand-built Python trees. */
final class PythonBlockChecksTest {

  private static final String MESSAGE = "found";
  private static final String PASS = PythonTrees.PASS;

  private final PythonTrees trees = new PythonTrees();

  @Test
  void reportsAnIfWhoseOnlyStatementIsAnIf() {
    final SyntaxNode outer =
        trees.ifStmt(List.of(trees.compound(trees.ifStmt(List.of(trees.simple(PASS))))));

    assertThat(collapsible(outer)).hasSize(1);
  }

  @Test
  void ignoresAnIfWithAnElseOrElifOnTheInnerOrOuterIf() {
    final SyntaxNode innerElse =
        trees.ifStmt(List.of(trees.compound(trees.ifStmt(List.of(trees.simple(PASS)), "else"))));
    final SyntaxNode outerElif =
        trees.ifStmt(List.of(trees.compound(trees.ifStmt(List.of(trees.simple(PASS))))), "elif");

    assertThat(collapsible(innerElse)).isEmpty();
    assertThat(collapsible(outerElif)).isEmpty();
  }

  @Test
  void ignoresAnIfWithMoreThanOneStatementOrAStatementThatIsNotAnIf() {
    final SyntaxNode two =
        trees.ifStmt(
            List.of(trees.compound(trees.ifStmt(List.of(trees.simple(PASS)))), trees.simple(PASS)));
    final SyntaxNode plain = trees.ifStmt(List.of(trees.simple(PASS)));

    assertThat(collapsible(two)).isEmpty();
    assertThat(collapsible(plain)).isEmpty();
  }

  @Test
  void reportsAnExceptBodyThatMatchesThePattern() {
    final SyntaxNode attempt =
        trees.attempt(List.of(trees.simple("f()")), List.of(trees.handler("continue")));

    assertThat(handlers(attempt, "continue", false)).hasSize(1);
    assertThat(handlers(attempt, PASS, false)).isEmpty();
  }

  @Test
  void reportsEachMatchingHandlerUnlessSole() {
    final SyntaxNode attempt =
        trees.attempt(
            List.of(trees.simple("f()")), List.of(trees.handler(PASS), trees.handler(PASS)));

    assertThat(handlers(attempt, PASS, false)).hasSize(2);
    assertThat(handlers(attempt, PASS, true)).isEmpty();
  }

  @Test
  void soleNeedsOneStatementOneHandlerAndNoElseOrFinally() {
    final SyntaxNode plain =
        trees.attempt(List.of(trees.simple("f()")), List.of(trees.handler(PASS)));
    final SyntaxNode two =
        trees.attempt(
            List.of(trees.simple("f()"), trees.simple("g()")), List.of(trees.handler(PASS)));
    final SyntaxNode withElse =
        trees.attempt(
            List.of(trees.simple("f()")), List.of(trees.handler(PASS), trees.clause("else")));
    final SyntaxNode withFinally =
        trees.attempt(
            List.of(trees.simple("f()")), List.of(trees.handler(PASS), trees.clause("finally")));

    assertThat(handlers(plain, PASS, true)).hasSize(1);
    assertThat(handlers(two, PASS, true)).isEmpty();
    assertThat(handlers(withElse, PASS, true)).isEmpty();
    assertThat(handlers(withFinally, PASS, true)).isEmpty();
  }

  @Test
  void readsTheStatementsOfAOneLineBlock() {
    final SyntaxNode both =
        Layout.node(
            "simple_stmts", trees.simple("a"), trees.layout().token(";", ";"), trees.simple("b"));
    final SyntaxNode block = Layout.node(PythonTrees.BLOCK, both);

    assertThat(StatementShapes.statements(block)).hasSize(2);
    assertThat(StatementShapes.soleIf(block)).isEmpty();
  }

  private static List<RuleMatch> collapsible(final SyntaxNode root) {
    return new CollapsibleIfCheck(MESSAGE).matches(root);
  }

  private static List<RuleMatch> handlers(
      final SyntaxNode root, final String body, final boolean sole) {
    return new HandlerBodyCheck(Pattern.compile(body), sole, MESSAGE).matches(root);
  }
}
