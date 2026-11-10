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
 * Reports a short-syntax fragment that holds one element or no child, or that sits in a JSX child
 * list, at the fragment, as Biome 2.5.15's {@code noUselessFragments} decides it (its run function
 * in crates/biome_js_analyze/src/lint/complexity/no_useless_fragments.rs). A fragment that is the
 * value of a declaration, an arrow body or a property is kept.
 *
 * <p>shortcut: only the short {@code <>} fragment is checked; {@code React.Fragment} and an
 * imported {@code Fragment} need import resolution that this check does not have. Upgrade when the
 * scope analysis exposes the import binding to the check.
 */
public final class BiomeUselessFragmentsCheck implements RuleCheck {

  private static final String FRAGMENT = "jsxFragment";
  private static final int CONTAINER = 2;
  private static final String ELEMENT = "jsxElement";
  private static final String TEXT = "jsxText";
  private static final String EXPRESSION_CHILD = "jsxExpressionContainer";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeUselessFragmentsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: blank text is judged from the source, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the useless-fragments check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(FRAGMENT)).stream()
        .filter(visit -> reported(visit.node(), visit.ancestors(), unit.text()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private boolean reported(
      final SyntaxNode fragment, final List<SyntaxNode> ancestors, final String source) {
    final FragmentContext.Context context = FragmentContext.of(ancestors);
    if (context == FragmentContext.Context.PRESERVED) {
      return false;
    }
    final Optional<List<SyntaxNode>> children = significantChildren(fragment, context, source);
    if (children.isEmpty()) {
      return false;
    }
    final List<SyntaxNode> significant = children.get();
    return switch (significant.size()) {
      case 0 -> true;
      case 1 -> singleChildReported(significant.get(0), context, ancestors);
      default -> context == FragmentContext.Context.LIST;
    };
  }

  /**
   * The children that count, or empty when the text or an expression inside the fragment must keep
   * it, as Biome's loop over the children decides.
   */
  private static Optional<List<SyntaxNode>> significantChildren(
      final SyntaxNode fragment, final FragmentContext.Context context, final String source) {
    final List<SyntaxNode> significant = new ArrayList<>();
    for (final SyntaxNode child : fragment.children()) {
      if (keepsFragment(child, context)) {
        return Optional.empty();
      }
      if (counts(child, source)) {
        significant.add(child);
      }
    }
    return Optional.of(significant);
  }

  /** Text inside an expression or a logical operand, or an expression inside a logical operand. */
  private static boolean keepsFragment(
      final SyntaxNode child, final FragmentContext.Context context) {
    final boolean inExpression =
        context == FragmentContext.Context.EXPRESSION_CHILD
            || context == FragmentContext.Context.LOGICAL;
    return TEXT.equals(child.kind()) && inExpression
        || EXPRESSION_CHILD.equals(child.kind()) && context == FragmentContext.Context.LOGICAL;
  }

  /** A child Biome counts: an element, an expression, or text that is not padding. */
  private static boolean counts(final SyntaxNode child, final String source) {
    if (TEXT.equals(child.kind())) {
      return !isPaddingText(child, source);
    }
    return ELEMENT.equals(child.kind()) || EXPRESSION_CHILD.equals(child.kind());
  }

  private static boolean singleChildReported(
      final SyntaxNode child,
      final FragmentContext.Context context,
      final List<SyntaxNode> ancestors) {
    if (TEXT.equals(child.kind())) {
      return textChildReported(context, ancestors);
    }
    return !(EXPRESSION_CHILD.equals(child.kind()) && context == FragmentContext.Context.RETURNED);
  }

  /**
   * Text alone keeps the fragment in an attribute or a return, and elsewhere only in a fragment.
   */
  private static boolean textChildReported(
      final FragmentContext.Context context, final List<SyntaxNode> ancestors) {
    if (context == FragmentContext.Context.ATTRIBUTE
        || context == FragmentContext.Context.RETURNED) {
      return false;
    }
    return FRAGMENT.equals(ancestors.get(ancestors.size() - CONTAINER).kind());
  }

  /** Text that is only whitespace with a line break is padding, as Biome's is_padding_spaces. */
  private static boolean isPaddingText(final SyntaxNode text, final String source) {
    final String raw = source.substring(text.span().startOffset(), text.span().endOffset());
    return raw.isBlank() && raw.contains("\n");
  }
}
