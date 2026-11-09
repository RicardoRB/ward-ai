package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an element of the given specs that has no content: a self-closing element, or one whose
 * children are only whitespace, at the element, as Biome does. An empty expression or a child
 * element counts as content.
 */
public final class BiomeJsxEmptyElementCheck implements RuleCheck {

  private static final String ELEMENT = "jsxElement";
  private static final String OPENING = JsxAttributes.OPENING;
  private static final String SELF_CLOSING = JsxAttributes.SELF_CLOSING;
  private static final String CLOSING = "jsxClosingElement";

  private final List<JsxElementSpec> elements;
  private final Set<String> exempt;
  private final String message;

  /**
   * Creates the check.
   *
   * @param elements the elements to check
   * @param exempt the attributes that make an element exempt, such as {@code aria-label}
   * @param message the message of each finding
   */
  public BiomeJsxEmptyElementCheck(
      final Set<String> elements, final Set<String> exempt, final String message) {
    this.elements = elements.stream().map(JsxElementSpec::parse).toList();
    this.exempt = Set.copyOf(exempt);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(ELEMENT, SELF_CLOSING)).stream()
        .filter(visit -> reportable(visit.node()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** Whether the node is a selected element without content. */
  private boolean reportable(final SyntaxNode node) {
    if (SELF_CLOSING.equals(node.kind())) {
      return selected(node);
    }
    final Optional<SyntaxNode> opening =
        node.children().stream().filter(child -> OPENING.equals(child.kind())).findFirst();
    return opening.isPresent()
        && selected(opening.get())
        && node.children().stream().noneMatch(BiomeJsxEmptyElementCheck::hasContent);
  }

  private boolean selected(final SyntaxNode element) {
    return elements.stream().anyMatch(spec -> spec.selects(element))
        && JsxAttributes.of(element).stream()
            .noneMatch(attribute -> exempt.contains(JsxAttributes.name(attribute)));
  }

  /** A child that is neither the opening nor the closing tag, and is not blank text. */
  static boolean hasContent(final SyntaxNode child) {
    return !OPENING.equals(child.kind())
        && !CLOSING.equals(child.kind())
        && !SyntaxWalk.text(child).isBlank();
  }
}
