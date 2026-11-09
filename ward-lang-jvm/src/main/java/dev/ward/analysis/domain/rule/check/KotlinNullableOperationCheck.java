package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.Nullness;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a Kotlin {@code name!!} or {@code name?.member} whose name has the given nullness. A name
 * whose nullness is unknown, such as one smart-cast by an earlier check, is never reported.
 */
public final class KotlinNullableOperationCheck implements RuleCheck {
  private static final String POSTFIX = KotlinNullableReceiver.POSTFIX;

  /** The operations a name can get that the nullness rules judge. */
  public enum Operation {
    /** The not-null assertion {@code !!}. */
    NOT_NULL,
    /** The safe call {@code ?.}. */
    SAFE_CALL;

    /** Returns whether the operation's text is this operation. */
    boolean matches(final String text) {
      return switch (this) {
        case NOT_NULL -> "!!".equals(text);
        case SAFE_CALL -> text.startsWith("?.");
      };
    }
  }

  private final Operation operation;
  private final Nullness wanted;
  private final String message;

  /**
   * Creates the check.
   *
   * @param operation the operation the report is about
   * @param wanted the nullness of the name that makes the operation a finding
   * @param message the message reported at the expression
   */
  public KotlinNullableOperationCheck(
      final Operation operation, final Nullness wanted, final String message) {
    this.operation = Objects.requireNonNull(operation, "operation");
    this.wanted = Objects.requireNonNull(wanted, "wanted");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return KotlinNullableReceiver.NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(POSTFIX)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(post -> isFinding(post, types))
        .map(post -> CheckSupport.at(post.span(), message))
        .toList();
  }

  private boolean isFinding(final SyntaxNode post, final UnitTypeFacts types) {
    return KotlinNullableReceiver.nullnessOf(post, types, operation::matches)
        .filter(wanted::equals)
        .isPresent();
  }
}
