package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tells which module of a multi-module build owns a source file, and which types changed module.
 */
public final class ApiModules {
  /** The kind of a type that keeps its name but is declared in another module. */
  public static final String MOVED = "type-module-changed";

  private static final String ROOT = "(root)";

  private ApiModules() {}

  /**
   * Returns the module of {@code path}: the longest module directory that contains it, or the empty
   * text for the project root.
   *
   * @param path a project-relative path with {@code /} separators
   * @param modules the module directories, relative to the project root
   */
  public static String owner(final String path, final List<String> modules) {
    String owner = "";
    for (final String module : modules) {
      if (path.startsWith(module + "/") && module.length() > owner.length()) {
        owner = module;
      }
    }
    return owner;
  }

  /**
   * Returns a change for each visible baseline type of {@code inScope} that the working tree
   * declares only in another module.
   */
  public static List<ApiChange> moved(
      final List<ApiType> baseline,
      final List<ApiType> current,
      final List<String> baselineModules,
      final List<String> currentModules,
      final Predicate<String> inScope) {
    final List<ApiChange> changes = new ArrayList<>();
    for (final ApiType type : baseline) {
      if (type.isVisible() && inScope.test(type.name())) {
        final String was = owner(type.location().path(), baselineModules);
        final List<String> now =
            current.stream()
                .filter(candidate -> candidate.name().equals(type.name()))
                .map(candidate -> owner(candidate.location().path(), currentModules))
                .toList();
        if (!now.isEmpty() && !now.contains(was)) {
          changes.add(
              new ApiChange(
                  MOVED,
                  type.name(),
                  "",
                  "module " + label(was),
                  "module " + label(now.get(0)),
                  type.location()));
        }
      }
    }
    return changes;
  }

  private static String label(final String module) {
    return module.isEmpty() ? ROOT : module;
  }
}
