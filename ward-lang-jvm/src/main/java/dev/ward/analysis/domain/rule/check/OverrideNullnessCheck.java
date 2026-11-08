package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an override whose explicit nullness annotations break the contract of the method it
 * overrides: a parameter made {@code @Nonnull} where the overridden one is nullable, or a return
 * value made nullable where the overridden one is {@code @Nonnull}. An overridden method that is
 * unknown, or that has no explicit annotation, is never reported.
 */
public final class OverrideNullnessCheck implements RuleCheck {

  /** What the check looks for. */
  public enum Mode {
    /** A parameter annotated non-null that the overridden method declares nullable. */
    PARAMETER_TIGHTENS,
    /** A return value annotated nullable that the overridden method declares non-null. */
    RETURN_RELAXES
  }

  private static final String METHOD = "methodDeclaration";
  private static final String PARAMETER = "formalParameter";
  private static final String ANNOTATION = "annotation";
  private static final FactNeed NEED =
      new FactNeed(Set.of(METHOD), Set.of(FactKind.OVERRIDES, FactKind.ANNOTATIONS));

  private final Mode mode;
  private final Set<String> nullable;
  private final Set<String> nonNull;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param nullable the qualified names of the annotations that make a value nullable
   * @param nonNull the qualified names of the annotations that make a value non-null
   * @param message the message reported at the method
   */
  public OverrideNullnessCheck(
      final Mode mode,
      final Set<String> nullable,
      final Set<String> nonNull,
      final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.nullable = Set.copyOf(nullable);
    this.nonNull = Set.copyOf(nonNull);
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
      final List<CallTarget> overridden = types.at(method.span()).overrides();
      final boolean broken =
          mode == Mode.RETURN_RELAXES
              ? relaxesReturn(types.at(method.span()).annotations(), overridden)
              : tightensParameter(method, overridden);
      if (broken) {
        matches.add(CheckSupport.at(method.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean relaxesReturn(final List<String> own, final List<CallTarget> overridden) {
    return !Collections.disjoint(own, nullable)
        && overridden.stream()
            .anyMatch(target -> !Collections.disjoint(target.annotations(), nonNull));
  }

  private boolean tightensParameter(final SyntaxNode method, final List<CallTarget> overridden) {
    final List<SyntaxNode> parameters =
        SyntaxWalk.descendants(method).stream()
            .filter(node -> PARAMETER.equals(node.kind()))
            .toList();
    for (final CallTarget target : overridden) {
      for (int index = 0;
          index < parameters.size() && index < target.parameterAnnotations().size();
          index++) {
        if (isNonNull(parameters.get(index))
            && !Collections.disjoint(target.parameterAnnotations().get(index), nullable)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isNonNull(final SyntaxNode parameter) {
    return SyntaxWalk.visits(parameter, Set.of(ANNOTATION)).stream()
        .map(visit -> NullSyntax.simpleName(visit.node()))
        .anyMatch(name -> nonNull.stream().anyMatch(full -> full.endsWith("." + name)));
  }
}
