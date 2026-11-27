package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers every function, arrow function and method of a TypeScript or JavaScript file into a
 * control-flow graph, and the top-level statements of the file as {@code <module>}. A body that
 * uses a construct the lowering does not handle is reported as a failure, and the other bodies are
 * still lowered.
 */
public final class NodeControlFlow implements ControlFlowLowering {
  private static final String RULE_ID = "control-flow";
  private static final String SEMICOLON = ";";
  private static final String MODULE = "<module>";
  private static final String EXPORT = "exportStatement";
  private static final String EXPORT_KEYWORD = "'export'";
  private static final String PROGRAM = "program";
  private static final String VARIABLE = "variableDeclaration";
  private static final String ARROW_BODY = "arrowFunctionBody";
  private static final String FUNCTION_BODY = "functionBody";
  private static final String SINGLE_EXPRESSION = "singleExpression";

  /** Nodes through which a variable's name still reaches the function it is bound to. */
  private static final Set<String> NAME_CARRIERS =
      Set.of(
          SINGLE_EXPRESSION,
          "expressionSequence",
          "anonymousFunction",
          "initializer",
          "objectLiteral"
      );

  /** A {@code name: value} member of an object literal, which names the function it holds. */
  private static final String PROPERTY = "propertyAssignment";

  /** Nodes that only wrap one statement or expression, which are looked through. */
  private static final Set<String> WRAPPERS =
      Set.of(
          "statement",
          "expressionStatement",
          "expressionSequence",
          "singleExpression",
          "anonymousFunction"
      );

  /** Top-level statements that only declare; a file of only these has no module graph. */
  private static final Set<String> DECLARATION_ONLY =
      Set.of(
          "functionDeclaration",
          "classDeclaration",
          "interfaceDeclaration",
          "typeAliasDeclaration",
          "enumDeclaration",
          "abstractDeclaration",
          "namespaceDeclaration",
          "importStatement",
          "emptyStatement_"
      );

