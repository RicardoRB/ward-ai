package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.Nullness;
import dev.ward.analysis.domain.types.NullnessAnnotations;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a public or protected property or function without a written type whose value is the
 * result of a call to a {@code java.*} member that returns an unannotated reference type. Such a
 * result is a platform type, which the declaration then exposes. A Kotlin callee, a private or
 * internal declaration, a primitive result, or any unknown fact is no finding.
 */
public final class KotlinPlatformTypeCheck implements RuleCheck {
  private static final String PROPERTY = "propertyDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final String VARIABLE = "variableDeclaration";
  private static final String BODY = "functionBody";
  private static final String EXPRESSION = "expression";
  private static final String TYPE = "type";
  private static final String COLON = "':'";
  private static final String MODIFIER_LIST = "modifierList";
  private static final String FUNCTION_MODIFIER_LIST = "functionModifierList";
  private static final String VISIBILITY = "visibilityModifier";
  private static final String PRIVATE = "private";
  private static final String INTERNAL = "internal";
  private static final String JAVA_PACKAGE = "java.";
  private static final String CALL_END = ")";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the declaration
   */
  public KotlinPlatformTypeCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(PROPERTY, FUNCTION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(declaration -> isPlatform(declaration, types))
        .map(declaration -> CheckSupport.at(declaration.span(), message))
        .toList();
  }

  private static boolean isPlatform(final SyntaxNode declaration, final UnitTypeFacts types) {
    return isExposed(declaration)
        && !hasWrittenType(declaration)
        && initializer(declaration)
            .filter(value -> SyntaxWalk.text(value).endsWith(CALL_END))
            .flatMap(value -> types.callTarget(value.span()))
            .filter(KotlinPlatformTypeCheck::isJavaReference)
            .isPresent();
  }

  /** Returns whether the declaration is public or protected, which is the default. */
  private static boolean isExposed(final SyntaxNode declaration) {
    final Optional<String> visibility =
        declaration.children().stream()
            .filter(
                child ->
                    MODIFIER_LIST.equals(child.kind())
                        || FUNCTION_MODIFIER_LIST.equals(child.kind()))
            .findFirst()
            .flatMap(list -> SyntaxWalk.visits(list, Set.of(VISIBILITY)).stream().findFirst())
            .map(visit -> SyntaxWalk.text(visit.node()));
    return visibility.map(text -> !PRIVATE.equals(text) && !INTERNAL.equals(text)).orElse(true);
  }

  /** Returns whether a property names its type in its variable, or a function in its header. */
  private static boolean hasWrittenType(final SyntaxNode declaration) {
    if (PROPERTY.equals(declaration.kind())) {
      return declaration.children().stream()
          .filter(child -> VARIABLE.equals(child.kind()))
          .anyMatch(
              variable -> variable.children().stream().anyMatch(part -> TYPE.equals(part.kind())));
    }
    return declaration.children().stream()
        .anyMatch(child -> TYPE.equals(child.kind()) || COLON.equals(child.kind()));
  }

  /** Returns the expression that gives a property or an expression-bodied function its value. */
  private static Optional<SyntaxNode> initializer(final SyntaxNode declaration) {
    if (PROPERTY.equals(declaration.kind())) {
      return declaration.children().stream()
          .filter(child -> EXPRESSION.equals(child.kind()))
          .findFirst();
    }
    return declaration.children().stream()
        .filter(child -> BODY.equals(child.kind()))
        .flatMap(body -> body.children().stream())
        .filter(child -> EXPRESSION.equals(child.kind()))
        .findFirst();
  }

  /**
   * Returns whether a call reaches a {@code java.*} method that returns an unannotated reference.
   */
  private static boolean isJavaReference(final CallTarget target) {
    final TypeRef returned = target.returnType();
    return target.declaringType().name().startsWith(JAVA_PACKAGE)
        && returned != null
        && !returned.primitive()
        && NullnessAnnotations.of(target.annotations(), target.declaringAnnotations())
            == Nullness.UNKNOWN;
  }
}
