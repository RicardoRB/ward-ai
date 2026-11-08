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
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code compareTo} or {@code compare} that returns only constants and so breaks the
 * contract, as Error Prone's ComparisonContractViolated does: a comparison that never returns zero,
 * or a comparator that returns only negative values (or only positive ones) besides zero. A method
 * that returns anything that is not a constant integer is not reported; a constant is a literal or
 * an expression whose constant value is known. A method with a lambda or a nested class is not
 * read, and neither is a class whose supertypes are unknown.
 */
public final class ComparisonContractCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final Set<String> TYPES =
      Set.of("classDeclaration", "enumDeclaration", "recordDeclaration");
  private static final String COMPARABLE = "java.lang.Comparable";
  private static final String COMPARATOR = "java.util.Comparator";
  private static final int CONDITIONAL_PARTS = 5;
  private static final int BINARY_PARTS = 3;
  private static final int TRUE_BRANCH = 2;
  private static final int SINGLE = 1;
  private static final int FALSE_BRANCH = 4;
  private static final Set<String> NESTED = Set.of("lambdaExpression", "classBody");
  private static final Set<String> ORDERING = Set.of("'<'", "'<='", "'>'", "'>='");
  private static final FactNeed NEED =
      new FactNeed(
          Set.of("classDeclaration", "enumDeclaration", "recordDeclaration", EXPRESSION),
          Set.of(FactKind.MEMBERS, FactKind.TYPE, FactKind.CONSTANT));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method or comparison
   */
  public ComparisonContractCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TYPES)) {
      final Set<String> ancestors = ancestors(visit.node(), types);
      for (final TypeMethods.Member member : TypeMethods.of(visit.node())) {
        final boolean comparable =
            ancestors.contains(COMPARABLE) && ComparisonMethods.isCompareTo(member);
        final boolean comparator =
            ancestors.contains(COMPARATOR) && ComparisonMethods.isCompare(member);
        if (comparable || comparator) {
          judge(member, comparator, types).ifPresent(matches::add);
        }
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> ancestors(final SyntaxNode type, final UnitTypeFacts types) {
    final Set<String> names = new java.util.HashSet<>();
    types
        .membersOf(type.span())
        .ifPresent(members -> members.ancestors().forEach(a -> names.add(a.name())));
    return names;
  }

  private Optional<RuleMatch> judge(
      final TypeMethods.Member member, final boolean comparator, final UnitTypeFacts types) {
    final Optional<SyntaxNode> body = JavaNodes.kid(member.method(), "methodBody");
    if (body.isEmpty() || hasNested(body.get())) {
      return Optional.empty();
    }
    final Set<ComparisonOutcome> seen = ComparisonOutcome.returned(body.get(), types);
    if (seen.isEmpty() || seen.contains(ComparisonOutcome.NONCONSTANT)) {
      return Optional.empty();
    }
    if (!seen.contains(ComparisonOutcome.ZERO)) {
      return Optional.of(CheckSupport.at(location(member, body.get(), types), message));
    }
    final boolean asymmetric =
        seen.contains(ComparisonOutcome.NEGATIVE) != seen.contains(ComparisonOutcome.POSITIVE);
    return comparator && asymmetric
        ? Optional.of(CheckSupport.at(member.declaration().span(), message))
        : Optional.empty();
  }

  private static boolean hasNested(final SyntaxNode body) {
    return SyntaxWalk.descendants(body).stream().anyMatch(n -> NESTED.contains(n.kind()));
  }

  /** Returns where the report goes: the comparison when it can be replaced, else the method. */
  private static dev.ward.analysis.domain.ir.Span location(
      final TypeMethods.Member member, final SyntaxNode body, final UnitTypeFacts types) {
    return replaceable(body, types).map(SyntaxNode::span).orElse(member.declaration().span());
  }

  /** Returns the conditional of {@code return a < b ? -1 : 1;} over ints or longs. */
  private static Optional<SyntaxNode> replaceable(
      final SyntaxNode body, final UnitTypeFacts types) {
    final List<SyntaxNode> statements =
        SyntaxWalk.descendants(body).stream()
            .filter(n -> "blockStatement".equals(n.kind()))
            .toList();
    if (statements.size() != SINGLE) {
      return Optional.empty();
    }
    final List<SyntaxNode> parts =
        SyntaxWalk.descendants(statements.get(0)).stream()
            .filter(n -> STATEMENT.equals(n.kind()))
            .findFirst()
            .map(SyntaxNode::children)
            .orElse(List.of());
    final Optional<SyntaxNode> conditional =
        parts.size() > 1 ? Optional.of(parts.get(1)) : Optional.empty();
    return conditional.filter(c -> orderedPrimitives(c, types));
  }

  private static boolean orderedPrimitives(
      final SyntaxNode conditional, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = conditional.children();
    if (parts.size() != CONDITIONAL_PARTS) {
      return false;
    }
    final List<SyntaxNode> condition = parts.get(0).children();
    final ComparisonOutcome whenTrue = ComparisonOutcome.classify(parts.get(TRUE_BRANCH), types);
    final ComparisonOutcome whenFalse = ComparisonOutcome.classify(parts.get(FALSE_BRANCH), types);
    return condition.size() == BINARY_PARTS
        && ORDERING.contains(condition.get(1).kind())
        && (whenTrue == ComparisonOutcome.NEGATIVE && whenFalse == ComparisonOutcome.POSITIVE
            || whenTrue == ComparisonOutcome.POSITIVE && whenFalse == ComparisonOutcome.NEGATIVE)
        && types
            .typeOf(condition.get(0).span())
            .filter(TypeRef::primitive)
            .filter(t -> "int".equals(t.name()) || "long".equals(t.name()))
            .isPresent();
  }
}
