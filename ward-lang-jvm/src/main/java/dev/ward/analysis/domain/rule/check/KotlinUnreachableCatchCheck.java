package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a {@code catch} clause whose exception type is the type of an earlier clause of the same
 * {@code try}, or a subtype of it. Subtyping reads all ancestors of the type. A type whose
 * hierarchy is unknown is never reported as a subtype.
 */
public final class KotlinUnreachableCatchCheck implements RuleCheck {
  private static final String TRY = "tryExpression";
  private static final String CATCH = "catchBlock";
  private static final String USER_TYPE = "userType";
  private static final FactNeed NEED =
      new FactNeed(Set.of(USER_TYPE), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the unreachable clause
   */
  public KotlinUnreachableCatchCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(TRY)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(tryNode -> unreachable(tryNode, types).stream())
        .map(clause -> CheckSupport.at(clause.span(), message))
        .toList();
  }

  /** Returns the clauses of {@code tryNode} that an earlier clause already catches. */
  private static List<SyntaxNode> unreachable(final SyntaxNode tryNode, final UnitTypeFacts types) {
    final List<SyntaxNode> clauses = new ArrayList<>();
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : tryNode.children()) {
      if (CATCH.equals(child.kind())) {
        if (isShadowed(child, clauses, types)) {
          found.add(child);
        }
        clauses.add(child);
      }
    }
    return found;
  }

  private static boolean isShadowed(
      final SyntaxNode clause, final List<SyntaxNode> earlier, final UnitTypeFacts types) {
    final Optional<SyntaxNode> userType = userTypeOf(clause);
    final Optional<TypeRef> current = userType.flatMap(node -> types.typeOf(node.span()));
    if (current.isEmpty()) {
      return false;
    }
    final Set<String> ancestors =
        userType
            .flatMap(node -> types.membersOf(node.span()))
            .map(KotlinUnreachableCatchCheck::names)
            .orElse(Set.of());
    return earlier.stream()
        .map(prior -> nameOf(prior, types))
        .flatMap(Optional::stream)
        .anyMatch(name -> name.equals(current.get().name()) || ancestors.contains(name));
  }

  private static Optional<String> nameOf(final SyntaxNode clause, final UnitTypeFacts types) {
    return userTypeOf(clause).flatMap(node -> types.typeOf(node.span())).map(TypeRef::name);
  }

  private static Optional<SyntaxNode> userTypeOf(final SyntaxNode clause) {
    return clause.children().stream().filter(child -> USER_TYPE.equals(child.kind())).findFirst();
  }

  private static Set<String> names(final TypeMembers members) {
    return members.ancestors().stream().map(TypeRef::name).collect(Collectors.toSet());
  }
}
