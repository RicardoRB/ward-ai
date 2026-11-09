package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A check that reads the native type facts of TypeScript expressions. A fact the resolver cannot
 * answer is unknown, so the check reports nothing that depends on it.
 */
public abstract class TypedCheck implements RuleCheck {

  /** The expression nodes whose types the checks read. */
  static final String EXPRESSION = "singleExpression";

  static final String SEQUENCE = "expressionSequence";
  static final String TEMPLATE = "templateStringAtom";

  private static final Set<FactKind> TYPE_ONLY = Set.of(FactKind.TYPE);
  private static final Set<String> UNTYPED = Set.of("any", "unknown");
  private static final Set<String> VALUE_PRIMITIVES =
      Set.of("string", "number", "boolean", "bigint", "null", "undefined", "void", "never");

  @Override
  public final FactNeed needs() {
    return new FactNeed(Set.of(EXPRESSION, SEQUENCE, TEMPLATE), kinds());
  }

  /** The fact kinds the check reads; a check that reads call targets adds {@link FactKind#CALL}. */
  protected Set<FactKind> kinds() {
    return TYPE_ONLY;
  }

  @Override
  public final List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public final List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return find(root, types);
  }

  /** Returns the places in {@code root} where the check fails, given the type facts. */
  protected abstract List<RuleMatch> find(SyntaxNode root, UnitTypeFacts types);

  /** Returns the type of an expression node, or empty when it is unknown. */
  static Optional<TypeRef> typeOf(final UnitTypeFacts types, final SyntaxNode node) {
    return types.typeOf(node.span());
  }

  /** Reports whether a type is {@code any}, of any array depth. */
  static boolean isAny(final TypeRef type) {
    return "any".equals(type.name()) && type.arrayDepth() == 0;
  }

  /** Reports whether a type is {@code any} or {@code unknown}. */
  static boolean isUntyped(final TypeRef type) {
    return UNTYPED.contains(type.name()) && type.arrayDepth() == 0;
  }

  /** Reports whether a type is an array or a tuple of any element type. */
  static boolean isArray(final TypeRef type) {
    return type.arrayDepth() > 0 || isNamed(type, "Array") || isNamed(type, "ReadonlyArray");
  }

  /** Reports whether a type is a primitive value, such as a string, a number or a literal. */
  static boolean isPrimitive(final TypeRef type) {
    return type.arrayDepth() == 0
        && (type.primitive() || VALUE_PRIMITIVES.contains(type.name()) || isLiteral(type.name()));
  }

  /** Reports whether a value of this type may be a promise: an unknown value or a non-primitive. */
  static boolean isMaybeAwaitable(final TypeRef type) {
    return isUntyped(type) || !isPrimitive(type);
  }

  /** Reports whether a type is an object, array or function type, which a template cannot print. */
  static boolean isObjectLike(final TypeRef type) {
    return !isUntyped(type) && !isPrimitive(type);
  }

  /** Reports whether a type is the named generic, such as {@code Promise<void>}, with no array. */
  private static boolean isNamed(final TypeRef type, final String name) {
    return type.arrayDepth() == 0
        && (name.equals(type.name()) || type.name().startsWith(name + "<"));
  }

  private static boolean isLiteral(final String name) {
    return name.startsWith("\"")
        || name.startsWith("'")
        || name.matches("-?[0-9].*")
        || "true".equals(name)
        || "false".equals(name);
  }

  /** Reports whether a type is a promise, possibly with its type argument. */
  static boolean isPromise(final TypeRef type) {
    return isNamed(type, "Promise");
  }
}
