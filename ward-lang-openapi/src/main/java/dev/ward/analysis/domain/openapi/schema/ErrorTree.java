package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tree {@code @stoplight/better-ajv-errors} builds from ajv errors: one level for each chunk of
 * the instance path, the errors of a value at its node. It drops the redundant errors and merges
 * the {@code enum} errors of a value.
 */
final class ErrorTree {

  private static final Pattern CHUNK = Pattern.compile("/[\\w$_-]+(/\\d+)?");

  private final Map<String, ErrorTree> children = new LinkedHashMap<>();
  private List<SchemaError> errors = new ArrayList<>();

  /** Builds the tree of {@code errors}, filtered. */
  static List<SchemaError> flatten(final List<SchemaError> errors) {
    final ErrorTree root = new ErrorTree();
    errors.forEach(root::attach);
    root.filter(null, null);
    final List<SchemaError> flat = new ArrayList<>();
    root.collect(flat);
    return flat;
  }

  private void attach(final SchemaError error) {
    final String path = error.instancePath();
    final List<String> chunks = new ArrayList<>();
    if (path.isEmpty()) {
      chunks.add("");
    } else {
      final Matcher matcher = CHUNK.matcher(path);
      while (matcher.find()) {
        chunks.add(matcher.group());
      }
    }
    ErrorTree node = this;
    for (final String chunk : chunks) {
      node = node.child(chunk);
    }
    node.errors.add(error);
  }

  private ErrorTree child(final String chunk) {
    return children.computeIfAbsent(chunk, key -> new ErrorTree());
  }

  private void filter(final ErrorTree parent, final String key) {
    if (errors.stream().anyMatch(error -> "anyOf".equals(error.keyword())) && !children.isEmpty()) {
      errors = new ArrayList<>();
    }
    if (allEnum(errors) && parent != null && parent.children.size() > 1) {
      parent.children.remove(key);
    }
    for (final Map.Entry<String, ErrorTree> child : new ArrayList<>(children.entrySet())) {
      child.getValue().filter(this, child.getKey());
    }
  }

  private static boolean allEnum(final List<SchemaError> errors) {
    return !errors.isEmpty() && errors.stream().allMatch(error -> "enum".equals(error.keyword()));
  }

  private void collect(final List<SchemaError> out) {
    if (allEnum(errors)) {
      out.add(merged(errors));
    } else {
      out.addAll(errors);
    }
    children.values().forEach(child -> child.collect(out));
  }

  /** One {@code enum} error with the distinct allowed values of all of them. */
  private static SchemaError merged(final List<SchemaError> errors) {
    final List<DocNode> allowed = new ArrayList<>();
    errors.stream()
        .flatMap(error -> error.allowed().stream())
        .filter(value -> allowed.stream().noneMatch(known -> JsonValues.equal(known, value)))
        .forEach(allowed::add);
    final SchemaError first = errors.getFirst();
    return new SchemaError("enum", first.instancePath(), first.message(), "", allowed, false);
  }
}
