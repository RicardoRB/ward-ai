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
 * Reports each call whose resolved target meets the given criteria and whose arguments have a given
 * shape: an odd number of them, a first argument of one of the given types, or the single literal
 * {@code null} in the place of a variadic parameter. A call whose facts are unknown is never
 * reported.
 */
public final class CallArgumentsCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String NULL = "null";
  private static final int PAIR = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported call meets; an empty set places no limit.
   *
   * @param declaringTypes qualified names of the type that declares the called method
   * @param names names of the called method
   * @param argumentTypes qualified names of the type of the first argument
   * @param oddArguments whether the call must pass an odd number of arguments
   * @param nullVarargs whether the last argument must be {@code null} in the place of the variadic
   *     parameter
   */
  public record Criteria(
      Set<String> declaringTypes,
      Set<String> names,
      Set<String> argumentTypes,
      boolean oddArguments,
      boolean nullVarargs) {

    /** Copies the sets. */
    public Criteria {
      declaringTypes = Set.copyOf(declaringTypes);
      names = Set.copyOf(names);
      argumentTypes = Set.copyOf(argumentTypes);
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported call meets
   * @param message the message reported at the call
   */
  public CallArgumentsCheck(final Criteria criteria, final String message) {
    this.criteria = Objects.requireNonNull(criteria, "criteria");
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
      final SyntaxNode call = visit.node();
      final Optional<CallTarget> target = types.callTarget(call.span());
      if (target.isPresent() && meets(call, target.get(), types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean meets(final SyntaxNode call, final CallTarget target, final UnitTypeFacts types) {
    final List<SyntaxNode> arguments = arguments(call);
    return (criteria.declaringTypes().isEmpty()
            || criteria.declaringTypes().contains(target.declaringType().name()))
        && (criteria.names().isEmpty() || criteria.names().contains(target.name()))
        && (!criteria.oddArguments() || arguments.size() % PAIR == 1)
        && (!criteria.nullVarargs() || passesNullForVarargs(arguments, target))
        && (criteria.argumentTypes().isEmpty() || firstArgumentIsOfType(arguments, types));
  }

  private boolean firstArgumentIsOfType(
      final List<SyntaxNode> arguments, final UnitTypeFacts types) {
    return !arguments.isEmpty()
        && types
            .typeOf(arguments.get(0).span())
            .filter(type -> criteria.argumentTypes().contains(type.name()))
            .isPresent();
  }

  private static boolean passesNullForVarargs(
      final List<SyntaxNode> arguments, final CallTarget target) {
    return target.isVarargs()
        && !arguments.isEmpty()
        && arguments.size() == target.parameters().size()
        && NULL.equals(SyntaxWalk.text(arguments.get(arguments.size() - 1)));
  }

  private static List<SyntaxNode> arguments(final SyntaxNode call) {
    return call.children().stream()
        .filter(part -> METHOD_CALL.equals(part.kind()))
        .findFirst()
        .flatMap(
            invoked ->
                SyntaxWalk.descendants(invoked).stream()
                    .filter(node -> EXPRESSION_LIST.equals(node.kind()))
                    .findFirst())
        .map(
            list ->
                list.children().stream().filter(child -> EXPRESSION.equals(child.kind())).toList())
        .orElse(List.of());
  }
}
