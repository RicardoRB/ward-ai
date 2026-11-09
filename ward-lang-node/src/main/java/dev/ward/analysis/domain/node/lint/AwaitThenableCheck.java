package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code await} of a value whose known type is a primitive, and a primitive element of
 * the array that a {@code Promise} aggregator receives. With {@code allow-mixed-promise-arrays}, an
 * aggregator whose array also holds a value that may be a promise is not reported.
 */
public final class AwaitThenableCheck extends TypedCheck {

  private static final String AWAIT = "await";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String ARGUMENT = "argument";
  private static final String ARRAY = "arrayLiteral";
  private static final String ELEMENT_LIST = "elementList";
  private static final String ELEMENT = "arrayElement";
  private static final Set<String> AGGREGATORS =
      Set.of("Promise.all", "Promise.allSettled", "Promise.any", "Promise.race");

  private final boolean allowMixedPromiseArrays;
  private final String message;

  /** Creates the check with the rule's option and message. */
  public AwaitThenableCheck(final boolean allowMixedPromiseArrays, final String message) {
    super();
    this.allowMixedPromiseArrays = allowMixedPromiseArrays;
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    awaits(root, types, out);
    aggregators(root, types, out);
    return out;
  }

  private void awaits(final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == 2 && SyntaxText.isToken(parts.get(0), AWAIT)) {
        final Optional<TypeRef> operand = typeOf(types, parts.get(1));
        if (operand.isPresent() && isPrimitive(operand.get()) && !isUntyped(operand.get())) {
          out.add(new RuleMatch(node.span(), message));
        }
      }
    }
  }

  private void aggregators(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode call : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = call.children();
      if (parts.size() == 2
          && ARGUMENTS.equals(parts.get(1).kind())
          && AGGREGATORS.contains(SyntaxText.of(parts.get(0)))) {
        aggregatorInput(parts.get(1), types, out);
      }
    }
  }

  /** Reports the primitive elements of the array literal that an aggregator receives first. */
  private void aggregatorInput(
      final SyntaxNode arguments, final UnitTypeFacts types, final List<RuleMatch> out) {
    final List<SyntaxNode> elements = firstArrayElements(arguments);
    final List<TypeRef> elementTypes = new ArrayList<>();
    for (final SyntaxNode element : elements) {
      typeOf(types, element).ifPresent(elementTypes::add);
    }
    if (allowMixedPromiseArrays && elementTypes.stream().anyMatch(TypedCheck::isMaybeAwaitable)) {
      return;
    }
    for (final SyntaxNode element : elements) {
      final Optional<TypeRef> type = typeOf(types, element);
      if (type.isPresent() && isPrimitive(type.get()) && !isUntyped(type.get())) {
        out.add(new RuleMatch(element.span(), message));
      }
    }
  }

  /** Returns the value expressions of the array literal that is the first argument, or empty. */
  private static List<SyntaxNode> firstArrayElements(final SyntaxNode arguments) {
    final List<SyntaxNode> lists = SyntaxText.childrenOf(arguments, ARGUMENT_LIST);
    if (lists.isEmpty()) {
      return List.of();
    }
    final List<SyntaxNode> first = SyntaxText.childrenOf(lists.get(0), ARGUMENT);
    if (first.isEmpty()) {
      return List.of();
    }
    final List<SyntaxNode> values = SyntaxText.childrenOf(first.get(0), EXPRESSION);
    final List<SyntaxNode> arrays = values.isEmpty() ? List.of() : arrayOf(values.get(0));
    return arrays.isEmpty() ? List.of() : elementsOf(arrays.get(0));
  }

  private static List<SyntaxNode> arrayOf(final SyntaxNode expression) {
    return SyntaxText.childrenOf(expression, ARRAY);
  }

  private static List<SyntaxNode> elementsOf(final SyntaxNode array) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode list : SyntaxText.childrenOf(array, ELEMENT_LIST)) {
      for (final SyntaxNode element : SyntaxText.childrenOf(list, ELEMENT)) {
        SyntaxText.childrenOf(element, EXPRESSION).forEach(out::add);
      }
    }
    return out;
  }
}
