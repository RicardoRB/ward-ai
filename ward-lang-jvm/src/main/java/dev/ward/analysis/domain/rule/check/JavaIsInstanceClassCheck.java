package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code Class.isInstance} called with a {@code Class} argument, which tests whether the
 * receiver is {@code Class} itself, as Error Prone's IsInstanceOfClass does. A call whose facts are
 * unknown is never reported.
 */
public final class JavaIsInstanceClassCheck implements RuleCheck {

  private static final String CLASS = "java.lang.Class";
  private static final String METHOD = "isInstance";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaIsInstanceClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
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
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      if (flagged(visit.node(), types)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean flagged(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    final List<SyntaxNode> arguments = JavaCalls.arguments(call);
    return target.isPresent()
        && CLASS.equals(target.get().declaringType().name())
        && METHOD.equals(target.get().name())
        && arguments.size() == 1
        && types
            .typeOf(arguments.get(0).span())
            .filter(JavaIsInstanceClassCheck::isClass)
            .isPresent();
  }

  private static boolean isClass(final TypeRef type) {
    return CLASS.equals(type.name()) && type.arrayDepth() == 0;
  }
}
