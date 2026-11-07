package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each call whose resolved target meets every given criterion: the declaring type, the
 * method name, the type of the receiver, and a pattern over the compact text of the call. A call
 * whose facts are unknown is never reported.
 */
public final class CallMatchCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String SEMICOLON = "';'";
  private static final int STATEMENT_PARTS = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final Criteria criteria;
  private final String message;

  /**
   * What a call must meet; an empty set or pattern places no limit.
   *
   * @param declaringTypes qualified names of the type that declares the called method
   * @param names names of the called method
   * @param receiverTypes qualified names of the receiver's type
   * @param receiverArray whether the receiver must be an array
   * @param text a pattern found in the compact text of the call, empty for any text
   * @param ignored whether the call must be a statement, so that its result is dropped
   * @param argumentTypes qualified names of the type of at least one argument
   * @param argumentPrimitiveArray whether at least one argument must be an array of primitives
   */
  public record Criteria(
      Set<String> declaringTypes,
      Set<String> names,
      Set<String> receiverTypes,
      boolean receiverArray,
      Pattern text,
      boolean ignored,
      Set<String> argumentTypes,
      boolean argumentPrimitiveArray) {

    /** Copies the sets. */
    public Criteria {
      declaringTypes = Set.copyOf(declaringTypes);
      names = Set.copyOf(names);
      receiverTypes = Set.copyOf(receiverTypes);
      argumentTypes = Set.copyOf(argumentTypes);
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported call meets
   * @param message the message reported at the call
   */
  public CallMatchCheck(final Criteria criteria, final String message) {
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
      if (meets(call, visit, types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean meets(
      final SyntaxNode call, final SyntaxWalk.Visit visit, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    return target.isPresent()
        && declares(target.get())
        && (criteria.names().isEmpty() || criteria.names().contains(target.get().name()))
        && receiverMeets(call, types)
        && argumentsMeet(call, types)
        && criteria.text().matcher(SyntaxWalk.text(call)).find()
        && (!criteria.ignored() || isStatement(call, visit));
  }

  private boolean declares(final CallTarget target) {
    return criteria.declaringTypes().isEmpty()
        || criteria.declaringTypes().contains(target.declaringType().name());
  }

  private boolean receiverMeets(final SyntaxNode call, final UnitTypeFacts types) {
    if (criteria.receiverTypes().isEmpty() && !criteria.receiverArray()) {
      return true;
    }
    final List<SyntaxNode> parts = call.children();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind())) {
      return false;
    }
    final Optional<TypeRef> receiver = types.typeOf(parts.get(0).span());
    return receiver.isPresent()
        && (!criteria.receiverArray() || receiver.get().arrayDepth() > 0)
        && (criteria.receiverTypes().isEmpty()
            || criteria.receiverTypes().contains(receiver.get().name()));
  }

  /** Tells whether the call is a whole expression statement, as in {@code call();}. */
  private boolean argumentsMeet(final SyntaxNode call, final UnitTypeFacts types) {
    return criteria.argumentTypes().isEmpty() && !criteria.argumentPrimitiveArray()
        || arguments(call).stream()
            .map(argument -> types.typeOf(argument.span()))
            .flatMap(Optional::stream)
            .anyMatch(this::argumentMeets);
  }

  private boolean argumentMeets(final TypeRef type) {
    final boolean array = type.arrayDepth() > 0;
    final boolean typeOk =
        criteria.argumentTypes().isEmpty()
            || !array && criteria.argumentTypes().contains(type.name());
    final boolean arrayOk = !criteria.argumentPrimitiveArray() || array && type.primitive();
    return typeOk && arrayOk;
  }

  /** The argument expressions of the call, in order; none when the call has no method part. */
  private static List<SyntaxNode> arguments(final SyntaxNode call) {
    return call.children().stream()
        .filter(part -> METHOD_CALL.equals(part.kind()))
        .flatMap(method -> SyntaxWalk.descendants(method).stream())
        .filter(node -> EXPRESSION_LIST.equals(node.kind()))
        .findFirst()
        .map(SyntaxNode::children)
        .orElse(List.of())
        .stream()
        .filter(node -> EXPRESSION.equals(node.kind()))
        .toList();
  }

  private static boolean isStatement(final SyntaxNode call, final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.isEmpty()) {
      return false;
    }
    final SyntaxNode parent = up.get(up.size() - 1);
    final List<SyntaxNode> parts = parent.children();
    return STATEMENT.equals(parent.kind())
        && parts.size() == STATEMENT_PARTS
        && parts.get(0).span().equals(call.span())
        && SEMICOLON.equals(parts.get(1).kind());
  }
}
