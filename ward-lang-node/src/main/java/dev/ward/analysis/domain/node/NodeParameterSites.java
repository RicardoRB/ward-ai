package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the flow sites at the entry of a TypeScript or JavaScript function: its decorated
 * parameters. Every parameter's type is recorded for the receiver types of later calls.
 */
final class NodeParameterSites {

  private static final Set<String> FORMALS =
      Set.of("formalParameterArg", "requiredParameter", "optionalParameter");
  private static final String DECORATOR = "decorator";
  private static final String DECORATOR_LIST = "decoratorList";
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final Set<String> NOT_BINDINGS =
      Set.of(DECORATOR_LIST, DECORATOR, TYPE_ANNOTATION, "accessibilityModifier");

  private final NodeTypes types;

  /**
   * Creates the finder of one body.
   *
   * @param types the declared types of the body, which the parameters are added to
   */
  NodeParameterSites(final NodeTypes types) {
    this.types = types;
  }

  /**
   * Returns the decorated parameters of a function, and records every parameter's type.
   *
   * @param function the function node, or null for a body without one
   * @return one site per decorated parameter, in source order
   */
  List<FlowSite> of(final SyntaxNode function) {
    final List<FlowSite> found = new ArrayList<>();
    final SyntaxNode list = function == null ? null : NodeBindings.parameterList(function);
    if (list != null) {
      for (final SyntaxNode formal : formalsIn(list, new ArrayList<>())) {
        parameter(formal, found);
      }
    }
    return found;
  }

  private void parameter(final SyntaxNode formal, final List<FlowSite> found) {
    final List<SyntaxNode> names = new ArrayList<>();
    formal.children().stream()
        .filter(child -> !NOT_BINDINGS.contains(child.kind()))
        .forEach(child -> names.addAll(NodeBindings.bindings(child)));
    if (names.isEmpty()) {
      return;
    }
    final String name = NodeFlowSyntax.text(names.get(0));
    types.declareParameter(name, formal);
    final List<String> decorators = decoratorsOf(formal);
    if (!decorators.isEmpty()) {
      found.add(
          new FlowSite(
              FlowSiteKind.PARAMETER,
              name,
              "",
              Optional.empty(),
              decorators,
              List.of(),
              formal.span()));
    }
  }

  private static List<SyntaxNode> formalsIn(final SyntaxNode node, final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      if (FORMALS.contains(child.kind())) {
        found.add(child);
      } else {
        formalsIn(child, found);
      }
    }
    return found;
  }

  private static List<String> decoratorsOf(final SyntaxNode formal) {
    final List<String> found = new ArrayList<>();
    for (final SyntaxNode child : formal.children()) {
      if (DECORATOR_LIST.equals(child.kind())) {
        child.children().forEach(decorator -> found.add(decoratorName(decorator)));
      } else if (DECORATOR.equals(child.kind())) {
        found.add(decoratorName(child));
      }
    }
    return found;
  }

  /** The name of a decorator, without its {@code @} and its argument list. */
  private static String decoratorName(final SyntaxNode decorator) {
    return NodeFlowSyntax.text(decorator).replace("@", "").replaceAll("\\(.*", "").trim();
  }
}
