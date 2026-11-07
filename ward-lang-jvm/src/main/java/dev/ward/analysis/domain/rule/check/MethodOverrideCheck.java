package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method that overrides a method with one of the given annotations, when it does not
 * carry one of the annotations that the override needs or, if asked, does not call {@code super} by
 * the same name. A method whose overridden method is unknown is never reported.
 */
public final class MethodOverrideCheck implements RuleCheck {

  private static final String METHOD = "methodDeclaration";
  private static final FactNeed NEED =
      new FactNeed(Set.of(METHOD), Set.of(FactKind.OVERRIDES, FactKind.ANNOTATIONS));

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported method meets.
   *
   * @param overriddenAnnotations qualified names of annotations, one of which an overridden method
   *     has
   * @param requiredAnnotations qualified names of annotations, one of which the method needs; empty
   *     for none
   * @param requireSuperCall whether the method must call {@code super.name(}
   */
  public record Criteria(
      Set<String> overriddenAnnotations,
      Set<String> requiredAnnotations,
      boolean requireSuperCall) {

    /** Copies the sets. */
    public Criteria {
      overriddenAnnotations = Set.copyOf(overriddenAnnotations);
      requiredAnnotations = Set.copyOf(requiredAnnotations);
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported method meets
   * @param message the message reported at the method
   */
  public MethodOverrideCheck(final Criteria criteria, final String message) {
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD))) {
      final SyntaxNode method = visit.node();
      final SpanFacts facts = types.at(method.span());
      if (overridesAnnotated(facts) && lacksWhatIsNeeded(method, facts)) {
        matches.add(CheckSupport.at(method.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean overridesAnnotated(final SpanFacts facts) {
    return facts.overrides().stream()
        .map(CallTarget::annotations)
        .anyMatch(found -> !Collections.disjoint(found, criteria.overriddenAnnotations()));
  }

  private boolean lacksWhatIsNeeded(final SyntaxNode method, final SpanFacts facts) {
    final boolean lacksAnnotation =
        !criteria.requiredAnnotations().isEmpty()
            && Collections.disjoint(facts.annotations(), criteria.requiredAnnotations());
    return lacksAnnotation || criteria.requireSuperCall() && !callsSuper(method);
  }

  private static boolean callsSuper(final SyntaxNode method) {
    final String name =
        method.children().stream()
            .filter(child -> "identifier".equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    return !name.isEmpty() && SyntaxWalk.text(method).contains("super." + name + "(");
  }
}
