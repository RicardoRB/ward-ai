package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a JSX element of the given names that lacks the attribute, at the element, as Biome does.
 * With allowed values, a static value outside them is reported at the value. A spread attribute can
 * supply the attribute when {@code silentOnSpread} is set, and then the element stays silent.
 */
public final class BiomeJsxRequiredAttributeCheck implements RuleCheck {

  private static final String SPREAD = "jsxSpreadAttribute";

  private final Set<String> elements;
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
    this.elements = Set.copyOf(elements);
    this.attribute = Objects.requireNonNull(attribute, "attribute");
    this.values = Set.copyOf(values);
    this.silentOnSpread = silentOnSpread;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> elements.contains(JsxAttributes.elementName(visit.node())))
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
  private static boolean hasSpread(final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .anyMatch(
            attribute ->
                attribute.children().stream().anyMatch(child -> SPREAD.equals(child.kind())));
  }
}
