package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of a varargs method whose last argument is {@code null}, or an array that is a
 * subtype of the varargs array type without being it, as PMD's ConfusingArgumentToVarargsMethod
 * does: the reader cannot tell whether the array or an element of it is passed.
 */
public final class ConfusingArgumentToVarargsMethodCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String NULL = "null";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the element type
   */
  public ConfusingArgumentToVarargsMethodCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final CallTarget target = types.callTarget(call.span()).orElse(null);
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    if (target == null
        || !target.isVarargs()
        || arguments.isEmpty()
        || arguments.size() != target.parameters().size()) {
      return List.of();
    }
    final TypeRef expected = target.parameters().get(arguments.size() - 1);
    final SyntaxNode last = arguments.get(arguments.size() - 1);
    return confusing(last, expected, types)
        ? List.of(CheckSupport.at(call.span(), CheckSupport.fill(message, expected.name(), "")))
        : List.of();
  }

  private static boolean confusing(
      final SyntaxNode last, final TypeRef expected, final UnitTypeFacts types) {
    return NULL.equals(SyntaxWalk.text(last)) || subtypeArray(last, expected, types);
  }

  private static boolean subtypeArray(
      final SyntaxNode last, final TypeRef expected, final UnitTypeFacts types) {
    return types
        .typeOf(last.span())
        .filter(given -> !given.primitive() && given.arrayDepth() >= expected.arrayDepth())
        .filter(
            given ->
                given.arrayDepth() > expected.arrayDepth() || !given.name().equals(expected.name()))
        .isPresent();
  }
}
