package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The OWASP rules that ask a schema to limit its values. */
final class OwaspSchemaRules {

  private static final String TYPE = "type";

  /**
   * The presence of minimum, maximum, exclusiveMinimum and exclusiveMaximum that vacuum rejects.
   */
  private static final Set<String> INCOMPLETE_BOUNDS =
      Set.of("0000", "1000", "1010", "0100", "0101", "0010", "0001", "1011", "1111");

  private OwaspSchemaRules() {}

  /** {@code owasp-array-limit}: an array a request sends has {@code maxItems}. */
  static List<OpenApiFinding> arrayLimit(final OpenApiDocument document) {
    return request(
        document,
        "array",
        schema -> schema.get("maxItems").isEmpty(),
        "schema of type `array` must specify `maxItems`");
  }

  /** {@code owasp-string-limit}: a string a request sends has {@code maxLength}. */
  static List<OpenApiFinding> stringLimit(final OpenApiDocument document) {
    return request(
        document,
        "string",
        schema ->
            schema.get("maxLength").isEmpty()
                && schema.get("const").isEmpty()
                && schema.get("enum").isEmpty(),
        "schema of type `string` must specify `maxLength`, `const` or `enum`");
  }

  /** {@code owasp-string-restricted}: a string has a format, a pattern, a const or an enum. */
  static List<OpenApiFinding> stringRestricted(final OpenApiDocument document) {
    return all(
        document,
        "string",
        schema ->
            schema.get("format").isEmpty()
                && schema.get("const").isEmpty()
                && schema.get("enum").isEmpty()
                && Findings.text(schema, "pattern").isEmpty(),
        "schema of type `string` must specify `format`, `const`, `enum` or `pattern`");
  }

  /** {@code owasp-integer-format}: an integer has the format {@code int32} or {@code int64}. */
  static List<OpenApiFinding> integerFormat(final OpenApiDocument document) {
    return all(
        document,
        "integer",
        schema -> !Set.of("int32", "int64").contains(Findings.text(schema, "format")),
        "schema of type `integer` must specify a format of `int32` or `int64`");
  }

  /** {@code owasp-integer-limit}: an integer has a minimum and a maximum. */
  static List<OpenApiFinding> integerLimit(final OpenApiDocument document) {
    return all(
        document,
        "integer",
        OwaspSchemaRules::boundsIncomplete,
        "schema of type `integer` must specify `minimum` and `maximum` or `exclusiveMinimum` and"
            + " `exclusiveMaximum`");
  }

  private static List<OpenApiFinding> request(
      final OpenApiDocument document,
      final String type,
      final Predicate<DocNode> unlimited,
      final String message) {
    final Set<DocNode> request = SchemaWalk.requestSchemas(document);
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      if (request.contains(schema)
          && OwaspSupport.declares(schema, type)
          && unlimited.test(schema)) {
        findings.add(Findings.in(schema, OwaspSupport.key(schema, TYPE), message));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> all(
      final OpenApiDocument document,
      final String type,
      final Predicate<DocNode> flagged,
      final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      if (OwaspSupport.declares(schema, type) && flagged.test(schema)) {
        findings.add(Findings.in(schema, OwaspSupport.key(schema, TYPE), message));
      }
    }
    return findings;
  }

  private static boolean boundsIncomplete(final DocNode schema) {
    final String bits =
        flag(schema, "minimum")
            + flag(schema, "maximum")
            + flag(schema, "exclusiveMinimum")
            + flag(schema, "exclusiveMaximum");
    return INCOMPLETE_BOUNDS.contains(bits);
  }

  private static String flag(final DocNode schema, final String key) {
    return schema.get(key).isPresent() ? "1" : "0";
  }
}
