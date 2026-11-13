package dev.ward.infrastructure.dependency;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.dependency.RequestedVersion;
import dev.ward.domain.json.JsonCodec;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The stored form of a resolved graph: plain maps and lists through the JSON port, so the document
 * needs no reflection. Anything that does not decode to the current format is rejected.
 */
final class InventoryJson {
  /** The version of the stored format; part of the cache key. */
  static final int FORMAT = 2;

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
                    .thenComparing(RequestedVersion::version)
                    .thenComparing(RequestedVersion::premanaged))
            .map(InventoryJson::request)
            .toList());
    return map;
  }

  private static Map<String, Object> request(final RequestedVersion request) {
    return Map.of(
        "by", request.requester(), VERSION, request.version(), "premanaged", request.premanaged());
  }

  private static Map<String, Object> id(final PackageId id) {
    return Map.of("ecosystem", id.ecosystem().name(), NAME, id.name(), VERSION, id.version());
  }
}
