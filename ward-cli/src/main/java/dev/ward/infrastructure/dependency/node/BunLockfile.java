package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the text {@code bun.lock} (JSON with comments and trailing commas, {@code lockfileVersion}
 * 0 and 1). A package is {@code "key": ["name@version", registry, {dependencies}, integrity]}; a
 * key that holds the requiring package's key before the name is a nested copy.
 */
final class BunLockfile {
  private static final Set<Integer> VERSIONS = Set.of(0, 1);
  private static final List<String> EDGES =
      List.of("dependencies", "optionalDependencies", "peerDependencies");
  private static final List<String> NON_RELEASE =
      List.of("workspace:", "github:", "git+", "file:", "link:", "http", "git:");

  private final Map<String, List<?>> packages;
  private final Map<String, String> workspaceNames;

  private BunLockfile(final Map<String, List<?>> packages, final Map<String, String> workspaces) {
    this.packages = packages;
    this.workspaceNames = workspaces;
  }

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if it is not a bun.lock this reader knows
   */
  static NodeLock read(final String text, final JsonCodec codec) {
    final Object parsed;
    try {
      parsed = codec.fromJson(Jsonc.toJson(text), Object.class);
    } catch (JsonCodecException | IllegalArgumentException e) {
      throw new LockfileFormatException("it is not valid JSON");
    }
    if (!(parsed instanceof Map<?, ?> root)) {
      throw new LockfileFormatException("it is not a JSON object");
    }
    if (!(root.get("lockfileVersion") instanceof Number version)
        || !VERSIONS.contains(version.intValue())) {
      throw new LockfileFormatException(
          "unsupported lockfileVersion " + root.get("lockfileVersion"));
    }
    final Map<String, List<?>> packages = new java.util.LinkedHashMap<>();
    if (root.get("packages") instanceof Map<?, ?> listed) {
      listed.forEach(
          (key, value) -> {
            if (value instanceof List<?> tuple && !tuple.isEmpty()) {
              packages.put(String.valueOf(key), tuple);
            }
          });
    }
    final Map<String, String> names = new java.util.HashMap<>();
    if (root.get("workspaces") instanceof Map<?, ?> workspaces) {
      workspaces.forEach(
          (path, body) ->
              names.put(
                  String.valueOf(path),
                  body instanceof Map<?, ?> map && map.get("name") instanceof String name
                      ? name
                      : ""));
    }
    return new BunLockfile(packages, names).toLock();
  }

  private NodeLock toLock() {
    final NodeLock.Collector collector = new NodeLock.Collector();
    packages.forEach(
        (key, tuple) -> {
          final Optional<PackageId> id = idOf(tuple);
          if (id.isPresent()) {
            final Set<PackageId> required = new HashSet<>();
            for (final String name : requiredNames(tuple)) {
              resolveKey(key, name).flatMap(this::idAt).ifPresent(required::add);
            }
            collector.add(id.get(), released(tuple), required);
          }
        });
    return new NodeLock(
        collector.packages(),
        (member, name, range) ->
            resolveKey(workspaceNames.getOrDefault(member, ""), name).flatMap(this::idAt));
  }

  private Optional<PackageId> idAt(final String key) {
    return idOf(packages.get(key));
  }

  private static Optional<PackageId> idOf(final List<?> tuple) {
    final String spec = tuple.getFirst() instanceof String text ? text : "";
    final int at = spec.indexOf('@', 1);
    if (at < 0 || spec.substring(at + 1).startsWith("workspace:")) {
      return Optional.empty();
    }
    return Optional.of(NodeLock.npm(spec.substring(0, at), spec.substring(at + 1)));
  }

  private static boolean released(final List<?> tuple) {
    final String spec = tuple.getFirst() instanceof String text ? text : "";
    final String version = spec.substring(spec.indexOf('@', 1) + 1);
    return NON_RELEASE.stream().noneMatch(version::startsWith);
  }

  private static List<String> requiredNames(final List<?> tuple) {
    final List<String> names = new ArrayList<>();
    for (final Object item : tuple) {
      if (item instanceof Map<?, ?> info) {
        for (final String section : EDGES) {
          if (info.get(section) instanceof Map<?, ?> deps) {
            deps.keySet().forEach(name -> names.add(String.valueOf(name)));
          }
        }
      }
    }
    return names;
  }

  /** Looks {@code name} up nested under {@code from}, then under each parent, then at the top. */
  private Optional<String> resolveKey(final String from, final String name) {
    final List<String> parents = segments(from);
    while (true) {
      final String key = parents.isEmpty() ? name : String.join("/", parents) + "/" + name;
      if (packages.containsKey(key)) {
        return Optional.of(key);
      }
      if (parents.isEmpty()) {
        return Optional.empty();
      }
      parents.removeLast();
    }
  }

  /** Splits a key into package names, keeping {@code @scope/name} together. */
  private static List<String> segments(final String key) {
    final List<String> result = new ArrayList<>();
    if (key.isEmpty()) {
      return result;
    }
    final String[] parts = key.split("/");
    for (int index = 0; index < parts.length; index++) {
      if (parts[index].startsWith("@") && index + 1 < parts.length) {
        result.add(parts[index] + "/" + parts[++index]);
      } else {
        result.add(parts[index]);
      }
    }
    return result;
  }
}
