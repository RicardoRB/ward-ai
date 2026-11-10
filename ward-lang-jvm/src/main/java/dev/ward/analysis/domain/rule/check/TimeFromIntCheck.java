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
 * Reports an absolute time built from an {@code int} product: {@code new Date(n * 1000)}, {@code
 * Instant.ofEpochMilli(n * 1000)} or {@code setTime(n * 1000)}. The product overflows in {@code
 * int} before it is widened to {@code long}. It reports nothing when the type is unknown.
 */
public final class TimeFromIntCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String CREATOR = "creator";
  private static final int PRODUCT_PARTS = 3;
  private static final Set<String> TIME_METHODS =
      Set.of("ofEpochMilli", "ofEpochSecond", "setTime");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the time
   */
  public TimeFromIntCheck(final String message) {
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
        .filter(time -> argument(time).filter(arg -> intProduct(arg, types)).isPresent())
        .map(time -> CheckSupport.at(time.span(), message))
        .toList();
  }

  /** Returns the argument of {@code new Date(arg)} or of a call of a time method. */
  private static Optional<SyntaxNode> argument(final SyntaxNode time) {
    final Optional<SyntaxNode> creator =
        time.children().stream().filter(c -> CREATOR.equals(c.kind())).findFirst();
    if (creator.isPresent()) {
      final boolean date =
          SyntaxWalk.descendants(creator.get()).stream()
              .anyMatch(
                  node ->
                      "createdName".equals(node.kind())
                          && SyntaxWalk.text(node).endsWith("Date")
                          && !SyntaxWalk.text(node).endsWith("SqlDate"));
      return date ? onlyArgument(creator.get()) : Optional.empty();
    }
    return time.children().stream()
        .filter(c -> METHOD_CALL.equals(c.kind()))
        .filter(
            c ->
                c.children().stream()
                    .anyMatch(
                        id ->
                            "identifier".equals(id.kind())
                                && TIME_METHODS.contains(SyntaxWalk.text(id))))
        .findFirst()
        .flatMap(TimeFromIntCheck::onlyArgument);
  }

  private static boolean intProduct(final SyntaxNode argument, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = argument.children();
    return parts.size() == PRODUCT_PARTS
        && "*".equals(SyntaxWalk.text(parts.get(1)))
        && types
            .typeOf(argument.span())
            .filter(TypeRef::primitive)
            .filter(type -> "int".equals(type.name()))
            .isPresent();
  }

  private static Optional<SyntaxNode> onlyArgument(final SyntaxNode owner) {
    final List<SyntaxNode> arguments =
        SyntaxWalk.descendants(owner).stream()
            .filter(node -> "expressionList".equals(node.kind()))
            .findFirst()
            .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
            .orElse(List.of());
    return arguments.size() == 1 ? Optional.of(arguments.get(0)) : Optional.empty();
  }
}
