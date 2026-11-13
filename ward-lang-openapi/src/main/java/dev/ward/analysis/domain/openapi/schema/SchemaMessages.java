package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns ajv errors into the messages of the {@code schema} function of Spectral, which uses
 * {@code @stoplight/better-ajv-errors}: it groups the errors by the value they are about, drops the
 * redundant ones and words each as a sentence.
 */
public final class SchemaMessages {

  private SchemaMessages() {}

  /**
   * One message, with the path of the value it is about below the validated value.
   *
   * @param text the message
   * @param path the property names and indexes from the validated value to the failing value
   */
  public record Message(String text, List<String> path) {
    /** Copies the path. */
    public Message {
      path = List.copyOf(path);
    }
  }

  /**
   * Words the errors.
   *
   * @param errors the errors of one validation
   * @param data the validated value
   * @param propertyPath the path of the validated value in its document, whose last name stands for
   *     a failing root value
   */
  public static List<Message> format(
      final List<SchemaError> errors, final DocNode data, final List<String> propertyPath) {
    final Set<String> unmatched =
        errors.stream()
            .filter(
                error ->
                    ("oneOf".equals(error.keyword()) || "anyOf".equals(error.keyword()))
                        && error.combinatorUnmatched())
            .map(SchemaError::instancePath)
            .collect(Collectors.toSet());
    return ErrorTree.flatten(errors).stream()
        .map(error -> message(error, data, propertyPath, unmatched))
        .toList();
  }

  private static Message message(
      final SchemaError error,
      final DocNode data,
      final List<String> propertyPath,
      final Set<String> unmatched) {
    final String pointer = error.instancePath();
    String text = Wording.of(error, data, propertyPath);
    text = describeArrayItem(text, pointer.substring(pointer.lastIndexOf('/') + 1));
    if (unmatched.contains(pointer) && text.endsWith(" in " + error.keyword())) {
      text += ". None of the schemas matched, see the other errors reported for this value";
    }
    final List<String> path =
        pointer.isEmpty() ? List.of() : Arrays.asList(pointer.substring(1).split("/", -1));
    return new Message(text, path);
  }

  private static String describeArrayItem(final String message, final String last) {
    if (last.isEmpty() || !last.chars().allMatch(Character::isDigit)) {
      return message;
    }
    final String typePrefix = "\"" + last + "\" property type";
    if (message.startsWith(typePrefix)) {
      return "Item at index " + last + message.substring(typePrefix.length());
    }
    final String prefix = "\"" + last + "\" property";
    return message.startsWith(prefix)
        ? "Item at index " + last + message.substring(prefix.length())
        : message;
  }
}
