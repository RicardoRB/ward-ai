package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowLowering;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers every function with a body of a Rust file into a control-flow graph named {@code
 * Owner#name(arity)}, so a call can reach it. The owner is the type of the {@code impl} or the name
 * of the trait, after the inline modules around it; a free function has none. The arity does not
 * count {@code self}. The variable accesses come from the file's scope analysis. A closure is part
 * of the statement that holds it.
 */
public final class RustControlFlow implements ControlFlowLowering {
  private static final String PATH_SEPARATOR = "::";
  private static final Set<String> OTHER_BODIES =
      Set.of(
          "function_",
          "structStruct",
          "tupleStruct",
          "enumeration",
          "union_",
          "trait_",
          "inherentImpl",
          "traitImpl"
      );

  @Override
  public ControlFlowResult lower(final SourceUnit unit, final SyntaxNode tree) {
    final UnitScopes scopes = new RustScopeAnalysis(environments -> Set.of()).analyze(unit, tree);
    final Collector collector = new Collector(scopes, RustWriteEnds.of(tree));
    collector.visit(tree, "");
    return new ControlFlowResult(collector.graphs, List.of());
  }

  /** Walks the tree once, lowering each body and keeping the owner in scope. */
  private static final class Collector {
    private final UnitScopes scopes;
    private final Map<Integer, Integer> writeEnds;
    private final List<ControlFlowGraph> graphs = new ArrayList<>();

    Collector(final UnitScopes scopes, final Map<Integer, Integer> writeEnds) {
      this.scopes = scopes;
      this.writeEnds = writeEnds;
    }

    void visit(final SyntaxNode node, final String owner) {
      if (RustKinds.FUNCTION.equals(node.kind())) {
        lower(node, owner);
      }
      final String inner = ownerOf(node, owner);
      node.children().forEach(child -> visit(child, inner));
    }

    private void lower(final SyntaxNode node, final String owner) {
      final SyntaxNode body = RustTrees.child(node, RustKinds.BLOCK);
      final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
      if (body == null || name == null) {
        return;
      }
      final SyntaxNode parameters = RustTrees.child(node, "functionParameters");
      final int arity =
          parameters == null ? 0 : RustTrees.children(parameters, "functionParam").size();
      final String graph =
          (owner.isEmpty() ? "" : owner + "#") + RustTrees.name(name) + "(" + arity + ")";
      final RustAccesses accesses =
          new RustAccesses(scopes, node.span(), nested(body), writeEnds);
      graphs.add(
          new RustBodyLowering(accesses)
              .lower(
                  graph,
                  parameters == null
                      ? List.of()
                      : RustAccesses.parameters(scopes, parameters.span()),
                  body,
                  node.span()
              )
      );
    }

    /** The spans of the functions and types inside a body, which are bodies of their own. */
    private static List<Span> nested(final SyntaxNode body) {
      final List<Span> found = new ArrayList<>();
      collectNested(body, found);
      return found;
    }

    private static void collectNested(final SyntaxNode node, final List<Span> found) {
      if (OTHER_BODIES.contains(node.kind())) {
        found.add(node.span());
      } else {
        node.children().forEach(child -> collectNested(child, found));
      }
    }

    private static String ownerOf(final SyntaxNode node, final String owner) {
      final String written = nameOf(node);
      if (written.isEmpty()) {
        return owner;
      }
      return owner.isEmpty() ? written : owner + PATH_SEPARATOR + written;
    }

    private static String nameOf(final SyntaxNode node) {
      return switch (node.kind()) {
        case "module", "trait_" -> identifier(node);
        case "inherentImpl" -> typeName(RustTrees.child(node, "type_"));
        case "traitImpl" -> typeName(
            RustTrees.children(node, "type_").stream().findFirst().orElse(null)
        );
        default -> "";
      };
    }

    private static String identifier(final SyntaxNode node) {
      final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
      return name == null ? "" : RustTrees.name(name);
    }

    /** The first segment of a type path, without its generic arguments, or empty. */
    private static String typeName(final SyntaxNode type) {
      final SyntaxNode path = type == null ? null : RustTrees.find(type, "typePathSegment");
      return path == null ? "" : identifier(RustTrees.child(path, "pathIdentSegment"));
    }
  }
}
