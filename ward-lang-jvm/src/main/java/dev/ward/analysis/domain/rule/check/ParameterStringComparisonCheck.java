package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports {@code ==} and {@code !=} between a {@code String} parameter of the enclosing method and
 * another {@code String}. A comparison with {@code null} is not reported, and neither is one whose
 * types are unknown.
 */
public final class ParameterStringComparisonCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String STRING = "java.lang.String";
  private static final Set<String> OPERATORS = Set.of("'=='", "'!='");
  private static final Set<String> METHODS = Set.of("methodDeclaration", "constructorDeclaration");
  private static final int OPERANDS = 3;
  private static final int RIGHT = 2;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the comparison
   */
  public ParameterStringComparisonCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final List<SyntaxNode> parts = visit.node().children();
      if (parts.size() == OPERANDS
          && OPERATORS.contains(parts.get(1).kind())
          && isString(parts.get(0), types)
          && isString(parts.get(RIGHT), types)
          && (parameter(parts.get(0), visit) || parameter(parts.get(RIGHT), visit))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isString(final SyntaxNode operand, final UnitTypeFacts types) {
    return types
        .typeOf(operand.span())
        .filter(ParameterStringComparisonCheck::stringType)
        .isPresent();
  }

  private static boolean stringType(final TypeRef type) {
    return STRING.equals(type.name()) && type.arrayDepth() == 0;
  }

  /** Tells whether the operand is a bare name that a parameter of the enclosing method declares. */
  private static boolean parameter(final SyntaxNode operand, final SyntaxWalk.Visit visit) {
    final String name = SyntaxWalk.text(operand);
    return name.chars().allMatch(Character::isJavaIdentifierPart)
        && visit.ancestors().stream()
            .filter(ancestor -> METHODS.contains(ancestor.kind()))
            .reduce((outer, inner) -> inner)
            .map(method -> declares(method, name))
            .orElse(false);
  }

  private static boolean declares(final SyntaxNode method, final String name) {
    return method.children().stream()
        .filter(child -> "formalParameters".equals(child.kind()))
        .flatMap(list -> SyntaxWalk.descendants(list).stream())
        .filter(node -> "variableDeclaratorId".equals(node.kind()))
        .anyMatch(node -> name.equals(SyntaxWalk.text(node)));
  }
}
