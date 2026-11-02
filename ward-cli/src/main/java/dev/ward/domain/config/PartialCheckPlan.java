package dev.ward.domain.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Pure decision model for selecting and narrowing gates for an edit. */
public final class PartialCheckPlan {

  private final Map<String, GatePlan> gates;

  /** How a partial check runs one gate. */
  public enum Scope {
    SKIP,
    FULL,
    NARROW
  }

  /** The decision for one gate; {@code args} are appended to its command when narrowed. */
  public record GatePlan(String gate, Scope scope, List<String> args) {
    /** Copies the arguments. */
    public GatePlan {
      args = List.copyOf(args);
    }
  }

  private PartialCheckPlan(final Map<String, GatePlan> gates) {
    this.gates = Collections.unmodifiableMap(new LinkedHashMap<>(gates));
  }

  /**
   * Decides, for each configured gate, whether the edited paths skip it, narrow it, or run it in
   * full. Without paths every gate runs in full. {@code packages} names the workspace package that
   * holds a path, or is empty for a path in no package.
   */
  public static PartialCheckPlan create(
      final Path root,
      final Map<String, GateConfiguration> configurations,
      final List<Path> editedPaths,
      final PathExistence existence,
      final Function<Path, Optional<String>> packages) {
    final List<EditedPath> paths = normalize(root, editedPaths, existence);
    final boolean noPaths = editedPaths == null || editedPaths.isEmpty();
    final Map<String, GatePlan> plans = new LinkedHashMap<>();
    configurations.forEach(
        (name, configuration) ->
            plans.put(name, decide(name, configuration, paths, noPaths, packages)));
    return new PartialCheckPlan(plans);
  }

  /**
   * Plans an {@code edit} run without edited paths: a check with a {@code paths} table is skipped,
   * any other check runs in full.
   */
  public static PartialCheckPlan withoutPaths(final Map<String, GateConfiguration> configurations) {
    final Map<String, GatePlan> plans = new LinkedHashMap<>();
    configurations.forEach(
        (name, configuration) ->
            plans.put(
                name,
                new GatePlan(
                    name, configuration.partial() == null ? Scope.FULL : Scope.SKIP, List.of())));
    return new PartialCheckPlan(plans);
  }

  /** Returns the decision for each gate, in configuration order. */
  public Map<String, GatePlan> gates() {
    return gates;
  }

  /** Returns how many gates the plan handles with {@code scope}. */
  public long count(final Scope scope) {
    return gates.values().stream().filter(plan -> plan.scope() == scope).count();
  }

  private static GatePlan decide(
      final String name,
      final GateConfiguration configuration,
      final List<EditedPath> paths,
      final boolean noPaths,
      final Function<Path, Optional<String>> packages) {
    final PartialGateConfiguration partial = configuration.partial();
    if (partial == null || noPaths) {
      return new GatePlan(name, Scope.FULL, List.of());
    }
    final List<EditedPath> relevant =
        paths.stream().filter(path -> matches(partial.match(), path.relative())).toList();
    if (paths.stream().anyMatch(path -> matches(partial.full(), path.relative()))) {
      return new GatePlan(name, Scope.FULL, List.of());
    }
    if (relevant.isEmpty()) {
      return new GatePlan(name, Scope.SKIP, List.of());
    }
    final List<EditedPath> existing = relevant.stream().filter(EditedPath::exists).toList();
    final List<String> items = PartialArguments.items(partial.item(), existing, packages);
    if (partial.args().isEmpty() || items.isEmpty()) {
      return new GatePlan(name, Scope.FULL, List.of());
    }
    return new GatePlan(name, Scope.NARROW, PartialArguments.render(partial, items));
  }

  private static List<EditedPath> normalize(
      final Path root, final List<Path> paths, final PathExistence existence) {
    if (paths == null) {
      return List.of();
    }
    final Path absoluteRoot = existence.canonical(root);
    return paths.stream()
        .map(existence::canonical)
        .distinct()
        .filter(path -> path.startsWith(absoluteRoot))
        .map(
            path ->
                new EditedPath(
                    absoluteRoot.relativize(path).toString().replace('\\', '/'),
                    String.valueOf(path.getFileName()),
                    existence.exists(path),
                    path))
        .toList();
  }

  private static boolean matches(final List<String> patterns, final String path) {
    return patterns.stream().anyMatch(pattern -> PathPattern.of(pattern).matches(path));
  }
}
