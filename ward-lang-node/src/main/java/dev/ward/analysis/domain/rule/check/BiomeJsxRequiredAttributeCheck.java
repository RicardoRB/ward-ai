package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a JSX element that lacks the attribute, at the element, as Biome does. An element is
 * named by a {@link JsxElementSpec}. A static value outside the allowed values is reported at the
 * value, and a static value that matches the invalid pattern at the attribute name. A spread
 * attribute can supply the attribute when {@code silentOnSpread} is set, and then the element stays
 * silent.
 */
public final class BiomeJsxRequiredAttributeCheck implements RuleCheck {

  private final List<JsxElementSpec> elements;
  private final String attribute;
  private final ValueRule rule;
  private final String message;

  /**
   * What a present attribute may hold: the allowed static values, a pattern for invalid static
   * values, and whether a spread silences the check.
   *
   * @param values the allowed static values; empty to allow any value
   * @param invalid the pattern a static value must not match, or {@code null} for none
   * @param silentOnSpread whether a spread attribute on the element silences the check
   */
  public record ValueRule(Set<String> values, Pattern invalid, boolean silentOnSpread) {

    /** Copies the allowed values. */
    public ValueRule {
      values = Set.copyOf(values);
    }

    /** A rule that allows any value and never silences on a spread. */
    public static ValueRule any() {
      return new ValueRule(Set.of(), null, false);
    }
  }

  /**
   * Creates the check.
   *
   * @param elements the elements to check
   * @param attribute the attribute the element must have
   * @param rule the rule for the attribute's value and spreads
   * @param message the message of each finding
   */
  public BiomeJsxRequiredAttributeCheck(
      final Set<String> elements,
      final String attribute,
      final ValueRule rule,
      final String message) {
    this.elements = elements.stream().map(JsxElementSpec::parse).toList();
    this.attribute = Objects.requireNonNull(attribute, "attribute");
    this.rule = Objects.requireNonNull(rule, "rule");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> elements.stream().anyMatch(spec -> spec.selects(visit.node())))
        .filter(visit -> !(rule.silentOnSpread() && JsxAttributes.hasSpread(visit.node())))
        .flatMap(visit -> violation(visit.node()).stream())
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  /** The node the finding sits on: the element, the attribute name, or the value. */
  private Optional<SyntaxNode> violation(final SyntaxNode element) {
    final Optional<SyntaxNode> present =
        JsxAttributes.of(element).stream()
            .filter(candidate -> attribute.equals(JsxAttributes.name(candidate)))
            .findFirst();
    if (present.isEmpty()) {
      return Optional.of(element);
    }
    final Optional<String> literal = JsxAttributes.literal(present.get());
    if (literal.isEmpty()) {
      return Optional.empty();
    }
    if (rule.invalid() != null && rule.invalid().matcher(literal.get()).find()) {
      return present;
    }
    if (rule.values().isEmpty() || rule.values().contains(literal.get())) {
      return Optional.empty();
    }
    return JsxAttributes.value(present.get());
  }
}
