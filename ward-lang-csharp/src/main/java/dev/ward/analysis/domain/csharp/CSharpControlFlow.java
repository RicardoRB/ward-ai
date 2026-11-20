package dev.ward.analysis.domain.csharp;

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
import java.util.Optional;
import java.util.Set;

/**
 * Lowers every method, constructor, finalizer, operator, accessor and local function of a C# file
 * into a control-flow graph named {@code Type#name(arity)}. A lambda is part of the statement that
 * holds it. The variable accesses come from the file's scope analysis, and the calls, creations and
 * member reads and writes of each statement are recorded as flow sites.
 */
public final class CSharpControlFlow implements ControlFlowLowering {
  private static final Set<String> TYPES =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "record_declaration",
          "interface_declaration",
          "enum_declaration"
      );
  private static final Set<String> BODIES =
      Set.of(
          "method_declaration",
          "constructor_declaration",
          "finalizer_declaration",
          "operator_declaration",
          "local_function_declaration",
          "accessor_declaration"
      );
  private static final String PARAMETER_LIST = "parameter_list";

  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final UnitScopes scopes = new CSharpScopeAnalysis(environments -> Set.of()).analyze(unit, tree);
    final Map<Integer, Integer> writeEnds = new HashMap<>();
    CSharpWriteEnds.collect(tree, writeEnds);
    final List<ControlFlowGraph> graphs = new ArrayList<>();
    visit(tree, "", new Flow(scopes, writeEnds), graphs);
    return new ControlFlowResult(graphs, List.of());
  }

  /** What every body of the file shares. */
  private record Flow(UnitScopes scopes, Map<Integer, Integer> writeEnds) {}

  private static void visit(
      final SyntaxNode node,
      final String owner,
      final Flow flow,
      final List<ControlFlowGraph> graphs
  ) {
    final String scope = TYPES.contains(node.kind()) ? qualified(owner, node) : owner;
    if (BODIES.contains(node.kind())) {
      body(node).ifPresent(
          block -> graphs.add(lowerBody(graphName(scope, node), node, block, flow))
      );
    }
    node.children().forEach(child -> visit(child, scope, flow, graphs));
  }

  private static ControlFlowGraph lowerBody(
      final String name,
      final SyntaxNode owner,
      final SyntaxNode block,
      final Flow flow
  ) {
    final Optional<SyntaxNode> parameters = CSharpSyntax.child(owner, PARAMETER_LIST);
    final CSharpAccesses accesses =
        new CSharpAccesses(flow.scopes(), owner.span(), nested(block), flow.writeEnds());
    final CSharpFlowSites sites =
        new CSharpFlowSites(CSharpLocalTypes.of(parameters.orElse(null), block));
    return new CSharpBodyLowering(accesses, sites)
        .lower(
            name,
            parameters
                .map(list -> CSharpAccesses.parameters(flow.scopes(), list.span()))
                .orElse(List.of()),
            block,
            owner.span()
        );
  }

  /** The spans of the functions and types inside a body, which are bodies of their own. */
  private static List<Span> nested(final SyntaxNode block) {
    final List<Span> found = new ArrayList<>();
    collectNested(block, found);
    return found;
  }

  private static void collectNested(final SyntaxNode node, final List<Span> found) {
    if (CSharpFlowSyntax.OTHER_BODIES.contains(node.kind())) {
      found.add(node.span());
    } else {
      node.children().forEach(child -> collectNested(child, found));
    }
  }

  private static String qualified(final String owner, final SyntaxNode type) {
    final String name = CSharpFlowSyntax.name(type);
    return owner.isEmpty() ? name : owner + "." + name;
  }

  private static Optional<SyntaxNode> body(final SyntaxNode node) {
    final String bodyKind =
        "accessor_declaration".equals(node.kind()) ? "accessor_body" : "method_body";
    return CSharpSyntax.child(node, bodyKind).filter(body -> !body.children().isEmpty());
  }

  private static String graphName(final String owner, final SyntaxNode node) {
    final String name =
        switch (node.kind()) {
          case "constructor_declaration" -> ".ctor";
          case "finalizer_declaration" -> ".finalizer";
          case "operator_declaration" -> ".operator";
          case "accessor_declaration" -> accessorName(node);
          case "local_function_declaration" -> CSharpFlowSyntax.name(node);
          default ->
              CSharpSyntax.child(node, "member_name").map(CSharpSyntax::text).orElse("");
        };
    return (owner.isEmpty() ? "" : owner + "#") + name + "(" + arity(node) + ")";
  }

  private static String accessorName(final SyntaxNode node) {
    return "."
        + CSharpSyntax.child(node, "accessor_keyword").map(CSharpSyntax::text).orElse("accessor");
  }

  private static int arity(final SyntaxNode node) {
    return CSharpSyntax.child(node, PARAMETER_LIST)
        .map(list -> CSharpSyntax.children(list, "parameter").size())
        .orElse(0);
  }
}
