package dev.ward.analysis.domain.jvm.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the members a class declares, as they look from a site that applies its type arguments. */
final class MemberViews {
  private MemberViews() {}

  static Optional<Hierarchy.Found<JvmField>> field(
      final JvmClass owner, final String name, final Map<String, JType> bound) {
    return owner.fields().stream()
        .filter(candidate -> candidate.name().equals(name))
        .findFirst()
        .map(field -> new Hierarchy.Found<>(field, owner, bound));
  }

  static List<Hierarchy.Found<JvmMethod>> methods(
      final JvmClass owner,
      final Map<String, JType> bound,
      final String name,
      final Set<String> signatures) {
    return owner.methods().stream()
        .filter(
            method -> method.name().equals(name) && !Flags.has(method.access(), Flags.SYNTHETIC))
        .filter(method -> signatures.add(signature(method, bound)))
        .map(method -> new Hierarchy.Found<>(method, owner, visible(method, bound)))
        .toList();
  }

  private static Map<String, JType> visible(
      final JvmMethod method, final Map<String, JType> bound) {
    final Map<String, JType> visible = new HashMap<>(bound);
    method.typeParameters().forEach(parameter -> visible.remove(parameter.name()));
    return visible;
  }

  private static String signature(final JvmMethod method, final Map<String, JType> bound) {
    return method.parameters().stream()
        .map(parameter -> Types.erasure(Types.substitute(parameter, bound), Map.of()))
        .collect(Collectors.joining(","));
  }
}
