package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a cast {@code (To) value} where the declared type of {@code value} is one of the {@code
 * from} types and the cast type, by its simple name, is one of the {@code to} names. It reports
 * nothing when the type of the value is unknown.
 */
public final class CastTypesCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final int CAST_PARTS = 4;
  private static final int CAST_TYPE = 1;
  private static final int CAST_OPERAND = 3;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final Set<String> from;
  private final Set<String> to;
  private final String message;

  /**
   * Creates the check.
   *
   * @param from the qualified names of the types of the cast value that are reported
   * @param to the simple names of the cast types that are reported
   * @param message the message reported at the cast
   */
  public CastTypesCheck(final Set<String> from, final Set<String> to, final String message) {
    this.from = Set.copyOf(from);
    this.to = Set.copyOf(to);
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(cast -> isReportedCast(cast, types))
        .map(cast -> CheckSupport.at(cast.span(), message))
        .toList();
  }

  private boolean isReportedCast(final SyntaxNode cast, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = cast.children();
    return parts.size() == CAST_PARTS
        && TYPE_TYPE.equals(parts.get(CAST_TYPE).kind())
        && to.contains(ClassHeaders.simple(SyntaxWalk.text(parts.get(CAST_TYPE))))
        && types
            .typeOf(parts.get(CAST_OPERAND).span())
            .filter(type -> type.arrayDepth() == 0 && from.contains(type.name()))
            .isPresent();
  }
}
