package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a void element of the given names that has children, at the element, as Biome 2.5.15's
 * {@code noVoidElementsWithChildren} does. Any child counts, blank text included.
 */
public final class BiomeVoidElementChildrenCheck implements RuleCheck {

  private static final String ELEMENT = "jsxElement";
  private static final String OPENING = JsxAttributes.OPENING;
  private static final String CLOSING = "jsxClosingElement";

  private final List<JsxElementSpec> elements;
  private final String message;

  /**
   * Creates the check.
   *
   * @param elements the void element names
   * @param message the message of each finding
   */
  public BiomeVoidElementChildrenCheck(final Set<String> elements, final String message) {
    this.elements = elements.stream().map(JsxElementSpec::parse).toList();
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(ELEMENT)).stream()
        .filter(visit -> reported(visit.node()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode element) {
    final List<SyntaxNode> children = element.children();
    return children.stream()
            .filter(child -> OPENING.equals(child.kind()))
            .anyMatch(opening -> elements.stream().anyMatch(spec -> spec.selects(opening)))
        && children.stream()
            .anyMatch(child -> !OPENING.equals(child.kind()) && !CLOSING.equals(child.kind()));
  }
}
