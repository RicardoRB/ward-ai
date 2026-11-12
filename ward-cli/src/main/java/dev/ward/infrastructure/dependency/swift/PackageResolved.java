package dev.ward.infrastructure.dependency.swift;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code Package.resolved}, versions 1, 2 and 3. A pin with a {@code version} is a release; a
 * pin on a revision or branch uses the revision as its version and is not a release, as is a local
 * or file-system pin.
 */
final class PackageResolved {
  private static final Set<Integer> VERSIONS = Set.of(1, 2, 3);
  private static final String LOCAL = "local";

  /** A pin: the package and the identity SwiftPM gives it. */
  record Pin(PackageId id, boolean released, String identity) {}

  private PackageResolved() {}

  /**
   * Reads {@code text}.
   *
   * @throws LockfileFormatException if it is not JSON or has another format version
   */
  static List<Pin> read(final String text, final JsonCodec codec) {
    final Map<?, ?> root = root(text, codec);
    if (!(root.get("version") instanceof Number version)
        || !VERSIONS.contains(version.intValue())) {
      throw new LockfileFormatException("unsupported version " + root.get("version"));
    }
    final int number = version.intValue();
    final Object pins =
        number == 1 && root.get("object") instanceof Map<?, ?> object
            ? object.get("pins")
            : root.get("pins");
    return pins instanceof List<?> list
        ? list.stream()
            .filter(Map.class::isInstance)
            .map(item -> pin((Map<?, ?>) item, number))
            .toList()
        : List.of();
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

  private static Pin pin(final Map<?, ?> pin, final int version) {
    final boolean first = version == 1;
    final String location = text(pin.get(first ? "repositoryURL" : "location"));
    final String identity = text(pin.get(first ? "package" : "identity"));
    final Map<?, ?> state = pin.get("state") instanceof Map<?, ?> map ? map : Map.of();
    final String name = SwiftLocations.normalize(location.isEmpty() ? identity : location);
    if (name.isEmpty()) {
      throw new LockfileFormatException("a pin has neither a location nor an identity");
    }
    final boolean release = !text(state.get("version")).isEmpty() && !local(text(pin.get("kind")));
    final String exact = release ? text(state.get("version")) : text(state.get("revision"));
    return new Pin(
        new PackageId(Ecosystem.SWIFT_URL, name, exact.isEmpty() ? LOCAL : exact),
        release,
        identity.toLowerCase(java.util.Locale.ROOT));
  }

  private static boolean local(final String kind) {
    return "localSourceControl".equals(kind) || "fileSystem".equals(kind);
  }

  private static String text(final Object value) {
    return value instanceof String text ? text : "";
  }
}
