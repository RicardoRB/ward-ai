package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the string literals that are a whole value: the literal alone, or the literal converted by
 * {@code getBytes} or {@code toCharArray}. A literal that is only a part of a larger expression, or
 * an argument of the call that produces the value, is not a site, so a secret rule never takes the
 * name of an environment variable for a secret.
 */
final class JavaLiteralSites {

  /** The literal is a value of its own: an initializer or an assigned value. */
  static final String VALUE = "value";

  /** The literal is a whole argument of a call. */
  static final String ARGUMENT = "argument";

  private static final Pattern WHOLE_VALUE =
      Pattern.compile(
          "^\"((?:[^\"\\\\]|\\\\.)*)\"(?:\\.(?:getBytes|toCharArray)\\((?:[^()]|\\([^()]*\\))*\\))?$");

  private JavaLiteralSites() {}

  /**
   * Returns the site of a value, when it is a whole string literal.
   *
   * @param value the node of the value
   * @param role {@link #VALUE} or {@link #ARGUMENT}
   * @return the literal site, or empty when the value is anything else
   */
  static Optional<FlowSite> of(final SyntaxNode value, final String role) {
    final Matcher matcher = WHOLE_VALUE.matcher(JavaFlowSyntax.text(value));
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new FlowSite(
            FlowSiteKind.LITERAL,
            matcher.group(1),
            role,
            Optional.empty(),
            List.of(),
            List.of(),
            value.span()));
  }
}
