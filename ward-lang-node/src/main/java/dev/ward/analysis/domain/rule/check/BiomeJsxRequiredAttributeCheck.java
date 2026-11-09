package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a JSX element that lacks the attribute, at the element, as Biome does. An element is
 * named as written, or as {@code name[attribute=value]} for an element that only counts with that
 * static attribute value. With allowed values, a static value outside them is reported at the
 * value. A spread attribute can supply the attribute when {@code silentOnSpread} is set, and then
 * the element stays silent.
 */
public final class BiomeJsxRequiredAttributeCheck implements RuleCheck {

  private static final String SPREAD = "jsxSpreadAttribute";

  private final List<JsxElementSpec> elements;
  private final String attribute;
  private final Set<String> values;
  private final boolean silentOnSpread;
  private final String message;

  /**
   * Creates the check.
   *
   * @param elements the element names to check, compared as written
   * @param attribute the attribute the element must have
   * @param values the allowed static values; empty to allow any value
   * @param silentOnSpread whether a spread attribute on the element silences the check
   * @param message the message of each finding
   */
  public BiomeJsxRequiredAttributeCheck(
      final Set<String> elements,
      final String attribute,
      final Set<String> values,
      final boolean silentOnSpread,
      final String message) {
    this.elements = elements.stream().map(JsxElementSpec::parse).toList();
    this.attribute = Objects.requireNonNull(attribute, "attribute");
    this.values = Set.copyOf(values);
    this.silentOnSpread = silentOnSpread;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> elements.stream().anyMatch(spec -> spec.selects(visit.node())))
        .filter(visit -> !(silentOnSpread && hasSpread(visit.node())))
        .flatMap(visit -> violation(visit.node()).stream())
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  /** The node the finding sits on: the element when the attribute is missing, else the value. */
  private Optional<SyntaxNode> violation(final SyntaxNode element) {
    final Optional<SyntaxNode> present =
        JsxAttributes.of(element).stream()
            .filter(candidate -> attribute.equals(JsxAttributes.name(candidate)))
            .findFirst();
    if (present.isEmpty()) {
      return Optional.of(element);
    }
    if (values.isEmpty()) {
      return Optional.empty();
    }
    return JsxAttributes.literal(present.get())
        .filter(value -> !values.contains(value))
        .flatMap(value -> JsxAttributes.value(present.get()));
  }

  /** A spread is a jsxSpreadAttribute inside an attribute node of the element. */
  /** A spread is a jsxSpreadAttribute inside an attribute node of the element. */
  /** An element name, with an optional static attribute value that it must have. */
  private record Spec(String name, String attribute, String value) {

    static Spec parse(final String text) {
      final int open = text.indexOf('[');
      if (open < 0) {
        return new Spec(text, "", "");
      }
      final String condition = text.substring(open + 1, text.length() - 1);
      final int equals = condition.indexOf('=');
      return new Spec(
          text.substring(0, open), condition.substring(0, equals), condition.substring(equals + 1));
    }

    boolean selects(final SyntaxNode element) {
      if (!name.equals(JsxAttributes.elementName(element))) {
        return false;
      }
      return attribute.isEmpty()
          || JsxAttributes.of(element).stream()
              .filter(candidate -> attribute.equals(JsxAttributes.name(candidate)))
              .anyMatch(
                  candidate -> JsxAttributes.literal(candidate).filter(value::equals).isPresent());
    }
  }

  private static boolean hasSpread(final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .anyMatch(
            attribute ->
                attribute.children().stream().anyMatch(child -> SPREAD.equals(child.kind())));
  }
}
