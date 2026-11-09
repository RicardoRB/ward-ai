package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an svg with a role that needs a name and no title, at the element, as Biome 2.5.15's
 * {@code noSvgWithoutTitle} does. Biome looks for the title as the second child (index 1), which
 * the mirror keeps. A self-closing svg, a valueless or true aria-hidden, aria-label, and a valid
 * aria-labelledby are silent.
 */
public final class BiomeSvgTitleCheck implements RuleCheck {

  private static final String SVG = "svg";
  private static final String TITLE = "title";
  private static final String ELEMENT = "jsxElement";
  private static final String CLOSING = "jsxClosingElement";
  private static final Set<String> NAME_REQUIRED =
      Set.of("img", "image", "graphics-document", "graphics-symbol");

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeSvgTitleCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role is read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the svg-title check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(ELEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> reported(node, unit.text()))
        .map(node -> CheckSupport.at(opening(node).span(), message))
        .toList();
  }

  private static SyntaxNode opening(final SyntaxNode element) {
    return element.children().stream()
        .filter(child -> JsxAttributes.OPENING.equals(child.kind()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * A jsxElement child with an opening tag; the grammar wraps a self-closing child, which Biome
   * does not call one.
   */
  private static boolean isElementWithOpening(final SyntaxNode child) {
    return ELEMENT.equals(child.kind())
        && child.children().stream().anyMatch(c -> JsxAttributes.OPENING.equals(c.kind()));
  }

  private boolean reported(final SyntaxNode element, final String source) {
    final Optional<SyntaxNode> opening =
        element.children().stream()
            .filter(child -> JsxAttributes.OPENING.equals(child.kind()))
            .findFirst();
    if (opening.isEmpty()
        || !SVG.equals(JsxAttributes.elementName(opening.get()))
        || ariaHiddenSilences(opening.get())
        || validTitle(element)
        || !NAME_REQUIRED.contains(AriaElements.roleOf(table, opening.get(), source).orElse(""))
        || JsxAttributes.of(opening.get()).stream()
            .anyMatch(attribute -> "aria-label".equals(JsxAttributes.name(attribute)))) {
      return false;
    }
    return !validLabelledBy(element, opening.get());
  }

  /** A valueless aria-hidden, or one that is the static value true, silences the svg. */
  private static boolean ariaHiddenSilences(final SyntaxNode opening) {
    return JsxAttributes.of(opening).stream()
        .filter(attribute -> "aria-hidden".equals(JsxAttributes.name(attribute)))
        .anyMatch(
            attribute ->
                JsxAttributes.value(attribute).isEmpty()
                    || JsxAttributes.literal(attribute).filter("true"::equals).isPresent());
  }

  /** Biome's title check: the child at index 1 is a title element with content. */
  private static boolean validTitle(final SyntaxNode svg) {
    final List<SyntaxNode> children =
        svg.children().stream()
            .filter(
                child ->
                    !JsxAttributes.OPENING.equals(child.kind()) && !CLOSING.equals(child.kind()))
            .toList();
    if (children.size() < 2 || !isElementWithOpening(children.get(1))) {
      return false;
    }
    final SyntaxNode title = children.get(1);
    final SyntaxNode titleOpening = opening(title);
    return TITLE.equals(JsxAttributes.elementName(titleOpening))
        && title.children().stream()
            .anyMatch(
                child ->
                    !JsxAttributes.OPENING.equals(child.kind()) && !CLOSING.equals(child.kind()));
  }

  /** aria-labelledby names the id of a direct jsxElement child, as Biome's id check does. */
  private static boolean validLabelledBy(final SyntaxNode svg, final SyntaxNode opening) {
    final Optional<String> labelledBy =
        JsxAttributes.of(opening).stream()
            .filter(attribute -> "aria-labelledby".equals(JsxAttributes.name(attribute)))
            .findFirst()
            .flatMap(JsxAttributes::literal);
    return labelledBy.isPresent()
        && svg.children().stream()
            .filter(BiomeSvgTitleCheck::isElementWithOpening)
            .map(BiomeSvgTitleCheck::opening)
            .anyMatch(
                childOpening ->
                    JsxAttributes.of(childOpening).stream()
                        .filter(attribute -> "id".equals(JsxAttributes.name(attribute)))
                        .anyMatch(
                            attribute ->
                                JsxAttributes.literal(attribute)
                                    .filter(labelledBy.get()::equals)
                                    .isPresent()));
  }
}
