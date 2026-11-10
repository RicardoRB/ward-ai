package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of a static method through an instance expression, as Error Prone's
 * StaticQualifiedUsingExpression does. A receiver with no known value type, such as a class name,
 * is never reported.
 */
public final class JavaStaticQualifiedCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public JavaStaticQualifiedCheck(final String message) {
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
      final SyntaxNode call = visit.node();
      types
          .callTarget(call.span())
          .filter(CallTarget::isStatic)
          .filter(target -> instanceReceiver(call, types))
          .ifPresent(
              target ->
                  matches.add(
                      CheckSupport.at(call.span(), CheckSupport.fill(message, target.name(), ""))));
    }
    return List.copyOf(matches);
  }

  private static boolean instanceReceiver(final SyntaxNode call, final UnitTypeFacts types) {
    return JavaCalls.receiver(call).filter(r -> types.typeOf(r.span()).isPresent()).isPresent();
  }
}
