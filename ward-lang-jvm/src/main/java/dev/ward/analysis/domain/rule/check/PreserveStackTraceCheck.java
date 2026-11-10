package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code throw} inside a catch block that does not pass the caught exception itself: as
 * an argument of a constructor, or to {@code initCause}. Passing only its message loses the trace.
 */
public final class PreserveStackTraceCheck implements UnitCheck {

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

  private static List<SyntaxNode> throwsIn(final SyntaxNode clause) {
    return SyntaxWalk.descendants(clause).stream()
        .filter(n -> n.children().stream().anyMatch(c -> THROW.equals(c.kind())))
        .toList();
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
