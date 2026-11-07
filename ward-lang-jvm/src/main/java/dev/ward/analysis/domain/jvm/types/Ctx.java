package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where an expression is typed: the type scope, the bounds of the type variables in scope, the
 * annotations of the enclosing declarations, and the sink for the types a lambda body returns.
 *
 * @param scope the names in scope
 * @param bounds the bound of each type variable in scope
 * @param annotations the annotations of the enclosing declarations, nearest first
 * @param returns where {@code return} statements report their types, if a lambda collects them
 */
record Ctx(
    TypeScope scope,
    Map<String, JType> bounds,
    List<String> annotations,
    Optional<List<Optional<JType>>> returns) {

  Ctx {
    bounds = Map.copyOf(bounds);
    annotations = List.copyOf(annotations);
  }

  Ctx withScope(final TypeScope inner, final List<TypeParameter> parameters) {
    final Map<String, JType> merged = new java.util.HashMap<>(bounds);
    for (final TypeParameter parameter : parameters) {
      merged.put(
          parameter.name(),
          parameter.bounds().isEmpty()
              ? JType.ClassType.of(Types.OBJECT)
              : parameter.bounds().get(0));
    }
    return new Ctx(inner, merged, annotations, returns);
  }

  Ctx within(final List<String> declared) {
    final List<String> all = new ArrayList<>(declared);
    all.addAll(annotations);
    return new Ctx(scope, bounds, all, returns);
  }

  Ctx collecting(final List<Optional<JType>> sink) {
    return new Ctx(scope, bounds, annotations, Optional.of(sink));
  }
}
