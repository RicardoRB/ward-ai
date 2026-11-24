package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads the host of a URL written as a template literal. The host part lies between the scheme and
 * the first {@code /}, {@code ?}, {@code #} or port colon; it is local when it holds nothing but
 * safe expressions and the brackets around them, and at least one expression.
 */
final class NodeUrlHosts {
  private static final String HOST_END = "/?#:";
  private static final String BRACKETS = "[]";
  private static final String SCHEME = "://";
  private static final String BACKTICK = "`";

  private final Predicate<SyntaxNode> safe;
  private final StringBuilder scheme = new StringBuilder();
  private boolean ended;
  private boolean expressions;

  /** Creates a reader that asks {@code safe} about each expression of the host. */
  NodeUrlHosts(final Predicate<SyntaxNode> safe) {
    this.safe = safe;
  }

  /** Whether the host of the URL is built only from safe expressions. */
  boolean isLocal(final SyntaxNode url) {
    final Optional<SyntaxNode> template = NodeTemplates.of(NodeFlowSyntax.unwrap(url));
    return template.isPresent() && scan(template.get());
  }

  private boolean scan(final SyntaxNode template) {
    for (final SyntaxNode atom : NodeFlowSyntax.parts(template)) {
      final Optional<SyntaxNode> expression = NodeTemplates.expression(atom);
      final boolean accepted =
          expression.isPresent() ? expression(expression.get()) : text(atom);
      if (!accepted) {
        return false;
      }
    }
    return expressions;
  }

  private boolean expression(final SyntaxNode value) {
    if (scheme.indexOf(SCHEME) < 0) {
      return false;
    }
    if (ended) {
      return true;
    }
    expressions = true;
    return safe.test(value);
  }

  private boolean text(final SyntaxNode atom) {
    String rest = NodeFlowSyntax.text(atom).replace(BACKTICK, "");
    if (scheme.indexOf(SCHEME) < 0) {
      scheme.append(rest);
      final int at = scheme.indexOf(SCHEME);
      rest = at < 0 ? "" : scheme.substring(at + SCHEME.length());
    }
    for (int index = 0; !ended && index < rest.length(); index++) {
      ended = HOST_END.indexOf(rest.charAt(index)) >= 0;
      if (!ended && BRACKETS.indexOf(rest.charAt(index)) < 0) {
        return false;
      }
    }
    return true;
  }
}
