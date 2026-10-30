package dev.ward.infrastructure.init;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the members of a top-level JSON object, skipping nested values and comments. */
final class JsonMembers {
  private JsonMembers() {}

  /** A member of a JSON object: its name and the span of its value. */
  record Member(String name, JsonSpan value) {}

  /**
   * Returns the names of the members of the top-level object {@code key}, in file order; empty when
   * the object is absent.
   */
  static Set<String> names(final String content, final String key) {
    final String masked = JsonText.mask(content);
    return JsonLayout.of(masked)
        .member(key)
        .map(
            object ->
                read(masked, object).stream()
                    .map(Member::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new)))
        .orElseGet(LinkedHashSet::new);
  }

  /** Returns the span of the string value of {@code name} in the top-level object {@code key}. */
  static Optional<JsonSpan> stringValue(final String masked, final String key, final String name) {
    return JsonLayout.of(masked)
        .member(key)
        .flatMap(
            object ->
                read(masked, object).stream()
                    .filter(member -> member.name().equals(name))
                    .map(Member::value)
                    .filter(value -> masked.charAt(value.open()) == JsonText.QUOTE)
                    .findFirst());
  }

  /** Reads the members of {@code object}; nested values are skipped, not descended into. */
  static List<Member> read(final String masked, final JsonSpan object) {
    final List<Member> members = new ArrayList<>();
    int position = JsonText.skipSeparators(masked, object.open() + 1, object.close());
    while (position < object.close()) {
      final int nameEnd = JsonText.stringEnd(masked, position);
      final String name = masked.substring(position + 1, nameEnd);
      final int colon = JsonText.skipWhitespace(masked, nameEnd + 1);
      if (masked.charAt(colon) != JsonText.COLON) {
        throw JsonText.malformed();
      }
      final int value = JsonText.skipWhitespace(masked, colon + 1);
      final int valueEnd = JsonText.valueEnd(masked, value);
      members.add(new Member(name, new JsonSpan(value, valueEnd)));
      position = JsonText.skipSeparators(masked, valueEnd + 1, object.close());
    }
    return members;
  }
}
