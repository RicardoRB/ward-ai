package dev.ward.infrastructure.dependency.dotnet;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code packages.lock.json} (versions 1 and 2) and {@code obj/project.assets.json}
 * (version 3). Each target framework is read on its own, so a package that two frameworks resolve
 * to different versions is two entries; a project reference is not a package.
 */
final class NugetLockfiles {
  private static final Set<Integer> LOCK_VERSIONS = Set.of(1, 2);
  private static final int ASSETS_VERSION = 3;
  private static final String DIRECT = "Direct";
  private static final String DEPENDENCIES = "dependencies";

  /** A package of one framework, with the packages it requires. */
  record Entry(PackageId id, boolean direct, List<PackageId> requires) {
    Entry {
      requires = List.copyOf(requires);
    }
  }

  private NugetLockfiles() {}

  /**
   * Reads {@code packages.lock.json}.
   *
   * @throws LockfileFormatException if it is not JSON or has another format version
   */
  static List<Entry> lock(final String text, final JsonCodec codec) {
    final Map<?, ?> root = root(text, codec);
    if (!(root.get("version") instanceof Number version)
        || !LOCK_VERSIONS.contains(version.intValue())) {
      throw new LockfileFormatException("unsupported version " + root.get("version"));
    }
    final List<Entry> entries = new ArrayList<>();
    for (final Object target : map(root.get(DEPENDENCIES)).values()) {
      entries.addAll(lockTarget(map(target)));
    }
    return entries;
  }

  private static List<Entry> lockTarget(final Map<?, ?> target) {
    final Map<String, PackageId> byName = new HashMap<>();
    target.forEach(
        (name, value) -> {
          final String resolved = text(map(value).get("resolved"));
          if (!resolved.isEmpty()) {
            byName.put(key(text(name)), id(text(name), resolved));
          }
        }
    );
    final List<Entry> entries = new ArrayList<>();
    target.forEach(
        (name, value) -> {
          final Map<?, ?> body = map(value);
          final PackageId id = byName.get(key(text(name)));
          if (id != null) {
            entries.add(
                new Entry(
                    id,
                    DIRECT.equals(text(body.get("type"))),
                    required(map(body.get(DEPENDENCIES)), byName)
                )
            );
          }
        }
    );
    return entries;
  }

  /**
   * Reads {@code project.assets.json}.
   *
   * @throws LockfileFormatException if it is not JSON or has another format version
   */
  static List<Entry> assets(final String text, final JsonCodec codec) {
    final Map<?, ?> root = root(text, codec);
    if (!(root.get("version") instanceof Number version)
        || version.intValue() != ASSETS_VERSION) {
      throw new LockfileFormatException("unsupported version " + root.get("version"));
    }
    final Map<?, ?> groups = map(root.get("projectFileDependencyGroups"));
    final List<Entry> entries = new ArrayList<>();
    map(root.get("targets"))
        .forEach(
            (framework, target) ->
                entries.addAll(assetsTarget(map(target), declared(groups.get(framework))))
        );
    return entries;
  }

  private static List<Entry> assetsTarget(final Map<?, ?> target, final Set<String> declared) {
    final Map<String, PackageId> byName = new HashMap<>();
    target.forEach(
        (library, value) -> {
          final String text = text(library);
          final int slash = text.lastIndexOf('/');
          if (slash > 0 && "package".equals(text(map(value).get("type")))) {
            byName.put(
                key(text.substring(0, slash)),
                id(text.substring(0, slash), text.substring(slash + 1))
            );
          }
        }
    );
    final List<Entry> entries = new ArrayList<>();
    byName
        .values()
        .forEach(
            id -> {
              final Map<?, ?> body = map(target.get(id.name() + "/" + id.version()));
              entries.add(
                  new Entry(
                      id,
                      declared.contains(key(id.name())),
                      required(map(body.get(DEPENDENCIES)), byName)
                  )
              );
            }
        );
    return entries;
  }

  /** The lower-case names a framework's dependency group lists, such as {@code Serilog >= 4}. */
  private static Set<String> declared(final Object group) {
    final Set<String> names = new java.util.HashSet<>();
    if (group instanceof List<?> items) {
      items.forEach(
          item -> {
            final String line = text(item).strip();
            final int space = line.indexOf(' ');
            names.add(key(space < 0 ? line : line.substring(0, space)));
          }
      );
    }
    return names;
  }

  private static List<PackageId> required(
      final Map<?,
      ?> dependencies,
      final Map<String,
      PackageId> byName
  ) {
    return dependencies.keySet().stream()
        .map(name -> byName.get(key(text(name))))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private static Map<?, ?> root(final String text, final JsonCodec codec) {
    final Object parsed;
    try {
      parsed = codec.fromJson(text, Object.class);
    } catch (JsonCodecException | IllegalArgumentException e) {
      throw new LockfileFormatException("it is not valid JSON", e);
    }
    if (parsed instanceof Map<?, ?> root) {
      return root;
    }
    throw new LockfileFormatException("it is not a JSON object");
  }

  private static PackageId id(final String name, final String version) {
    return new PackageId(Ecosystem.NUGET, name, version);
  }

  private static String key(final String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static Map<?, ?> map(final Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private static String text(final Object value) {
    return value instanceof String text ? text : "";
  }
}
