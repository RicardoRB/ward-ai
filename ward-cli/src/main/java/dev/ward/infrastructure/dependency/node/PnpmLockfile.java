package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads {@code pnpm-lock.yaml}, versions 6.0 and 9.0. Version 9 keeps each package's resolution
 * under {@code packages} and its edges under {@code snapshots}; version 6 keeps both under {@code
 * packages}. A peer-dependency suffix such as {@code (react@18.3.1)} is dropped from identities.
 */
final class PnpmLockfile {
  private static final String PEER_SUFFIX_START = "(";
  private static final String LINK = "link:";
  private static final List<String> EDGES = List.of("dependencies", "optionalDependencies");
  private static final List<String> IMPORTER_SECTIONS =
      List.of("dependencies", "devDependencies", "optionalDependencies");
  private static final Set<String> VERSIONS = Set.of("6.0", "9.0");

  private PnpmLockfile() {}

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if the text is not a lockfile this reader knows
   */
  static NodeLock read(final String text) {
    final Map<String, Object> root = YamlSubset.parse(text);
    final String version = YamlSubset.scalar(root.get("lockfileVersion"));
    if (!VERSIONS.contains(version)) {
      throw new LockfileFormatException("unsupported lockfileVersion " + version);
    }
    final Map<String, Object> packages = YamlSubset.map(root.get("packages"));
    final boolean split = version.startsWith("9");
    final Map<String, Object> graph = split ? YamlSubset.map(root.get("snapshots")) : packages;
    final NodeLock.Collector collector = new NodeLock.Collector();
    graph.forEach(
        (key, body) -> {
          final PackageId id = parseKey(key);
          final Map<String, Object> meta =
              YamlSubset.map(split ? packages.get(baseKey(key)) : body);
          final Set<PackageId> required = new HashSet<>();
          for (final String section : EDGES) {
            YamlSubset.map(YamlSubset.map(body).get(section))
                .forEach(
                    (name, value) -> toId(name, YamlSubset.scalar(value)).ifPresent(required::add));
          }
          collector.add(id, released(meta), required);
        });
    final Map<String, Object> importers = importers(root);
    return new NodeLock(
        collector.packages(), (member, name, range) -> direct(importers, member, name));
  }

  /** v6 without workspaces keeps the root importer's sections at the top. */
  private static Map<String, Object> importers(final Map<String, Object> root) {
    if (root.get("importers") instanceof Map) {
      return YamlSubset.map(root.get("importers"));
    }
    return Map.of(".", root);
  }

  private static Optional<PackageId> direct(
      final Map<String, Object> importers, final String member, final String name) {
    final Map<String, Object> importer =
        YamlSubset.map(importers.get(member.isEmpty() ? "." : member));
    for (final String section : IMPORTER_SECTIONS) {
      final Object entry = YamlSubset.map(importer.get(section)).get(name);
      if (entry != null) {
        final String resolved =
            entry instanceof Map
                ? YamlSubset.scalar(YamlSubset.map(entry).get("version"))
                : YamlSubset.scalar(entry);
        return toId(name, resolved);
      }
    }
    return Optional.empty();
  }

  private static boolean released(final Map<String, Object> meta) {
    final Map<String, Object> resolution = YamlSubset.map(meta.get("resolution"));
    return !(resolution.containsKey("tarball")
        || resolution.containsKey("commit")
        || resolution.containsKey("repo")
        || resolution.containsKey("directory")
        || resolution.containsKey("type"));
  }

  /** Returns the package {@code name} resolves to for the dependency value {@code value}. */
  private static Optional<PackageId> toId(final String name, final String value) {
    if (value.isEmpty() || value.startsWith(LINK)) {
      return Optional.empty();
    }
    final String plain = baseKey(value);
    if (plain.startsWith("/") || plain.indexOf('@') > 0) {
      return Optional.of(parseKey(plain));
    }
    return Optional.of(NodeLock.npm(name, plain));
  }

  /** Strips a version 6 leading slash and the peer suffix. */
  private static String baseKey(final String key) {
    final int peers = key.indexOf(PEER_SUFFIX_START);
    final String plain = peers < 0 ? key : key.substring(0, peers);
    return plain.startsWith("/") ? plain.substring(1) : plain;
  }

  private static PackageId parseKey(final String key) {
    final String plain = baseKey(key);
    final int at = plain.lastIndexOf('@');
    if (at <= 0 || at == plain.length() - 1) {
      throw new LockfileFormatException("the package key '" + key + "' has no version");
    }
    return NodeLock.npm(plain.substring(0, at), plain.substring(at + 1));
  }
}
