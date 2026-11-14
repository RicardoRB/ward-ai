package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The parameters of a Python function that a web framework fills from the request. A parameter
 * carries, as annotations, the last name of each decorator of its function with an {@code @} prefix
 * (such as {@code @route} or {@code @get}) and the name of the call that is its default (such as
 * {@code Query}), so a specification can name a handler or a FastAPI parameter kind. The receiver
 * and response parameters of a handler carry nothing.
 */
final class PythonParameterSites {

  private static final String CALL_OPEN = "(";
  private static final String DECORATOR_MARK = "@";
  private static final Set<String> NOT_INPUT = Set.of("self", "cls", "request", "response");

  private PythonParameterSites() {}

  /**
   * The parameter sites of a function.
   *
   * @param definition the {@code funcdef} node
   * @param decorators the {@code decorator} nodes of the function, in source order
   * @return one site per parameter that has an annotation
   */
  static List<FlowSite> of(final SyntaxNode definition, final List<SyntaxNode> decorators) {
    final List<String> marks = decorators.stream().flatMap(d -> mark(d).stream()).toList();
    final List<SyntaxNode> lists = new ArrayList<>();
    PythonSyntax.children(definition, "parameters")
        .forEach(parameters -> lists.addAll(PythonSyntax.children(parameters, "typedargslist")));
    final List<FlowSite> sites = new ArrayList<>();
    for (final SyntaxNode list : lists) {
      parameters(list, marks, sites);
    }
    return sites;
  }

  private static void parameters(
      final SyntaxNode list, final List<String> marks, final List<FlowSite> sites) {
    final List<SyntaxNode> children = list.children();
    for (int index = 0; index < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (!"tfpdef".equals(child.kind())) {
        continue;
      }
      final SyntaxNode name = PythonSyntax.children(child, PythonNames.NAME).get(0);
      final String identifier = PythonNames.identifier(name);
      final List<String> annotations = annotationsOf(identifier, marks, children, index);
      if (!annotations.isEmpty()) {
        sites.add(site(identifier, annotations, name));
      }
    }
  }

  private static List<String> annotationsOf(
      final String identifier,
      final List<String> marks,
      final List<SyntaxNode> children,
      final int index) {
    final List<String> annotations = new ArrayList<>();
    if (!NOT_INPUT.contains(identifier)) {
      annotations.addAll(marks);
      defaultCall(children, index).ifPresent(annotations::add);
    }
    return annotations;
  }

  private static FlowSite site(
      final String identifier, final List<String> annotations, final SyntaxNode name) {
    return new FlowSite(
        FlowSiteKind.PARAMETER,
        identifier,
        "",
        Optional.empty(),
        annotations,
        List.of(),
        name.span());
  }

  /**
   * The called name of the default that follows the parameter at {@code index}, if there is one.
   */
  private static Optional<String> defaultCall(final List<SyntaxNode> children, final int index) {
    if (index + 2 >= children.size() || !PythonSyntax.isToken(children.get(index + 1), "=")) {
      return Optional.empty();
    }
    return calledName(children.get(index + 2));
  }

  /**
   * {@code @app.get("/x")} gives {@code @get}; {@code @login_required} gives
   * {@code @login_required}.
   */
  private static Optional<String> mark(final SyntaxNode decorator) {
    return calledName(decorator.children().get(1)).map(DECORATOR_MARK::concat);
  }

  /** The last name before the first opening parenthesis of an expression, or the whole name. */
  private static Optional<String> calledName(final SyntaxNode expression) {
    final StringBuilder text = new StringBuilder();
    for (final SyntaxNode token : PythonSyntax.tokens(expression)) {
      if (CALL_OPEN.equals(PythonSyntax.text(token))) {
        break;
      }
      text.append(PythonSyntax.text(token));
    }
    final String written = text.toString();
    if (!written.matches("[A-Za-z_][\\w.]*")) {
      return Optional.empty();
    }
    return Optional.of(written.substring(written.lastIndexOf('.') + 1));
  }
}