  /**
   * Lowers the bodies of the file.
   *
   * @param unit the source unit the tree was parsed from
   * @param tree the syntax tree of the unit
   * @return the graphs and the failures
   */
  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final Collector collector =
        new Collector(
            unit,
            NodeImports.of(tree, unit.path()),
            NodeFixedTables.of(tree),
            NodeCollections.of(tree),
            NodeObjectCaches.of(tree)
        );
    if (PROGRAM.equals(tree.kind()) && hasTopLevelCode(tree)) {
      collector.lowerModule(tree);
    }
    collector.visit(tree, null, false);
    return new ControlFlowResult(collector.graphs, collector.failures);
  }

  /** Reports whether the file runs code at top level, which a module graph is needed for. */
  private static boolean hasTopLevelCode(final SyntaxNode program) {
    for (final SyntaxNode statement : NodeFlowSyntax.statements(program)) {
      if (!isDeclarationOnly(statement)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reports whether a top-level statement only declares a function or type. An expression statement
   * that is just a function (an {@code async function} parses this way) counts as a declaration.
   */
  private static boolean isDeclarationOnly(final SyntaxNode statement) {
    SyntaxNode node = statement;
    while (true) {
      if (DECLARATION_ONLY.contains(node.kind()) || NodeFlowSyntax.isFunction(node)) {
        return true;
      }
      final List<SyntaxNode> parts = significant(node);
      if (!WRAPPERS.contains(node.kind()) || parts.size() != 1) {
        return false;
      }
      node = parts.get(0);
    }
  }

  private static List<SyntaxNode> significant(final SyntaxNode node) {
    final List<SyntaxNode> parts = new ArrayList<>();
    for (final SyntaxNode part : NodeFlowSyntax.parts(node)) {
      if (!SEMICOLON.equals(NodeFlowSyntax.text(part))) {
        parts.add(part);
      }
    }
    return parts;
  }

  /**
   * Walks the tree once, lowering each body and carrying a variable's name down to its function.
   */
  private static final class Collector {
    private final SourceUnit unit;
    private final NodeImports imports;
    private final Set<String> tables;
    private final Set<String> collections;
    private final Map<String, List<Integer>> caches;
    private final List<ControlFlowGraph> graphs = new ArrayList<>();
    private final List<AnalysisFinding> failures = new ArrayList<>();

    Collector(
        final SourceUnit unit,
        final NodeImports imports,
        final Set<String> tables,
        final Set<String> collections,
        final Map<String,
        List<Integer>> caches
    ) {
      this.unit = unit;
      this.imports = imports;
      this.tables = tables;
      this.collections = collections;
      this.caches = caches;
    }

    void visit(final SyntaxNode node, final String hint, final boolean exported) {
      String childHint = null;
      if (NodeFlowSyntax.isFunction(node)) {
        lowerFunction(node, hint, exported);
      } else if (VARIABLE.equals(node.kind())) {
        childHint = NodeFunctionNames.variable(node);
      } else if (NAME_CARRIERS.contains(node.kind())) {
        childHint = hint;
      } else if (PROPERTY.equals(node.kind())) {
        childHint = NodeFunctionNames.member(node, hint);
      }
      final boolean inExport =
          !NodeFlowSyntax.isFunction(node) && (exported || exports(node));
      for (final SyntaxNode child : node.children()) {
        visit(child, childHint, inExport);
      }
    }

    void lowerModule(final SyntaxNode program) {
      lower(
          MODULE,
          program.span(),
          () ->
              new NodeBodyLowering(imports, tables, collections, caches)
                  .lowerStatements(MODULE, null, NodeFlowSyntax.statements(program), program.span())
      );
    }

    /** An {@code export} keyword before a statement, or an export statement of its own. */
    private static boolean exports(final SyntaxNode node) {
      return EXPORT.equals(node.kind())
          || !node.children().isEmpty() && EXPORT_KEYWORD.equals(node.children().get(0).kind());
    }

    private void lowerFunction(
        final SyntaxNode function,
        final String hint,
        final boolean exported
    ) {
      final String name = owned(NodeFunctionNames.of(function, hint), exported);
      final SyntaxNode body = NodeFlowSyntax.body(function);
      lower(name, function.span(), () -> lowerBody(name, function, body));
    }

    private ControlFlowGraph lowerBody(
        final String name,
        final SyntaxNode function,
        final SyntaxNode body
    ) {
      if (ARROW_BODY.equals(body.kind())) {
        final SyntaxNode expression =
            NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(body), SINGLE_EXPRESSION);
        if (expression != null) {
          return new NodeBodyLowering(imports, tables, collections, caches)
              .lowerExpression(name, function, expression, function.span());
        }
        return lowerStatements(
            name,
            function,
            NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(body), FUNCTION_BODY)
        );
      }
      return lowerStatements(name, function, body);
    }

    private ControlFlowGraph lowerStatements(
        final String name,
        final SyntaxNode function,
        final SyntaxNode body
    ) {
      return new NodeBodyLowering(imports, tables, collections, caches)
          .lowerStatements(name, function, NodeFlowSyntax.statements(body), function.span());
    }

    private void lower(final String name, final Span span, final Lowering lowering) {
      try {
        graphs.add(lowering.run());
      } catch (IllegalStateException failure) {
        failures.add(
            new AnalysisFinding(
                RULE_ID,
                AnalysisSeverity.WARNING,
                "control flow not lowered for " + name + ": " + failure.getMessage(),
                unit.path(),
                span.startLine(),
                span.startColumn(),
                null,
                List.of(),
                null,
                List.of()
            )
        );
      }
    }

    /**
     * A function that the file exports is {@code module#name}, so that a call through an import of
     * another file reaches it; any other function is named by its own name.
     */
    private String owned(final String name, final boolean exported) {
      return exported && !name.startsWith("<")
          ? NodeModuleNames.of(unit.path()) + "#" + name
          : name;
    }
  }

  /** A lowering of one body, which may fail with an {@link IllegalStateException}. */
  @FunctionalInterface
  private interface Lowering {
    ControlFlowGraph run();
  }
}
