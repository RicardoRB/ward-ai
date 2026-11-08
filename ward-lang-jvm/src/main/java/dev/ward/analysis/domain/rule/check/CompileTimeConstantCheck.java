package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an argument of a parameter annotated {@code @CompileTimeConstant} that cannot be a
 * constant because it calls a method or creates an object. An argument that might be a constant,
 * such as a name, is never reported.
 */
public final class CompileTimeConstantCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String CONSTANT = "com.google.errorprone.annotations.CompileTimeConstant";
  private static final Set<String> NON_CONSTANT = Set.of("methodCall", "creator");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the argument
   */
  public CompileTimeConstantCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final Optional<CallTarget> target = types.callTarget(visit.node().span());
      if (target.isPresent()) {
        final List<SyntaxNode> arguments = CallArgumentNodes.all(visit.node());
        for (int index = 0; index < arguments.size(); index++) {
          if (constantRequired(target.get(), index) && cannotBeConstant(arguments.get(index))) {
            matches.add(CheckSupport.at(arguments.get(index).span(), message));
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean constantRequired(final CallTarget target, final int index) {
    return index < target.parameterAnnotations().size()
        && target.parameterAnnotations().get(index).contains(CONSTANT);
  }

  private static boolean cannotBeConstant(final SyntaxNode argument) {
    return NON_CONSTANT.contains(argument.kind())
        || SyntaxWalk.descendants(argument).stream()
            .anyMatch(node -> NON_CONSTANT.contains(node.kind()) || startsWithNew(node));
  }

  private static boolean startsWithNew(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind()) && SyntaxWalk.text(node).startsWith("new");
  }
}
