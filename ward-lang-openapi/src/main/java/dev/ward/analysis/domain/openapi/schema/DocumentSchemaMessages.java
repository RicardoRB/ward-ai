package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The messages of {@code oasDocumentSchema} of Spectral for the errors of a whole document: one
 * message for each error but the {@code if} ones, worded without grouping.
 */
public final class DocumentSchemaMessages {

  private static final String ADDITIONAL = "additionalProperties";
  private static final Pattern QUOTES = Pattern.compile("['\"]");

  private DocumentSchemaMessages() {}

  /**
   * Words the errors.
   *
   * @param errors the errors of validating {@code input} against an OpenAPI schema
   * @param input the validated document
   */
  public static List<SchemaMessages.Message> format(
      final List<SchemaError> errors, final DocNode input) {
    final List<SchemaMessages.Message> messages = new ArrayList<>();
    for (final SchemaError error : errors) {
      if (!"if".equals(error.keyword())) {
        messages.add(message(error, input));
      }
    }
    return messages;
  }

  private static SchemaMessages.Message message(final SchemaError error, final DocNode input) {
    final List<String> path = new ArrayList<>();
    String property = null;
    if (!error.instancePath().isEmpty()) {
      for (final String segment : error.instancePath().substring(1).split("/", -1)) {
        path.add(segment.replace("~1", "/").replace("~0", "~"));
        property = segment;
      }
    }
    if (ADDITIONAL.equals(error.keyword())) {
      path.add(error.detail());
    }
    final String text =
        switch (error.keyword()) {
          case ADDITIONAL -> "Property \"" + error.detail() + "\" is not expected to be here";
          case "enum" -> enumeration(error, input, property);
          case "errorMessage" -> error.message();
          default -> clean(property, error.message());
        };
    return new SchemaMessages.Message(text, path);
  }

  private static String enumeration(
      final SchemaError error, final DocNode input, final String property) {
    final String printed =
        error.allowed().stream().map(Wording::allowed).collect(Collectors.joining(", "));
    final String suggestion =
        Pointers.at(input, error.instancePath())
            .filter(value -> value.kind() == DocNode.Kind.STRING)
            .map(value -> Wording.suggestion(value.text(), error.allowed()))
            .orElse("");
    return clean(property, error.message()) + ": " + printed + suggestion;
  }

  private static String clean(final String property, final String message) {
    final String cleaned = QUOTES.matcher(message).replaceAll("\"").replace("NOT", "not");
    return property == null ? cleaned : "\"" + property + "\" property " + cleaned;
  }
}
