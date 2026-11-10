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
 * Reports an iframe whose static sandbox names both allow-scripts and allow-same-origin, at the
 * sandbox attribute, as Biome 2.5.15's {@code noUnsafeIframeSandbox} does. Tokens compare without
 * case.
 */
public final class BiomeUnsafeIframeSandboxCheck implements RuleCheck {

  private static final String IFRAME = "iframe";
  private static final String SANDBOX = "sandbox";
  private static final String SCRIPTS = "allow-scripts";
  private static final String SAME_ORIGIN = "allow-same-origin";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeUnsafeIframeSandboxCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the sandbox tokens are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the iframe-sandbox check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> IFRAME.equals(JsxAttributes.elementName(visit.node())))
        .flatMap(visit -> JsxAttributes.of(visit.node()).stream())
        .filter(attribute -> SANDBOX.equals(JsxAttributes.name(attribute)))
        .filter(attribute -> unsafe(attribute, unit.text()))
        .map(attribute -> CheckSupport.at(attribute.span(), message))
        .toList();
  }

  private static boolean unsafe(final SyntaxNode sandbox, final String source) {
    final List<String> tokens =
        JsxAttributes.tokens(sandbox, source).stream()
            .map(token -> token.toLowerCase(java.util.Locale.ROOT))
            .toList();
    return tokens.contains(SCRIPTS) && tokens.contains(SAME_ORIGIN);
  }
}
