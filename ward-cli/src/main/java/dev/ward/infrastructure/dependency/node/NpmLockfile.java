package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
  private static final int OLDEST = 1;
  private static final int NEWEST = 3;

  private final Map<String, NpmEntry> entries = new LinkedHashMap<>();

  private NpmLockfile() {}

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
      throw new LockfileFormatException("it is not valid JSON", e);
    }
    if (!(parsed instanceof Map<?, ?> root)) {
      throw new LockfileFormatException("it is not a JSON object");
    }
    final int version = version(root.get("lockfileVersion"));
    final NpmLockfile lock = new NpmLockfile();
    if (version == OLDEST) {
      lock.entries.putAll(NpmEntry.flatten(root.get("dependencies")));
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
            entries.put(String.valueOf(key), NpmEntry.of(String.valueOf(key), body));
          }
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
  private static boolean isPackage(final String key, final NpmEntry entry) {
    return key.contains(NODE_MODULES) && entry.link().isEmpty() && !entry.version().isEmpty();
  }

  private Optional<PackageId> idOf(final String key) {
    final NpmEntry entry = key == null ? null : entries.get(key);
    return entry == null || !isPackage(key, entry) ? Optional.empty() : Optional.of(id(entry));
  }

  private static PackageId id(final NpmEntry entry) {
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
}
