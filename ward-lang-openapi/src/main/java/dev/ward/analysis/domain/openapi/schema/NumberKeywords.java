package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Optional;

/** The keywords of numbers: bounds, exclusive bounds of draft 4, and multipleOf. */
final class NumberKeywords {

  private NumberKeywords() {}

  static boolean check(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final double value = JsonValues.number(data);
    final boolean draft4 = validator.getDialect() == JsonSchemaValidator.Dialect.DRAFT4;
    return Chain.run(
        validator,
        () -> bound(validator, schema, "maximum", value, path),
        () -> bound(validator, schema, "minimum", value, path),
        () -> draft4 || bound(validator, schema, "exclusiveMaximum", value, path),
        () -> draft4 || bound(validator, schema, "exclusiveMinimum", value, path),
        () -> multipleOf(validator, schema, value, path));
  }

  private static boolean flag(final DocNode schema, final String key) {
    return JsonValues.isTrue(schema.get(key));
  }

  private static boolean bound(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String keyword,
      final double value,
      final String path) {
    final Optional<DocNode> limit = schema.get(keyword);
    if (limit.isEmpty() || limit.get().kind() != DocNode.Kind.NUMBER) {
      return true;
    }
    final boolean upper = keyword.endsWith("aximum");
    final boolean exclusive = exclusive(validator, schema, keyword, upper);
    final double bound = JsonValues.number(limit.get());
    final boolean ok = holds(upper, exclusive, value, bound);
    if (ok) {
      return true;
    }
    final String comparison = (upper ? "<" : ">") + (exclusive ? "" : "=");
    validator
        .errors()
        .add(SchemaError.of(keyword, path, "must be " + comparison + " " + JsonValues.show(bound)));
    return false;
  }

  private static boolean exclusive(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String keyword,
      final boolean upper) {
    return keyword.startsWith("exclusive")
        || validator.getDialect() == JsonSchemaValidator.Dialect.DRAFT4
            && flag(schema, upper ? "exclusiveMaximum" : "exclusiveMinimum");
  }

  private static boolean holds(
      final boolean upper, final boolean exclusive, final double value, final double bound) {
    if (upper) {
      return exclusive ? value < bound : value <= bound;
    }
    return exclusive ? value > bound : value >= bound;
  }

  private static boolean multipleOf(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final double value,
      final String path) {
    final Optional<DocNode> factor = schema.get("multipleOf");
    if (factor.isEmpty() || factor.get().kind() != DocNode.Kind.NUMBER) {
      return true;
    }
    final double divisor = JsonValues.number(factor.get());
    final double quotient = value / divisor;
    if (divisor != 0 && quotient == Math.rint(quotient)) {
      return true;
    }
    validator
        .errors()
        .add(SchemaError.of("multipleOf", path, "must be multiple of " + JsonValues.show(divisor)));
    return false;
  }
}
