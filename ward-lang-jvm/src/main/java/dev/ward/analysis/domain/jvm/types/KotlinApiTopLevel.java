package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The top-level functions and properties of the packages of a project. Callers in Kotlin see a
 * package, not the file that declares a function, so each package has one type for all its files.
 */
final class KotlinApiTopLevel {
  /** The simple name of the type that holds the top-level declarations of a package. */
  static final String NAME = "<top-level>";

  private final Map<String, List<ApiMember>> members = new TreeMap<>();
  private final Map<String, ApiLocation> locations = new TreeMap<>();

  /** Returns the name of the type of {@code packageName}'s top-level declarations. */
  static String typeName(final String packageName) {
    return BinaryNames.qualified(packageName, NAME);
  }

  /** Adds declarations of the package whose type is {@code name}. */
  void add(final String name, final List<ApiMember> added) {
    if (!added.isEmpty()) {
      members.computeIfAbsent(name, key -> new ArrayList<>()).addAll(added);
      locations.putIfAbsent(name, added.getFirst().location());
    }
  }

  /** Returns one type for each package that has top-level declarations. */
  List<ApiType> types() {
    return members.entrySet().stream()
        .map(entry -> type(entry.getKey(), entry.getValue()))
        .toList();
  }

  private ApiType type(final String name, final List<ApiMember> own) {
    return new ApiType(
        name,
        ApiType.Kind.OBJECT,
        ApiVisibility.PUBLIC,
        true,
        false,
        false,
        List.of(),
        List.of(),
        own,
        locations.get(name));
  }
}
