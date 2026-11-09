package dev.ward.analysis.domain.rule.check;

import java.util.Arrays;
import java.util.Set;

/**
 * Whether a static attribute value is valid for the attribute's value type, as Biome 2.5.15's
 * {@code AriaValueType::contains} decides it. An empty value is never valid.
 */
final class AriaValues {

  private static final long U32_MAX = 4_294_967_295L;
  private static final String INTEGER = "\\+?\\d+";
  private static final String NUMBER = "[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?";
  private static final String SPACE = "\\s+";

  private AriaValues() {}

  /** Tells whether {@code raw} is a valid value of an attribute of this value type. */
  static boolean valid(final AriaRoleTable.AriaAttributeRow attribute, final String raw) {
    if (raw.isEmpty()) {
      return false;
    }
    return switch (attribute.valueType()) {
      case "true/false" -> Set.of("false", "true").contains(raw);
      case "true/false/undefined" -> Set.of("undefined", "false", "true").contains(raw);
      case "tristate" -> Set.of("false", "true", "mixed").contains(raw);
      case "integer" -> raw.matches(INTEGER) && withinU32(raw);
      case "number" -> raw.matches(NUMBER);
      case "ID reference" -> !raw.matches(".*\\s.*");
      case "ID reference list" -> tokens(raw).stream().noneMatch(token -> token.matches(".*\\s.*"));
      case "token" -> attribute.values().contains(raw);
      case "token list" -> tokens(raw).stream().allMatch(attribute.values()::contains);
      default -> true;
    };
  }

  private static boolean withinU32(final String raw) {
    final String digits = raw.startsWith("+") ? raw.substring(1) : raw;
    return digits.length() < 11 && Long.parseLong(digits) <= U32_MAX;
  }

  private static java.util.List<String> tokens(final String raw) {
    final String trimmed = raw.trim();
    return trimmed.isEmpty() ? java.util.List.of() : Arrays.asList(trimmed.split(SPACE));
  }
}
