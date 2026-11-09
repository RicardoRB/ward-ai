package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;

/**
 * An element named as written, optionally with a condition on it: {@code name} for any element of
 * that name, {@code name[attribute]} for one that has the attribute, or {@code
 * name[attribute=value]} for one whose static attribute value is that value.
 *
 * @param name the element name
 * @param attribute the attribute the element must have, or empty for none
 * @param value the static value the attribute must have, or empty for presence only
 */
record JsxElementSpec(String name, String attribute, String value) {

  /** Reads a spec written as in the rule file. */
  static JsxElementSpec parse(final String text) {
    final int open = text.indexOf('[');
    if (open < 0) {
      return new JsxElementSpec(text, "", "");
    }
    final String condition = text.substring(open + 1, text.length() - 1);
    final int equals = condition.indexOf('=');
    if (equals < 0) {
      return new JsxElementSpec(text.substring(0, open), condition, "");
    }
    return new JsxElementSpec(
        text.substring(0, open), condition.substring(0, equals), condition.substring(equals + 1));
  }

  /** Whether the element is named by this spec and meets its condition. */
  boolean selects(final SyntaxNode element) {
    if (!name.equals(JsxAttributes.elementName(element))) {
      return false;
    }
    if (attribute.isEmpty()) {
      return true;
    }
    return JsxAttributes.of(element).stream()
        .filter(candidate -> attribute.equals(JsxAttributes.name(candidate)))
        .anyMatch(
            candidate ->
                value.isEmpty()
                    || JsxAttributes.literal(candidate).filter(value::equals).isPresent());
  }
}
