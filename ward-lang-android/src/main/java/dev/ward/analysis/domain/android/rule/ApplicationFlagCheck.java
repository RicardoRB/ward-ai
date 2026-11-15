package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Optional;

/**
 * Finds an {@code application} element of the manifest that sets one Android attribute to {@code
 * true}, such as {@code android:debuggable} or {@code android:usesCleartextTraffic}. The finding
 * points at the attribute.
 */
public final class ApplicationFlagCheck implements RuleCheck {

  private final String attribute;
  private final String message;

  /**
   * Creates the check.
   *
   * @param attribute the local name of the {@code android:} attribute
   * @param message the text of a finding
   */
  public ApplicationFlagCheck(final String attribute, final String message) {
    this.attribute = attribute;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return AndroidNodes.elements(root).stream()
        .filter(element -> "application".equals(AndroidNodes.name(element)))
        .filter(element -> AndroidNodes.androidTrue(element, attribute))
        .map(element -> AndroidNodes.attribute(element, AndroidNodes.ANDROID, attribute))
        .flatMap(Optional::stream)
        .map(found -> new RuleMatch(found.span(), message))
        .toList();
  }
}
