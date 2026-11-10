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
import java.util.Optional;
import java.util.Set;

/**
 * Reports a conditional expression passed in the varargs position of a call when one branch is an
 * array and the other is not, so the array branch is wrapped as a single element, as Error Prone's
 * InexactVarargsConditional does. A call or branch whose facts are unknown is never reported.
 */
public final class JavaVarargsConditionalCheck implements RuleCheck {

  private static final String CONDITION_MARK = "'?'";
  private static final int CONDITIONAL_PARTS = 5;
  private static final int THEN = 2;
  private static final int ELSE = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaVarargsConditionalCheck(final String message) {
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
      conditional(visit.node(), types)
          .ifPresent(node -> matches.add(CheckSupport.at(node.span(), message)));
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> conditional(
      final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span()).filter(CallTarget::isVarargs);
    final List<SyntaxNode> arguments = JavaCalls.arguments(call);
    if (target.isEmpty() || arguments.size() != target.get().parameters().size()) {
      return Optional.empty();
    }
    final SyntaxNode last = arguments.get(arguments.size() - 1);
    final List<SyntaxNode> parts = last.children();
    final boolean ternary =
        parts.size() == CONDITIONAL_PARTS && CONDITION_MARK.equals(parts.get(1).kind());
    return ternary && mixed(parts.get(THEN), parts.get(ELSE), types)
        ? Optional.of(last)
        : Optional.empty();
  }

  private static boolean mixed(
      final SyntaxNode first, final SyntaxNode second, final UnitTypeFacts types) {
    final Optional<Boolean> a = types.typeOf(first.span()).map(type -> type.arrayDepth() > 0);
    final Optional<Boolean> b = types.typeOf(second.span()).map(type -> type.arrayDepth() > 0);
    return a.isPresent() && b.isPresent() && !a.get().equals(b.get());
  }
}
