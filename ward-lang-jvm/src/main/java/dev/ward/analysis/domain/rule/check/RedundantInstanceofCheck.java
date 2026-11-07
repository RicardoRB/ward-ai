package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code x instanceof T} when the static type of {@code x} is exactly {@code T}, so the
 * test is true for every value but null. It reports nothing when a type is unknown.
 */
public final class RedundantInstanceofCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final String INSTANCEOF = "instanceof";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION, TYPE_TYPE), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the test
   */
  public RedundantInstanceofCheck(final String message) {
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
      final UnitTypeFacts facts,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(test -> redundant(test, facts))
        .map(test -> CheckSupport.at(test.span(), message))
        .toList();
  }

  private static boolean redundant(final SyntaxNode test, final UnitTypeFacts facts) {
    final List<SyntaxNode> parts = test.children();
    for (int i = 1; i + 1 < parts.size(); i++) {
      if (INSTANCEOF.equals(SyntaxWalk.text(parts.get(i)))) {
        return same(parts.get(0), parts.get(i + 1), facts);
      }
    }
    return false;
  }

  private static boolean same(
      final SyntaxNode operand, final SyntaxNode target, final UnitTypeFacts facts) {
    final Optional<SyntaxNode> type =
        TYPE_TYPE.equals(target.kind())
            ? Optional.of(target)
            : SyntaxWalk.descendants(target).stream()
                .filter(node -> TYPE_TYPE.equals(node.kind()))
                .findFirst();
    final Optional<TypeRef> declared = type.flatMap(node -> facts.typeOf(node.span()));
    final Optional<TypeRef> actual = facts.typeOf(operand.span());
    return declared.isPresent()
        && actual.isPresent()
        && !actual.get().primitive()
        && actual.get().name().equals(declared.get().name())
        && actual.get().arrayDepth() == declared.get().arrayDepth();
  }
}
