package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The keywords of arrays: size, uniqueness and the schemas of the items. */
final class ArrayKeywords {

  private ArrayKeywords() {}

  /** Checks the array keywords in ajv order. */
  static boolean check(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    return Chain.run(
        validator,
        () -> size(validator, schema, "maxItems", data, path),
        () -> size(validator, schema, "minItems", data, path),
        () -> unique(validator, schema, data, path),
        () -> items(validator, schema, data, path));
  }

  private static boolean size(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String keyword,
      final DocNode data,
      final String path) {
    final Optional<DocNode> limit = schema.get(keyword);
    if (limit.isEmpty() || limit.get().kind() != DocNode.Kind.NUMBER) {
      return true;
    }
    final double bound = JsonValues.number(limit.get());
    final boolean max = "maxItems".equals(keyword);
    final int size = data.items().size();
    if (max ? size <= bound : size >= bound) {
      return true;
    }
    validator
        .errors()
        .add(
            SchemaError.of(
                keyword,
                path,
                "must NOT have "
                    + (max ? "more" : "fewer")
                    + " than "
                    + JsonValues.show(bound)
                    + " items"));
    return false;
  }

  private static boolean unique(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    if (!JsonValues.isTrue(schema.get("uniqueItems"))) {
      return true;
    }
    final List<DocNode> items = data.items();
    final boolean scalars = scalarItems(schema);
    final Map<String, Integer> seen = new HashMap<>();
    for (int i = items.size() - 1; i >= 0 && scalars; i--) {
      final String key = items.get(i).kind() + ":" + items.get(i).text();
      final Integer j = seen.put(JsonValues.canonical(items.get(i), key), i);
      if (j != null) {
        return duplicate(validator, path, j, i);
      }
    }
    for (int i = items.size() - 1; i > 0 && !scalars; i--) {
      for (int j = i - 1; j >= 0; j--) {
        if (JsonValues.equal(items.get(i), items.get(j))) {
          return duplicate(validator, path, j, i);
        }
      }
    }
    return true;
  }

  /** Tells whether ajv takes its fast path: the items declare only scalar types. */
  private static boolean scalarItems(final DocNode schema) {
    final List<String> types =
        schema
            .get("items")
            .filter(node -> node.kind() == DocNode.Kind.OBJECT)
            .map(Types::declared)
            .orElse(List.of());
    return !types.isEmpty() && !types.contains("object") && !types.contains("array");
  }

  private static boolean duplicate(
      final JsonSchemaValidator validator, final String path, final int first, final int second) {
    validator
        .errors()
        .add(
            SchemaError.of(
                "uniqueItems",
                path,
                "must NOT have duplicate items (items ## "
                    + first
                    + " and "
                    + second
                    + " are identical)"));
    return false;
  }

  private static boolean items(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final boolean tuples = validator.getDialect() == JsonSchemaValidator.Dialect.DRAFT2020;
    final Optional<DocNode> prefix = tuples ? schema.get("prefixItems") : Optional.empty();
    final Optional<DocNode> items = schema.get("items");
    final List<DocNode> positional =
        prefix.isPresent()
            ? prefix.get().items()
            : items
                .filter(node -> node.kind() == DocNode.Kind.ARRAY && !tuples)
                .map(DocNode::items)
                .orElse(List.of());
    boolean valid = true;
    for (int index = 0; index < data.items().size(); index++) {
      final Optional<DocNode> applicable =
          index < positional.size()
              ? Optional.of(positional.get(index))
              : rest(schema, items, !positional.isEmpty() || prefix.isPresent());
      if (applicable.isPresent()) {
        valid &=
            validator.check(
                applicable.get(), data.items().get(index), path + "/" + index, validator.scratch());
        if (!valid && !validator.allErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  /** The schema of the items beyond the positional ones. */
  private static Optional<DocNode> rest(
      final DocNode schema, final Optional<DocNode> items, final boolean tuple) {
    if (tuple && items.isPresent() && items.get().kind() == DocNode.Kind.ARRAY) {
      return schema.get("additionalItems");
    }
    return items.filter(node -> node.kind() != DocNode.Kind.ARRAY);
  }
}
