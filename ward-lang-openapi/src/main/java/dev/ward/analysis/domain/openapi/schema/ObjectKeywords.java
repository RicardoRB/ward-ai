package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Optional;
import java.util.Set;

/** The keywords of objects: size, required, and the dispatch to those of the properties. */
final class ObjectKeywords {

  private ObjectKeywords() {}

  /** Checks the object keywords in ajv order. */
  static boolean check(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    return Chain.run(
        validator,
        () -> count(validator, schema, "maxProperties", data, path),
        () -> count(validator, schema, "minProperties", data, path),
        () -> required(validator, schema, data, path),
        () -> DependencyKeywords.dependentRequired(validator, schema, data, path),
        () -> DependencyKeywords.dependencies(validator, schema, data, path),
        () -> DependencyKeywords.propertyNames(validator, schema, data, path),
        () -> DependencyKeywords.dependentSchemas(validator, schema, data, path),
        () -> PropertyKeywords.additional(validator, schema, data, path, evaluated),
        () -> PropertyKeywords.properties(validator, schema, data, path, evaluated),
        () -> PropertyKeywords.patterns(validator, schema, data, path, evaluated),
        () -> PropertyKeywords.unevaluated(validator, schema, data, path, evaluated));
  }

  private static boolean count(
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
    final boolean max = "maxProperties".equals(keyword);
    final int size = data.entries().size();
    if (max ? size <= bound : size >= bound) {
      return true;
    }
    final String direction = max ? "more" : "fewer";
    validator
        .getErrors()
        .add(
            SchemaError.of(
                keyword,
                path,
                "must NOT have " + direction + " than " + JsonValues.show(bound) + " properties"));
    return false;
  }

  private static boolean required(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> names = schema.get("required");
    if (names.isEmpty() || names.get().kind() != DocNode.Kind.ARRAY) {
      return true;
    }
    boolean valid = true;
    for (final DocNode name : names.get().items()) {
      if (data.get(name.text()).isEmpty() && !relaxed(validator, schema, name.text())) {
        validator.getErrors().add(missing(path, name.text()));
        valid = false;
        if (!validator.isAllErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  private static SchemaError missing(final String path, final String name) {
    return SchemaError.about("required", path, "must have required property '" + name + "'", name);
  }

  /** Tells whether the property is left out of {@code required} by readOnly or writeOnly. */
  private static boolean relaxed(
      final JsonSchemaValidator validator, final DocNode schema, final String name) {
    final String flag =
        switch (validator.getRelax()) {
          case READ_ONLY -> "readOnly";
          case WRITE_ONLY -> "writeOnly";
          default -> "";
        };
    return !flag.isEmpty()
        && !validator.isInsideNot()
        && schema
            .get("properties")
            .flatMap(properties -> properties.get(name))
            .filter(property -> JsonValues.isTrue(property.get(flag)))
            .isPresent();
  }
}
