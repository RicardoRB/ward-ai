package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each call by what the resolved target and the receiver's type declare: the annotations of
 * the called method or of its type, and the supertypes of the receiver. A call whose facts are
 * unknown is never reported.
 */
public final class CallFactsCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String RESOURCE = "resource";
  private static final String RETURN = "'return'";
  private static final String SEMICOLON = "';'";
  private static final int STATEMENT_PARTS = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS));

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported call meets; an empty set or pattern places no limit.
   *
   * @param declaringTypes qualified names of the type that declares the called method
   * @param names names of the called method
   * @param methodAnnotations qualified names of annotations of the called method, one of which it
   *     has
   * @param declaringAnnotations qualified names of annotations of the declaring type, one of which
   *     it has
   * @param receiverAncestors qualified names of types the receiver's type is or extends, one of
   *     which it is
   * @param text a pattern found in the compact text of the call, empty for any text
   * @param ignored whether the call must be a statement, so that its result is dropped
   * @param outsideResource whether the call must be neither a try resource nor returned
   */
  public record Criteria(
      Set<String> declaringTypes,
      Set<String> names,
      Set<String> methodAnnotations,
      Set<String> declaringAnnotations,
      Set<String> receiverAncestors,
      Pattern text,
      boolean ignored,
      boolean outsideResource) {

    /** Copies the sets. */
    public Criteria {
      declaringTypes = Set.copyOf(declaringTypes);
      names = Set.copyOf(names);
      methodAnnotations = Set.copyOf(methodAnnotations);
      declaringAnnotations = Set.copyOf(declaringAnnotations);
      receiverAncestors = Set.copyOf(receiverAncestors);
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported call meets
   * @param message the message reported at the call
   */
  public CallFactsCheck(final Criteria criteria, final String message) {
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
      if (target.isPresent() && meets(call, visit, target.get(), types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean meets(
      final SyntaxNode call,
      final SyntaxWalk.Visit visit,
      final CallTarget target,
      final UnitTypeFacts types) {
    return targetMeets(target)
        && receiverMeets(call, types)
        && criteria.text().matcher(SyntaxWalk.text(call)).find()
        && (!criteria.ignored() || isStatement(call, visit))
        && (!criteria.outsideResource() || outsideResource(visit));
  }

  private boolean targetMeets(final CallTarget target) {
    return (criteria.declaringTypes().isEmpty()
            || criteria.declaringTypes().contains(target.declaringType().name()))
        && (criteria.names().isEmpty() || criteria.names().contains(target.name()))
        && shares(criteria.methodAnnotations(), target.annotations())
        && shares(criteria.declaringAnnotations(), target.declaringAnnotations());
  }

  private static boolean shares(final Set<String> wanted, final List<String> found) {
    return wanted.isEmpty() || !Collections.disjoint(wanted, found);
  }

  private boolean receiverMeets(final SyntaxNode call, final UnitTypeFacts types) {
    if (criteria.receiverAncestors().isEmpty()) {
      return true;
    }
    final List<SyntaxNode> parts = call.children();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind())) {
      return false;
    }
    final Set<String> known = new HashSet<>();
    types.typeOf(parts.get(0).span()).map(TypeRef::name).ifPresent(known::add);
    types
        .membersOf(parts.get(0).span())
        .map(TypeMembers::ancestors)
        .ifPresent(ancestors -> ancestors.forEach(ancestor -> known.add(ancestor.name())));
    return !Collections.disjoint(known, criteria.receiverAncestors());
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

  private static boolean outsideResource(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    final boolean inResource = up.stream().anyMatch(node -> RESOURCE.equals(node.kind()));
    final boolean returned =
        !up.isEmpty()
            && STATEMENT.equals(up.get(up.size() - 1).kind())
            && up.get(up.size() - 1).children().stream()
                .anyMatch(child -> RETURN.equals(child.kind()));
    return !inResource && !returned;
  }
}
