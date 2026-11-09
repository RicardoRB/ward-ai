package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.UnusedParameterCheck;
import dev.ward.analysis.domain.scope.Scope;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Swift's unused-parameter check, like detekt's {@code UnusedParameter}: the shared check over the
 * functions whose header carries only plain modifiers. A public, attributed or {@code override}
 * function keeps its signature, so its parameters are left alone.
 */
public final class SwiftUnusedParameterCheck implements RuleCheck {

  /** A function declaration that no fixed signature constrains: only plain modifiers come first. */
  private static final Pattern PLAIN_FUNCTION =
      Pattern.compile(
          "(?:(?:private|fileprivate|internal|static|final|mutating|nonmutating)\\s+)*func\\s");

  private final UnusedParameterCheck shared;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the parameter name
   */
  public SwiftUnusedParameterCheck(final String message) {
    this.shared = new UnusedParameterCheck(message, SwiftUnusedParameterCheck::plainFunction);
  }

  /**
   * Always fails: the check reads the scopes and the text of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused-parameter check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    return shared.matches(root, flow, view, unit);
  }

  /** Whether the function's declaration starts with a keyword and no attribute. */
  private static boolean plainFunction(final Scope function, final SourceUnit unit) {
    final int start = function.span().startOffset();
    final String text = unit.text();
    return start < text.length()
        && PLAIN_FUNCTION.matcher(text).region(start, text.length()).lookingAt();
  }
}
