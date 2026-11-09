package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the value of a JSX attribute with the given name when the value's text matches, at the
 * value rather than at the attribute, as Biome reports some JSX rules.
 */
public final class BiomeJsxAttributeValueCheck implements RuleCheck {

  private static final String ATTRIBUTE = "jsxAttribute";
  private static final String NAME = "JsxIdentifier";
  private static final String VALUE = "jsxAttributeValue";

  private final String attribute;
  private final Pattern text;
  private final String message;

  /**
   * Creates the check.
   *
   * @param attribute the attribute name, such as {@code tabIndex}
   * @param text the pattern the value's text must match
   * @param message the message; {@code {value}} is the value's text
   */
  public BiomeJsxAttributeValueCheck(
      final String attribute, final Pattern text, final String message) {
    this.attribute = Objects.requireNonNull(attribute, "attribute");
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
                    value.span(), CheckSupport.fill(message, SyntaxWalk.text(value), "")))
        .toList();
  }

  private boolean named(final SyntaxNode attributeNode) {
    return attributeNode.children().stream()
        .anyMatch(child -> NAME.equals(child.kind()) && attribute.equals(SyntaxWalk.text(child)));
  }

  private static List<SyntaxNode> value(final SyntaxNode attributeNode) {
    return attributeNode.children().stream().filter(child -> VALUE.equals(child.kind())).toList();
  }
}
