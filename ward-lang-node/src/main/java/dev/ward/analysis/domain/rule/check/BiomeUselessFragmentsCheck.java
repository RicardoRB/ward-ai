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
  private static final String ELEMENT = "jsxElement";
  private static final String TEXT = "jsxText";
  private static final String EXPRESSION_CHILD = "jsxExpressionContainer";
  private static final String EXPRESSION = "singleExpression";
  private static final String RETURN = "returnStatement";
  private static final String ATTRIBUTE_VALUE = "jsxAttributeValue";
  private static final Set<String> PRESERVING =
      Set.of("variableDeclaration", "arrowFunctionBody", "propertyAssignment");
  private static final Set<String> LOGICAL_OPERATORS = Set.of("&&", "||", "??");

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

  /** The context a fragment sits in, from the nearest ancestors. */
  private enum Context {
    PRESERVED,
    RETURNED,
    ATTRIBUTE,
    LOGICAL,
    EXPRESSION_CHILD,
    LIST,
    OTHER
  }

  private boolean reported(
      final SyntaxNode fragment, final List<SyntaxNode> ancestors, final String source) {
    final Context context = contextOf(ancestors);
    if (context == Context.PRESERVED) {
      return false;
    }
    final List<SyntaxNode> children =
        fragment.children().stream().filter(child -> isSignificantKind(child.kind())).toList();
    final List<SyntaxNode> significant = new java.util.ArrayList<>();
    for (final SyntaxNode child : children) {
      if (child.kind().equals(TEXT)) {
        if (context == Context.EXPRESSION_CHILD || context == Context.LOGICAL) {
          return false;
        }
        if (!isPaddingText(child, source)) {
          significant.add(child);
        }
      } else if (child.kind().equals(EXPRESSION_CHILD)) {
        if (context == Context.LOGICAL) {
          return false;
        }
        significant.add(child);
      } else {
        significant.add(child);
      }
    }
    return switch (significant.size()) {
      case 0 -> true;
      case 1 -> singleChildReported(significant.get(0), context, ancestors);
      default -> context == Context.LIST;
    };
  }

  private static boolean singleChildReported(
      final SyntaxNode child, final Context context, final List<SyntaxNode> ancestors) {
    if (child.kind().equals(TEXT)) {
      if (context == Context.ATTRIBUTE || context == Context.RETURNED) {
        return false;
      }
      return ancestors.get(ancestors.size() - 2).kind().equals(FRAGMENT);
    }
    if (child.kind().equals(EXPRESSION_CHILD) && context == Context.RETURNED) {
      return false;
    }
    return true;
  }

  /** A child Biome counts: an element, a fragment, an expression or text. */
  private static boolean isSignificantKind(final String kind) {
    return kind.equals(ELEMENT) || kind.equals(TEXT) || kind.equals(EXPRESSION_CHILD);
  }

  /** Text that is only whitespace with a line break is padding, as Biome's is_padding_spaces. */
  private static boolean isPaddingText(final SyntaxNode text, final String source) {
    final String raw = source.substring(text.span().startOffset(), text.span().endOffset());
    return raw.isBlank() && raw.contains("\n");
  }

  /**
   * The context of the fragment: its wrapper element's parent decides it. The wrapper is the
   * jsxElement holding the fragment; the expression above it is a singleExpression.
   */
  private static Context contextOf(final List<SyntaxNode> ancestors) {
    final int wrapper = ancestors.size() - 1;
    if (wrapper < 1) {
      return Context.OTHER;
    }
    final SyntaxNode parent = ancestors.get(wrapper - 1);
    if (parent.kind().equals(ELEMENT) || parent.kind().equals(FRAGMENT)) {
      return Context.LIST;
    }
    if (!parent.kind().equals(EXPRESSION) || wrapper < 2) {
      return Context.OTHER;
    }
    final SyntaxNode outer = ancestors.get(wrapper - 2);
    if (outer.kind().equals(EXPRESSION_CHILD)) {
      final boolean inAttribute =
          wrapper >= 3 && ancestors.get(wrapper - 3).kind().equals(ATTRIBUTE_VALUE);
      return inAttribute ? Context.ATTRIBUTE : Context.EXPRESSION_CHILD;
    }
    if (outer.kind().equals(EXPRESSION) && isLogical(outer)) {
      return Context.LOGICAL;
    }
    if (outer.kind().equals(RETURN)) {
      return Context.RETURNED;
    }
    return PRESERVING.contains(outer.kind()) ? Context.PRESERVED : Context.OTHER;
  }

  private static boolean isLogical(final SyntaxNode expression) {
    return expression.children().stream()
        .anyMatch(child -> LOGICAL_OPERATORS.contains(SyntaxWalk.text(child)));
  }
}
