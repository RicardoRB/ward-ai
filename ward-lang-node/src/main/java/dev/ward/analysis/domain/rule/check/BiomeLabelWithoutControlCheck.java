package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a label that has no text or label attribute, or no control, at the opening tag, as Biome
 * 2.5.15's {@code noLabelWithoutControl} does with its default components. A control is a {@code
 * for} or {@code htmlFor} attribute on the label, or a nested input, meter, output, progress,
 * select, textarea or button. Whitespace text counts as text, as Biome counts it.
 */
public final class BiomeLabelWithoutControlCheck implements RuleCheck {

  private static final String LABEL = "label";
  private static final String ELEMENT = "jsxElement";
  private static final String TEXT = "jsxText";
  private static final String EXPRESSION_CHILD = "jsxExpressionContainer";
  private static final String ATTRIBUTE = "jsxAttribute";
  private static final String OPENING = JsxAttributes.OPENING;
  private static final String SELF_CLOSING = JsxAttributes.SELF_CLOSING;
  private static final Set<String> FOR_ATTRIBUTES = Set.of("for", "htmlFor");
  private static final Set<String> LABEL_ATTRIBUTES =
      Set.of("aria-label", "aria-labelledby", "alt");
  private static final Set<String> INPUT_COMPONENTS =
      Set.of("input", "meter", "output", "progress", "select", "textarea", "button");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeLabelWithoutControlCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: a label attribute's value is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the label check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(OPENING, SELF_CLOSING)).stream()
        .filter(visit -> LABEL.equals(JsxAttributes.elementName(visit.node())))
        .filter(visit -> !(hasText(visit, unit.text()) && hasControl(visit)))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** Whether the label has text: JSX text, an expression, or a label attribute, in its subtree. */
  private static boolean hasText(final SyntaxWalk.Visit visit, final String source) {
    return subtree(visit).stream().anyMatch(node -> textNode(node, source));
  }

  /** The label's subtree: its jsxElement for an opening tag, the tag itself when self-closing. */
  private static List<SyntaxNode> subtree(final SyntaxWalk.Visit visit) {
    if (OPENING.equals(visit.node().kind())) {
      return List.of(visit.ancestors().get(visit.ancestors().size() - 1));
    }
    return List.of(visit.node());
  }

  private static boolean textNode(final SyntaxNode node, final String source) {
    return switch (node.kind()) {
      case TEXT, EXPRESSION_CHILD -> true;
      case ATTRIBUTE -> labelAttribute(node, source);
      case ELEMENT, OPENING, SELF_CLOSING ->
          node.children().stream().anyMatch(child -> textNode(child, source));
      default -> false;
    };
  }

  /** A label attribute with a value that is not blank text, or with a dynamic value. */
  private static boolean labelAttribute(final SyntaxNode attribute, final String source) {
    return LABEL_ATTRIBUTES.contains(JsxAttributes.name(attribute))
        && JsxAttributes.value(attribute)
            .map(value -> labelValue(JsxTruthiness.raw(value, source)))
            .orElse(false);
  }

  private static boolean labelValue(final String raw) {
    return raw.startsWith("{") || !raw.substring(1, raw.length() - 1).isBlank();
  }

  private static boolean hasControl(final SyntaxWalk.Visit visit) {
    final boolean forAttribute =
        JsxAttributes.of(visit.node()).stream()
            .anyMatch(attribute -> FOR_ATTRIBUTES.contains(JsxAttributes.name(attribute)));
    return forAttribute
        || subtree(visit).stream().anyMatch(BiomeLabelWithoutControlCheck::nestedControl);
  }

  private static boolean nestedControl(final SyntaxNode node) {
    if (!(ELEMENT.equals(node.kind())
        || OPENING.equals(node.kind())
        || SELF_CLOSING.equals(node.kind()))) {
      return false;
    }
    final boolean self =
        !ELEMENT.equals(node.kind()) && INPUT_COMPONENTS.contains(JsxAttributes.elementName(node));
    return self
        || node.children().stream()
            .filter(child -> !ATTRIBUTE.equals(child.kind()))
            .anyMatch(BiomeLabelWithoutControlCheck::nestedControl);
  }
}
