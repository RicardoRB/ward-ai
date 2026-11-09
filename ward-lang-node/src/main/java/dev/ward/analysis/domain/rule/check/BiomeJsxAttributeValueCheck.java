package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the value of a JSX attribute when the value's text matches, at the value rather than at
 * the attribute, as Biome reports some JSX rules; {@code atExpression} moves the report inside the
 * braces. With no attribute name, every attribute counts.
 */
public final class BiomeJsxAttributeValueCheck implements RuleCheck {

  private static final String ATTRIBUTE = "jsxAttribute";
  private static final String NAME = "JsxIdentifier";
  private static final String VALUE = "jsxAttributeValue";

  private static final String BRACE_OPEN = "{";
  private static final String BRACE_CLOSE = "}";

  private final String attribute;
  private final boolean atExpression;
  private final Pattern text;
  private final String message;

  /**
   * Creates the check.
   *
   * @param attribute the attribute name, such as {@code tabIndex}; {@code null} for any name
   * @param atExpression whether to report the expression inside the braces, not the value
   * @param text the pattern the value's text must match
   * @param message the message; {@code {value}} is the value's text
   */
  public BiomeJsxAttributeValueCheck(
      final String attribute,
      final boolean atExpression,
      final Pattern text,
      final String message) {
    this.attribute = attribute;
    this.atExpression = atExpression;
    this.text = Objects.requireNonNull(text, "text");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(ATTRIBUTE)).stream()
        .filter(visit -> named(visit.node()))
        .flatMap(visit -> value(visit.node()).stream())
        .filter(value -> text.matcher(SyntaxWalk.text(value)).find())
        .map(
            value ->
                CheckSupport.at(
                    reported(value).span(), CheckSupport.fill(message, SyntaxWalk.text(value), "")))
        .toList();
  }

  private boolean named(final SyntaxNode attributeNode) {
    return attributeNode.children().stream()
        .anyMatch(
            child ->
                NAME.equals(child.kind())
                    && (attribute == null || attribute.equals(SyntaxWalk.text(child))));
  }

  /** The node the finding is reported at: the value, or its expression inside the braces. */
  private SyntaxNode reported(final SyntaxNode value) {
    if (!atExpression) {
      return value;
    }
    return value.children().stream()
        .flatMap(container -> container.children().stream())
        .filter(child -> !BRACE_OPEN.equals(SyntaxWalk.text(child)))
        .filter(child -> !BRACE_CLOSE.equals(SyntaxWalk.text(child)))
        .findFirst()
        .orElse(value);
  }

  private static List<SyntaxNode> value(final SyntaxNode attributeNode) {
    return attributeNode.children().stream().filter(child -> VALUE.equals(child.kind())).toList();
  }
}
