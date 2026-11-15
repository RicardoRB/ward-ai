package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an assignment to a member or an element of a variable, such as {@code box.f = v} or {@code
 * a[i] = v}, as a member write: the name is the member, or {@code []} for an element, the qualifier
 * is the variable that the target starts with, and argument 0 is the assigned value. A plain
 * assignment to a variable is not a store.
 */
final class JavaStoreSites {

  private static final Pattern TARGET = Pattern.compile("^([A-Za-z_$][\\w$]*)[.\\[].*$");
  private static final String ELEMENT = "[]";

  private JavaStoreSites() {}

  /**
   * Returns the member write of one assignment.
   *
   * @param target the left-hand side
   * @param value the assigned value
   * @param span the span of the whole assignment
   * @return the site, or empty when the target is a plain variable
   */
  static Optional<FlowSite> of(final SyntaxNode target, final SyntaxNode value, final Span span) {
    final String written = JavaFlowSyntax.text(target);
    final Matcher matcher = TARGET.matcher(written);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    final String name =
        written.endsWith("]") ? ELEMENT : written.substring(written.lastIndexOf('.') + 1);
    return Optional.of(
        new FlowSite(
            FlowSiteKind.MEMBER_WRITE,
            name,
            matcher.group(1),
            Optional.empty(),
            List.of(),
            List.of(value.span()),
            span));
  }
}
