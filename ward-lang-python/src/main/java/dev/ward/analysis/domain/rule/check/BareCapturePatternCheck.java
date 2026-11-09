package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code match} case whose pattern is a bare name without a guard and that is not the
 * last case, like Pylint's {@code bare-name-capture-pattern}: the name matches anything, so the
 * cases after it can never run. A wildcard {@code _} is not a capture.
 */
public final class BareCapturePatternCheck implements RuleCheck {

  private static final Set<String> MATCHES = Set.of("match_stmt");
  private static final String CASE_BLOCK = "case_block";
  private static final String GUARD = "guard";
  private static final String WILDCARD = "_";
  private static final Pattern NAME = Pattern.compile("^[A-Za-z_]\\w*$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BareCapturePatternCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, MATCHES)) {
      final List<SyntaxNode> cases =
          visit.node().children().stream()
              .filter(child -> CASE_BLOCK.equals(child.kind()))
              .toList();
      for (int index = 0; index + 1 < cases.size(); index++) {
        final SyntaxNode pattern = bareName(cases.get(index));
        if (pattern != null) {
          found.add(new RuleMatch(pattern.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The pattern node of a case when it is a bare capture name with no guard, or null. */
  private static SyntaxNode bareName(final SyntaxNode caseBlock) {
    if (caseBlock.children().stream().anyMatch(child -> GUARD.equals(child.kind()))) {
      return null;
    }
    for (final SyntaxNode child : caseBlock.children()) {
      if (!"case".equals(SyntaxWalk.text(child))
          && !":".equals(SyntaxWalk.text(child))
          && !"block".equals(child.kind())) {
        final String text = SyntaxWalk.text(child);
        return NAME.matcher(text).matches() && !WILDCARD.equals(text) ? child : null;
      }
    }
    return null;
  }
}
