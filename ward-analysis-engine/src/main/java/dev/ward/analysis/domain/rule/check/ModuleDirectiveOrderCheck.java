package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the directives of each module declaration. Directives of a listed kind come in the listed
 * order and each kind forms one block; with {@code validateBlockSeparation}, exactly one blank line
 * separates two blocks and none appears inside a block. Directives of other kinds are not checked.
 */
public final class ModuleDirectiveOrderCheck implements RuleCheck {

  private static final String MODULE_KIND = "moduleDeclaration";
  private static final String DIRECTIVE_KIND = "moduleDirective";

  private final List<String> order;
  private final boolean validateBlockSeparation;
  private final String message;

  /**
   * Creates the check.
   *
   * @param order the directive keywords, first to last
   * @param validateBlockSeparation whether blank lines between and inside blocks are checked
   * @param message the message; {@code {value}} is the directive keyword
   */
  public ModuleDirectiveOrderCheck(
      final List<String> order, final boolean validateBlockSeparation, final String message) {
    this.order = List.copyOf(order);
    this.validateBlockSeparation = validateBlockSeparation;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return check(root, List.of());
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return check(root, ImportLines.of(unit));
  }

  private List<RuleMatch> check(final SyntaxNode root, final List<String> lines) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MODULE_KIND))) {
      checkModule(visit.node(), lines, matches);
    }
    return List.copyOf(matches);
  }

  private void checkModule(
      final SyntaxNode module, final List<String> lines, final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    String previousKeyword = null;
    Span previousSpan = null;
    int highest = -1;
    for (final SyntaxNode directive : module.children()) {
      final String keyword = keyword(directive);
      final int rank = order.indexOf(keyword);
      if (!DIRECTIVE_KIND.equals(directive.kind()) || rank < 0) {
        continue;
      }
      if (rank < highest) {
        matches.add(at(directive.span(), keyword));
      }
      highest = Math.max(highest, rank);
      if (keyword.equals(previousKeyword)) {
        if (validateBlockSeparation && blankLines(lines, previousSpan, directive) > 0) {
          matches.add(at(directive.span(), keyword));
        }
      } else {
        if (!seen.add(keyword)) {
          matches.add(at(directive.span(), keyword));
        }
        final int blank = previousKeyword == null ? -1 : blankLines(lines, previousSpan, directive);
        if (validateBlockSeparation && blank >= 0 && blank != 1) {
          matches.add(at(directive.span(), keyword));
        }
      }
      previousKeyword = keyword;
      previousSpan = directive.span();
    }
  }

  /** Returns the blank lines before a directive, or -1 when the text is unknown. */
  private static int blankLines(
      final List<String> lines, final Span previous, final SyntaxNode next) {
    if (lines.isEmpty()) {
      return -1;
    }
    return ImportLines.blankBetween(lines, previous.endLine(), next.span().startLine());
  }

  private static String keyword(final SyntaxNode directive) {
    return SyntaxWalk.leaves(directive).stream()
        .findFirst()
        .map(leaf -> leaf.fields().get("text"))
        .orElse("");
  }

  private RuleMatch at(final Span span, final String keyword) {
    return CheckSupport.at(span, CheckSupport.fill(message, keyword, ""));
  }
}
