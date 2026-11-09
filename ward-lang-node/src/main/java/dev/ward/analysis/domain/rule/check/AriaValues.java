package dev.ward.analysis.domain.rule.check;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Whether a static attribute value is valid for the attribute's value type, as Biome 2.5.15's
 * {@code AriaValueType::contains} decides it. An empty value is never valid, and a value type that
 * is not listed accepts any value.
 */
final class AriaValues {

  private static final long U32_MAX = 4_294_967_295L;
  private static final int U32_DIGITS = 10;
  private static final String INTEGER = "\\+?\\d+";
  private static final String NUMBER = "[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?";
  private static final String SPACE = "\\s+";
  private static final String WHITESPACE_CHAR = ".*\\s.*";

  private static final Map<String, BiPredicate<AriaRoleTable.AriaAttributeRow, String>> RULES =
      Map.of(
          "true/false", (attribute, raw) -> Set.of("false", "true").contains(raw),
          "true/false/undefined",
              (attribute, raw) -> Set.of("undefined", "false", "true").contains(raw),
          "tristate", (attribute, raw) -> Set.of("false", "true", "mixed").contains(raw),
          "integer", (attribute, raw) -> raw.matches(INTEGER) && withinU32(raw),
          "number", (attribute, raw) -> raw.matches(NUMBER),
          "ID reference", (attribute, raw) -> !raw.matches(WHITESPACE_CHAR),
          "ID reference list",
              (attribute, raw) ->
                  tokens(raw).stream().noneMatch(token -> token.matches(WHITESPACE_CHAR)),
          "token", (attribute, raw) -> attribute.values().contains(raw),
          "token list",
              (attribute, raw) -> tokens(raw).stream().allMatch(attribute.values()::contains));

  private AriaValues() {}

  /** Tells whether {@code raw} is a valid value of an attribute of this value type. */
  static boolean valid(final AriaRoleTable.AriaAttributeRow attribute, final String raw) {
    return !raw.isEmpty()
        && RULES.getOrDefault(attribute.valueType(), (row, value) -> true).test(attribute, raw);
  }

  private static boolean withinU32(final String raw) {
    final String digits = raw.startsWith("+") ? raw.substring(1) : raw;
    return digits.length() <= U32_DIGITS && Long.parseLong(digits) <= U32_MAX;
  }

  private static List<String> tokens(final String raw) {
    final String trimmed = raw.trim();
    return trimmed.isEmpty() ? List.of() : Arrays.asList(trimmed.split(SPACE));
  }
}
