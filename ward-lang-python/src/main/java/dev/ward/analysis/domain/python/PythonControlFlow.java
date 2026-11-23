package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  private static final String FUNCDEF = "funcdef";
  private static final String CLASSDEF = "classdef";
  private static final String IMPORT_FROM = "import_from";
  private static final String STAR = "*";
  private static final String GLOBAL = "global";
  private static final String NONLOCAL = "nonlocal";
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";
  private static final Set<String> DECLARATIONS = Set.of("global_stmt", "nonlocal_stmt");

  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final List<ControlFlowGraph> graphs = new ArrayList<>();
    final Map<String, String> imports = PythonImportAliases.of(tree);
    final Set<String> collections = PythonLiteralCollections.of(tree);
    graphs.add(
        body(MODULE, tree, PythonParameters.NONE, tree.span(), new FileFacts(imports, collections))
    );
    collectDefinitions(tree, graphs, imports, collections, List.of());
    return new ControlFlowResult(graphs, List.of());
  }

  /** Adds the graph of every function and class under {@code node}, nested ones included. */
  private static void collectDefinitions(
      final SyntaxNode node,
      final List<ControlFlowGraph> out,
      final Map<String,
      String> imports,
      final Set<String> collections,
      final List<SyntaxNode> decorators
  ) {
    final List<SyntaxNode> inherited =
        "decorated".equals(node.kind()) ? PythonParameters.decoratorsOf(node) : decorators;
    for (final SyntaxNode child : node.children()) {
      if (FUNCDEF.equals(child.kind()) || CLASSDEF.equals(child.kind())) {
        out.add(definition(child, imports, collections, inherited));
      }
      collectDefinitions(
          child,
          out,
          imports,
          collections,
          FUNCDEF.equals(child.kind()) ? List.of() : inherited
      );
    }
  }

  private static ControlFlowGraph definition(
      final SyntaxNode definition,
      final Map<String,
      String> imports,
      final Set<String> collections,
      final List<SyntaxNode> decorators
  ) {
    final SyntaxNode block = PythonSyntax.children(definition, BLOCK).get(0);
    final PythonParameters parameters = PythonParameters.of(definition, decorators);
    final Set<String> usable = new HashSet<>(collections);
    parameters.accesses().forEach(access -> usable.remove(access.name()));
    return body(
        PythonSyntax.firstOfKind(definition, NAME).map(PythonControlFlow::text).orElse(MODULE),
        block,
        parameters,
        definition.span(),
        new FileFacts(imports, usable)
    );
  }

  /** What the whole file says that a body needs: its imports and the collections it fixes. */
  private record FileFacts(Map<String, String> imports, Set<String> collections) {}

  private static ControlFlowGraph body(
      final String name,
      final SyntaxNode block,
      final PythonParameters parameters,
      final Span span,
      final FileFacts facts
  ) {
    return new PythonBodyLowering(
        declaredNames(block),
        !hasStarImport(block),
        facts.imports(),
        facts.collections()
    )
        .lower(name, block, parameters, span);
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
    return IMPORT_FROM.equals(node.kind())
        && node.children().stream().anyMatch(child -> STAR.equals(PythonSyntax.text(child)))
        || node.children().stream().anyMatch(PythonControlFlow::hasStarImport);
  }

  private static String text(final SyntaxNode name) {
    return PythonSyntax.text(PythonSyntax.tokens(name).get(0));
  }
}
