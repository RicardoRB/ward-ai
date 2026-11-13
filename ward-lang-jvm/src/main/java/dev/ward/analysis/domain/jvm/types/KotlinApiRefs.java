package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/**
 * Turns the types a Kotlin declaration writes into API type references: resolved ones like the type
 * facts do, the rest by their written text, so that nothing is dropped silently.
 */
final class KotlinApiRefs {
  private static final String NULLABLE = "?";
  private static final String ARGUMENTS = "typeArguments";
  private static final String FUNCTION_TYPE = "functionType";

  private final TypeScope scope;
  private final ApiTypeRefs refs;

  KotlinApiRefs(final TypeScope scope, final ApiTypeRefs refs) {
    this.scope = scope;
    this.refs = refs;
  }

  TypeScope scope() {
    return scope;
  }

  ApiTypeRefs refs() {
    return refs;
  }

  KotlinApiRefs withScope(final TypeScope next) {
    return new KotlinApiRefs(next, refs);
  }

  KotlinApiRefs withRefs(final ApiTypeRefs next) {
    return new KotlinApiRefs(scope, next);
  }

  /** Returns the type that {@code written} writes, with its nullability. */
  ApiTypeRef of(final SyntaxNode written) {
    final ApiTypeRef base =
        resolve(written).map(refs::of).orElseGet(() -> KotlinWrittenTypes.of(written, scope));
    return Syntax.text(written).endsWith(NULLABLE)
        ? new ApiTypeRef(base.text() + NULLABLE, base.key(), base.erasure(), true)
        : base;
  }

  private Optional<JType> resolve(final SyntaxNode written) {
    if (Syntax.find(written, FUNCTION_TYPE).isPresent()) {
      return Optional.empty();
    }
    final boolean generic = Syntax.find(written, ARGUMENTS).isPresent();
    return KotlinTypes.type(written, scope).filter(type -> !generic || hasArguments(type));
  }

  private static boolean hasArguments(final JType type) {
    return !(type instanceof JType.ClassType named) || !named.arguments().isEmpty();
  }
}
