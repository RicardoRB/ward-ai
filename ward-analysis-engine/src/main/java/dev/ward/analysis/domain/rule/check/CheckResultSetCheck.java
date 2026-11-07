package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.TypeNeed;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a {@code next()} call on a {@code ResultSet} whose boolean result is dropped. */
public final class CheckResultSetCheck implements RuleCheck {

  private static final String RESULT_SET = "java.sql.ResultSet";
  private static final String NEXT = "next";
  private static final String STATEMENT = "statement";
  private static final String EXPRESSION = "expression";
  private static final String SEMICOLON = "';'";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public CheckResultSetCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public Set<TypeNeed> typeNeeds() {
    return Set.of(TypedChecks.CALLS);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final dev.ward.analysis.domain.rule.FlowView flow,
      final dev.ward.analysis.domain.rule.ScopeView scopes,
      final dev.ward.analysis.domain.ir.SourceUnit unit,
      final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENT))) {
      if (!isExpressionStatement(visit.node())) {
        continue;
      }
      for (final SyntaxNode child : visit.node().children()) {
        if (EXPRESSION.equals(child.kind()) && ignoresNext(child, types)) {
          matches.add(CheckSupport.at(child.span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isExpressionStatement(final SyntaxNode statement) {
    return statement.children().stream().anyMatch(child -> SEMICOLON.equals(child.kind()));
  }

  private static boolean ignoresNext(final SyntaxNode call, final UnitTypeFacts types) {
    return types
        .callTarget(call.span())
        .filter(target -> RESULT_SET.equals(target.declaringType().name()))
        .filter(target -> NEXT.equals(target.name()))
        .isPresent();
  }
}
