package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code allof-conflicts}: a property declared by several {@code allOf} branches has a common type.
 *
 * <p>shortcut: vacuum merges {@code allOf} through a graph of strongly connected schemas; this
 * reads the branches of one schema and their own {@code allOf} branches. Upgrade when a real
 * specification differs.
 */
final class AllOfRules {

  private static final List<String> ORDER =
      List.of("string", "number", "integer", "boolean", "object", "array");

  private AllOfRules() {}

  /** Reports a property whose declared types across the {@code allOf} branches do not overlap. */
  static List<OpenApiFinding> conflicts(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      schema.get("allOf").ifPresent(allOf -> findings.addAll(conflictsIn(document, allOf)));
    }
    return findings;
  }

  private static List<OpenApiFinding> conflictsIn(
      final OpenApiDocument document, final DocNode allOf) {
    final Map<String, List<Set<String>>> declared = new TreeMap<>();
    final Set<DocNode> visited = new LinkedHashSet<>();
    allOf.items().forEach(branch -> branchProperties(document, branch, declared, visited));
    final List<OpenApiFinding> findings = new ArrayList<>();
    declared.forEach(
        (name, sets) -> {
          if (sets.size() > 1 && !overlap(sets)) {
            findings.add(Findings.at(allOf, message(name, sets)));
          }
        });
    return findings;
  }

  private static void branchProperties(
      final OpenApiDocument document,
      final DocNode branch,
      final Map<String, List<Set<String>>> declared,
      final Set<DocNode> visited) {
    final DocNode resolved = document.resolve(branch);
    if (!visited.add(resolved)) {
      return;
    }
    resolved
        .get("properties")
        .map(DocNode::entries)
        .orElse(List.of())
        .forEach(property -> declare(document, property, declared));
    resolved
        .get("allOf")
        .ifPresent(
            nested ->
                nested
                    .items()
                    .forEach(item -> branchProperties(document, item, declared, visited)));
  }

  private static void declare(
      final OpenApiDocument document,
      final DocNode.Entry property,
      final Map<String, List<Set<String>>> declared) {
    final Set<String> types = typesOf(document.resolve(property.value()));
    if (!types.isEmpty()) {
      declared.computeIfAbsent(property.key(), key -> new ArrayList<>()).add(types);
    }
  }

  private static Set<String> typesOf(final DocNode schema) {
    final Set<String> types = new LinkedHashSet<>();
    schema
        .get("type")
        .ifPresent(
            type -> {
              if (type.kind() == DocNode.Kind.STRING) {
                types.add(type.text());
              } else {
                type.items().forEach(item -> types.add(item.text()));
              }
            });
    return types;
  }

  /** Tells whether one type is allowed by every set, counting an integer as a number. */
  private static boolean overlap(final List<Set<String>> sets) {
    return ORDER.stream()
        .anyMatch(
            type ->
                sets.stream()
                    .allMatch(
                        set ->
                            set.contains(type)
                                || "integer".equals(type) && set.contains("number")
                                || "number".equals(type) && set.contains("integer")));
  }

  private static String message(final String name, final List<Set<String>> sets) {
    final Map<String, Boolean> union = new LinkedHashMap<>();
    sets.forEach(set -> set.forEach(type -> union.put(type, true)));
    final List<String> labels = ORDER.stream().filter(union::containsKey).toList();
    return "`allOf` property `"
        + name
        + "` declared as ["
        + String.join(", ", labels)
        + "]: properties defined across an `allOf` composition must have a non-empty common valid"
        + " type";
  }
}
