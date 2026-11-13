package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The types of one version of the API by qualified name, with their supertypes and API status. */
final class ApiIndex {
  private final Map<String, ApiType> types = new HashMap<>();
  private final Map<String, Boolean> api = new HashMap<>();

  ApiIndex(final List<ApiType> declared) {
    declared.forEach(type -> types.put(type.name(), type));
    declared.stream()
        .sorted(Comparator.comparingInt((ApiType type) -> type.name().length()))
        .forEach(type -> api.put(type.name(), computeApi(type)));
  }

  /** A type is API when it is visible and its enclosing type, if the project has one, is API. */
  private boolean computeApi(final ApiType type) {
    if (!type.isVisible()) {
      return false;
    }
    final int dot = type.name().lastIndexOf('.');
    final String enclosing = dot < 0 ? "" : type.name().substring(0, dot);
    return !types.containsKey(enclosing) || api.getOrDefault(enclosing, false);
  }

  Optional<ApiType> find(final String name) {
    return Optional.ofNullable(types.get(name));
  }

  boolean isApi(final String name) {
    return api.getOrDefault(name, false);
  }

  /** Returns the qualified names of every direct and transitive supertype of {@code type}. */
  Set<String> supertypeNames(final ApiType type) {
    final Set<String> found = new LinkedHashSet<>();
    final Deque<ApiType> pending = new ArrayDeque<>(List.of(type));
    while (!pending.isEmpty()) {
      for (final ApiTypeRef supertype : pending.poll().supertypes()) {
        if (found.add(supertype.erasure())) {
          find(supertype.erasure()).ifPresent(pending::add);
        }
      }
    }
    return found;
  }

  /**
   * Returns whether an API supertype of {@code type} in the project declares the member {@code id}.
   */
  boolean inheritsApiMember(final ApiType type, final String id) {
    return supertypeNames(type).stream()
        .filter(this::isApi)
        .flatMap(name -> find(name).stream())
        .anyMatch(
            supertype ->
                supertype.members().stream()
                    .anyMatch(member -> member.id().equals(id) && supertype.exposes(member)));
  }

  static Optional<ApiMember> member(final ApiType type, final String id) {
    return type.members().stream().filter(member -> member.id().equals(id)).findFirst();
  }
}
