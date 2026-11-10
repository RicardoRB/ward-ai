package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an li whose rendered parent is not a ul, ol or menu, at the li's opening tag, as Biome
 * 2.5.15's {@code noMisplacedListElements} does. The parent is the nearest element the li renders
 * into, looking through fragments, expression containers and logical or conditional operands; an li
 * with no such parent is not reported.
 */
public final class BiomeMisplacedListElementCheck implements RuleCheck {

  private static final String LIST_ITEM = "li";
  private static final Set<String> LIST_CONTAINERS = Set.of("ul", "ol", "menu");
  private static final String ELEMENT = "jsxElement";
  private static final String FRAGMENT = "jsxFragment";
  private static final String EXPRESSION_CHILD = "jsxExpressionContainer";
  private static final String EXPRESSION = "singleExpression";
  private static final String OPENING = JsxAttributes.OPENING;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeMisplacedListElementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the rendered parent is found through the tree, which this overload lacks. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the misplaced-list check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(OPENING, JsxAttributes.SELF_CLOSING)).stream()
        .filter(visit -> LIST_ITEM.equals(JsxAttributes.elementName(visit.node())))
        .filter(BiomeMisplacedListElementCheck::misplaced)
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean misplaced(final SyntaxWalk.Visit visit) {
    return renderedParent(visit).filter(name -> !LIST_CONTAINERS.contains(name)).isPresent();
  }

  /**
   * The name of the element the li renders into. For an opening tag the search starts above the
   * li's own element; a self-closing li starts at its wrapper, which is transparent.
   */
  private static Optional<String> renderedParent(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> chain = new ArrayList<>(visit.ancestors());
    if (OPENING.equals(visit.node().kind()) && !chain.isEmpty()) {
      chain.remove(chain.size() - 1);
    }
    for (int index = chain.size() - 1; index >= 0; index--) {
      final Optional<Optional<String>> step = renderedStep(chain.get(index));
      if (step.isPresent()) {
        return step.get();
      }
    }
    return Optional.empty();
  }

  /**
   * One ancestor's answer: empty to keep looking, a present value to stop with the element's name
   * (or no name when the walk must stop without a parent).
   */
  private static Optional<Optional<String>> renderedStep(final SyntaxNode ancestor) {
    if (ELEMENT.equals(ancestor.kind())) {
      final Optional<SyntaxNode> opening =
          ancestor.children().stream().filter(child -> OPENING.equals(child.kind())).findFirst();
      return opening.isPresent()
          ? Optional.of(Optional.of(JsxAttributes.elementName(opening.get())))
          : Optional.empty();
    }
    if (FRAGMENT.equals(ancestor.kind())
        || EXPRESSION_CHILD.equals(ancestor.kind())
        || EXPRESSION.equals(ancestor.kind())) {
      return Optional.empty();
    }
    return Optional.of(Optional.empty());
  }
}
