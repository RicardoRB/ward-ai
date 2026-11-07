package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports each assignment to a catch parameter inside its catch block. */
public final class AvoidReassigningCatchVariablesCheck implements UnitCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the catch parameter
   */
  public AvoidReassigningCatchVariablesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("catchClause"))) {
      final SyntaxNode clause = visit.node();
      final String name =
          clause.children().stream()
              .filter(c -> "identifier".equals(c.kind()))
              .findFirst()
              .map(SyntaxWalk::text)
              .orElse("");
      for (final SyntaxNode assignment : Assignments.in(clause)) {
        if (name.equals(Assignments.target(assignment))) {
          matches.add(CheckSupport.at(assignment.span(), CheckSupport.fill(message, name, "")));
        }
      }
    }
    return List.copyOf(matches);
  }
}
