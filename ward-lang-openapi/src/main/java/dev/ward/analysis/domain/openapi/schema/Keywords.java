package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** The keywords of numbers and strings, and the dispatch to those of arrays and objects. */
final class Keywords {

  private Keywords() {}

  /** Checks the keywords that apply to the kind of {@code data}. */
  static boolean check(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    return switch (data.kind()) {
      case NUMBER -> NumberKeywords.check(validator, schema, data, path);
      case STRING -> strings(validator, schema, data, path);
      case ARRAY -> ArrayKeywords.check(validator, schema, data, path);
      case OBJECT -> ObjectKeywords.check(validator, schema, data, path, evaluated);
      default -> true;
    };
  }

  private static boolean strings(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final String text = data.text();
    return Chain.run(
        validator,
        () -> length(validator, schema, "maxLength", text, path),
        () -> length(validator, schema, "minLength", text, path),
        () -> pattern(validator, schema, text, path),
        () -> format(validator, schema, text, path));
  }

  private static boolean length(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String keyword,
      final String text,
      final String path) {
    final Optional<DocNode> limit = schema.get(keyword);
    if (limit.isEmpty() || limit.get().kind() != DocNode.Kind.NUMBER) {
      return true;
    }
    final double bound = JsonValues.number(limit.get());
    final boolean max = "maxLength".equals(keyword);
    final int size = JsonValues.length(text);
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
                    + " characters"));
    return false;
  }

  private static boolean pattern(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String text,
      final String path) {
    final Optional<DocNode> expression = schema.get("pattern");
    if (expression.isEmpty() || expression.get().kind() != DocNode.Kind.STRING) {
      return true;
    }
    try {
      if (Pattern.compile(expression.get().text()).matcher(text).find()) {
        return true;
      }
    } catch (final PatternSyntaxException exception) {
      return true;
    }
    validator
        .errors()
        .add(
            SchemaError.of(
                "pattern", path, "must match pattern \"" + expression.get().text() + "\""));
    return false;
  }

  private static boolean format(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String text,
      final String path) {
    final Optional<DocNode> format = schema.get("format");
    if (format.isEmpty()
        || format.get().kind() != DocNode.Kind.STRING
        || Formats.valid(format.get().text(), text)) {
      return true;
    }
    validator
        .errors()
        .add(SchemaError.of("format", path, "must match format \"" + format.get().text() + "\""));
    return false;
  }
}
