package dev.ward.infrastructure.dependency;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.dependency.RequestedVersion;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the stored form that {@link InventoryJson} writes; anything else is rejected. */
final class InventoryDecoder {
  private static final String PATH = "path";
  private static final String DEPENDENCIES = "dependencies";
  private static final String NAME = "name";
  private static final String VERSION = "version";
  private static final String REQUIRED_BY = "requiredBy";
  private static final String REQUESTED = "requested";

  private InventoryDecoder() {}

  /**
   * Decodes {@code json}.
   *
   * @throws IllegalArgumentException if it is not a document of the current format
   */
  static DependencyInventory decode(final JsonCodec codec, final String json) {
    final Object parsed;
    try {
      parsed = codec.fromJson(json, Object.class);
    } catch (JsonCodecException e) {
      throw new IllegalArgumentException("not a stored graph", e);
    }
    if (!(parsed instanceof Map<?, ?> root)
        || !(root.get("format") instanceof Number format)
        || format.intValue() != InventoryJson.FORMAT) {
      throw new IllegalArgumentException("not a stored graph");
    }
    return new DependencyInventory(
        list(root.get("modules")).stream().map(item -> decodeModule(map(item))).toList());
  }

  private static ModuleDependencies decodeModule(final Map<?, ?> module) {
    return new ModuleDependencies(
        text(module, PATH),
        list(module.get(DEPENDENCIES)).stream().map(item -> decodeEntry(map(item))).toList());
  }

  private static Dependency decodeEntry(final Map<?, ?> map) {
    final Set<PackageId> requiredBy =
        list(map.get(REQUIRED_BY)).stream()
            .map(item -> packageId(map(item)))
            .collect(Collectors.toSet());
    final Set<RequestedVersion> requested =
        list(map.get(REQUESTED)).stream()
            .map(item -> decodeRequest(map(item)))
            .collect(Collectors.toSet());
    return new Dependency(
        packageId(map(map.get("id"))),
        DependencyRelation.valueOf(text(map, "relation")),
        DependencyScope.valueOf(text(map, "scope")),
        Boolean.TRUE.equals(map.get("released")),
        text(map, "source"),
        text(map, "declaredIn"),
        requiredBy,
        requested);
  }

  private static RequestedVersion decodeRequest(final Map<?, ?> map) {
    return new RequestedVersion(text(map, "by"), text(map, VERSION));
  }

  private static PackageId packageId(final Map<?, ?> map) {
    return new PackageId(
        Ecosystem.valueOf(text(map, "ecosystem")), text(map, NAME), text(map, VERSION));
  }

  private static Map<?, ?> map(final Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    throw new IllegalArgumentException("not an object");
  }

  private static List<?> list(final Object value) {
    if (value instanceof List<?> list) {
      return list;
    }
    throw new IllegalArgumentException("not a list");
  }

  private static String text(final Map<?, ?> map, final String key) {
    if (map.get(key) instanceof String text) {
      return text;
    }
    throw new IllegalArgumentException("missing " + key);
  }
}
