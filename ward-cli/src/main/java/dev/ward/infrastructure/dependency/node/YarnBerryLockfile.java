package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the Yarn Berry {@code yarn.lock}, a YAML file whose stanzas are headed by comma-separated
 * {@code name@protocol:range} descriptors and hold {@code version}, {@code resolution} and
 * dependencies. Workspace stanzas are members, not packages.
 */
final class YarnBerryLockfile {
  private static final String METADATA = "__metadata";
  private static final String VERSION = "version";
  private static final String WORKSPACE = "@workspace:";
  private static final String NPM = "npm:";
  private static final int FIRST_SUPPORTED = 6;

  private YarnBerryLockfile() {}

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if the metadata version is not one Berry writes, or a stanza
   *     lacks a version
   */
  static NodeLock read(final String text) {
    final Map<String, Object> root = YamlSubset.parse(text);
    final String metadata = YamlSubset.scalar(YamlSubset.map(root.get(METADATA)).get(VERSION));
    if (!supported(metadata)) {
      throw new LockfileFormatException("unsupported __metadata version " + metadata);
    }
    final Map<String, Map<String, Object>> byDescriptor = new HashMap<>();
    root.forEach(
        (header, body) -> {
          if (!METADATA.equals(header)) {
            for (final String descriptor : header.split(",\\s*")) {
              byDescriptor.put(descriptor.strip(), YamlSubset.map(body));
            }
          }
        });
    final NodeLock.Collector collector = new NodeLock.Collector();
    root.forEach(
        (header, body) -> {
          if (!METADATA.equals(header) && !header.contains(WORKSPACE)) {
            final Map<String, Object> stanza = YamlSubset.map(body);
            final Set<PackageId> required = new HashSet<>();
            for (final String section : new String[] {"dependencies", "optionalDependencies"}) {
              YamlSubset.map(stanza.get(section))
                  .forEach(
                      (name, range) ->
                          find(byDescriptor, name, YamlSubset.scalar(range))
                              .ifPresent(target -> required.add(id(name, target))));
            }
            collector.add(id(header.split(",")[0].strip(), stanza), released(stanza), required);
          }
        });
    return new NodeLock(
        collector.packages(),
        (member, name, range) -> find(byDescriptor, name, range).map(target -> id(name, target)));
  }

  private static boolean supported(final String metadata) {
    try {
      return Integer.parseInt(metadata) >= FIRST_SUPPORTED;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** Finds the stanza of {@code name@range}, which Berry writes with an explicit protocol. */
  private static Optional<Map<String, Object>> find(
      final Map<String, Map<String, Object>> stanzas, final String name, final String range) {
    final Map<String, Object> plain = stanzas.get(name + "@" + range);
    return Optional.ofNullable(plain != null ? plain : stanzas.get(name + "@" + NPM + range));
  }

  private static PackageId id(final String descriptor, final Map<String, Object> stanza) {
    final String version = YamlSubset.scalar(stanza.get(VERSION));
    if (version.isEmpty()) {
      throw new LockfileFormatException("package " + descriptor + " has no version");
    }
    final int at = descriptor.indexOf('@', 1);
    return NodeLock.npm(at < 0 ? descriptor : descriptor.substring(0, at), version);
  }

  private static boolean released(final Map<String, Object> stanza) {
    final String resolution = YamlSubset.scalar(stanza.get("resolution"));
    return resolution.contains("@" + NPM) || resolution.contains("@patch:");
  }
}
