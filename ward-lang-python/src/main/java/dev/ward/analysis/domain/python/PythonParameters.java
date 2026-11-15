package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The parameters of one Python body: the writes that bind them on entry, and the flow sites that
 * mark the ones a web framework fills from the request.
 *
 * @param accesses the writes that bind the parameters on entry
 * @param sites the flow sites of the parameters
 */
record PythonParameters(List<VariableAccess> accesses, List<FlowSite> sites) {

  /** The parameters of a module or a class, which has none. */
  static final PythonParameters NONE = new PythonParameters(List.of(), List.of());

  private static final String NAME = "name";
  private static final String PARAMETERS = "parameters";
  private static final String FUNCDEF = "funcdef";
  private static final Set<String> TYPE_NODES = Set.of("test", "namedexpr_test");

  PythonParameters {
    accesses = List.copyOf(accesses);
    sites = List.copyOf(sites);
  }

  /**
   * The parameters of a definition.
   *
   * @param definition a {@code funcdef} or {@code classdef} node
   * @param decorators the decorators of the definition, in source order
   * @return the parameters; a class has none
   */
  static PythonParameters of(final SyntaxNode definition, final List<SyntaxNode> decorators) {
    final List<SyntaxNode> found = PythonSyntax.children(definition, PARAMETERS);
    if (found.isEmpty()) {
      return NONE;
    }
    final List<SyntaxNode> names = new ArrayList<>();
    collectNames(found.get(0), names);
    final List<VariableAccess> accesses = new ArrayList<>();
    for (final SyntaxNode name : names) {
      accesses.add(access(name));
    }
    final List<FlowSite> sites =
        FUNCDEF.equals(definition.kind())
            ? PythonParameterSites.of(definition, decorators)
            : List.of();
    return new PythonParameters(accesses, sites);
  }

  private static VariableAccess access(final SyntaxNode name) {
    return new VariableAccess(
        PythonSyntax.text(PythonSyntax.tokens(name).get(0)),
        AccessKind.PARAMETER,
        name.span(),
        name.span());
  }

  /** The parameter names of a parameter list, skipping the defaults and annotations. */
  private static void collectNames(final SyntaxNode node, final List<SyntaxNode> names) {
    if (TYPE_NODES.contains(node.kind())) {
      return;
    }
    if (NAME.equals(node.kind())) {
      names.add(node);
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectNames(child, names);
    }
  }

  /** The decorators of a {@code decorated} node, in source order. */
  static List<SyntaxNode> decoratorsOf(final SyntaxNode decorated) {
    return PythonSyntax.children(decorated, "decorators").stream()
        .flatMap(list -> PythonSyntax.children(list, "decorator").stream())
        .toList();
  }
}
