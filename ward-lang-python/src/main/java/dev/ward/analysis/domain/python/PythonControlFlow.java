package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lowers the module and every function and class body of a Python file into a control-flow graph.
 * The module is the graph named {@code <module>}; a body whose names are not all known, because of
 * a star import, has no name accesses, so no rule reads it as bound or unbound.
 */
public final class PythonControlFlow implements ControlFlowLowering {

  private static final String MODULE = "<module>";
  private static final String BLOCK = "block";
  private static final String NAME = "name";
  private static final String PARAMETERS = "parameters";
  private static final String FUNCDEF = "funcdef";
  private static final String CLASSDEF = "classdef";
  private static final String IMPORT_FROM = "import_from";
  private static final String STAR = "*";
  private static final String GLOBAL = "global";
  private static final String NONLOCAL = "nonlocal";
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";
  private static final Set<String> TYPE_NODES = Set.of("test", "namedexpr_test");
  private static final Set<String> DECLARATIONS = Set.of("global_stmt", "nonlocal_stmt");

  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final List<ControlFlowGraph> graphs = new ArrayList<>();
    graphs.add(body(MODULE, tree, List.of(), tree.span(), hasStarImport(tree)));
    collectDefinitions(tree, graphs);
    return new ControlFlowResult(graphs, List.of());
  }

  /** Adds the graph of every function and class under {@code node}, nested ones included. */
  private static void collectDefinitions(final SyntaxNode node, final List<ControlFlowGraph> out) {
    for (final SyntaxNode child : node.children()) {
      if (FUNCDEF.equals(child.kind()) || CLASSDEF.equals(child.kind())) {
        out.add(definition(child));
      }
      collectDefinitions(child, out);
    }
  }

  private static ControlFlowGraph definition(final SyntaxNode definition) {
    final SyntaxNode block = PythonSyntax.children(definition, BLOCK).get(0);
    return body(
        nameOf(definition), block, parameters(definition), definition.span(), hasStarImport(block));
  }

  private static ControlFlowGraph body(
      final String name,
      final SyntaxNode block,
      final List<VariableAccess> parameters,
      final Span span,
      final boolean star) {
    return new PythonBodyLowering(declaredNames(block), !star).lower(name, block, parameters, span);
  }

  /** The parameters of a function, which are bound on entry; a class has none. */
  private static List<VariableAccess> parameters(final SyntaxNode definition) {
    final List<SyntaxNode> found = PythonSyntax.children(definition, PARAMETERS);
    if (found.isEmpty()) {
      return List.of();
    }
    final List<SyntaxNode> names = new ArrayList<>();
    collectParameterNames(found.get(0), names);
    final List<VariableAccess> accesses = new ArrayList<>();
    for (final SyntaxNode name : names) {
      accesses.add(new VariableAccess(text(name), AccessKind.PARAMETER, name.span(), name.span()));
    }
    return List.copyOf(accesses);
  }

  /** The parameter names of a parameter list, skipping the defaults and annotations. */
  private static void collectParameterNames(final SyntaxNode node, final List<SyntaxNode> names) {
    if (TYPE_NODES.contains(node.kind())) {
      return;
    }
    if (NAME.equals(node.kind())) {
      names.add(node);
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectParameterNames(child, names);
    }
  }

  /** The names a body declares {@code global} or {@code nonlocal}, which are not its own locals. */
  private static Set<String> declaredNames(final SyntaxNode node) {
    final Set<String> names = new HashSet<>();
    collectDeclared(node, names);
    return names;
  }

  private static void collectDeclared(final SyntaxNode node, final Set<String> names) {
    if (FUNCDEF.equals(node.kind()) || CLASSDEF.equals(node.kind())) {
      return;
    }
    if (DECLARATIONS.contains(node.kind())) {
      for (final SyntaxNode token : PythonSyntax.tokens(node)) {
        final String text = PythonSyntax.text(token);
        if (text.matches(IDENTIFIER) && !GLOBAL.equals(text) && !NONLOCAL.equals(text)) {
          names.add(text);
        }
      }
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectDeclared(child, names);
    }
  }

  /** Reports whether a star import appears under {@code node}, which makes its names unknown. */
  private static boolean hasStarImport(final SyntaxNode node) {
    if (IMPORT_FROM.equals(node.kind())
        && node.children().stream().anyMatch(child -> STAR.equals(PythonSyntax.text(child)))) {
      return true;
    }
    return node.children().stream().anyMatch(PythonControlFlow::hasStarImport);
  }

  private static String nameOf(final SyntaxNode definition) {
    for (final SyntaxNode child : definition.children()) {
      if (NAME.equals(child.kind())) {
        return text(child);
      }
    }
    return MODULE;
  }

  private static String text(final SyntaxNode name) {
    return PythonSyntax.text(PythonSyntax.tokens(name).get(0));
  }
}
