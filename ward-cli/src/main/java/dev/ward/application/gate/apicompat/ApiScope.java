package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.api.ApiType;
import dev.ward.domain.apicompat.ApiModules;
import dev.ward.domain.config.ApiCompatOptions;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The part of the API the {@code include} prefixes and {@code modules} of the options select. */
final class ApiScope {
  private final ApiCompatOptions options;

  ApiScope(final ApiCompatOptions options) {
    this.options = options;
  }

  /**
   * Checks that every {@code include} prefix matches a type at the baseline and every named module
   * is a module of the build at one of the revisions.
   *
   * @throws ApiCompatException if one does not
   */
  void require(
      final List<ApiType> baselineApi,
      final List<String> baselineModules,
      final List<String> currentModules) {
    for (final String prefix : options.include()) {
      if (baselineApi.stream().noneMatch(type -> matches(prefix, type.name()))) {
        throw new ApiCompatException(
            "The include prefix '"
                + prefix
                + "' of api-compat matches no type at the baseline; check the package name.");
      }
    }
    for (final String module : options.modules()) {
      if (!baselineModules.contains(module) && !currentModules.contains(module)) {
        throw new ApiCompatException(
            "The module '"
                + module
                + "' of api-compat is not a module of the build at the baseline or in the"
                + " working tree; check the directory.");
      }
    }
  }

  /** Returns whether a type of the given qualified name is in scope. */
  Predicate<String> of(final List<ApiType> baselineApi, final List<String> baselineModules) {
    final Set<String> named =
        baselineApi.stream()
            .filter(
                type ->
                    options.modules().isEmpty()
                        || options
                            .modules()
                            .contains(ApiModules.owner(type.location().path(), baselineModules)))
            .map(ApiType::name)
            .collect(Collectors.toSet());
    return typeName ->
        included(typeName) && (options.modules().isEmpty() || named.contains(typeName));
  }

  private boolean included(final String typeName) {
    return options.include().isEmpty()
        || options.include().stream().anyMatch(prefix -> matches(prefix, typeName));
  }

  private static boolean matches(final String prefix, final String typeName) {
    return typeName.equals(prefix) || typeName.startsWith(prefix + ".");
  }
}
