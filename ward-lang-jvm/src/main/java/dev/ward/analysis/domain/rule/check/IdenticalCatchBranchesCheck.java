package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a catch clause whose block is the text of an earlier catch clause of the same try
 * statement, as PMD's IdenticalCatchBranches does. Without type facts it cannot tell whether the
 * two exception types are related by subclassing, so a clause that catches one of the broad JDK
 * supertypes is skipped.
 */
public final class IdenticalCatchBranchesCheck implements RuleCheck {

  private static final String CATCH = "catchClause";
  private static final Set<String> BROAD =
      Set.of(
          "Throwable",
          "Exception",
          "RuntimeException",
          "Error",
          "IOException",
          "ReflectiveOperationException",
          "SQLException");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the exception type of the earlier clause
   */
  public IdenticalCatchBranchesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      final List<SyntaxNode> clauses = JavaNodes.kids(visit.node(), CATCH);
      for (int index = 1; index < clauses.size(); index++) {
        final SyntaxNode clause = clauses.get(index);
        findEarlier(clauses.subList(0, index), clause)
            .ifPresent(earlier -> matches.add(report(clause, earlier)));
      }
    }
    return List.copyOf(matches);
  }

  private RuleMatch report(final SyntaxNode clause, final SyntaxNode earlier) {
    return CheckSupport.at(clause.span(), CheckSupport.fill(message, types(earlier), ""));
  }

  private static Optional<SyntaxNode> findEarlier(
      final List<SyntaxNode> before, final SyntaxNode clause) {
    final String body = body(clause);
    if (broad(clause)) {
      return Optional.empty();
    }
    return before.stream()
        .filter(earlier -> !broad(earlier) && body(earlier).equals(body))
        .findFirst();
  }

  private static String body(final SyntaxNode clause) {
    return JavaNodes.kid(clause, "block").map(SyntaxWalk::text).orElse("");
  }

  private static String types(final SyntaxNode clause) {
    return JavaNodes.kid(clause, "catchType").map(SyntaxWalk::text).orElse("");
  }

  private static boolean broad(final SyntaxNode clause) {
    return JavaNodes.kid(clause, "catchType").stream()
        .flatMap(type -> JavaNodes.kids(type, "qualifiedName").stream())
        .anyMatch(name -> BROAD.contains(lastSegment(SyntaxWalk.text(name))));
  }

  private static String lastSegment(final String name) {
    return name.substring(name.lastIndexOf('.') + 1);
  }
}
