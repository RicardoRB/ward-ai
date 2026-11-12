package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The sentence {@code @stoplight/better-ajv-errors} makes of one ajv error. */
final class Wording {

  private static final Pattern QUOTES = Pattern.compile("['\"]");

  private Wording() {}

  /** Words {@code error}; {@code propertyPath} names the root value when the error is about it. */
  static String of(final SchemaError error, final DocNode data, final List<String> propertyPath) {
    return switch (error.keyword()) {
      case "additionalProperties" ->
          "Property \"" + error.detail() + "\" is not expected to be here";
      case "type" -> type(error, propertyPath);
      case "enum" -> enumeration(error, data, propertyPath);
      default -> pretty(error, data, propertyPath) + " " + clean(error.message());
    };
  }

  private static String type(final SchemaError error, final List<String> propertyPath) {
    return name(error.instancePath(), propertyPath)
        .map(name -> "\"" + name + "\" property type " + error.message())
        .orElse("Value type " + error.message());
  }

  private static String enumeration(
      final SchemaError error, final DocNode data, final List<String> propertyPath) {
    final String values =
        error.allowed().stream().map(Wording::allowed).collect(Collectors.joining(", "));
    final String text = pretty(error, data, propertyPath) + " " + error.message() + ": " + values;
    return Pointers.at(data, error.instancePath())
        .filter(current -> current.kind() == DocNode.Kind.STRING)
        .map(current -> text + suggestion(current.text(), error.allowed()))
        .orElse(text);
  }

  private static String allowed(final DocNode value) {
    return switch (value.kind()) {
      case STRING -> "\"" + value.text() + "\"";
      case NULL -> "null";
      case ARRAY -> "[]";
      case OBJECT -> "{}";
      default -> value.text();
    };
  }

  private static String suggestion(final String value, final List<DocNode> allowed) {
    final List<String> strings =
        allowed.stream()
            .filter(candidate -> candidate.kind() == DocNode.Kind.STRING)
            .map(DocNode::text)
            .toList();
    final Optional<String> best =
        strings.stream().min((left, right) -> distance(left, value) - distance(right, value));
    return best.filter(text -> allowed.size() == 1 || distance(text, value) < text.length())
        .map(text -> ". Did you mean \"" + text + "\"?")
        .orElse("");
  }

  /** The Levenshtein distance, which {@code leven} computes. */
  private static int distance(final String left, final String right) {
    final int[] previous = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= left.length(); i++) {
      int diagonal = previous[0];
      previous[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        final int above = previous[j];
        final int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        previous[j] = Math.min(Math.min(previous[j] + 1, previous[j - 1] + 1), diagonal + cost);
        diagonal = above;
      }
    }
    return previous[right.length()];
  }

  private static String pretty(
      final SchemaError error, final DocNode data, final List<String> propertyPath) {
    return name(error.instancePath(), propertyPath)
        .map(name -> "\"" + name + "\" property")
        .orElseGet(() -> capitalized(Pointers.at(data, error.instancePath())));
  }

  private static String capitalized(final Optional<DocNode> value) {
    final String type =
        value
            .map(
                node ->
                    switch (node.kind()) {
                      case STRING -> "string";
                      case NUMBER -> "number";
                      case BOOLEAN -> "boolean";
                      default -> "object";
                    })
            .orElse("undefined");
    return Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }

  /** The last segment of the instance path, or the last name of the validated value's path. */
  static Optional<String> name(final String instancePath, final List<String> propertyPath) {
    final int slash = instancePath.lastIndexOf('/');
    if (slash != -1) {
      return Optional.of(instancePath.substring(slash + 1));
    }
    return propertyPath.isEmpty()
        ? Optional.empty()
        : Optional.of(propertyPath.get(propertyPath.size() - 1));
  }

  private static String clean(final String message) {
    return QUOTES.matcher(message).replaceAll("\"").replace("NOT", "not");
  }
}
