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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The stored form of a resolved graph: plain maps and lists through the JSON port, so the document
 * needs no reflection. Anything that does not decode to the current format is rejected.
 */
final class InventoryJson {
  /** The version of the stored format; part of the cache key. */
  static final int FORMAT = 1;

  private InventoryJson() {}

  /** Returns {@code inventory} as JSON. */
  static String encode(final JsonCodec codec, final DependencyInventory inventory) {
    final List<Object> modules = new ArrayList<>();
    for (final ModuleDependencies module : inventory.modules()) {
      final List<Object> entries = new ArrayList<>();
      module.dependencies().forEach(entry -> entries.add(entry(entry)));
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("path", module.modulePath());
      map.put("dependencies", entries);
      modules.add(map);
    }
    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("format", FORMAT);
    root.put("modules", modules);
    return codec.toJson(root);
  }

  private static Map<String, Object> entry(final Dependency entry) {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id(entry.id()));
    map.put("relation", entry.relation().name());
    map.put("scope", entry.scope().name());
    map.put("released", entry.released());
    map.put("source", entry.source());
    map.put("declaredIn", entry.declaredIn());
    map.put(
        "requiredBy", new TreeSet<>(entry.requiredBy()).stream().map(InventoryJson::id).toList());
    final List<Object> requested = new ArrayList<>();
    entry.requested().stream()
        .sorted(
            java.util.Comparator.comparing(RequestedVersion::requester)
                .thenComparing(RequestedVersion::version))
        .forEach(
            request ->
                requested.add(Map.of("by", request.requester(), "version", request.version())));
    map.put("requested", requested);
    return map;
  }

  private static Map<String, Object> id(final PackageId id) {
    return Map.of("ecosystem", id.ecosystem().name(), "name", id.name(), "version", id.version());
  }

  /**
   * Decodes {@code json}.
   *
   * @throws IllegalArgumentException if it is not a document of the current format
   */
  static DependencyInventory decode(final JsonCodec codec, final String json) {
    try {
      if (!(codec.fromJson(json, Object.class) instanceof Map<?, ?> root)
          || !(root.get("format") instanceof Number format)
          || format.intValue() != FORMAT
          || !(root.get("modules") instanceof List<?> modules)) {
        throw new IllegalArgumentException("not a stored graph");
      }
      final List<ModuleDependencies> result = new ArrayList<>();
      for (final Object module : modules) {
        result.add(module(map(module)));
      }
      return new DependencyInventory(result);
    } catch (JsonCodecException
        | ClassCastException
        | NullPointerException
        | IllegalStateException e) {
      throw new IllegalArgumentException("not a stored graph", e);
    }
  }

  private static ModuleDependencies module(final Map<?, ?> module) {
    final List<Dependency> entries = new ArrayList<>();
    for (final Object entry : list(module.get("dependencies"))) {
      final Map<?, ?> map = map(entry);
      final Set<PackageId> requiredBy = new TreeSet<>();
      list(map.get("requiredBy")).forEach(item -> requiredBy.add(packageId(map(item))));
      final Set<RequestedVersion> requested = new java.util.HashSet<>();
      list(map.get("requested"))
          .forEach(
              item ->
                  requested.add(
                      new RequestedVersion(text(map(item), "by"), text(map(item), "version"))));
      entries.add(
          new Dependency(
              packageId(map(map.get("id"))),
              DependencyRelation.valueOf(text(map, "relation")),
              DependencyScope.valueOf(text(map, "scope")),
              Boolean.TRUE.equals(map.get("released")),
              text(map, "source"),
              text(map, "declaredIn"),
              requiredBy,
              requested));
    }
    return new ModuleDependencies(text(module, "path"), entries);
  }

  private static PackageId packageId(final Map<?, ?> map) {
    return new PackageId(
        Ecosystem.valueOf(text(map, "ecosystem")), text(map, "name"), text(map, "version"));
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
