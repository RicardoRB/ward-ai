package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code throw new} inside a catch block that does not pass the caught exception itself:
 * as an argument of a constructor, or to {@code initCause}. Passing only its message loses the
 * trace.
 */
public final class PreserveStackTraceCheck implements UnitCheck {

  private static final String CATCH = "catchClause";
  private static final String THROW = "'throw'";
  private static final String ARGUMENTS = "arguments";
  private static final String EXPRESSION = "expression";
  private static final String EXPRESSION_LIST = "expressionList";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the throw statement
   */
  public PreserveStackTraceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("catchClause"))) {
      final SyntaxNode clause = visit.node();
      final String caught = caughtName(clause);
      for (final SyntaxNode throwing : throwsIn(clause)) {
        if (!preserves(throwing, caught)) {
          matches.add(CheckSupport.at(throwing.span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static String caughtName(final SyntaxNode clause) {
    return clause.children().stream()
        .filter(c -> "identifier".equals(c.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  /** The statements that throw a new exception; a nested catch clause answers for its own. */
  private static List<SyntaxNode> throwsIn(final SyntaxNode clause) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : clause.children()) {
      collect(child, found);
    }
    return found;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    if (CATCH.equals(node.kind())) {
      return;
    }
    if (isNewThrow(node)) {
      found.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found);
    }
  }

  private static boolean isNewThrow(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return parts.size() > 1
        && THROW.equals(parts.get(0).kind())
        && (SyntaxWalk.text(parts.get(1)).startsWith("new")
            || parts.get(1).children().stream().anyMatch(c -> "creator".equals(c.kind())));
  }

  private static boolean preserves(final SyntaxNode throwing, final String caught) {
    return SyntaxWalk.descendants(throwing).stream()
        .filter(n -> ARGUMENTS.equals(n.kind()))
        .flatMap(args -> argumentTexts(args).stream())
        .anyMatch(caught::equals);
  }

  private static List<String> argumentTexts(final SyntaxNode arguments) {
    final List<String> texts = new ArrayList<>();
    for (final SyntaxNode child : arguments.children()) {
      if (EXPRESSION.equals(child.kind())) {
        texts.add(SyntaxWalk.text(child));
      } else if (EXPRESSION_LIST.equals(child.kind())) {
        child.children().stream()
            .filter(e -> EXPRESSION.equals(e.kind()))
            .forEach(e -> texts.add(SyntaxWalk.text(e)));
      }
    }
    return texts;
  }
}
