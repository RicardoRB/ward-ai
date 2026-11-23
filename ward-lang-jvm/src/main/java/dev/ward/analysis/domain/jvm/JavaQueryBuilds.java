package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the strings that are built from a constant prefix with an {@code http} or {@code https}
 * scheme, a host and a {@code ?} or {@code #}, followed by values: {@code "https://host/p?x=" + v},
 * {@code String.format("https://host/p?x=%s", v)} and {@code "https://host/p?x=%s".formatted(v)}.
 * Whatever the values hold, they only fill the query or the fragment, so the host and the path
 * stay the constants. Such a build gives a site named {@link #NAME} whose arguments are the values
 * (built by {@code JavaFlowSites}), so a request specification can list it as a sanitizer of them.
 *
 * <p>shortcut: only a concatenation, {@code String.format} and {@code formatted}; a {@code
 * StringBuilder} or a {@code +=} build is not recognised; upgrade when a project needs it.
 */
final class JavaQueryBuilds {
  /** The name of the site of a build whose values only fill the query or the fragment. */
  static final String NAME = "<constant-host-query>";

  private static final String PLUS = "+";
  private static final String EXPRESSION = "expression";
  private static final String FORMAT = "format";
  private static final String FORMATTED = "formatted";
  private static final int PARTS = 3;
  private static final int LITERAL_GROUP = 1;
  private static final Set<String> STRING_TYPES = Set.of("String", "java.lang.String");
  private static final Pattern LITERAL = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*+)\"$");
  private static final Pattern CONSTANT_BASE =
      Pattern.compile("^https?://[^/?#\\s\\\\@]+(?:[^?#\\s]*)[?#]");

  private JavaQueryBuilds() {}

  /** The site of an expression that is a {@code +} chain, when its constant start holds the host and the query. */
  static Optional<FlowSite> concatenation(final SyntaxNode node) {
    final List<SyntaxNode> operands = new ArrayList<>();
    flatten(node, operands);
    final StringBuilder prefix = new StringBuilder();
    int next = 0;
    while (next < operands.size()) {
      final Matcher literal = LITERAL.matcher(JavaFlowSyntax.text(operands.get(next)));
      if (!literal.matches()) {
        break;
      }
      prefix.append(literal.group(LITERAL_GROUP));
      next++;
    }
    return built(prefix.toString(), operands.subList(next, operands.size()), node.span());
  }

  /** The site of a call to {@code String.format} or {@code formatted} with such a format. */
  static Optional<FlowSite> format(
      final String name,
      final String qualifier,
      final List<SyntaxNode> arguments,
      final Span span
  ) {
    if (FORMAT.equals(name) && STRING_TYPES.contains(qualifier) && !arguments.isEmpty()) {
      return formatted(arguments.get(0), arguments.subList(1, arguments.size()), span);
    }
    if (FORMATTED.equals(name) && qualifier.startsWith("\"")) {
      final Matcher literal = LITERAL.matcher(qualifier);
      return literal.matches()
          ? built(untilFormat(literal.group(LITERAL_GROUP)), arguments, span)
          : Optional.empty();
    }
    return Optional.empty();
  }

  private static Optional<FlowSite> formatted(
      final SyntaxNode pattern,
      final List<SyntaxNode> values,
      final Span span
  ) {
    final Matcher literal = LITERAL.matcher(JavaFlowSyntax.text(pattern));
    return literal.matches()
        ? built(untilFormat(literal.group(LITERAL_GROUP)), values, span)
        : Optional.empty();
  }

  /** The text before the first format specifier, which is all that is constant. */
  private static String untilFormat(final String pattern) {
    final int percent = pattern.indexOf('%');
    return percent < 0 ? pattern : pattern.substring(0, percent);
  }

  private static Optional<FlowSite> built(
      final String prefix,
      final List<SyntaxNode> values,
      final Span span
  ) {
    if (values.isEmpty() || !CONSTANT_BASE.matcher(prefix).find()) {
      return Optional.empty();
    }
    return Optional.of(
        new FlowSite(
            FlowSiteKind.CALL,
            NAME,
            "",
            Optional.empty(),
            List.of(),
            JavaCallArguments.spans(values),
            span
        )
    );
  }

  /** Lists the operands of a chain {@code a + b + c}, which parses as {@code (a + b) + c}. */
  private static void flatten(final SyntaxNode node, final List<SyntaxNode> operands) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(node);
    if (EXPRESSION.equals(node.kind())
        && parts.size() == PARTS
        && PLUS.equals(JavaFlowSyntax.text(parts.get(1)))) {
      flatten(parts.get(0), operands);
      operands.add(parts.get(2));
    } else {
      operands.add(node);
    }
  }
}
