package dev.ward.infrastructure.dependency.node;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One entry of an npm lockfile, keyed by its {@code node_modules} path: the package name and
 * version, where it was resolved from, the link target of a workspace link, and the names of the
 * packages it requires.
 */
record NpmEntry(String name, String version, String resolved, String link, Set<String> required) {
  private static final String NODE_MODULES = "node_modules/";
  private static final String SEGMENT = "/" + NODE_MODULES;
  private static final List<String> EDGE_SECTIONS =
      List.of("dependencies", "optionalDependencies", "peerDependencies");

  /** Reads an entry of the {@code packages} map of version 2 and 3. */
  static NpmEntry of(final String key, final Map<?, ?> body) {
    final String link = Boolean.TRUE.equals(body.get("link")) ? text(body.get("resolved")) : "";
    final String declaredName = text(body.get("name"));
    final Set<String> required = new HashSet<>();
    for (final String section : EDGE_SECTIONS) {
      if (body.get(section) instanceof Map<?, ?> deps) {
        deps.keySet().forEach(dependency -> required.add(String.valueOf(dependency)));
      }
    }
    return new NpmEntry(
        declaredName.isEmpty() ? nameOf(key) : declaredName,
        text(body.get("version")),
        text(body.get("resolved")),
        link,
        required);
  }

  /** Flattens a version 1 {@code dependencies} tree into the path keys of version 2. */
  static Map<String, NpmEntry> flatten(final Object tree) {
    final Map<String, NpmEntry> entries = new LinkedHashMap<>();
    flatten(entries, "", tree);
    return entries;
  }

  private static void flatten(
      final Map<String, NpmEntry> entries, final String parent, final Object tree) {
    if (tree instanceof Map<?, ?> dependencies) {
      dependencies.forEach(
          (name, value) -> flattenOne(entries, parent, String.valueOf(name), value));
    }
  }

  private static void flattenOne(
      final Map<String, NpmEntry> entries,
      final String parent,
      final String name,
      final Object value) {
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
        new NpmEntry(name, text(body.get("version")), text(body.get("resolved")), "", required));
    flatten(entries, key, body.get("dependencies"));
  }

  private static String nameOf(final String key) {
    final int cut = key.lastIndexOf(NODE_MODULES);
    return cut < 0 ? key : key.substring(cut + NODE_MODULES.length());
  }

  private static String text(final Object value) {
    return value instanceof String text ? text : "";
  }
}
