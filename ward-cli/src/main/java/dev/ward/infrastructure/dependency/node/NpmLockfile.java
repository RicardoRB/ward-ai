package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads {@code package-lock.json} and {@code npm-shrinkwrap.json}, versions 1, 2 and 3. A version 1
 * {@code dependencies} tree is turned into the path keys of version 2 so one resolution serves
 * both. A dependency is looked up the way Node does: in {@code node_modules} next to the requiring
 * package, then in each parent directory.
 */
final class NpmLockfile {
  private static final String NODE_MODULES = "node_modules/";
  private static final String SEGMENT = "/" + NODE_MODULES;
  private static final List<String> EDGE_SECTIONS =
      List.of("dependencies", "optionalDependencies", "peerDependencies");
  private static final int OLDEST = 1;
  private static final int NEWEST = 3;

  private final Map<String, Entry> entries = new LinkedHashMap<>();

  private NpmLockfile() {}

  /** One {@code packages} entry. */
  private record Entry(
      String name, String version, String resolved, String link, Set<String> required) {}

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if it is not JSON, has another format version, or lacks the
   *     packages section
   */
  static NodeLock read(final String text, final JsonCodec codec) {
    final Object parsed;
    try {
      parsed = codec.fromJson(text, Object.class);
    } catch (JsonCodecException | IllegalArgumentException e) {
      throw new LockfileFormatException("it is not valid JSON");
    }
    if (!(parsed instanceof Map<?, ?> root)) {
      throw new LockfileFormatException("it is not a JSON object");
    }
    final int version = version(root.get("lockfileVersion"));
    final NpmLockfile lock = new NpmLockfile();
    if (version == OLDEST) {
      lock.readTree("", root.get("dependencies"));
    } else if (root.get("packages") instanceof Map<?, ?> packages) {
      lock.readPackages(packages);
    } else {
      throw new LockfileFormatException("it has no packages section");
    }
    return lock.toLock();
  }

  private static int version(final Object declared) {
    if (declared instanceof Number number
        && number.intValue() >= OLDEST
        && number.intValue() <= NEWEST) {
      return number.intValue();
    }
    throw new LockfileFormatException("unsupported lockfileVersion " + declared);
  }

  private void readPackages(final Map<?, ?> packages) {
    packages.forEach(
        (key, value) -> {
          if (value instanceof Map<?, ?> body) {
            entries.put(String.valueOf(key), entry(String.valueOf(key), body));
          }
        });
  }

  private static Entry entry(final String key, final Map<?, ?> body) {
    final String link = Boolean.TRUE.equals(body.get("link")) ? string(body.get("resolved")) : "";
    final String declaredName = string(body.get("name"));
    final String name = declaredName.isEmpty() ? nameOf(key) : declaredName;
    final Set<String> required = new HashSet<>();
    for (final String section : EDGE_SECTIONS) {
      if (body.get(section) instanceof Map<?, ?> deps) {
        deps.keySet().forEach(dependency -> required.add(String.valueOf(dependency)));
      }
    }
    return new Entry(
        name, string(body.get("version")), string(body.get("resolved")), link, required);
  }

  /** Flattens a version 1 {@code dependencies} tree into path keys. */
  private void readTree(final String parent, final Object tree) {
    if (!(tree instanceof Map<?, ?> dependencies)) {
      return;
    }
    dependencies.forEach(
        (name, value) -> {
          if (!(value instanceof Map<?, ?> body)) {
            return;
          }
          final String key = parent.isEmpty() ? NODE_MODULES + name : parent + SEGMENT + name;
          final Set<String> required = new HashSet<>();
          if (body.get("requires") instanceof Map<?, ?> requires) {
            requires.keySet().forEach(dependency -> required.add(String.valueOf(dependency)));
          }
          entries.put(
              key,
              new Entry(
                  String.valueOf(name),
                  string(body.get("version")),
                  string(body.get("resolved")),
                  "",
                  required));
          readTree(key, body.get("dependencies"));
        });
  }

  private NodeLock toLock() {
    final NodeLock.Collector collector = new NodeLock.Collector();
    entries.forEach(
        (key, entry) -> {
          if (isPackage(key, entry)) {
            final Set<PackageId> required = new HashSet<>();
            entry.required().forEach(name -> idOf(lookup(key, name)).ifPresent(required::add));
            collector.add(
                id(entry),
                entry.resolved().isEmpty() || NodeLock.registryTarball(entry.resolved()),
                required);
          }
        });
    return new NodeLock(collector.packages(), (member, name, range) -> idOf(lookup(member, name)));
  }

  /** A package is a {@code node_modules} entry that is neither a link nor a workspace member. */
  private static boolean isPackage(final String key, final Entry entry) {
    return key.contains(NODE_MODULES) && entry.link().isEmpty() && !entry.version().isEmpty();
  }

  private Optional<PackageId> idOf(final String key) {
    final Entry entry = key == null ? null : entries.get(key);
    return entry == null || !isPackage(key, entry) ? Optional.empty() : Optional.of(id(entry));
  }

  private static PackageId id(final Entry entry) {
    return NodeLock.npm(entry.name(), entry.version());
  }

  /** Returns the key of the entry that {@code name} resolves to from {@code from}, or null. */
  private String lookup(final String from, final String name) {
    String base = from;
    while (true) {
      final String key = (base.isEmpty() ? "" : base + "/") + NODE_MODULES + name;
      if (entries.containsKey(key)) {
        return key;
      }
      if (base.isEmpty()) {
        return null;
      }
      final int cut = base.lastIndexOf(SEGMENT);
      base = cut < 0 ? "" : base.substring(0, cut);
    }
  }

  private static String nameOf(final String key) {
    final int cut = key.lastIndexOf(NODE_MODULES);
    return cut < 0 ? key : key.substring(cut + NODE_MODULES.length());
  }

  private static String string(final Object value) {
    return value instanceof String text ? text : "";
  }
}
