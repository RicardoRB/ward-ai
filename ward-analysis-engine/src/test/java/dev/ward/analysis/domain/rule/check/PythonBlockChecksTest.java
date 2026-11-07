package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks the collapsible {@code if} and the handler body checks over hand-built Python trees. */
final class PythonBlockChecksTest {

  private static final String MESSAGE = "found";
  private static final String BLOCK = "block";
  private static final String COLON = ":";
  private static final String PASS = "pass";
  private static final String STMT = "stmt";

  private final Layout layout = new Layout();

  @Test
  void reportsAnIfWhoseOnlyStatementIsAnIf() {
    final SyntaxNode outer = ifStmt(List.of(compound(ifStmt(List.of(simple(PASS))))));

    assertThat(collapsible(outer)).hasSize(1);
  }

  @Test
  void ignoresAnIfWithAnElseOrElifOnTheInnerOrOuterIf() {
    final SyntaxNode innerElse = ifStmt(List.of(compound(ifStmt(List.of(simple(PASS)), "else"))));
    final SyntaxNode outerElif = ifStmt(List.of(compound(ifStmt(List.of(simple(PASS))))), "elif");

    assertThat(collapsible(innerElse)).isEmpty();
    assertThat(collapsible(outerElif)).isEmpty();
  }

  @Test
  void ignoresAnIfWithMoreThanOneStatementOrAStatementThatIsNotAnIf() {
    final SyntaxNode two = ifStmt(List.of(compound(ifStmt(List.of(simple(PASS)))), simple(PASS)));
    final SyntaxNode plain = ifStmt(List.of(simple(PASS)));

    assertThat(collapsible(two)).isEmpty();
    assertThat(collapsible(plain)).isEmpty();
  }

  @Test
  void reportsAnExceptBodyThatMatchesThePattern() {
    final SyntaxNode attempt = attempt(List.of(simple("f()")), List.of(handler("continue")));

    assertThat(handlers(attempt, "continue", false)).hasSize(1);
    assertThat(handlers(attempt, PASS, false)).isEmpty();
  }

  @Test
  void reportsEachMatchingHandlerUnlessSole() {
    final SyntaxNode attempt =
        attempt(List.of(simple("f()")), List.of(handler(PASS), handler(PASS)));

    assertThat(handlers(attempt, PASS, false)).hasSize(2);
    assertThat(handlers(attempt, PASS, true)).isEmpty();
  }

  @Test
  void soleNeedsOneStatementOneHandlerAndNoElseOrFinally() {
    final SyntaxNode plain = attempt(List.of(simple("f()")), List.of(handler(PASS)));
    final SyntaxNode two = attempt(List.of(simple("f()"), simple("g()")), List.of(handler(PASS)));
    final SyntaxNode withElse =
        attempt(List.of(simple("f()")), List.of(handler(PASS), clause("else")));
    final SyntaxNode withFinally =
        attempt(List.of(simple("f()")), List.of(handler(PASS), clause("finally")));

    assertThat(handlers(plain, PASS, true)).hasSize(1);
    assertThat(handlers(two, PASS, true)).isEmpty();
    assertThat(handlers(withElse, PASS, true)).isEmpty();
    assertThat(handlers(withFinally, PASS, true)).isEmpty();
  }

  @Test
  void readsTheStatementsOfAOneLineBlock() {
    final SyntaxNode both =
        Layout.node("simple_stmts", simple("a"), layout.token(";", ";"), simple("b"));
    final SyntaxNode block = Layout.node(BLOCK, both);

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

  private SyntaxNode simple(final String text) {
    return Layout.node("simple_stmt", Layout.node("expr_stmt", layout.token("NAME", text)));
  }

  /** A statement of a block that wraps the compound statement {@code inner}. */
  private static SyntaxNode compound(final SyntaxNode inner) {
    return Layout.node(STMT, Layout.node("compound_stmt", inner));
  }

  private SyntaxNode block(final List<SyntaxNode> statements) {
    final List<SyntaxNode> all = new ArrayList<>();
    all.add(layout.token("NEWLINE", "\n"));
    all.add(layout.token("INDENT", "  "));
    for (final SyntaxNode statement : statements) {
      all.add(
          STMT.equals(statement.kind())
              ? statement
              : Layout.node(STMT, Layout.node("simple_stmts", statement)));
    }
    all.add(layout.token("DEDENT", ""));
    return Layout.node(BLOCK, all.toArray(SyntaxNode[]::new));
  }

  /** An if holding the statements; each keyword adds a trailing clause with its own block. */
  private SyntaxNode ifStmt(final List<SyntaxNode> statements, final String... keywords) {
    final List<SyntaxNode> parts = new ArrayList<>();
    parts.add(layout.token("if", "if"));
    parts.add(layout.token("NAME", "a"));
    parts.add(layout.token(COLON, COLON));
    parts.add(block(statements));
    for (final String keyword : keywords) {
      parts.addAll(clauseParts(keyword));
    }
    return Layout.node("if_stmt", parts.toArray(SyntaxNode[]::new));
  }

  private List<SyntaxNode> clauseParts(final String keyword) {
    return List.of(
        layout.token(keyword, keyword), layout.token(COLON, COLON), block(List.of(simple(PASS))));
  }

  /** A try holding the statements, followed by the flat clause parts of handlers and clauses. */
  private SyntaxNode attempt(final List<SyntaxNode> statements, final List<List<SyntaxNode>> tail) {
    final List<SyntaxNode> parts = new ArrayList<>();
    parts.add(layout.token("try", "try"));
    parts.add(layout.token(COLON, COLON));
    parts.add(block(statements));
    for (final List<SyntaxNode> part : tail) {
      parts.addAll(part);
    }
    return Layout.node("try_stmt", parts.toArray(SyntaxNode[]::new));
  }

  private List<SyntaxNode> handler(final String body) {
    return List.of(
        Layout.node("except_clause", layout.token("except", "except")),
        layout.token(COLON, COLON),
        block(List.of(simple(body))));
  }

  private List<SyntaxNode> clause(final String keyword) {
    return clauseParts(keyword);
  }
}
