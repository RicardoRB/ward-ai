package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers every method, constructor, finalizer, operator, accessor and local function of a C# file
 * into a control-flow graph named {@code Type#name(arity)}. A lambda is part of the statement that
 * holds it. Variable accesses and flow sites are not recorded yet: the graphs carry the shape of
 * the control flow only.
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

  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final List<ControlFlowGraph> graphs = new ArrayList<>();
    visit(tree, "", graphs);
    return new ControlFlowResult(graphs, List.of());
  }

  private static void visit(
      final SyntaxNode node,
      final String owner,
      final List<ControlFlowGraph> graphs
  ) {
    final String scope = TYPES.contains(node.kind()) ? qualified(owner, node) : owner;
    if (BODIES.contains(node.kind())) {
      body(node).ifPresent(
          block ->
              graphs.add(new CSharpBodyLowering().lower(graphName(scope, node), block, node.span()))
      );
    }
    node.children().forEach(child -> visit(child, scope, graphs));
  }

  private static String qualified(final String owner, final SyntaxNode type) {
    final String name = CSharpSyntax.name(type).map(CSharpSyntax::text).orElse("");
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
          case "local_function_declaration" ->
              CSharpSyntax.name(node).map(CSharpSyntax::text).orElse("");
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
    return CSharpSyntax.child(node, "parameter_list")
        .map(list -> CSharpSyntax.children(list, "parameter").size())
        .orElse(0);
  }
}
