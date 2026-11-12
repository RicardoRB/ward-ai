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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The stored form of a resolved graph: plain maps and lists through the JSON port, so the document
 * needs no reflection. Anything that does not decode to the current format is rejected.
 */
final class InventoryJson {
  /** The version of the stored format; part of the cache key. */
  static final int FORMAT = 1;

  private static final String PATH = "path";
  private static final String DEPENDENCIES = "dependencies";
  private static final String NAME = "name";
  private static final String VERSION = "version";
  private static final String REQUIRED_BY = "requiredBy";
  private static final String REQUESTED = "requested";

  private InventoryJson() {}

  /** Returns {@code inventory} as JSON. */
  static String encode(final JsonCodec codec, final DependencyInventory inventory) {
    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("format", FORMAT);
    root.put("modules", inventory.modules().stream().map(InventoryJson::module).toList());
    return codec.toJson(root);
  }

  private static Map<String, Object> module(final ModuleDependencies module) {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put(PATH, module.modulePath());
    map.put(DEPENDENCIES, module.dependencies().stream().map(InventoryJson::entry).toList());
    return map;
  }

  private static Map<String, Object> entry(final Dependency entry) {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id(entry.id()));
    map.put("relation", entry.relation().name());
    map.put("scope", entry.scope().name());
    map.put("released", entry.released());
    map.put("source", entry.source());
    map.put("declaredIn", entry.declaredIn());
    map.put(REQUIRED_BY, entry.requiredBy().stream().sorted().map(InventoryJson::id).toList());
    map.put(
        REQUESTED,
        entry.requested().stream()
            .sorted(
                Comparator.comparing(RequestedVersion::requester)
                    .thenComparing(RequestedVersion::version))
            .map(InventoryJson::request)
            .toList());
    return map;
  }

  private static Map<String, Object> request(final RequestedVersion request) {
    return Map.of("by", request.requester(), VERSION, request.version());
  }

  private static Map<String, Object> id(final PackageId id) {
    return Map.of("ecosystem", id.ecosystem().name(), NAME, id.name(), VERSION, id.version());
  }

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
        || format.intValue() != FORMAT) {
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
