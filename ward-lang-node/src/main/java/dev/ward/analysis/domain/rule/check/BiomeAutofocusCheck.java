package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code autoFocus} attribute on a DOM element, as Biome does: a component (a capitalized
 * or member-expression tag) is exempt, and so is an element inside a {@code dialog} or an element
 * with a {@code popover} attribute.
 */
public final class BiomeAutofocusCheck implements RuleCheck {

  private static final String ATTRIBUTE = "jsxAttribute";
  private static final String ELEMENT = "jsxOpeningElement";
  private static final String ELEMENT_WRAPPER = "jsxElement";
  private static final String NAME = "jsxElementName";
  private static final String TOKEN = "JsxIdentifier";
  private static final String AUTOFOCUS = "autoFocus";
  private static final String POPOVER = "popover";
  private static final String DIALOG = "dialog";
  private static final String DOT = ".";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the attribute's text
   */
  public BiomeAutofocusCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(ATTRIBUTE)).stream()
        .filter(visit -> named(visit.node(), AUTOFOCUS))
        .filter(visit -> reportable(visit.ancestors()))
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(message, SyntaxWalk.text(visit.node()), "")))
        .toList();
  }

  /** Whether the element with the attribute is a DOM element and no ancestor exempts it. */
  private static boolean reportable(final List<SyntaxNode> ancestors) {
    final SyntaxNode own = ancestors.get(ancestors.size() - 1);
    return domElement(own)
        && ancestors.subList(0, ancestors.size() - 1).stream()
            .filter(node -> ELEMENT_WRAPPER.equals(node.kind()))
            .map(BiomeAutofocusCheck::openingElement)
            .flatMap(Optional::stream)
            .filter(opening -> !opening.equals(own))
            .noneMatch(BiomeAutofocusCheck::exemptsDescendants);
  }

  private static Optional<SyntaxNode> openingElement(final SyntaxNode wrapper) {
    return wrapper.children().stream().filter(child -> ELEMENT.equals(child.kind())).findFirst();
  }

  private static boolean domElement(final SyntaxNode element) {
    final String name = elementName(element);
    return !name.isEmpty()
        && Character.isLowerCase(name.charAt(0))
        && !name.contains(DOT);
  }

  private static boolean exemptsDescendants(final SyntaxNode element) {
    final String name = elementName(element);
    final String last = name.substring(name.lastIndexOf('.') + 1);
    return DIALOG.equalsIgnoreCase(last)
        || element.children().stream().anyMatch(child -> named(child, POPOVER));
  }

  private static String elementName(final SyntaxNode element) {
    return element.children().stream()
        .filter(child -> NAME.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static boolean named(final SyntaxNode node, final String name) {
    return ATTRIBUTE.equals(node.kind()) && hasToken(node, name);
  }

  private static boolean hasToken(final SyntaxNode attributeNode, final String name) {
    return attributeNode.children().stream()
        .anyMatch(child -> TOKEN.equals(child.kind()) && name.equals(SyntaxWalk.text(child)));
  }
}
