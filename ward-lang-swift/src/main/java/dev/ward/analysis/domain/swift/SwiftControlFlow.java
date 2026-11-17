package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers every function and initializer of a Swift file into a control-flow graph named {@code
 * Type#name(arity)}, so a call can reach it. The variable accesses come from the file's scope
 * analysis. A closure is part of the statement that holds it. Computed properties, subscripts and
 * observers are not lowered.
 */
public final class SwiftControlFlow implements ControlFlowLowering {
  private static final String PARAMETERS = "parameter_clause";
  private static final String CODE_BLOCK = "code_block";
  private static final String EXTENSION = "extension_declaration";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final Set<String> TYPE_NAMES =
      Set.of("class_name", "struct_name", "enum_name", "protocol_name");

  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final UnitScopes scopes = new SwiftScopeAnalysis(environments -> Set.of()).analyze(unit, tree);
    final Map<Integer, Integer> writeEnds = new HashMap<>();
    collectWriteEnds(tree, writeEnds);
    final Collector collector = new Collector(scopes, writeEnds);
    collector.visit(tree, "");
    return new ControlFlowResult(collector.graphs, List.of());
  }

  /** Walks the tree once, lowering each body and keeping the owning type's name in scope. */
  private static final class Collector {
    private final UnitScopes scopes;
    private final Map<Integer, Integer> writeEnds;
    private final List<ControlFlowGraph> graphs = new ArrayList<>();

    Collector(final UnitScopes scopes, final Map<Integer, Integer> writeEnds) {
      this.scopes = scopes;
      this.writeEnds = writeEnds;
    }

    void visit(final SyntaxNode node, final String owner) {
      final String scope = ownerOf(node, owner);
      switch (node.kind()) {
        case "function_declaration" -> lowerFunction(node, scope);
        case "initializer_declaration" -> lowerInitializer(node, scope);
        default -> {
          // Other nodes hold no body of their own.
        }
      }
      node.children().forEach(child -> visit(child, scope));
    }

    private void lowerFunction(final SyntaxNode node, final String scope) {
      final SyntaxNode body = SwiftFlowSyntax.child(node, "function_body");
      final SyntaxNode signature = SwiftFlowSyntax.child(node, "function_signature");
      if (body == null || signature == null) {
        return;
      }
      lower(
          scope,
          SwiftFlowSyntax.text(SwiftFlowSyntax.child(node, "function_name")),
          node,
          SwiftFlowSyntax.child(signature, PARAMETERS),
          SwiftFlowSyntax.child(body, CODE_BLOCK)
      );
    }

    private void lowerInitializer(final SyntaxNode node, final String scope) {
      final SyntaxNode body = SwiftFlowSyntax.child(node, "initializer_body");
      if (body != null) {
        lower(
            scope,
            "init",
            node,
            SwiftFlowSyntax.child(node, PARAMETERS),
            SwiftFlowSyntax.child(body, CODE_BLOCK)
        );
      }
    }

    private void lower(
        final String scope,
        final String name,
        final SyntaxNode owner,
        final SyntaxNode parameters,
        final SyntaxNode block
    ) {
      if (block == null || parameters == null) {
        return;
      }
      final int arity = SwiftFlowSyntax.children(parametersList(parameters), "parameter").size();
      final String graph = (scope.isEmpty() ? "" : scope + "#") + name + "(" + arity + ")";
      final SwiftAccesses accesses =
          new SwiftAccesses(scopes, owner.span(), nested(block), writeEnds);
      graphs.add(
          new SwiftBodyLowering(accesses, new SwiftFlowSites(SwiftLocalTypes.of(parameters, block)))
              .lower(
                  graph,
                  SwiftAccesses.parameters(scopes, parameters.span()),
                  block,
                  owner.span()
              )
      );
    }

    private static SyntaxNode parametersList(final SyntaxNode clause) {
      final SyntaxNode list = SwiftFlowSyntax.child(clause, "parameter_list");
      return list == null ? clause : list;
    }

    /** The spans of the functions and types inside a body, which are bodies of their own. */
    private static List<Span> nested(final SyntaxNode block) {
      final List<Span> found = new ArrayList<>();
      collectNested(block, found);
      return found;
    }

    private static void collectNested(final SyntaxNode node, final List<Span> found) {
      if (SwiftFlowSyntax.OTHER_BODIES.contains(node.kind())) {
        found.add(node.span());
      } else {
        node.children().forEach(child -> collectNested(child, found));
      }
    }

    private static String ownerOf(final SyntaxNode node, final String owner) {
      final String written = typeName(node);
      if (written.isEmpty()) {
        return owner;
      }
      return owner.isEmpty() ? written : owner + "." + written;
    }

    private static String typeName(final SyntaxNode node) {
      if (EXTENSION.equals(node.kind())) {
        final SyntaxNode type = SwiftFlowSyntax.child(node, "type_identifier");
        return type == null ? "" : SwiftFlowSyntax.text(type);
      }
      return node.children().stream()
          .filter(child -> TYPE_NAMES.contains(child.kind()))
          .findFirst()
          .map(SwiftFlowSyntax::text)
          .orElse("");
    }
  }

  /**
   * The end offset of the declaration or assignment that writes each name, by the start offset of
   * the name, so the write comes after the reads of its value.
   */
  private static void collectWriteEnds(final SyntaxNode node, final Map<Integer, Integer> ends) {
    switch (node.kind()) {
      case "pattern_initializer", "optional_binding_condition" ->
          patternNames(SwiftFlowSyntax.child(node, "pattern"), node.span().endOffset(), ends);
      case "for_in_statement" -> {
        final SyntaxNode iterable = SwiftFlowSyntax.child(node, "expression");
        if (iterable != null) {
          patternNames(SwiftFlowSyntax.child(node, "pattern"), iterable.span().endOffset(), ends);
        }
      }
      case "expression" -> assignedName(node, ends);
      default -> {
        // Other nodes write nothing.
      }
    }
    node.children().forEach(child -> collectWriteEnds(child, ends));
  }

  private static void patternNames(
      final SyntaxNode pattern,
      final int end,
      final Map<Integer,
      Integer> ends
  ) {
    if (pattern == null) {
      return;
    }
    if (IDENTIFIER.equals(pattern.kind())) {
      ends.put(pattern.span().startOffset(), end);
    }
    pattern.children().forEach(child -> patternNames(child, end, ends));
  }

  private static void assignedName(final SyntaxNode node, final Map<Integer, Integer> ends) {
    final SyntaxNode target = SwiftFlowSyntax.child(node, "prefix_expression");
    final SyntaxNode operators = SwiftFlowSyntax.child(node, "binary_expressions");
    if (target != null && operators != null && SwiftScopeExpressions.assignsTo(operators)) {
      final SyntaxNode name = SwiftScopeExpressions.plainName(target);
      if (name != null) {
        ends.put(name.span().startOffset(), node.span().endOffset());
      }
    }
  }
}
